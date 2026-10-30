package com.zachgrosser.memo

import java.io.File

data class Recording(
    val name: String,
    val file: File,
    val durationMillis: Long?,
    val lastModified: Long,
)

class RecordingStore(
    filesDir: File,
    private val durationOf: (File) -> Long?,
) {
    // files/shared is the folder Light's Tool Manager can see.
    val recordingsDir = File(filesDir, "$SHARED_DIR/$RECORDINGS_PATH")
    private val waveformsDir = File(filesDir, "waveforms")
    // Recorded here first, so the Tool Manager never offers a half-written file.
    val inProgressFile = File(filesDir, "in-progress.$RECORDING_EXTENSION")

    private val durations = mutableMapOf<Triple<String, Long, Long>, Long?>()

    fun files(): List<File> =
        recordingsDir.listFiles { f -> f.isFile && f.extension.equals(RECORDING_EXTENSION, ignoreCase = true) }
            ?.toList().orEmpty()

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

    fun waveform(name: String): List<Int>? =
        waveformFile(name).takeIf { it.isFile }?.let { Waveform.decode(it.readBytes()) }

    fun saveInProgress(name: String, levels: List<Int>): String? {
        recordingsDir.mkdirs()
        val finalName = RecordingNames.unique(name, names())
        if (!inProgressFile.renameTo(fileFor(finalName))) return null
        waveformsDir.mkdirs()
        runCatching { waveformFile(finalName).writeBytes(Waveform.encode(levels)) }
        return finalName
    }

    fun discardInProgress() {
        inProgressFile.delete()
    }

    fun rename(from: String, typed: String): String? {
        val cleaned = RecordingNames.clean(typed)
        if (cleaned.isEmpty()) return null
        if (cleaned == from) return from
        // A rename that only changes case isn't a clash.
        val newName = RecordingNames.unique(cleaned, names().filterNot { it == from })
        if (!fileFor(from).renameTo(fileFor(newName))) return null
        waveformFile(from).takeIf { it.isFile }?.renameTo(waveformFile(newName))
        return newName
    }

    fun delete(name: String): Boolean {
        val file = fileFor(name)
        if (file.exists() && !file.delete()) return false
        waveformFile(name).delete()
        return true
    }

    private fun waveformFile(name: String) = File(waveformsDir, "$name.wave")

    companion object {
        const val SHARED_DIR = "shared"

        const val RECORDINGS_PATH = "recordings"
    }
}
