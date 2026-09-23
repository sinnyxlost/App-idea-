package com.devy.fleasionshizuku

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

/**
 * WiFi-safe HTTP proxy — no CA install, no MITM, no system changes.
 *
 * Intercepts ONLY Roblox's HTTP (port 80) traffic to asset CDN IPs.
 * HTTPS (443) is untouched so game joins, matchmaking, and everything
 * else work exactly like stock Roblox.
 *
 * Trade-off: only HTTP assets get swapped. Sounds, textures, and CDN
 * files fetched over HTTP still work — the majority in most configs.
 */
class ProxyService : Service() {

    companion object {
        const val ACTION_START = "com.devy.fleasionshizuku.START"
        const val ACTION_STOP  = "com.devy.fleasionshizuku.STOP"
        const val PROXY_PORT = 8081
        const val NOTIF_ID = 4201
        const val CHANNEL_ID = "devy_proxy"
        var isRunning = false

        private val ASSET_IP_RANGES = listOf(
            "23.62.0.0/16",
            "23.34.0.0/16",
            "96.7.0.0/16",
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

        // NO CA generation, NO system trust install — that broke joins.

        val p = AssetRewriter(this, PROXY_PORT)
        p.loadFromConfig(ConfigRepository.loadAllConfigs(this))
        p.start()
        ConfigBridge.attachRewriter(p)
        proxy = p
        isRunning = true

        installRedirects()

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
            }
            logLine("→ UID $uid → 127.0.0.1:$PROXY_PORT (HTTP/80 asset CDN only)")
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
            .setContentText("HTTP asset intercept via 127.0.0.1:$PROXY_PORT")
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
