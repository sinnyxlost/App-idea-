package com.devy.fleasionshizuku

import android.content.Context
import android.content.Intent
import java.io.File

object DeepCleanupManager {

    fun wipe(
        ctx: Context,
        shizuku: ShizukuManager,
        includeFflags: Boolean,
        includeRobloxFullWipe: Boolean,
        log: (String) -> Unit
    ) {
        log("╔══════════════════════════════════════╗")
        log("║  DEEP CLEANUP — ROOT-LEVEL WIPE      ║")
        log("╚══════════════════════════════════════╝")

        killProxy(ctx, log)
        clearIptablesRules(shizuku, log)
        clearAllCerts(shizuku, log)
        unmountHostsBinds(shizuku, log)
        removeLocalTmpLeftovers(shizuku, log)
        removeOurStaging(ctx, log)
        removeRobloxInjectedSkies(ctx, shizuku, log)
        clearRobloxCaches(ctx, shizuku, log)

        if (includeRobloxFullWipe) fullRobloxWipe(ctx, shizuku, log)
        if (includeFflags) clearFflags(shizuku, log)

        log("")
        log("✓ DEEP CLEANUP COMPLETE")
        log("Reboot strongly recommended.")
    }

    private fun killProxy(ctx: Context, log: (String) -> Unit) {
        log("[1] Stopping proxy services...")
        try { ctx.stopService(Intent(ctx, ProxyService::class.java)) } catch (_: Throwable) {}
        log("    ✓ Proxy stopped")
    }

    private fun clearIptablesRules(shizuku: ShizukuManager, log: (String) -> Unit) {
        log("[2] Removing iptables redirects...")

        val destPorts = listOf(80, 443, 8081, 53127, 58443)
        val proxyPorts = listOf(8081, 53127, 58443)

        destPorts.forEach { port ->
            proxyPorts.forEach { proxyPort ->
                val cmd = "while iptables -t nat -D OUTPUT -p tcp --dport " +
                        port + " -j REDIRECT --to-ports " + proxyPort +
                        " 2>/dev/null; do :; done"
                shizuku.shellCapture(cmd)
            }
        }

        // Flush any remaining REDIRECT rules without using shell $ variables
        shizuku.shellCapture(
            "iptables -t nat -S OUTPUT 2>/dev/null | grep -i REDIRECT | " +
                    "sed 's/-A/-D/' | while read line; do iptables -t nat " +
                    "\$line 2>/dev/null; done"
        )

        log("    ✓ iptables OUTPUT chain flushed")
    }

    private fun clearAllCerts(shizuku: ShizukuManager, log: (String) -> Unit) {
        log("[3] Removing CA certificates...")

        val patterns = listOf("devy", "fleasion", "devy_fleasion")
        patterns.forEach { pat ->
            val cmd = "rm -f /system/etc/security/cacerts/*" + pat + "* 2>/dev/null; " +
                    "rm -f /data/misc/user/0/cacerts-added/*" + pat + "* 2>/dev/null; " +
                    "rm -f /data/misc/keychain/*" + pat + "* 2>/dev/null; " +
                    "rm -rf /data/misc/keychain/cacerts-added/*" + pat + "* 2>/dev/null"
            shizuku.shellCapture(cmd)
        }

        // Hash-based cert removal — pipe through sh -c so $f is expanded by shell
        val hashCmd = "sh -c 'ls /system/etc/security/cacerts/ 2>/dev/null | while read f; do " +
                "if openssl x509 -in /system/etc/security/cacerts/\$f -noout -subject 2>/dev/null | " +
                "grep -qi \"Devy\\|Fleasion\"; then rm -f /system/etc/security/cacerts/\$f; fi; done'"
        shizuku.shellCapture(hashCmd)

        log("    ✓ All Devy/Fleasion CAs removed")
    }

    private fun unmountHostsBinds(shizuku: ShizukuManager, log: (String) -> Unit) {
        log("[4] Unmounting hosts bind-mounts...")
        shizuku.shellCapture(
            "umount /system/etc/hosts 2>/dev/null; " +
                    "umount -l /system/etc/hosts 2>/dev/null; " +
                    "mount -o remount,ro /system 2>/dev/null; " +
                    "rm -f /data/local/tmp/hosts_devy 2>/dev/null; " +
                    "rm -f /data/local/tmp/hosts_merged 2>/dev/null; " +
                    "rm -f /data/local/tmp/hosts_base 2>/dev/null; " +
                    "rm -f /data/local/tmp/hosts_final 2>/dev/null"
        )
        log("    ✓ Hosts bind-mounts cleared")
    }

    private fun removeLocalTmpLeftovers(shizuku: ShizukuManager, log: (String) -> Unit) {
        log("[5] Removing /data/local/tmp leftovers...")
        shizuku.shellCapture(
            "rm -f /data/local/tmp/*devy* 2>/dev/null; " +
                    "rm -f /data/local/tmp/*fleasion* 2>/dev/null; " +
                    "rm -rf /data/local/tmp/devy* 2>/dev/null"
        )
        log("    ✓ /data/local/tmp cleaned")
    }

    private fun removeOurStaging(ctx: Context, log: (String) -> Unit) {
        log("[6] Removing our staging folders...")
        val dirs = listOf("certs", "custom_skies", "imported_configs", "fflags")
        dirs.forEach { name ->
            try {
                val f = File(ctx.filesDir, name)
                if (f.exists()) { f.deleteRecursively(); log("    ✓ Deleted ${f.absolutePath}") }
            } catch (t: Throwable) { log("    ⚠ $name: ${t.message}") }
        }
    }

    private fun removeRobloxInjectedSkies(ctx: Context, shizuku: ShizukuManager, log: (String) -> Unit) {
        log("[7] Removing injected skies...")
        val targetDirs = listOf(
            "files/assets/android/textures/sky",
            "files/assets/android/textures/skybox",
            "files/assets/android/textures/environment",
            "files/assets/android/textures",
            "files/asset_cache/sky"
        )
        RobloxPathResolver.findAll(ctx).forEach { inst ->
            val internalDir = "/data/data/" + inst.packageName
            targetDirs.forEach { rel ->
                val cmd = "rm -f " + internalDir + "/" + rel + "/custom_* 2>/dev/null; " +
                        "rm -f " + internalDir + "/" + rel + "/sky_* 2>/dev/null; " +
                        "rm -f " + internalDir + "/" + rel + "/*.devy 2>/dev/null"
                shizuku.shellCapture(cmd)
            }
            inst.externalDataDir?.let { ext ->
                try { File(ext, "custom_skies").deleteRecursively() } catch (_: Throwable) {}
            }
        }
        log("    ✓ Roblox injected skies removed")
    }

    private fun clearRobloxCaches(ctx: Context, shizuku: ShizukuManager, log: (String) -> Unit) {
        log("[8] Clearing Roblox caches...")
        RobloxPathResolver.findAll(ctx).forEach { inst ->
            val base = "/sdcard/Android/data/" + inst.packageName
            shizuku.shellCapture(
                "rm -rf " + base + "/cache/* 2>/dev/null; " +
                        "rm -rf " + base + "/files/asset_cache/* 2>/dev/null; " +
                        "rm -rf " + base + "/files/http 2>/dev/null; " +
                        "rm -rf " + base + "/files/https 2>/dev/null"
            )
            log("    ✓ ${inst.packageName} cache cleared")
        }
    }

    private fun fullRobloxWipe(ctx: Context, shizuku: ShizukuManager, log: (String) -> Unit) {
        log("[9] FULL ROBLOX DATA WIPE...")
        RobloxPathResolver.findAll(ctx).forEach { inst ->
            val ext = "/sdcard/Android/data/" + inst.packageName
            val internal = "/data/data/" + inst.packageName
            shizuku.shellCapture("rm -rf " + ext + " 2>/dev/null")
            shizuku.shellCapture(
                "rm -rf " + internal + "/cache/* 2>/dev/null; " +
                        "rm -rf " + internal + "/files/asset_cache/* 2>/dev/null; " +
                        "rm -rf " + internal + "/files/http 2>/dev/null; " +
                        "rm -rf " + internal + "/files/https 2>/dev/null"
            )
            log("    ✓ ${inst.packageName} data wiped")
        }
        log("    ⚠ You will need to re-login to Roblox.")
    }

    private fun clearFflags(shizuku: ShizukuManager, log: (String) -> Unit) {
        log("[10] Removing Devy-managed FFlags...")
        val flagPaths = listOf(
            "/sdcard/Android/data/com.roblox.client/files/fflags",
            "/sdcard/Android/data/com.roblox.client/files/ClientSettings",
            "/sdcard/Android/data/com.roblox.client/files/ClientSettings/ClientAppSettings.json"
        )
        flagPaths.forEach { p ->
            shizuku.shellCapture(
                "if [ -f " + p + ".devy_backup ]; then mv " + p + ".devy_backup " + p + "; fi"
            )
        }
        shizuku.shellCapture("rm -f /sdcard/Android/data/com.roblox.client/files/devy_fflags.json 2>/dev/null")
        log("    ✓ FFlags cleaned, originals restored")
    }
}
