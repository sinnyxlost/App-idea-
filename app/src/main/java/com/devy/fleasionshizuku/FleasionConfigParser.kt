package com.devy.fleasionshizuku

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonElement

object FleasionConfigParser {

    private val gson = Gson()

    fun parse(raw: String, sourceName: String): FleasionConfig {
        val root = try {
            gson.fromJson(raw, JsonObject::class.java)
        } catch (t: Throwable) {
            try {
                val arr = gson.fromJson(raw, JsonArray::class.java)
                val cfg = FleasionConfig(name = sourceName)
                arr.forEach { walk(it, cfg) }
                return cfg
            } catch (_: Throwable) {
                return FleasionConfig(name = sourceName)
            }
        }

        val config = FleasionConfig(name = sourceName)

        // Format A: Fleasion's real "replacement_rules" tree
        root.getAsJsonArray("replacement_rules")?.forEach { el ->
            walk(el, config)
        }

        // Format B: legacy "replacements"
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

        // Format C: categorized maps
        listOf("skybox","sky","textures","texture","sounds","sound","meshes","mesh")
            .forEach { category ->
                root.getAsJsonObject(category)?.entrySet()?.forEach { (key, value) ->
                    config.rules.add(FleasionRule(key, value.asString))
                }
            }

        // Format D: flat { "id": "path" }
        root.entrySet().forEach { (k, v) ->
            if (k.all { it.isDigit() } && v.isJsonPrimitive) {
                config.rules.add(FleasionRule(k, v.asString))
            }
        }

        return config
    }

    /** Recursively walk a Fleasion rule node (group or leaf). */
    private fun walk(el: JsonElement, cfg: FleasionConfig) {
        if (!el.isJsonObject) return
        val obj = el.asJsonObject

        // Skip disabled rules
        val enabled = obj.get("enabled")?.asBoolean ?: true
        if (!enabled) return

        // Recurse into groups first
        obj.getAsJsonArray("children")?.forEach { child ->
            walk(child, cfg)
        }

        // Leaf node — read replace_ids
        val idsArr = obj.getAsJsonArray("replace_ids") ?: return
        if (idsArr.size() == 0) return

        val mode = obj.get("mode")?.asString ?: "cdn"
        val remove = obj.get("remove")?.asBoolean ?: false
        val cdnUrl = obj.get("cdn_url")?.asString
        val withId = obj.get("with_id")?.asString

        idsArr.forEach { idEl ->
            val id = idEl.asString

            val rule: FleasionRule? = when {
                remove -> FleasionRule(matchId = id, removeAsset = true)

                mode == "cdn" && cdnUrl != null ->
                    FleasionRule(matchId = id, cdnUrl = cdnUrl)

                mode == "id" && withId != null ->
                    FleasionRule(matchId = id, withAssetId = withId)

                else -> null
            }

            if (rule != null) cfg.rules.add(rule)
        }
    }
}

data class FleasionConfig(
    val name: String,
    val rules: MutableList<FleasionRule> = mutableListOf()
)

data class FleasionRule(
    val matchId: String,
    val replacementPath: String? = null,
    val replacementBytes: ByteArray? = null,
    val cdnUrl: String? = null,
    val withAssetId: String? = null,
    val removeAsset: Boolean = false
) {
    override fun equals(other: Any?): Boolean =
        other is FleasionRule && other.matchId == matchId
    override fun hashCode(): Int = matchId.hashCode()
}
