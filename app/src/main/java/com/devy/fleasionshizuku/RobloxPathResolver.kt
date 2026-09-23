package com.devy.fleasionshizuku

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

object RobloxPathResolver {

    private val KNOWN_PACKAGES = listOf(
        "com.roblox.client",
        "com.roblox.client.beta",
        "com.roblox.client.canary",
        "com.roblox.client.vnggames",
        "com.roblox.client.amazon"
    )

    data class RobloxInstall(
        val packageName: String,
        val versionName: String,
        val externalDataDir: File?,
        val internalDataDir: String
    )

    fun findAll(ctx: Context): List<RobloxInstall> {
        val pm = ctx.packageManager
        val found = mutableListOf<RobloxInstall>()

        KNOWN_PACKAGES.forEach { pkg ->
            try {
                val info = pm.getPackageInfo(pkg, 0)
                val ext = File("/sdcard/Android/data/$pkg/files")
                found.add(
                    RobloxInstall(
                        packageName = pkg,
                        versionName = info.versionName ?: "?",
                        externalDataDir = if (ext.exists()) ext else null,
                        internalDataDir = "/data/data/$pkg"
                    )
                )
            } catch (_: PackageManager.NameNotFoundException) {}
        }
        return found
    }

    fun active(ctx: Context): RobloxInstall? =
        findAll(ctx).firstOrNull { it.packageName == "com.roblox.client" }
            ?: findAll(ctx).firstOrNull()
}
