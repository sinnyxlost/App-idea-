package com.devy.fleasionshizuku

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

/**
 * Redirects ONLY Roblox's asset delivery traffic to our local HTTPS proxy.
 *
 * Wi-Fi safe: no system proxy, no VPN, no /etc/hosts, no DNS.
 * Only iptables rules filtered by Roblox UID + destination IPs.
 */
class ProxyService : Service() {

    companion object {
        const val ACTION_START = "com.devy.fleasionshizuku.START"
        const val ACTION_STOP  = "com.devy.fleasionshizuku.STOP"
        const val PROXY_PORT = 8081
        const val NOTIF_ID = 4201
        const val CHANNEL_ID = "devy_proxy"
        var isRunning = false

        // Roblox asset delivery IP ranges — only these get redirected
        // (Roblox CDN / rbxcdn / assetdelivery)
        private val ASSET_IP_RANGES = listOf(
            "23.62.0.0/16",     // Roblox CDN Akamai
            "23.34.0.0/16",     // Roblox CDN backup
            "104.16.0.0/12",    // Cloudflare
            "172.64.0.0/13",    // Cloudflare
            "96.7.0.0/16",      // rbxcdn
            "45.15.72.0/22",
            "45.15.76.0/22",
            "45.15.80.0/22",
            "45.15.84.0/22",
            "45.15.88.0/22"
        )
    }

    private var proxy: AssetRewriter? = null
    private val shizuku by lazy { ShizukuManager(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP  -> stopProxy()
        }
        return START_STICKY
    }

    private fun start() {
        startForegroundNotification()

        // 1. Ensure CA + server cert + keystore exist before starting proxy
        val keystore = CaGenerator.ensureEverything(shizuku, filesDir) { logLine(it) }
        if (keystore == null) logLine("⚠ Keystore generation failed — HTTPS will fall back to HTTP")
        else logLine("→ Keystore ready at ${keystore.absolutePath}")

        // 2. Install CA into system trust store so Roblox trusts us
        installCaIntoSystemTrust()

        // 3. Start the local HTTPS proxy
        val p = AssetRewriter(this, PROXY_PORT)
        p.loadFromConfig(ConfigRepository.loadAllConfigs(this))
        p.start()
        ConfigBridge.attachRewriter(p)
        proxy = p
        isRunning = true

        // 4. iptables — Roblox UID only
        installRedirects()

        // 5. Launch Roblox
        Thread {
            Thread.sleep(1500)
            RobloxLauncher.launchAny(this)
        }.start()
    }

    private fun stopProxy() {
        try {
            removeRedirects()
            ConfigBridge.detachRewriter()
            proxy?.stop()
            proxy = null
            isRunning = false
        } catch (_: Throwable) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Copy our CA into the system trust store via Shizuku. */
    private fun installCaIntoSystemTrust() {
        val caCert = File(filesDir, "certs/ca.pem")
        if (!caCert.exists()) { logLine("⚠ No CA file"); return }

        val hash = shizuku.shellCapture(
            "openssl x509 -inform PEM -subject_hash_old -in ${caCert.absolutePath} | head -1"
        ) ?: return
        val certName = "${hash.trim()}.0"

        // Path 1: /system/etc/security/cacerts (works with rw system)
        shizuku.shell(
            "cp ${caCert.absolutePath} /system/etc/security/cacerts/$certName 2>/dev/null && " +
                    "chmod 644 /system/etc/security/cacerts/$certName 2>/dev/null && " +
                    "echo 'installed to /system'",
            { logLine("CA: $it") }
        )

        // Path 2: user CA store
        shizuku.shell(
            "mkdir -p /data/misc/user/0/cacerts-added && " +
                    "cp ${caCert.absolutePath} /data/misc/user/0/cacerts-added/$certName 2>/dev/null && " +
                    "chmod 644 /data/misc/user/0/cacerts-added/$certName 2>/dev/null && " +
                    "echo 'installed to user store'",
            { logLine("CA: $it") }
        )
    }

    /**
     * Redirect Roblox's TCP 80 + 443 → our proxy, but ONLY for the
     * asset CDN IP ranges. Game servers stay direct.
     */
    private fun installRedirects() {
        val pkgUids = RobloxPathResolver.findAll(this).mapNotNull { inst ->
            try { packageManager.getApplicationInfo(inst.packageName, 0).uid }
            catch (_: Throwable) { null }
        }.distinct()

        if (pkgUids.isEmpty()) {
            logLine("⚠ No Roblox packages found.")
            return
        }

        removeRedirects()

        pkgUids.forEach { uid ->
            ASSET_IP_RANGES.forEach { cidr ->
                // HTTP for CDN
                shizuku.shell(
                    "iptables -t nat -A OUTPUT -m owner --uid-owner $uid " +
                            "-d $cidr -p tcp --dport 80 " +
                            "-j REDIRECT --to-ports $PROXY_PORT",
                    { }
                )
                // HTTPS for CDN
                shizuku.shell(
                    "iptables -t nat -A OUTPUT -m owner --uid-owner $uid " +
                            "-d $cidr -p tcp --dport 443 " +
                            "-j REDIRECT --to-ports $PROXY_PORT",
                    { }
                )
            }
            logLine("→ UID $uid → 127.0.0.1:$PROXY_PORT (asset CDN only)")
        }
    }

    private fun removeRedirects() {
        for (port in listOf(80, 443)) {
            shizuku.shell(
                "while iptables -t nat -D OUTPUT -p tcp --dport $port " +
                        "-j REDIRECT --to-ports $PROXY_PORT 2>/dev/null; do :; done",
                { }
            )
        }
        logLine("← Redirects cleared. Wi-Fi untouched.")
    }

    private fun logLine(s: String) {
        val log = File(filesDir, "proxy.log")
        try { log.appendText("$s\n") } catch (_: Throwable) {}
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID, "Devy Fleasion Proxy",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(chan)
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Devy Fleasion Proxy")
            .setContentText("Intercepting Roblox asset CDN via 127.0.0.1:$PROXY_PORT")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    override fun onDestroy() {
        stopProxy()
        super.onDestroy()
    }
}
