package com.devy.fleasionshizuku

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object SkyInstaller {

    private const val SKY_DIR = "custom_skies"

    fun appSkyDir(ctx: Context): File {
        val d = File(ctx.filesDir, SKY_DIR)
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun importSky(ctx: Context, uri: Uri, nameHint: String = "sky.png"): File? {
        return try {
            val out = File(appSkyDir(ctx), nameHint)
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { input.copyTo(it) }
            }
            out
        } catch (_: Throwable) { null }
    }

    fun installToRoblox(
        ctx: Context, skyFile: File,
        shizuku: ShizukuManager, log: (String) -> Unit
    ): Boolean {
        val installs = RobloxPathResolver.findAll(ctx)
        if (installs.isEmpty()) { log("No Roblox install found."); return false }

        var ok = false
        installs.forEach { inst ->
            inst.externalDataDir?.let { ext ->
                try {
                    val skyDir = File(ext, "custom_skies").apply { mkdirs() }
                    val dest = File(skyDir, skyFile.name)
                    skyFile.copyTo(dest, overwrite = true)
                    log("✓ External sky → ${dest.absolutePath}")
                    ok = true
                } catch (t: Throwable) {
                    log("✗ External copy failed: ${t.message}")
                }
            }

            val internalSky = "${inst.internalDataDir}/files/custom_skies"
            shizuku.shell("mkdir -p $internalSky", log)
            shizuku.shell("cp ${skyFile.absolutePath} $internalSky/", log)
            shizuku.shell("chmod 644 $internalSky/${skyFile.name}", log)
            log("→ Attempted internal push: $internalSky")
        }
        return ok
    }
}
