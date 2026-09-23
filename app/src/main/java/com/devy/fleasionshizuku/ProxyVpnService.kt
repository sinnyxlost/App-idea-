package com.devy.fleasionshizuku

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class ProxyVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.devy.fleasionshizuku.START"
        const val PROXY_PORT = 8081
        const val NOTIF_ID = 4201
        const val CHANNEL_ID = "devy_proxy"
        var autoLaunch = true
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var proxyThread: Thread? = null
    private lateinit var localProxy: AssetRewriter

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            startForegroundNotification()
            setupVpn()
            startLocalProxy()
            localProxy.loadFromConfig(ConfigRepository.loadAllConfigs(this))

            if (autoLaunch) {
                Thread {
                    Thread.sleep(1200)
                    RobloxLauncher.launchAny(this)
                }.start()
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID, "Devy Roblox Proxy",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(chan)
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Devy Fleasion Proxy")
            .setContentText("Routing Roblox assets through 127.0.0.1:$PROXY_PORT")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    private fun setupVpn() {
        val builder = Builder()
            .setSession("DevyFleasion")
            .addAddress("10.8.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .setBlocking(true)

        RobloxPathResolver.findAll(this).forEach { inst ->
            try { builder.addAllowedApplication(inst.packageName) } catch (_: Throwable) {}
        }

        vpnInterface = builder.establish()

        proxyThread = Thread {
            val input = FileInputStream(vpnInterface!!.fileDescriptor)
            val output = FileOutputStream(vpnInterface!!.fileDescriptor)
            val buffer = ByteBuffer.allocate(32767)
            try {
                while (!Thread.interrupted()) {
                    buffer.clear()
                    val len = input.channel.read(buffer)
                    if (len > 0) {
                        buffer.flip()
                        val result = localProxy.processPacket(buffer, len)
                        output.write(result)
                    }
                }
            } catch (_: Throwable) {}
        }.also { it.start() }
    }

    private fun startLocalProxy() {
        localProxy = AssetRewriter(this, PROXY_PORT)
        localProxy.start()
    }

    override fun onDestroy() {
        try {
            proxyThread?.interrupt()
            localProxy.stop()
            vpnInterface?.close()
        } catch (_: Throwable) {}
        super.onDestroy()
    }
}
