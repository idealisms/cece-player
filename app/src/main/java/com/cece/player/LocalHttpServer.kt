package com.cece.player

import fi.iki.elonen.NanoHTTPD
import java.io.FileInputStream

class LocalHttpServer(private val tracks: List<Track>) : NanoHTTPD(8765) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri  // e.g. "/track/0"
        val match = Regex("^/track/(\\d+)$").matchEntire(uri)
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")

        val index = match.groupValues[1].toIntOrNull()
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")

        val track = tracks.getOrNull(index)
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")

        return try {
            val file = java.io.File(track.dataPath)
            newChunkedResponse(Response.Status.OK, "audio/mpeg", FileInputStream(file))
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message ?: "Error")
        }
    }
}
