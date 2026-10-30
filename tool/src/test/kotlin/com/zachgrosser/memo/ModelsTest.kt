package com.zachgrosser.memo

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelsTest {

    @Test
    fun defaultNameIsDateAndTwentyFourHourTime() {
        assertEquals("2026.10.30 14.05", RecordingNames.default(LocalDateTime.of(2026, 10, 30, 14, 5, 59)))
        assertEquals("2027.01.02 00.00", RecordingNames.default(LocalDateTime.of(2027, 1, 2, 0, 0)))
        assertEquals("2026.12.31 23.59", RecordingNames.default(LocalDateTime.of(2026, 12, 31, 23, 59)))
    }

    @Test
    fun cleanRemovesCharactersFileSystemsReject() {
        assertEquals("Notes a b", RecordingNames.clean("Notes/a:b"))
        assertEquals("Idea", RecordingNames.clean("  Idea \n"))
        assertEquals("hidden", RecordingNames.clean("..hidden"))
        assertEquals("", RecordingNames.clean(" / ? * "))
        assertEquals("Ünïcode & émoji 🎙", RecordingNames.clean("Ünïcode & émoji 🎙"))
        assertEquals(RecordingNames.MAX_LENGTH, RecordingNames.clean("x".repeat(200)).length)
    }

    @Test
    fun uniqueAddsANumberIgnoringCase() {
        assertEquals("Memo", RecordingNames.unique("Memo", listOf("Other")))
        assertEquals("Memo (2)", RecordingNames.unique("Memo", listOf("memo")))
        assertEquals("Memo (3)", RecordingNames.unique("Memo", listOf("Memo", "Memo (2)")))
    }

    @Test
    fun storageLevels() {
        assertEquals(StorageLevel.Ok, StorageLevel.of(50, 100))
        assertEquals(StorageLevel.Ok, StorageLevel.of(11, 100))
        assertEquals(StorageLevel.Low, StorageLevel.of(10, 100))
        assertEquals(StorageLevel.Low, StorageLevel.of(3, 100))
        assertEquals(StorageLevel.Full, StorageLevel.of(2, 100))
        assertEquals(StorageLevel.Full, StorageLevel.of(0, 100))
        assertEquals(StorageLevel.Ok, StorageLevel.of(0, 0))
    }

    @Test
    fun storageDescription() {
        assertEquals("12 GB free of 128 GB (9%)", StorageInfo(12_000_000_000, 128_000_000_000).describe())
        assertEquals("1.5 GB", formatBytes(1_500_000_000))
        assertEquals("2 MB", formatBytes(2_000_000))
        assertEquals("999 bytes", formatBytes(999))
    }

    @Test
    fun durations() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:07", formatDuration(7_999))
        assertEquals("12:34", formatDuration((12 * 60 + 34) * 1000L))
        assertEquals("1:02:03", formatDuration((3600 + 2 * 60 + 3) * 1000L))
        assertEquals("0:00", formatDuration(-5))
    }

    @Test
    fun speedsCycleAndLabel() {
        var speed = 1f
        val seen = mutableListOf<Float>()
        repeat(PlaybackSpeeds.all.size) { speed = PlaybackSpeeds.next(speed); seen += speed }
        assertEquals(PlaybackSpeeds.all.toSet(), seen.toSet())
        assertEquals(1f, speed)
        assertEquals("1×", PlaybackSpeeds.label(1f))
        assertEquals("1.25×", PlaybackSpeeds.label(1.25f))
        assertEquals("0.5×", PlaybackSpeeds.label(0.5f))
        assertEquals("2×", PlaybackSpeeds.label(2f))
    }

    @Test
    fun waveformLevels() {
        assertEquals(0, Waveform.level(0))
        assertEquals(0, Waveform.level(100))
        assertEquals(255, Waveform.level(32767))
        val quiet = Waveform.level(500)
        val loud = Waveform.level(8000)
        assertTrue(quiet in 1 until loud, "louder sounds draw taller bars")
    }

    @Test
    fun waveformRoundTripsThroughBytes() {
        val levels = listOf(0, 1, 127, 128, 200, 255)
        assertEquals(levels, Waveform.decode(Waveform.encode(levels)))
        assertEquals(listOf(0, 255), Waveform.decode(Waveform.encode(listOf(-3, 999))))
    }

    @Test
    fun waveformTime() {
        assertEquals(10, Waveform.samplesFor(1_000))
        assertEquals(25f, Waveform.positionOf(2_500))
        assertEquals(2_500, Waveform.millisAt(25f))
    }
}
