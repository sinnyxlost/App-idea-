package com.devy.fleasionshizuku

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class AssetRewriter(
    private val ctx: Context,
    port: Int
) : NanoHTTPD("127.0.0.1", port) {

    private val localPort: Int = port

    private val replacements  = ConcurrentHashMap<String, String>()   // id → local file path
    private val replacementBytes = ConcurrentHashMap<String, ByteArray>() // id → bytes
    private val cdnRewrites   = ConcurrentHashMap<String, String>()   // id → cdn url
    private val idRewrites    = ConcurrentHashMap<String, String>()   // id → other roblox asset id
    private val removals      = ConcurrentHashMap.newKeySet<String>() // ids to blank

    fun loadFromConfig(configs: List<FleasionConfig>) {
        replacements.clear()
        replacementBytes.clear()
        cdnRewrites.clear()
        idRewrites.clear()
        removals.clear()

        configs.forEach { cfg ->
            cfg.rules.forEach { rule ->
                rule.replacementPath?.let { replacements[rule.matchId] = it }
                rule.replacementBytes?.let { replacementBytes[rule.matchId] = it }
                rule.cdnUrl?.let { cdnRewrites[rule.matchId] = it }
                rule.withAssetId?.let { idRewrites[rule.matchId] = it }
                if (rule.removeAsset) removals.add(rule.matchId)
            }
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: return newFixedLengthResponse(
            Response.Status.BAD_REQUEST, "text/plain", "no uri"
        )

        val assetId = extractAssetId(uri)
        if (assetId != null) {

            // Blank asset (remove)
            if (removals.contains(assetId)) {
                return blankAsset(uri)
            }

            // Replace with another Roblox asset id
            idRewrites[assetId]?.let { otherId ->
                return proxyPassThrough("https://assetdelivery.roblox.com/v1/asset/?id=$otherId")
            }

            // CDN url rewrite
            cdnRewrites[assetId]?.let { url ->
                return proxyPassThrough(url)
            }

            // Local bytes
            replacementBytes[assetId]?.let { bytes ->
                return newFixedLengthResponse(
                    Response.Status.OK, guessMime(uri),
                    ByteArrayInputStream(bytes), bytes.size.toLong()
                )
            }

            // Local file
            replacements[assetId]?.let { path ->
                val file = java.io.File(path)
                if (file.exists()) {
                    return newChunkedResponse(
                        Response.Status.OK, guessMime(uri),
                        file.inputStream()
                    )
                }
            }
        }
        return proxyPassThrough(uri)
    }

    private fun blankAsset(uri: String): Response {
        // Tiny transparent 1x1 PNG for image requests, empty for everything else
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(),
            0x89.toByte(), 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41,
            0x54, 0x78, 0x9C.toByte(), 0x62, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42,
            0x60, 0x82.toByte()
        )
        return newFixedLengthResponse(
            Response.Status.OK, "image/png",
            ByteArrayInputStream(png), png.size.toLong()
        )
    }

    private fun proxyPassThrough(uri: String): Response {
        return try {
            val real = if (uri.startsWith("http")) uri else "https://$uri"
            val conn = java.net.URL(real).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", "Roblox/WinInet")
            conn.connect()
            newChunkedResponse(
                Response.Status.OK,
                conn.contentType ?: "application/octet-stream",
                conn.inputStream
            )
        } catch (t: Throwable) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, "text/plain", "proxy error: ${t.message}"
            )
        }
    }

    fun processPacket(buffer: ByteBuffer, len: Int): ByteArray {
        val data = ByteArray(len)
        buffer.get(data, 0, len)
        val text = String(data, Charsets.ISO_8859_1)
        var rewritten = text

        // Rewrite rbxassetid://<id> → local proxy
        val allIds = HashSet<String>()
        allIds.addAll(replacements.keys)
        allIds.addAll(replacementBytes.keys)
        allIds.addAll(cdnRewrites.keys)
        allIds.addAll(idRewrites.keys)
        allIds.addAll(removals)

        allIds.forEach { id ->
            if (text.contains("rbxassetid://$id")) {
                rewritten = rewritten.replace(
                    "rbxassetid://$id",
                    "http://127.0.0.1:$localPort/asset/$id"
                )
            }
        }
        return rewritten.toByteArray(Charsets.ISO_8859_1)
    }

    private fun extractAssetId(uri: String): String? {
        val patterns = listOf(
            Regex("""rbxassetid://(\d+)"""),
            Regex("""[?&]assetid=(\d+)"""),
            Regex("""[?&]id=(\d+)"""),
            Regex("""/asset/(\d+)"""),
            Regex("""/(\d+)(?:\?|$)""")
        )
        patterns.forEach { p -> p.find(uri)?.let { return it.groupValues[1] } }
        return null
    }

    private fun guessMime(uri: String): String = when {
        uri.endsWith(".png") -> "image/png"
        uri.endsWith(".jpg") || uri.endsWith(".jpeg") -> "image/jpeg"
        uri.endsWith(".webp") -> "image/webp"
        uri.endsWith(".dds") -> "image/vnd-ms.dds"
        uri.endsWith(".ogg") -> "audio/ogg"
        uri.endsWith(".mp3") -> "audio/mpeg"
        uri.endsWith(".wav") -> "audio/wav"
        uri.endsWith(".ttf") -> "font/ttf"
        uri.endsWith(".mesh") || uri.endsWith(".obj") -> "application/octet-stream"
        else -> "application/octet-stream"
    }
}
