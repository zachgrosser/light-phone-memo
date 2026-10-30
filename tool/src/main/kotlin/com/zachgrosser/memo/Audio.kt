package com.zachgrosser.memo

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.StatFs
import java.io.File

fun recordingStore(filesDir: File) = RecordingStore(filesDir, ::mediaDuration)

fun storageInfo(dir: File): StorageInfo {
    val stat = StatFs(dir.path)
    return StorageInfo(freeBytes = stat.availableBytes, totalBytes = stat.totalBytes)
}

fun mediaDuration(file: File): Long? = runCatching {
    MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(file.path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    }
}.getOrNull()

class Recorder(private val file: File) {

    // Tools can't get a Context, so this uses the old constructor.
    @Suppress("DEPRECATION")
    private val recorder = MediaRecorder()
    private var started = false

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

    fun amplitude(): Int = if (started) runCatching { recorder.maxAmplitude }.getOrDefault(0) else 0

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
        // Setting the speed while paused would start playback, so it's applied here.
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
