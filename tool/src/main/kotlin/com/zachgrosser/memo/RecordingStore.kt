package com.zachgrosser.memo

import java.io.File

/** A saved recording: its audio file and, if Memo recorded it, its waveform. */
data class Recording(
    val name: String,
    val file: File,
    /** Length in milliseconds, or null if the file couldn't be read. */
    val durationMillis: Long?,
    val lastModified: Long,
)

/**
 * Where Memo keeps recordings, all on the phone:
 *
 * - `files/shared/recordings/<name>.m4a`: the recordings. Light's Tool Manager serves
 *   `files/shared` (see [MemoEntryPoint]), which is how a recording is shared to a computer.
 * - `files/waveforms/<name>.wave`: each recording's line drawing (see [Waveform]). Private.
 * - `files/in-progress.m4a`: the recording being made. Moved into `recordings` when it stops,
 *   so the Tool Manager never offers a half-written file.
 *
 * Uses plain files and no Android classes; [durationOf] reads a file's length (Android's
 * MediaMetadataRetriever in the app, a stand-in in tests).
 */
class RecordingStore(
    filesDir: File,
    private val durationOf: (File) -> Long?,
) {
    val recordingsDir = File(filesDir, "$SHARED_DIR/$RECORDINGS_PATH")
    private val waveformsDir = File(filesDir, "waveforms")
    val inProgressFile = File(filesDir, "in-progress.$RECORDING_EXTENSION")

    /** Durations already read, by file path, size and date, since reading them takes a moment. */
    private val durations = mutableMapOf<Triple<String, Long, Long>, Long?>()

    /** The audio files in the recordings folder, including any added with the Tool Manager. */
    fun files(): List<File> =
        recordingsDir.listFiles { f -> f.isFile && f.extension.equals(RECORDING_EXTENSION, ignoreCase = true) }
            ?.toList().orEmpty()

    /** Every recording, newest first. */
    fun list(): List<Recording> =
        files()
            .map { file ->
                val key = Triple(file.path, file.length(), file.lastModified())
                val duration = synchronized(durations) { durations.getOrPut(key) { durationOf(file) } }
                Recording(file.nameWithoutExtension, file, duration, file.lastModified())
            }
            .sortedWith(compareByDescending<Recording> { it.lastModified }.thenBy { it.name })

    private fun names(): List<String> =
        recordingsDir.listFiles()?.filter { it.isFile }?.map { it.nameWithoutExtension }.orEmpty()

    fun fileFor(name: String) = File(recordingsDir, "$name.$RECORDING_EXTENSION")

    /** The waveform drawn while recording [name], or null (for files added from a computer). */
    fun waveform(name: String): List<Int>? =
        waveformFile(name).takeIf { it.isFile }?.let { Waveform.decode(it.readBytes()) }

    /**
     * Moves the finished [inProgressFile] into the recordings folder as [name] (made unique),
     * saves its waveform, and returns the name used, or null if the move failed.
     */
    fun saveInProgress(name: String, levels: List<Int>): String? {
        recordingsDir.mkdirs()
        val finalName = RecordingNames.unique(name, names())
        if (!inProgressFile.renameTo(fileFor(finalName))) return null
        waveformsDir.mkdirs()
        runCatching { waveformFile(finalName).writeBytes(Waveform.encode(levels)) }
        return finalName
    }

    /** Removes a recording left over from Memo being closed mid-recording (it can't be played). */
    fun discardInProgress() {
        inProgressFile.delete()
    }

    /**
     * Renames [from] to the cleaned-up [typed] name. Returns the new name, the old name if
     * nothing changed, or null if the rename failed or the name was empty.
     */
    fun rename(from: String, typed: String): String? {
        val cleaned = RecordingNames.clean(typed)
        if (cleaned.isEmpty()) return null
        if (cleaned == from) return from
        // Renaming "memo" to "Memo" only changes case: that's not a clash with itself.
        val newName = RecordingNames.unique(cleaned, names().filterNot { it == from })
        if (!fileFor(from).renameTo(fileFor(newName))) return null
        waveformFile(from).takeIf { it.isFile }?.renameTo(waveformFile(newName))
        return newName
    }

    /** Deletes the recording and its waveform. Returns false if the recording is still there. */
    fun delete(name: String): Boolean {
        val file = fileFor(name)
        if (file.exists() && !file.delete()) return false
        waveformFile(name).delete()
        return true
    }

    private fun waveformFile(name: String) = File(waveformsDir, "$name.wave")

    companion object {
        /** Light's Tool Manager serves this folder inside the tool's files (LightFileProvider.SHARED_DIR). */
        const val SHARED_DIR = "shared"

        /** The Tool Manager folder for recordings, inside [SHARED_DIR]. */
        const val RECORDINGS_PATH = "recordings"
    }
}
