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
            shell("rm -rf /sdcard/Android/data/com.roblox.client/cache/*", log)
            shell("rm -rf /sdcard/Android/data/com.roblox.client/files/asset_cache/*", log)
            log("Roblox HTTP cache wiped (login untouched).")
        } else {
            log("Keep-login enabled — cache untouched.")
        }

        installCaCertificate(log)
        log("Auto-setup complete.")
    }

    private fun installCaCertificate(log: (String) -> Unit) {
        val cmd = "cp /data/misc/user/0/cacerts-added/* /system/etc/security/cacerts/ 2>/dev/null; " +
                "chmod 644 /system/etc/security/cacerts/* 2>/dev/null"
        shell(cmd, log)
    }

    /**
     * Run a shell command via Shizuku using ProcessBuilder wrapped
     * through the Shizuku binder's newProcess (accessed via reflection
     * to avoid compile-time visibility issues).
     */
    fun shell(command: String, log: (String) -> Unit) {
        try {
            if (!Shizuku.pingBinder()) { log("Shizuku binder dead."); return }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                log("Shizuku permission not granted."); return
            }

            // Use reflection to call newProcess — works across Shizuku versions
            val shizukuClass = Shizuku::class.java
            val newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true

            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as? rikka.shizuku.ShizukuRemoteProcess

            if (process == null) {
                log("Failed to spawn Shizuku process.")
                return
            }

            val out = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (out.isNotBlank()) log("» $out")
            if (err.isNotBlank()) log("⚠ $err")
        } catch (t: Throwable) {
            log("Shell error: ${t.message}")
        }
    }
}
