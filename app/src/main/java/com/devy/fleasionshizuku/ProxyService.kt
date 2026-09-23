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
 * Uses Shizuku to set the device-wide HTTP proxy setting so Roblox
 * (and every other app) routes HTTP through us — without a VPN,
 * so game server connections stay direct and work normally.
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

        // Start the local HTTP proxy
        val p = AssetRewriter(this, PROXY_PORT)
        p.loadFromConfig(ConfigRepository.loadAllConfigs(this))
        p.start()
        ConfigBridge.attachRewriter(p)
        proxy = p
        isRunning = true

        // Use Shizuku to set system-wide proxy so every app uses us
        applySystemProxy(true)

        // Auto-launch Roblox after a small delay
        Thread {
            Thread.sleep(1200)
            RobloxLauncher.launchAny(this)
        }.start()
    }

    private fun stopProxy() {
        try {
            applySystemProxy(false)
            ConfigBridge.detachRewriter()
            proxy?.stop()
            proxy = null
            isRunning = false
        } catch (_: Throwable) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Set (or clear) the Android global HTTP proxy via Shizuku. */
    private fun applySystemProxy(enable: Boolean) {
        val shizuku = ShizukuManager(this)
        if (enable) {
            shizuku.shell("settings put global http_proxy 127.0.0.1:$PROXY_PORT") { logLine(it) }
            // Also route through the proxy for apps that respect it
            shizuku.shell("settings put global global_http_proxy_host 127.0.0.1") { logLine(it) }
            shizuku.shell("settings put global global_http_proxy_port $PROXY_PORT") { logLine(it) }
        } else {
            shizuku.shell("settings put global http_proxy :0") { logLine(it) }
            shizuku.shell("settings delete global global_http_proxy_host") { logLine(it) }
            shizuku.shell("settings delete global global_http_proxy_port") { logLine(it) }
        }
    }

    private fun logLine(s: String) {
        // Push to a log file the UI can tail
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
            .setContentText("Serving Roblox assets via 127.0.0.1:$PROXY_PORT")
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
