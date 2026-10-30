package com.zachgrosser.memo

import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.ln
import kotlin.math.roundToInt

const val RECORDING_EXTENSION = "m4a"

object RecordingNames {

    const val MAX_LENGTH = 80

    // No colon in the time: Windows doesn't allow one in file names.
    fun default(start: LocalDateTime): String =
        "%04d.%02d.%02d %02d%02d".format(Locale.ROOT, start.year, start.monthValue, start.dayOfMonth, start.hour, start.minute)

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

    fun unique(name: String, taken: Collection<String>): String {
        val lower = taken.map { it.lowercase() }.toSet()
        if (name.lowercase() !in lower) return name
        var n = 2
        while ("$name ($n)".lowercase() in lower) n++
        return "$name ($n)"
    }

    private const val FORBIDDEN = "/\\:*?\"<>|"
}

enum class StorageLevel {
    Ok, Low, Full;

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

data class StorageInfo(val freeBytes: Long, val totalBytes: Long) {
    val level: StorageLevel get() = StorageLevel.of(freeBytes, totalBytes)
    val freeFraction: Float get() = if (totalBytes <= 0) 1f else (freeBytes.toDouble() / totalBytes).toFloat()

    fun describe(): String =
        "${formatBytes(freeBytes)} free of ${formatBytes(totalBytes)} (${(freeFraction * 100).roundToInt()}%)"
}

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
        value < 100 -> "%.1f %s".format(Locale.ROOT, value, units[unit]).replace(".0 ", " ")
        else -> "${value.roundToInt()} ${units[unit]}"
    }
}

fun formatDuration(millis: Long): String {
    val total = (millis.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
}

object PlaybackSpeeds {
    val all = listOf(1f, 1.25f, 1.5f, 2f, 0.5f, 0.75f)

    fun next(current: Float): Float {
        val i = all.indexOfFirst { it == current }
        return all[(i + 1) % all.size]
    }

    fun label(speed: Float): String =
        (if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()) + "×"
}

// One bar per 100 ms, saved as one byte each.
object Waveform {
    const val SAMPLES_PER_SECOND = 10
    const val SAMPLE_MILLIS = 1000L / SAMPLES_PER_SECOND

    private const val MAX_AMPLITUDE = 32767.0

    private const val FLOOR_AMPLITUDE = 120.0

    // Log scale, so quiet speech still shows up.
    fun level(amplitude: Int): Int {
        if (amplitude <= FLOOR_AMPLITUDE) return 0
        val fraction = ln(amplitude / FLOOR_AMPLITUDE) / ln(MAX_AMPLITUDE / FLOOR_AMPLITUDE)
        return (fraction.coerceIn(0.0, 1.0) * 255).roundToInt()
    }

    fun encode(levels: List<Int>): ByteArray = ByteArray(levels.size) { levels[it].coerceIn(0, 255).toByte() }

    fun decode(bytes: ByteArray): List<Int> = bytes.map { it.toInt() and 0xFF }

    fun samplesFor(millis: Long): Int = (millis / SAMPLE_MILLIS).toInt()

    fun positionOf(millis: Long): Float = millis.toFloat() / SAMPLE_MILLIS

    fun millisAt(position: Float): Long = (position * SAMPLE_MILLIS).toLong()
}
