package com.devy.fleasionshizuku

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

class ShizukuManager(private val ctx: Context) {

    fun runAutoSetup(keepLogin: Boolean, log: (String) -> Unit) {
        // Grant runtime permissions
        shell("pm grant ${ctx.packageName} android.permission.POST_NOTIFICATIONS", log)
        shell("pm grant ${ctx.packageName} android.permission.READ_EXTERNAL_STORAGE", log)

        // Keep us alive in background
        shell("cmd deviceidle whitelist +${ctx.packageName}", log)
        shell("cmd appops set ${ctx.packageName} RUN_IN_BACKGROUND allow", log)
        shell("cmd appops set ${ctx.packageName} RUN_ANY_IN_BACKGROUND allow", log)

        // Optional: purge Roblox HTTP cache (login-safe)
        if (!keepLogin) {
            shell("rm -rf /sdcard/Android/data/com.roblox.client/cache/* 2>/dev/null", log)
            shell("rm -rf /sdcard/Android/data/com.roblox.client/files/asset_cache/* 2>/dev/null", log)
            log("Roblox HTTP cache wiped.")
        }

        log("Auto-setup complete. Wi-Fi settings untouched.")
    }

    fun shell(command: String, log: (String) -> Unit) {
        try {
            if (!Shizuku.pingBinder()) { log("Shizuku binder dead."); return }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                log("Shizuku permission not granted."); return
            }

            // Use reflection for cross-version compatibility
            val cls = Shizuku::class.java
            val method = cls.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true

            val proc = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as? rikka.shizuku.ShizukuRemoteProcess ?: run {
                log("Shizuku process spawn failed.")
                return
            }

            val out = proc.inputStream.bufferedReader().readText()
            val err = proc.errorStream.bufferedReader().readText()
            proc.waitFor()
            if (out.isNotBlank()) log("» $out")
            if (err.isNotBlank()) log("⚠ $err")
        } catch (t: Throwable) {
            log("Shell error: ${t.message}")
        }
    }
}
