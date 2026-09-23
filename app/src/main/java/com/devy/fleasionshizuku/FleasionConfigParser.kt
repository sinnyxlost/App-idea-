package com.devy.fleasionshizuku

import com.google.gson.Gson
import com.google.gson.JsonObject

object FleasionConfigParser {

    private val gson = Gson()

    fun parse(raw: String, sourceName: String): FleasionConfig {
        val root = gson.fromJson(raw, JsonObject::class.java)
        val config = FleasionConfig(name = sourceName)

        root.getAsJsonArray("replacements")?.forEach { el ->
            val obj = el.asJsonObject
            config.rules.add(
                FleasionRule(
                    matchId = obj.get("assetId")?.asString
                        ?: obj.get("id")?.asString
                        ?: obj.get("from")?.asString ?: "",
                    replacementPath = obj.get("path")?.asString
                        ?: obj.get("to")?.asString
                )
            )
        }

        listOf("skybox", "sky", "textures", "texture", "sounds", "sound", "meshes", "mesh")
            .forEach { category ->
                root.getAsJsonObject(category)?.entrySet()?.forEach { (key, value) ->
                    config.rules.add(FleasionRule(key, value.asString))
                }
            }

        root.entrySet().forEach { (k, v) ->
            if (k.all { it.isDigit() } && v.isJsonPrimitive) {
                config.rules.add(FleasionRule(k, v.asString))
            }
        }
        return config
    }
}

data class FleasionConfig(
    val name: String,
    val rules: MutableList<FleasionRule> = mutableListOf()
)

data class FleasionRule(
    val matchId: String,
    val replacementPath: String? = null,
    val replacementBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean =
        other is FleasionRule && other.matchId == matchId
    override fun hashCode(): Int = matchId.hashCode()
}
