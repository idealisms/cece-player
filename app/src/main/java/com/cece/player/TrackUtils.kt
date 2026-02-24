package com.cece.player

object TrackUtils {

    /** Extracts the album name from a full file path.
     *  Album = the first path segment inside a folder named "Cece".
     *  Returns "Cece" if the file sits directly inside Cece/ with no sub-folder,
     *  or if the path contains no Cece/ segment at all. */
    fun albumFromPath(fullPath: String): String {
        val ceceSegment = fullPath.indexOf("/Cece/")
        if (ceceSegment < 0) return "Cece"
        val afterCece = fullPath.substring(ceceSegment + 6) // skip "/Cece/"
        val nextSlash = afterCece.indexOf('/')
        return if (nextSlash > 0) afterCece.substring(0, nextSlash) else "Cece"
    }

    /** Strips the file extension from a display name. */
    fun titleFromFilename(displayName: String): String = displayName.substringBeforeLast(".")

    /** Returns the index of the next track, wrapping around. */
    fun nextIndex(current: Int, size: Int): Int = (current + 1) % size

    /** Returns the index of the previous track, wrapping around. */
    fun prevIndex(current: Int, size: Int): Int = (current - 1 + size) % size
}
