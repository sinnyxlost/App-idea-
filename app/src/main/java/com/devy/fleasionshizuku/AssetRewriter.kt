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

    private val replacements = ConcurrentHashMap<String, String>()
    private val replacementBytes = ConcurrentHashMap<String, ByteArray>()

    fun loadFromConfig(configs: List<FleasionConfig>) {
        configs.forEach { cfg ->
            cfg.rules.forEach { rule ->
                rule.replacementPath?.let { replacements[rule.matchId] = it }
                rule.replacementBytes?.let { replacementBytes[rule.matchId] = it }
            }
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: return newFixedLengthResponse(
            Response.Status.BAD_REQUEST, "text/plain", "no uri"
        )

        val assetId = extractAssetId(uri)
        if (assetId != null) {
            replacementBytes[assetId]?.let { bytes ->
                return newFixedLengthResponse(
                    Response.Status.OK, guessMime(uri),
                    ByteArrayInputStream(bytes), bytes.size.toLong()
                )
            }
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

    private fun proxyPassThrough(uri: String): Response {
        return try {
            val real = if (uri.startsWith("http")) uri else "https://$uri"
            val conn = java.net.URL(real).openConnection() as java.net.HttpURLConnection
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
        replacements.keys.forEach { id ->
            if (text.contains("assetid=$id") || text.contains("rbxassetid://$id")) {
                rewritten = text.replace("rbxassetid://$id", "http://127.0.0.1:$myPort/$id")
            }
        }
        return rewritten.toByteArray(Charsets.ISO_8859_1)
    }

    private fun extractAssetId(uri: String): String? {
        val patterns = listOf(
            Regex("""rbxassetid://(\d+)"""),
            Regex("""[?&]assetid=(\d+)"""),
            Regex("""/(\d+)(?:\?|$)""")
        )
        patterns.forEach { p -> p.find(uri)?.let { return it.groupValues[1] } }
        return null
    }

    private fun guessMime(uri: String): String = when {
        uri.endsWith(".png") -> "image/png"
        uri.endsWith(".jpg") || uri.endsWith(".jpeg") -> "image/jpeg"
        uri.endsWith(".dds") -> "image/vnd-ms.dds"
        uri.endsWith(".ogg") -> "audio/ogg"
        uri.endsWith(".mp3") -> "audio/mpeg"
        uri.endsWith(".mesh") -> "application/octet-stream"
        else -> "application/octet-stream"
    }
}
