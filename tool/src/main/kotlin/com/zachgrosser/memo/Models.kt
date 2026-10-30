package com.zachgrosser.memo

import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.ln
import kotlin.math.roundToInt

// Memo's rules, kept free of Android classes so the unit tests can run on the JVM.
// Numbers use Locale.ROOT: Memo is in English, and file names should read the same on any phone.

/** Recordings are AAC audio in an MPEG-4 file, which every computer and phone can play. */
const val RECORDING_EXTENSION = "m4a"

/** Names: what a recording is called by default, and what a rename may contain. */
object RecordingNames {

    /** Longest name Memo keeps, so file names stay well inside Android's 255-byte limit. */
    const val MAX_LENGTH = 80

    /**
     * The date and time the recording started, such as "2026.10.30 14.05". The time uses a
     * dot rather than a colon: file names with colons can't be saved on Windows, and recordings
     * are downloaded to computers through Light's Tool Manager.
     */
    fun default(start: LocalDateTime): String =
        "%04d.%02d.%02d %02d.%02d".format(Locale.ROOT, start.year, start.monthValue, start.dayOfMonth, start.hour, start.minute)

    /**
     * Makes typed text safe as a file name: removes characters that file systems reject
     * (/ \ : * ? " < > | and control characters), squeezes spaces, and drops leading dots so
     * the file isn't hidden. Returns "" if nothing usable is left.
     */
    fun clean(typed: String): String =
        typed
            .map { if (it in FORBIDDEN || it.isISOControl()) ' ' else it }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimStart('.')
            .trim()
            .take(MAX_LENGTH)
            .trimEnd()

    /**
     * [name], or "[name] (2)", "[name] (3)"… if it's already taken. Comparison ignores case,
     * because some computers treat "Memo" and "memo" as the same file.
     */
    fun unique(name: String, taken: Collection<String>): String {
        val lower = taken.map { it.lowercase() }.toSet()
        if (name.lowercase() !in lower) return name
        var n = 2
        while ("$name ($n)".lowercase() in lower) n++
        return "$name ($n)"
    }

    private const val FORBIDDEN = "/\\:*?\"<>|"
}

/** How full the phone is, and what Memo does about it. */
enum class StorageLevel {
    /** More than [LOW_FRACTION] free. */
    Ok,

    /** [LOW_FRACTION] or less free: Memo warns, full screen. */
    Low,

    /** [FULL_FRACTION] or less free: Memo won't record, and stops a recording in progress. */
    Full;

    companion object {
        const val LOW_FRACTION = 0.10
        const val FULL_FRACTION = 0.02

        fun of(freeBytes: Long, totalBytes: Long): StorageLevel {
            if (totalBytes <= 0) return Ok
            val free = freeBytes.toDouble() / totalBytes
            return when {
                free <= FULL_FRACTION -> Full
                free <= LOW_FRACTION -> Low
                else -> Ok
            }
        }
    }
}

/** Free and total space where Memo keeps recordings. */
data class StorageInfo(val freeBytes: Long, val totalBytes: Long) {
    val level: StorageLevel get() = StorageLevel.of(freeBytes, totalBytes)
    val freeFraction: Float get() = if (totalBytes <= 0) 1f else (freeBytes.toDouble() / totalBytes).toFloat()

    /** Such as "12.4 GB free of 128 GB (9%)". */
    fun describe(): String =
        "${formatBytes(freeBytes)} free of ${formatBytes(totalBytes)} (${(freeFraction * 100).roundToInt()}%)"
}

/** Sizes in the units phones use: 1 GB = 1,000,000,000 bytes. */
fun formatBytes(bytes: Long): String {
    val units = listOf("bytes", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1000 && unit < units.lastIndex) {
        value /= 1000
        unit++
    }
    return when {
        unit == 0 -> "$bytes bytes"
        value < 10 -> "%.1f %s".format(Locale.ROOT, value, units[unit]).replace(".0 ", " ")
        else -> "${value.roundToInt()} ${units[unit]}"
    }
}

/** "0:07", "12:34", or "1:02:03" for an hour or more. Rounds down to the second. */
fun formatDuration(millis: Long): String {
    val total = (millis.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
}

/** Playback speeds, in the order the speed button steps through them. */
object PlaybackSpeeds {
    val all = listOf(1f, 1.25f, 1.5f, 2f, 0.5f, 0.75f)

    fun next(current: Float): Float {
        val i = all.indexOfFirst { it == current }
        return all[(i + 1) % all.size]
    }

    /** "1×", "1.25×", "0.5×". */
    fun label(speed: Float): String =
        (if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()) + "×"
}

/**
 * The line drawing of a recording's loudness, Google Recorder style: one bar per sample,
 * [SAMPLES_PER_SECOND] samples a second, each 0–255. Saved beside Memo's private files, one byte
 * per sample, so playback can show the same bars that were drawn while recording.
 */
object Waveform {
    const val SAMPLES_PER_SECOND = 10
    const val SAMPLE_MILLIS = 1000L / SAMPLES_PER_SECOND

    /** Loudest value Android's MediaRecorder.getMaxAmplitude() reports. */
    private const val MAX_AMPLITUDE = 32767.0

    /** Quieter than this (room hiss) draws as the smallest bar. */
    private const val FLOOR_AMPLITUDE = 120.0

    /**
     * Turns a MediaRecorder amplitude into a bar height (0–255). Uses a log scale, as ears
     * hear it: speaking and shouting both show, and silence stays flat.
     */
    fun level(amplitude: Int): Int {
        if (amplitude <= FLOOR_AMPLITUDE) return 0
        val fraction = ln(amplitude / FLOOR_AMPLITUDE) / ln(MAX_AMPLITUDE / FLOOR_AMPLITUDE)
        return (fraction.coerceIn(0.0, 1.0) * 255).roundToInt()
    }

    fun encode(levels: List<Int>): ByteArray = ByteArray(levels.size) { levels[it].coerceIn(0, 255).toByte() }

    fun decode(bytes: ByteArray): List<Int> = bytes.map { it.toInt() and 0xFF }

    /** Number of samples covering [millis] of audio. */
    fun samplesFor(millis: Long): Int = (millis / SAMPLE_MILLIS).toInt()

    /** Where [millis] falls, in samples (fractional, for smooth scrolling). */
    fun positionOf(millis: Long): Float = millis.toFloat() / SAMPLE_MILLIS

    /** The audio time at sample [position]. */
    fun millisAt(position: Float): Long = (position * SAMPLE_MILLIS).toLong()
}
