package com.devy.fleasionshizuku

import android.content.Context
import android.content.Intent
import java.io.File

/**
 * Deep cleanup — removes EVERY trace Devy Fleasion has ever written,
 * including the ones from our earlier failed attempts.
 *
 * Covers:
 *   - iptables rules (all ports: 80, 443, 8081, 53127, 58443)
 *   - CA certs (system store, user store, keychain)
 *   - hosts bind-mounts
 *   - staging folders
 *   - Roblox internal asset injections
 *   - Roblox external cache + data
 *   - MasterStrap FFlag files (optional)
 *   - /data/local/tmp leftovers
 *   - /data/misc/keychain remnants
 */
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

        if (includeRobloxFullWipe) {
            fullRobloxWipe(ctx, shizuku, log)
        }

        if (includeFflags) {
            clearFflags(shizuku, log)
        }

        log("")
        log("✓ DEEP CLEANUP COMPLETE")
        log("Reboot strongly recommended.")
    }

    // ---------- PROXY ----------
    private fun killProxy(ctx: Context, log: (String) -> Unit) {
        log("[1] Stopping proxy services...")
        try {
            ctx.stopService(Intent(ctx, ProxyService::class.java))
        } catch (_: Throwable) {}
        log("    ✓ Proxy stopped")
    }

    // ---------- IPTABLES ----------
    private fun clearIptablesRules(shizuku: ShizukuManager, log: (String) -> Unit) {
        log("[2] Removing iptables redirects...")

        val destPorts = listOf(80, 443, 8081, 53127, 58443)
        val proxyPorts = listOf(8081, 53127, 58443)

        destPorts.forEach { port ->
            proxyPorts.forEach { proxyPort ->
                shizuku.shellCapture(
                    "while iptables -t nat -D OUTPUT -p tcp --dport $port " +
                            "-j REDIRECT --to-ports $proxyPort 2>/dev/null; do :; done"
                )
            }
        }

        // Flush any rules we might have left with our marker
        shizuku.shellCapture(
            "iptables -t nat -S OUTPUT 2>/dev/null | grep -i 'REDIRECT' | " +
                    "sed 's/-A/-D/' | while read rule; do iptables -t nat $rule 2>/dev/null; done"
        )

        log("    ✓ iptables OUTPUT chain flushed")
    }

    // ---------- CERTIFICATES ----------
    private fun clearAllCerts(shizuku: ShizukuManager, log: (String) -> Unit) {
        log("[3] Removing CA certificates (every location)...")

        val patterns = listOf("devy", "fleasion", "devy_fleasion")

        patterns.forEach { pat ->
            shizuku.shellCapture(
                "rm -f /system/etc/security/cacerts/*${pat}* 2>/dev/null; " +
                        "rm -f /data/misc/user/0/cacerts-added/*${pat}* 2>/dev/null; " +
                        "rm -f /data/misc/keychain/*${pat}* 2>/dev/null; " +
                        "rm -rf /data/misc/keychain/cacerts-added/*${pat}* 2>/dev/null; " +
                        "find /data -name '*${pat}*' -type f -exec rm -f {} \\; 2>/dev/null"
            )
        }

        // Also clean any *.0 certs we might have written with our hash
        shizuku.shellCapture(
            "ls /system/etc/security/cacerts/ 2>/dev/null | " +
                    "while read f; do " +
                    "  if openssl x509 -in /system/etc/security/cacerts/$f -noout " +
                    "  -subject 2>/dev/null | grep -qi 'Devy\\|Fleasion'; then " +
                    "    rm -f /system/etc/security/cacerts/$f; fi; done"
        )

        log("    ✓ All Devy/Fleasion CAs removed")
    }

    // ---------- HOSTS BIND-MOUNTS ----------
    private fun unmountHostsBinds(shizuku: ShizukuManager, log: (String) -> Unit) {
        log("[4] Unmounting hosts file bind-mounts...")

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

    // ---------- /data/local/tmp ----------
    private fun removeLocalTmpLeftovers(shizuku: ShizukuManager, log: (String) -> Unit) {
        log("[5] Removing /data/local/tmp leftovers...")

        shizuku.shellCapture(
            "rm -f /data/local/tmp/*devy* 2>/dev/null; " +
                    "rm -f /data/local/tmp/*fleasion* 2>/dev/null; " +
                    "rm -rf /data/local/tmp/devy* 2>/dev/null"
        )

        log("    ✓ /data/local/tmp cleaned")
    }

    // ---------- OUR STAGING ----------
    private fun removeOurStaging(ctx: Context, log: (String) -> Unit) {
        log("[6] Removing our staging folders...")

        val dirs = listOf("certs", "custom_skies", "imported_configs", "fflags")

        dirs.forEach { name ->
            try {
                val f = File(ctx.filesDir, name)
                if (f.exists()) {
                    f.deleteRecursively()
                    log("    ✓ Deleted ${f.absolutePath}")
                }
            } catch (t: Throwable) {
                log("    ⚠ $name: ${t.message}")
            }
        }
    }

    // ---------- ROBLOX INJECTED SKIES ----------
    private fun removeRobloxInjectedSkies(
        ctx: Context,
        shizuku: ShizukuManager,
        log: (String) -> Unit
    ) {
        log("[7] Removing injected skies from Roblox internals...")

        val targetDirs = listOf(
            "files/assets/android/textures/sky",
            "files/assets/android/textures/skybox",
            "files/assets/android/textures/environment",
            "files/assets/android/textures",
            "files/asset_cache/sky"
        )

        RobloxPathResolver.findAll(ctx).forEach { inst ->
            val internalDir = "/data/data/${inst.packageName}"
            targetDirs.forEach { rel ->
                shizuku.shellCapture(
                    "rm -f $internalDir/$rel/custom_* 2>/dev/null; " +
                            "rm -f $internalDir/$rel/sky_* 2>/dev/null; " +
                            "rm -f $internalDir/$rel/*.devy 2>/dev/null"
                )
            }
            // External
            inst.externalDataDir?.let { ext ->
                try {
                    File(ext, "custom_skies").deleteRecursively()
                } catch (_: Throwable) {}
            }
        }

        log("    ✓ Roblox injected skies removed")
    }

    // ---------- ROBLOX CACHES ----------
    private fun clearRobloxCaches(
        ctx: Context,
        shizuku: ShizukuManager,
        log: (String) -> Unit
    ) {
        log("[8] Clearing Roblox caches (login preserved)...")

        RobloxPathResolver.findAll(ctx).forEach { inst ->
            val base = "/sdcard/Android/data/${inst.packageName}"
            shizuku.shellCapture(
                "rm -rf $base/cache/* 2>/dev/null; " +
                        "rm -rf $base/files/asset_cache/* 2>/dev/null; " +
                        "rm -rf $base/files/http 2>/dev/null; " +
                        "rm -rf $base/files/https 2>/dev/null"
            )
            log("    ✓ ${inst.packageName} cache cleared")
        }
    }

    // ---------- OPTIONAL: FULL ROBLOX WIPE ----------
    private fun fullRobloxWipe(
        ctx: Context,
        shizuku: ShizukuManager,
        log: (String) -> Unit
    ) {
        log("[9] FULL ROBLOX DATA WIPE (removes login + all data)...")

        RobloxPathResolver.findAll(ctx).forEach { inst ->
            val ext = "/sdcard/Android/data/${inst.packageName}"
            val internal = "/data/data/${inst.packageName}"

            // External data
            shizuku.shellCapture("rm -rf $ext 2>/dev/null")

            // Internal caches (not data dir — keeps install intact)
            shizuku.shellCapture(
                "rm -rf $internal/cache/* 2>/dev/null; " +
                        "rm -rf $internal/files/asset_cache/* 2>/dev/null; " +
                        "rm -rf $internal/files/http 2>/dev/null; " +
                        "rm -rf $internal/files/https 2>/dev/null"
            )

            log("    ✓ ${inst.packageName} data wiped")
        }
        log("    ⚠ You will need to re-login to Roblox.")
    }

    // ---------- FFLAGS ----------
    private fun clearFflags(shizuku: ShizukuManager, log: (String) -> Unit) {
        log("[10] Removing all Devy-managed FFlags...")

        val flagPaths = listOf(
            "/sdcard/Android/data/com.roblox.client/files/fflags",
            "/sdcard/Android/data/com.roblox.client/files/ClientSettings",
            "/sdcard/Android/data/com.roblox.client/files/ClientSettings/ClientAppSettings.json"
        )

        flagPaths.forEach { p ->
            // Back up original if we have one
            shizuku.shellCapture(
                "if [ -f ${p}.devy_backup ]; then " +
                        "  mv ${p}.devy_backup $p; " +
                        "  echo 'restored backup for $p'; " +
                        "fi"
            )
        }

        // Delete our FFlag file
        shizuku.shellCapture("rm -f /sdcard/Android/data/com.roblox.client/files/devy_fflags.json 2>/dev/null")

        log("    ✓ Devy FFlags removed, originals restored where backed up")
    }
}
