package com.zachgrosser.memo

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordingStoreTest {

    private val dir: File = Files.createTempDirectory("memo").toFile()
    private val store = RecordingStore(dir) { file -> file.length() * 1000 }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun record(name: String, levels: List<Int> = listOf(1, 2, 3)): String? {
        store.inProgressFile.writeText("audio")
        return store.saveInProgress(name, levels)
    }

    @Test
    fun recordingsAreInTheToolManagerFolder() {
        assertEquals(File(dir, "shared/recordings"), store.recordingsDir)
        record("2026.10.30 1405")
        assertTrue(File(dir, "shared/recordings/2026.10.30 1405.m4a").isFile)
        assertFalse(store.inProgressFile.exists())
    }

    @Test
    fun savingKeepsTheWaveformAndAvoidsClashes() {
        assertEquals("A", record("A", listOf(9, 8)))
        assertEquals("A (2)", record("A"))
        assertEquals(listOf(9, 8), store.waveform("A"))
        assertEquals(listOf("A", "A (2)"), store.list().map { it.name }.sorted())
        assertEquals(5000L, store.list().first().durationMillis)
    }

    @Test
    fun listIsNewestFirstAndOnlyAudio() {
        record("Old")
        store.fileFor("Old").setLastModified(1_000_000)
        record("New")
        File(store.recordingsDir, "notes.txt").writeText("x")
        assertEquals(listOf("New", "Old"), store.list().map { it.name })
    }

    @Test
    fun filesFromAComputerHaveNoWaveform() {
        store.recordingsDir.mkdirs()
        store.fileFor("Uploaded").writeText("audio")
        assertNull(store.waveform("Uploaded"))
        assertEquals(listOf("Uploaded"), store.list().map { it.name })
    }

    @Test
    fun renameMovesAudioAndWaveform() {
        record("A", listOf(5))
        assertEquals("Shopping list", store.rename("A", "  Shopping/list "))
        assertFalse(store.fileFor("A").exists())
        assertTrue(store.fileFor("Shopping list").isFile)
        assertEquals(listOf(5), store.waveform("Shopping list"))
        assertNull(store.waveform("A"))
    }

    @Test
    fun renameRefusesEmptyAndAvoidsClashes() {
        record("A")
        record("B")
        assertNull(store.rename("A", " :: "))
        assertEquals("A", store.rename("A", "A"))
        assertEquals("b (2)", store.rename("A", "b"))
        assertEquals("B", store.rename("B", "B"))
    }

    @Test
    fun renameCanChangeJustTheCase() {
        record("memo")
        assertEquals("Memo", store.rename("memo", "Memo"))
        assertEquals(listOf("Memo"), store.list().map { it.name })
    }

    @Test
    fun deleteRemovesAudioAndWaveform() {
        record("A")
        assertTrue(store.delete("A"))
        assertTrue(store.list().isEmpty())
        assertNull(store.waveform("A"))
        assertTrue(store.delete("Missing"))
    }

    @Test
    fun discardInProgress() {
        store.inProgressFile.writeText("half")
        store.discardInProgress()
        assertFalse(store.inProgressFile.exists())
    }
}
