package com.devy.fleasionshizuku

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

/**
 * WiFi-safe proxy.
 *
 * ⚠️  NEVER touches:
 *   - Wi-Fi settings
 *   - System HTTP proxy (settings put global http_proxy)
 *   - /etc/hosts
 *   - DNS configuration
 *   - VPN routes
 *   - Any other app's traffic
 *
 * ✅  ONLY does:
 *   - iptables NAT rule, filtered by Roblox UID + asset CDN IP range
 *
 * Result: game joins work, assets swap via HTTPS MITM, Wi-Fi is stock.
 */
class ProxyService : Service() {

    companion object {
        const val ACTION_START = "com.devy.fleasionshizuku.START"
        const val ACTION_STOP  = "com.devy.fleasionshizuku.STOP"
        const val PROXY_PORT = 8081
        const val NOTIF_ID = 4201
        const val CHANNEL_ID = "devy_proxy"
        var isRunning = false

        // Roblox asset CDN IP ranges ONLY.
        // Game-join servers / matchmaking / live game — NOT in this list.
        private val ASSET_IP_RANGES = listOf(
            "23.62.0.0/16",     // Roblox asset CDN (Akamai)
            "23.34.0.0/16",     // Roblox asset CDN backup
            "96.7.0.0/16",      // rbxcdn
            "45.15.72.0/22",    // Roblox asset delivery blocks
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

        // 1. CA + keystore for HTTPS MITM
        val ks = CaGenerator.ensureEverything(shizuku, filesDir) { logLine(it) }
        if (ks == null) logLine("⚠ Keystore generation failed.")
        else logLine("→ Keystore ready: ${ks.absolutePath}")

        // 2. Install our CA into system trust so Roblox trusts the MITM
        installCaIntoSystemTrust()

        // 3. Local HTTPS proxy
        val p = AssetRewriter(this, PROXY_PORT)
        p.loadFromConfig(ConfigRepository.loadAllConfigs(this))
        p.start()
        ConfigBridge.attachRewriter(p)
        proxy = p
        isRunning = true

        // 4. iptables redirect — ONLY asset CDN IP ranges, Roblox UID only
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

    /** Copy our CA into system + user trust stores via Shizuku. */
    private fun installCaIntoSystemTrust() {
        val caCert = File(filesDir, "certs/ca.pem")
        if (!caCert.exists()) { logLine("⚠ No CA file"); return }

        val hash = shizuku.shellCapture(
            "openssl x509 -inform PEM -subject_hash_old -in ${caCert.absolutePath} | head -1"
        ) ?: return
        val certName = "${hash.trim()}.0"

        shizuku.shell(
            "cp ${caCert.absolutePath} /system/etc/security/cacerts/$certName 2>/dev/null && " +
                    "chmod 644 /system/etc/security/cacerts/$certName 2>/dev/null && echo ok",
            { logLine("CA /system: $it") }
        )
        shizuku.shell(
            "mkdir -p /data/misc/user/0/cacerts-added && " +
                    "cp ${caCert.absolutePath} /data/misc/user/0/cacerts-added/$certName 2>/dev/null && " +
                    "chmod 644 /data/misc/user/0/cacerts-added/$certName 2>/dev/null && echo ok",
            { logLine("CA user: $it") }
        )
    }

    /**
     * Redirect Roblox's TCP 80 + 443 to our proxy ONLY when the
     * destination IP is in an asset CDN range.
     *
     * No system proxy. No hosts file. No DNS. No VPN.
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
                shizuku.shell(
                    "iptables -t nat -A OUTPUT -m owner --uid-owner $uid " +
                            "-d $cidr -p tcp --dport 80 " +
                            "-j REDIRECT --to-ports $PROXY_PORT",
                    { }
                )
                shizuku.shell(
                    "iptables -t nat -A OUTPUT -m owner --uid-owner $uid " +
                            "-d $cidr -p tcp --dport 443 " +
                            "-j REDIRECT --to-ports $PROXY_PORT",
                    { }
                )
            }
            logLine("→ UID $uid → 127.0.0.1:$PROXY_PORT (asset CDN IPs only)")
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
        logLine("← Redirects cleared. Wi-Fi never modified.")
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
            .setContentText("Intercepting asset CDN via 127.0.0.1:$PROXY_PORT")
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
