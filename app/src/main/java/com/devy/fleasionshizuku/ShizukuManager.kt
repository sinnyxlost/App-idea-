package com.devy.fleasionshizuku

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

class ShizukuManager(private val ctx: Context) {

    fun runAutoSetup(keepLogin: Boolean, log: (String) -> Unit) {
        shell("pm grant ${ctx.packageName} android.permission.POST_NOTIFICATIONS", log)
        shell("pm grant ${ctx.packageName} android.permission.READ_EXTERNAL_STORAGE", log)
        shell("cmd deviceidle whitelist +${ctx.packageName}", log)
        shell("cmd appops set ${ctx.packageName} RUN_IN_BACKGROUND allow", log)
        shell("cmd appops set ${ctx.packageName} RUN_ANY_IN_BACKGROUND allow", log)

        if (!keepLogin) {
            shell("rm -rf /sdcard/Android/data/com.roblox.client/cache/* 2>/dev/null", log)
            shell("rm -rf /sdcard/Android/data/com.roblox.client/files/asset_cache/* 2>/dev/null", log)
            log("Roblox HTTP cache wiped.")
        }
        log("Auto-setup complete. Wi-Fi / system network untouched.")
    }

    /** Run a shell command and pipe its output to a log callback. */
    fun shell(command: String, log: (String) -> Unit) {
        shellCapture(command)?.let { if (it.isNotBlank()) log("» $it") }
    }

    /** Run a shell command and return the combined stdout+stderr. */
    fun shellCapture(command: String): String? {
        return try {
            if (!Shizuku.pingBinder()) return null
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return null

            val cls = Shizuku::class.java
            val method = cls.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true

            val proc = method.invoke(
                null, arrayOf("sh", "-c", command), null, null
            ) as? rikka.shizuku.ShizukuRemoteProcess ?: return null

            val out = proc.inputStream.bufferedReader().readText()
            val err = proc.errorStream.bufferedReader().readText()
            proc.waitFor()
            (out + if (err.isNotBlank()) "\n⚠ $err" else "").trim()
        } catch (_: Throwable) { null }
    }
}
