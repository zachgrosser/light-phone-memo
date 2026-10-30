package com.zachgrosser.memo

import android.Manifest
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.checkPermission
import com.thelightphone.sdk.rememberPermissionRequestLauncher
import com.thelightphone.sdk.shared.LightServiceMethod.GetPermission
import com.thelightphone.sdk.shared.getOrNull
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

sealed interface RecordPhase {
    /** Asking LightOS whether Memo may use the microphone. */
    data object Checking : RecordPhase

    /** The microphone permission hasn't been given (yet). [asked] once the request has been shown. */
    data class NeedsPermission(val asked: Boolean, val blocked: Boolean = false) : RecordPhase

    data object Recording : RecordPhase
    data object Paused : RecordPhase

    /** Stopping and saving. */
    data object Saving : RecordPhase

    /** Done: go back to the list (after the notice, if there is one). */
    data class Finished(val savedName: String?) : RecordPhase

    data class Failed(val message: String) : RecordPhase
}

data class RecordState(
    val phase: RecordPhase = RecordPhase.Checking,
    val name: String = "",
    val elapsedMillis: Long = 0,
    /** Number of waveform samples so far; the samples themselves are [RecordViewModel.levels]. */
    val samples: Int = 0,
    /** A full-screen notice: low storage, or why recording stopped. */
    val notice: String? = null,
)

class RecordViewModel(private val store: RecordingStore, private val filesDir: File) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(RecordState())
    val state: StateFlow<RecordState> = _state

    /** Bar heights so far (see [Waveform]). Only touched on the main thread. */
    val levels = ArrayList<Int>()

    private var recorder: Recorder? = null
    private var ticker: Job? = null
    private var lowWarned = false

    /** Elapsed time before the current stretch of recording, and when that stretch began. */
    private var elapsedBefore = 0L
    private var stretchStart = 0L

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        // First visit, or back from LightOS's permission request.
        val phase = _state.value.phase
        if (phase == RecordPhase.Checking || phase is RecordPhase.NeedsPermission) checkMicrophone()
    }

    override fun onAppPause() {
        super.onAppPause()
        // Android stops apps in the background from hearing the microphone, so leaving Memo
        // (or the screen being switched off) saves the recording rather than recording silence.
        if (recorder != null) stop()
    }

    private fun checkMicrophone() {
        viewModelScope.launch {
            val result = checkPermission(Manifest.permission.RECORD_AUDIO).getOrNull()?.permissionResult
            val current = _state.value.phase
            when (result) {
                GetPermission.Result.Granted -> start()
                GetPermission.Result.BlockedByServer ->
                    _state.update { it.copy(phase = RecordPhase.NeedsPermission(asked = true, blocked = true)) }
                else -> _state.update {
                    it.copy(phase = RecordPhase.NeedsPermission(asked = (current as? RecordPhase.NeedsPermission)?.asked == true))
                }
            }
        }
    }

    /** The screen has shown LightOS's permission request. */
    fun permissionAsked() = _state.update { it.copy(phase = RecordPhase.NeedsPermission(asked = true)) }

    fun retryPermission() = _state.update { it.copy(phase = RecordPhase.NeedsPermission(asked = false)) }

    private suspend fun start() {
        if (recorder != null) return
        val storage = withContext(Dispatchers.IO) { storageInfo(filesDir) }
        if (storage.level == StorageLevel.Full) {
            _state.update { it.copy(phase = RecordPhase.Finished(null), notice = lowStorageMessage(storage)) }
            return
        }
        val name = RecordingNames.default(LocalDateTime.now())
        val newRecorder = Recorder(store.inProgressFile)
        val started = withContext(Dispatchers.IO) { runCatching { newRecorder.start() } }
        if (started.isFailure) {
            newRecorder.release()
            store.discardInProgress()
            _state.update { it.copy(phase = RecordPhase.Failed("Couldn't start the microphone. Another app may be using it.")) }
            return
        }
        recorder = newRecorder
        elapsedBefore = 0
        stretchStart = SystemClock.elapsedRealtime()
        lowWarned = storage.level == StorageLevel.Low
        _state.update {
            it.copy(
                phase = RecordPhase.Recording,
                name = name,
                notice = if (lowWarned) lowStorageMessage(storage) else null,
            )
        }
        ticker = viewModelScope.launch { tick() }
    }

    /** Every [Waveform.SAMPLE_MILLIS]: a new bar and the time; every second: the free space. */
    private suspend fun tick() {
        var count = 0
        while (true) {
            delay(Waveform.SAMPLE_MILLIS)
            val rec = recorder ?: return
            if (_state.value.phase != RecordPhase.Recording) continue
            levels += Waveform.level(rec.amplitude())
            _state.update { it.copy(samples = levels.size, elapsedMillis = elapsed()) }
            if (++count % Waveform.SAMPLES_PER_SECOND == 0) checkStorage()
        }
    }

    private suspend fun checkStorage() {
        val storage = withContext(Dispatchers.IO) { storageInfo(filesDir) }
        when (storage.level) {
            StorageLevel.Full -> stop(
                notice = "Phone storage is almost full (${storage.describe()}), so Memo stopped recording " +
                    "and saved what it had.",
            )
            StorageLevel.Low -> if (!lowWarned) {
                lowWarned = true
                _state.update { it.copy(notice = lowStorageMessage(storage)) }
            }
            StorageLevel.Ok -> Unit
        }
    }

    private fun elapsed() =
        elapsedBefore + if (_state.value.phase == RecordPhase.Recording) SystemClock.elapsedRealtime() - stretchStart else 0

    fun pause() {
        val rec = recorder ?: return
        if (_state.value.phase != RecordPhase.Recording) return
        runCatching { rec.pause() }
        elapsedBefore = elapsed()
        _state.update { it.copy(phase = RecordPhase.Paused, elapsedMillis = elapsedBefore) }
    }

    fun resume() {
        val rec = recorder ?: return
        if (_state.value.phase != RecordPhase.Paused) return
        runCatching { rec.resume() }
        stretchStart = SystemClock.elapsedRealtime()
        _state.update { it.copy(phase = RecordPhase.Recording) }
    }

    /** Stops and saves. With [notice], says why before going back to the list. */
    fun stop(notice: String? = null) {
        val rec = recorder ?: run {
            _state.update { it.copy(phase = RecordPhase.Finished(null)) }
            return
        }
        recorder = null
        ticker?.cancel()
        val name = _state.value.name
        val finalLevels = levels.toList()
        _state.update { it.copy(phase = RecordPhase.Saving, elapsedMillis = elapsed(), notice = notice) }
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                if (rec.stop()) {
                    store.saveInProgress(name, finalLevels)
                } else {
                    store.discardInProgress()
                    null
                }
            }
            _state.update { it.copy(phase = RecordPhase.Finished(saved)) }
        }
    }

    fun closeNotice() = _state.update { it.copy(notice = null) }

    override fun onCleared() {
        // The screen went away without stopping (shouldn't happen): keep what was recorded.
        recorder?.let { rec ->
            ticker?.cancel()
            if (rec.stop()) store.saveInProgress(_state.value.name, levels.toList()) else store.discardInProgress()
        }
        recorder = null
        super.onCleared()
    }
}

/** Records a new memo. Starts as soon as it opens (once the microphone is allowed). */
class RecordScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, RecordViewModel>(sealedActivity) {

    override val viewModelClass: Class<RecordViewModel>
        get() = RecordViewModel::class.java

    override fun createViewModel() =
        RecordViewModel(RecordingStore(lightContext.filesDir, ::mediaDuration), lightContext.filesDir)

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        val permissionLauncher = rememberPermissionRequestLauncher(Manifest.permission.RECORD_AUDIO)
        val phase = state.phase

        LaunchedEffect(phase) {
            if (phase is RecordPhase.NeedsPermission && !phase.asked && !phase.blocked && permissionLauncher != null) {
                viewModel.permissionAsked()
                permissionLauncher.launch()
            }
        }
        // Back to the list once saved, after any notice has been read.
        LaunchedEffect(phase, state.notice) {
            if (phase is RecordPhase.Finished && state.notice == null) goBack()
        }

        MemoTheme {
            when (phase) {
                is RecordPhase.NeedsPermission -> MessageScreen(
                    title = "Microphone",
                    message = if (phase.blocked) {
                        "LightOS isn't letting Memo use the microphone."
                    } else {
                        "Memo needs the microphone to record. Recordings stay on this phone."
                    },
                    onBack = { goBack() },
                    action = if (phase.blocked) null else "ALLOW MICROPHONE",
                    onAction = viewModel::retryPermission,
                )
                is RecordPhase.Failed -> MessageScreen(title = "Memo", message = phase.message, onBack = { goBack() })
                else -> RecordContent(
                    state = state,
                    levels = viewModel.levels,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onStop = { viewModel.stop() },
                )
            }
            state.notice?.let { FullScreenNotice(it, onClose = viewModel::closeNotice) }
        }
    }
}

@Composable
internal fun RecordContent(
    state: RecordState,
    levels: List<Int>,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val unit = 1f.gridUnitsAsDp()
    val phase = state.phase
    val title = when (phase) {
        RecordPhase.Paused -> "Paused"
        RecordPhase.Saving, is RecordPhase.Finished -> "Saving"
        else -> "Recording"
    }
    Column(modifier = Modifier.fillMaxSize()) {
        // Back saves, like stop: a recording is never thrown away by accident.
        LightTopBar(leftButton = backButton(onStop), center = LightTopBarCenter.Text(title))
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LightText(
                text = formatDuration(state.elapsedMillis),
                variant = LightTextVariant.Title,
                monospace = true,
                modifier = Modifier.padding(top = unit),
            )
            LightText(text = state.name, variant = LightTextVariant.Detail, lighten = true)
            WaveformView(
                levels = levels,
                position = state.samples.toFloat(),
                totalSamples = state.samples,
                dotsBeyondEnd = true,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = unit),
            )
        }
        LightBottomBar(
            items = listOf(
                when (phase) {
                    RecordPhase.Recording -> LightBarButton.LightIcon(icon = LightIcons.PAUSE, onClick = onPause, contentDescription = "Pause")
                    RecordPhase.Paused -> LightBarButton.LightIcon(icon = LightIcons.MICROPHONE, onClick = onResume, contentDescription = "Resume")
                    else -> null
                },
                if (phase == RecordPhase.Recording || phase == RecordPhase.Paused) {
                    LightBarButton.LightIcon(icon = LightIcons.STOP, onClick = onStop, contentDescription = "Stop and save")
                } else {
                    null
                },
            ),
            modifier = Modifier.padding(bottom = unit),
        )
    }
}
