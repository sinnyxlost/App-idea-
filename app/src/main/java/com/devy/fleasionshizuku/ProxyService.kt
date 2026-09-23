package com.devy.fleasionshizuku

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File

/**
 * Runs the local HTTP proxy in a foreground service.
 *
 * Does NOT modify system Wi-Fi or global HTTP proxy settings.
 * Instead, uses Shizuku (ADB-level) to add iptables rules that redirect
 * ONLY Roblox's outbound TCP:443 to our local proxy. Every other app
 * and every other port uses the normal connection — Wi-Fi stays intact.
 */
class ProxyService : Service() {

    companion object {
        const val ACTION_START = "com.devy.fleasionshizuku.START"
        const val ACTION_STOP  = "com.devy.fleasionshizuku.STOP"
        const val PROXY_PORT = 8081
        const val NOTIF_ID = 4201
        const val CHANNEL_ID = "devy_proxy"

        var isRunning = false
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

        // 1. Start local proxy
        val p = AssetRewriter(this, PROXY_PORT)
        p.loadFromConfig(ConfigRepository.loadAllConfigs(this))
        p.start()
        ConfigBridge.attachRewriter(p)
        proxy = p
        isRunning = true

        // 2. Install iptables redirect rules ONLY for Roblox UIDs
        installIptablesRules()

        // 3. Auto-launch Roblox
        Thread {
            Thread.sleep(1200)
            RobloxLauncher.launchAny(this)
        }.start()
    }

    private fun stopProxy() {
        try {
            removeIptablesRules()
            ConfigBridge.detachRewriter()
            proxy?.stop()
            proxy = null
            isRunning = false
        } catch (_: Throwable) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Redirect only Roblox's TCP:80 and TCP:443 to our local proxy.
     * All other apps and ports are untouched — Wi-Fi works normally.
     */
    private fun installIptablesRules() {
        val pkgUids = RobloxPathResolver.findAll(this).mapNotNull { inst ->
            try {
                packageManager.getApplicationInfo(inst.packageName, 0).uid
            } catch (_: Throwable) { null }
        }.distinct()

        if (pkgUids.isEmpty()) {
            logLine("⚠ No Roblox packages found — skipping iptables rules.")
            return
        }

        // Clean any old rules first
        removeIptablesRules()

        pkgUids.forEach { uid ->
            // Redirect HTTP (80) and HTTPS (443) from Roblox UID → local proxy
            shizuku.shell(
                "iptables -t nat -A OUTPUT -m owner --uid-owner $uid -p tcp --dport 80 " +
                        "-j REDIRECT --to-ports $PROXY_PORT",
                { logLine("$it") }
            )
            shizuku.shell(
                "iptables -t nat -A OUTPUT -m owner --uid-owner $uid -p tcp --dport 443 " +
                        "-j REDIRECT --to-ports $PROXY_PORT",
                { logLine("$it") }
            )
            logLine("→ Redirected Roblox UID $uid → 127.0.0.1:$PROXY_PORT")
        }
    }

    /** Remove all our iptables rules — safe to call multiple times. */
    private fun removeIptablesRules() {
        // Fire-and-forget removal; ignore errors if rules don't exist
        shizuku.shell(
            "iptables -t nat -D OUTPUT -p tcp --dport 80 -j REDIRECT --to-ports $PROXY_PORT 2>/dev/null || true",
            { }
        )
        shizuku.shell(
            "iptables -t nat -D OUTPUT -p tcp --dport 443 -j REDIRECT --to-ports $PROXY_PORT 2>/dev/null || true",
            { }
        )
        logLine("← Cleared iptables redirects.")
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
            .setContentText("Redirecting Roblox assets via 127.0.0.1:$PROXY_PORT")
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
