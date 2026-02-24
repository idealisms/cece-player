package com.cece.player

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackUtilsTest {

    // ---- albumFromPath -------------------------------------------------------

    @Test
    fun `album is sub-folder name when file is inside a sub-folder of Cece`() {
        assertEquals("Dinosaurs", TrackUtils.albumFromPath("/storage/emulated/0/Cece/Dinosaurs/roar.mp3"))
    }

    @Test
    fun `album is Cece when file sits directly inside Cece with no sub-folder`() {
        assertEquals("Cece", TrackUtils.albumFromPath("/storage/emulated/0/Cece/song.mp3"))
    }

    @Test
    fun `album is Cece when path contains no Cece segment`() {
        assertEquals("Cece", TrackUtils.albumFromPath("/storage/emulated/0/Music/song.mp3"))
    }

    @Test
    fun `album is Cece for empty path`() {
        assertEquals("Cece", TrackUtils.albumFromPath(""))
    }

    @Test
    fun `album uses first segment only when deeply nested`() {
        assertEquals("Bedtime", TrackUtils.albumFromPath("/sdcard/Cece/Bedtime/Lullabies/song.mp3"))
    }

    @Test
    fun `Cece folder name is case-sensitive`() {
        // lowercase "cece" does not match the "/Cece/" pattern
        assertEquals("Cece", TrackUtils.albumFromPath("/storage/emulated/0/cece/Album/song.mp3"))
    }

    @Test
    fun `album handles Cece appearing in a longer folder name`() {
        // "CecePlayer" should not match "/Cece/"
        assertEquals("Cece", TrackUtils.albumFromPath("/storage/CecePlayer/Album/song.mp3"))
    }

    @Test
    fun `album handles multiple Cece segments — uses first occurrence`() {
        // First /Cece/ match gives "Outer"; the second Cece segment is ignored
        assertEquals("Outer", TrackUtils.albumFromPath("/Cece/Outer/Cece/Inner/song.mp3"))
    }

    // ---- titleFromFilename ---------------------------------------------------

    @Test
    fun `title strips simple mp3 extension`() {
        assertEquals("Happy Birthday", TrackUtils.titleFromFilename("Happy Birthday.mp3"))
    }

    @Test
    fun `title strips last dot only when filename has multiple dots`() {
        assertEquals("track.01", TrackUtils.titleFromFilename("track.01.mp3"))
    }

    @Test
    fun `title returns full name when there is no extension`() {
        assertEquals("nosuffix", TrackUtils.titleFromFilename("nosuffix"))
    }

    @Test
    fun `title handles empty string`() {
        assertEquals("", TrackUtils.titleFromFilename(""))
    }

    @Test
    fun `title strips flac extension`() {
        assertEquals("Song", TrackUtils.titleFromFilename("Song.flac"))
    }

    // ---- nextIndex -----------------------------------------------------------

    @Test
    fun `nextIndex advances by one`() {
        assertEquals(1, TrackUtils.nextIndex(0, 5))
        assertEquals(3, TrackUtils.nextIndex(2, 5))
    }

    @Test
    fun `nextIndex wraps from last to first`() {
        assertEquals(0, TrackUtils.nextIndex(4, 5))
    }

    @Test
    fun `nextIndex wraps on single-track list`() {
        assertEquals(0, TrackUtils.nextIndex(0, 1))
    }

    // ---- prevIndex -----------------------------------------------------------

    @Test
    fun `prevIndex goes back by one`() {
        assertEquals(2, TrackUtils.prevIndex(3, 5))
        assertEquals(0, TrackUtils.prevIndex(1, 5))
    }

    @Test
    fun `prevIndex wraps from first to last`() {
        assertEquals(4, TrackUtils.prevIndex(0, 5))
    }

    @Test
    fun `prevIndex wraps on single-track list`() {
        assertEquals(0, TrackUtils.prevIndex(0, 1))
    }
}
