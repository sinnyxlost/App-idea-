package com.devy.fleasionshizuku

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Manages Roblox FFlags compatible with MasterStrap and other
 * non-root FFlag injectors.
 *
 * Writes to Roblox's ClientAppSettings.json — the same file MasterStrap
 * uses. Any FFlag set there gets read by Roblox on next launch.
 */
object FFlagManager {

    private const val FFLAG_TARGETS = "files/ClientSettings/ClientAppSettings.json"

    // Common FFlags people actually use
    val KNOWN_FLAGS = listOf(
        "DFIntTaskSchedulerTargetFps" to "240",
        "FFlagTaskSchedulerLimitTargetFpsTo2402" to "True",
        "DFIntDebugFRMQualityLevelOverride" to "1",
        "DFFlagDebugPauseVoxelizer" to "True",
        "FFlagFixGraphicsQuality" to "True",
        "DFIntDebugRestrictGCDistance" to "1",
        "FFlagDisablePostFx" to "False",
        "FIntFRMMinGrassDistance" to "0",
        "FIntFRMMaxGrassDistance" to "0",
        "FIntRenderGrassDetailStrands" to "0",
        "FIntRenderShadowIntensity" to "0",
        "FFlagNewLightAttenuation" to "True",
        "DFIntMaxFrameBufferSize" to "4",
        "FFlagDebugSkyGray" to "False",
        "FFlagDebugDisableSkyboxes" to "False",
        "FIntSkyReflections" to "1",
        "DFIntTextureCompositorActiveJobs" to "0",
        "FFlagCommitToGraphicsQualityFix" to "True"
    )

    fun fflagPath(ctx: Context): File? {
        val install = RobloxPathResolver.active(ctx) ?: return null
        val base = install.externalDataDir ?: return null
        val dir = File(base, "ClientSettings").apply { mkdirs() }
        return File(dir, "ClientAppSettings.json")
    }

    fun readAll(ctx: Context): Map<String, String> {
        val f = fflagPath(ctx) ?: return emptyMap()
        if (!f.exists()) return emptyMap()
        return try {
            val json = JSONObject(f.readText())
            json.keys().asSequence().associateWith { json.getString(it) }
        } catch (_: Throwable) { emptyMap() }
    }

    fun writeFlags(
        ctx: Context,
        flags: Map<String, String>,
        shizuku: ShizukuManager,
        log: (String) -> Unit
    ) {
        val target = fflagPath(ctx) ?: run {
            log("✗ Roblox install not found"); return
        }

        // Backup original
        if (target.exists() && !File(target.absolutePath + ".devy_backup").exists()) {
            target.copyTo(File(target.absolutePath + ".devy_backup"), overwrite = false)
            log("✓ Backed up original ClientAppSettings.json")
        }

        // Merge with existing
        val merged = readAll(ctx).toMutableMap()
        flags.forEach { (k, v) -> merged[k] = v }

        val json = JSONObject()
        merged.forEach { (k, v) -> json.put(k, v) }

        try {
            target.writeText(json.toString(2))
            log("✓ Wrote ${flags.size} FFlags to ${target.absolutePath}")
        } catch (t: Throwable) {
            log("✗ Write failed: ${t.message}")
        }
    }

    fun removeAll(ctx: Context, shizuku: ShizukuManager, log: (String) -> Unit) {
        val target = fflagPath(ctx) ?: return
        val backup = File(target.absolutePath + ".devy_backup")
        if (backup.exists()) {
            backup.copyTo(target, overwrite = true)
            log("✓ Restored original from backup")
        } else if (target.exists()) {
            target.delete()
            log("✓ Deleted our FFlags file")
        }
    }
}
