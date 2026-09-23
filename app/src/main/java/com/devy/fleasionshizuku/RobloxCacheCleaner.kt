package com.devy.fleasionshizuku

import android.content.Context

object RobloxCacheCleaner {

    fun clearOnly(
        ctx: Context,
        shizuku: ShizukuManager,
        log: (String) -> Unit
    ) {
        val installs = RobloxPathResolver.findAll(ctx)
        if (installs.isEmpty()) {
            log("✗ No Roblox packages found.")
            return
        }
        installs.forEach { inst ->
            val ext = "/sdcard/Android/data/" + inst.packageName
            shizuku.shellCapture("rm -rf " + ext + "/cache/* 2>/dev/null")
            shizuku.shellCapture("rm -rf " + ext + "/files/asset_cache/* 2>/dev/null")
            shizuku.shellCapture("rm -rf " + ext + "/files/http 2>/dev/null")
            log("✓ Cleared ${inst.packageName} cache (login safe)")
        }
        log("Reboot recommended before playing.")
    }
}
