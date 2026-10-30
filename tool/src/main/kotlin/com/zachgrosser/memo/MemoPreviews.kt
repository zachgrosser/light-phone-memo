package com.zachgrosser.memo

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import java.io.File
import kotlin.math.abs
import kotlin.math.sin

// Previews of every screen at each Light device's screen size, to check layouts without an
// emulator. In Android Studio, open this file and choose "Split" or "Design" (top right).
//
// Light Phone III: 1080 x 1240 pixels, 3.92". Light's own previews use these dp values.
// Light Flip:      480 x 640 pixels, 2.8" (shipping April 2027). The dp size assumes roughly
//                  280 dpi; the Light SDK scales text and spacing to the screen.

@Preview(name = "Light Phone III", widthDp = 360, heightDp = 413, showBackground = true, backgroundColor = 0xFF000000)
@Preview(name = "Light Flip", widthDp = 274, heightDp = 366, showBackground = true, backgroundColor = 0xFF000000)
annotation class LightDevicePreviews

/** A made-up voice: bursts of words with pauses between them. */
private val SAMPLE_LEVELS = List(600) { i ->
    val word = abs(sin(i / 3.0)) * 150 + abs(sin(i / 7.3)) * 90
    if ((i / 25) % 4 == 3) (i * 7 % 13) else word.toInt().coerceAtMost(255)
}

private val SAMPLE_RECORDINGS = listOf(
    Recording("2026.10.30 14.05", File("a"), 754_000, 3),
    Recording("Grocery list", File("b"), 31_000, 2),
    Recording("Song idea, chorus", File("c"), 3_725_000, 1),
)

private val SAMPLE_STORAGE = StorageInfo(41_200_000_000, 128_000_000_000)

@LightDevicePreviews
@Composable
private fun HomeEmptyPreview() = MemoTheme {
    HomeContent(HomeState(loaded = true), onOpen = {}, onRecord = {}, onAbout = {})
}

@LightDevicePreviews
@Composable
private fun HomePreview() = MemoTheme {
    HomeContent(HomeState(loaded = true, recordings = SAMPLE_RECORDINGS), onOpen = {}, onRecord = {}, onAbout = {})
}

@LightDevicePreviews
@Composable
private fun LowStoragePreview() = MemoTheme {
    FullScreenNotice(lowStorageMessage(StorageInfo(9_800_000_000, 128_000_000_000)), onClose = {})
}

@LightDevicePreviews
@Composable
private fun RecordingPreview() = MemoTheme {
    RecordContent(
        RecordState(phase = RecordPhase.Recording, name = "2026.10.30 14.05", elapsedMillis = 23_400, samples = 234),
        levels = SAMPLE_LEVELS,
        onPause = {}, onResume = {}, onStop = {},
    )
}

@LightDevicePreviews
@Composable
private fun PausedPreview() = MemoTheme {
    RecordContent(
        RecordState(phase = RecordPhase.Paused, name = "2026.10.30 14.05", elapsedMillis = 5_100, samples = 51),
        levels = SAMPLE_LEVELS,
        onPause = {}, onResume = {}, onStop = {},
    )
}

@LightDevicePreviews
@Composable
private fun PlayerPreview() = MemoTheme {
    PlayerContent(
        PlayerState(
            name = "Grocery list", loaded = true, durationMillis = 60_000, positionMillis = 21_300,
            playing = true, speed = 1.5f, levels = SAMPLE_LEVELS,
        ),
        onBack = {}, onOptions = {}, onTogglePlay = {}, onSkip = {}, onSeekFraction = {}, onSpeed = {},
        onScrubStart = {}, onScrub = {}, onScrubEnd = {},
    )
}

@LightDevicePreviews
@Composable
private fun PlayerNoWaveformPreview() = MemoTheme {
    PlayerContent(
        PlayerState(name = "Uploaded from a computer", loaded = true, durationMillis = 40_000, positionMillis = 0),
        onBack = {}, onOptions = {}, onTogglePlay = {}, onSkip = {}, onSeekFraction = {}, onSpeed = {},
        onScrubStart = {}, onScrub = {}, onScrubEnd = {},
    )
}

@LightDevicePreviews
@Composable
private fun OptionsPreview() = MemoTheme {
    OptionsContent("Grocery list", message = null, onBack = {}, onRename = {}, onShare = {}, onDelete = {})
}

@LightDevicePreviews
@Composable
private fun SharePreview() = MemoTheme {
    ShareContent("Grocery list", onBack = {})
}

@LightDevicePreviews
@Composable
private fun AboutPreview() = MemoTheme {
    AboutContent(AboutState(SAMPLE_STORAGE, recordingCount = 3, recordingBytes = 38_500_000), onBack = {})
}
