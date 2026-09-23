package com.devy.fleasionshizuku

import android.content.Context
import java.io.File

object ConfigRepository {

    private val SEARCH_DIRS = listOf(
        "/sdcard/Fleasion/configs",
        "/sdcard/Android/data/com.fleasion/files/configs",
        "/sdcard/Download/Fleasion"
    )

    fun loadAllConfigs(ctx: Context): List<FleasionConfig> {
        val configs = mutableListOf<FleasionConfig>()

        SEARCH_DIRS.forEach { path ->
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles { f -> f.extension in listOf("json", "json5", "txt") }
                    ?.forEach { f ->
                        try {
                            configs.add(FleasionConfigParser.parse(f.readText(), f.name))
                        } catch (_: Throwable) {}
                    }
            }
        }

        try {
            ctx.assets.list("configs")?.forEach { name ->
                ctx.assets.open("configs/$name").use { stream ->
                    configs.add(
                        FleasionConfigParser.parse(
                            stream.bufferedReader().readText(), name
                        )
                    )
                }
            }
        } catch (_: Throwable) {}

        return configs
    }
}
