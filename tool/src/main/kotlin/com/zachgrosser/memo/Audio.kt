package com.zachgrosser.memo

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.StatFs
import java.io.File

// The Android parts: the microphone, the speaker, and the storage figures.

/** Memo's recordings, with lengths read by Android. */
fun recordingStore(filesDir: File) = RecordingStore(filesDir, ::mediaDuration)

/** Free and total space on the storage holding [dir]. */
fun storageInfo(dir: File): StorageInfo {
    val stat = StatFs(dir.path)
    return StorageInfo(freeBytes = stat.availableBytes, totalBytes = stat.totalBytes)
}

/** A recording's length in milliseconds, or null if it can't be read. */
fun mediaDuration(file: File): Long? = runCatching {
    MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(file.path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    }
}.getOrNull()

/** Records the microphone to an .m4a file: mono AAC, good for voices at about 0.5 MB a minute. */
class Recorder(private val file: File) {

    // Tools can't hand Android a Context, so this uses MediaRecorder's original constructor,
    // which still works on every Android version Light uses.
    @Suppress("DEPRECATION")
    private val recorder = MediaRecorder()
    private var started = false

    /** Starts recording. Throws if the microphone can't be used (for example, permission refused). */
    fun start() {
        file.parentFile?.mkdirs()
        file.delete()
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(44_100)
            setAudioEncodingBitRate(64_000)
            setOutputFile(file.path)
            prepare()
            start()
        }
        started = true
    }

    fun pause() = recorder.pause()

    fun resume() = recorder.resume()

    /** Loudest moment since the last call, 0–32767. */
    fun amplitude(): Int = if (started) runCatching { recorder.maxAmplitude }.getOrDefault(0) else 0

    /**
     * Finishes the file. Returns false if Android couldn't (a recording under a second or so
     * long has no audio to keep); the file is then unusable.
     */
    fun stop(): Boolean {
        val ok = started && runCatching { recorder.stop() }.isSuccess
        started = false
        recorder.release()
        return ok
    }

    fun release() {
        started = false
        recorder.release()
    }
}

/** Plays a recording, with seeking and speed. */
class Player(file: File) {

    private val player = MediaPlayer().apply {
        setDataSource(file.path)
        prepare()
    }

    val durationMillis: Long get() = player.duration.toLong().coerceAtLeast(0)
    val positionMillis: Long get() = player.currentPosition.toLong()

    private var speed = 1f

    fun setOnCompletion(onComplete: () -> Unit) = player.setOnCompletionListener { onComplete() }

    fun play() {
        // Speed is applied as playback starts: setting it while paused would start playback.
        player.playbackParams = PlaybackParams().setSpeed(speed)
        player.start()
    }

    fun pause() {
        if (player.isPlaying) player.pause()
    }

    fun seekTo(millis: Long) = player.seekTo(millis.coerceIn(0, durationMillis), MediaPlayer.SEEK_CLOSEST)

    fun setSpeed(newSpeed: Float) {
        speed = newSpeed
        if (player.isPlaying) player.playbackParams = PlaybackParams().setSpeed(speed)
    }

    fun release() = player.release()
}
