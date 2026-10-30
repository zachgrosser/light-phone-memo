package com.zachgrosser.memo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.LightTouchableProgressBar
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerState(
    val name: String,
    val loaded: Boolean = false,
    val failed: Boolean = false,
    val durationMillis: Long = 0,
    val positionMillis: Long = 0,
    val playing: Boolean = false,
    val speed: Float = 1f,
    /** The waveform drawn while recording, or empty for files added from a computer. */
    val levels: List<Int> = emptyList(),
    /** Set once the recording has been deleted: go back to the list. */
    val deleted: Boolean = false,
)

class PlayerViewModel(private val store: RecordingStore, name: String) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(PlayerState(name))
    val state: StateFlow<PlayerState> = _state

    private var player: Player? = null
    private var ticker: Job? = null
    private var scrubbing = false
    private var playAfterScrub = false

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { Player(store.fileFor(name)) to store.waveform(name).orEmpty() }.getOrNull()
            }
            if (loaded == null) {
                _state.update { it.copy(loaded = true, failed = true) }
                return@launch
            }
            val (newPlayer, levels) = loaded
            player = newPlayer
            newPlayer.setOnCompletion {
                ticker?.cancel()
                _state.update { it.copy(playing = false, positionMillis = it.durationMillis) }
            }
            _state.update { it.copy(loaded = true, durationMillis = newPlayer.durationMillis, levels = levels) }
        }
    }

    override fun onAppPause() {
        super.onAppPause()
        pause()
    }

    fun togglePlay() = if (_state.value.playing) pause() else play()

    fun play() {
        val p = player ?: return
        val s = _state.value
        if (s.positionMillis >= s.durationMillis - 50) p.seekTo(0)
        p.play()
        _state.update { it.copy(playing = true) }
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                if (!scrubbing) _state.update { it.copy(positionMillis = p.positionMillis) }
                delay(33)
            }
        }
    }

    fun pause() {
        val p = player ?: return
        p.pause()
        ticker?.cancel()
        _state.update { it.copy(playing = false, positionMillis = p.positionMillis) }
    }

    fun seekTo(millis: Long) {
        val p = player ?: return
        val target = millis.coerceIn(0, _state.value.durationMillis)
        p.seekTo(target)
        _state.update { it.copy(positionMillis = target) }
    }

    fun skip(byMillis: Long) = seekTo(_state.value.positionMillis + byMillis)

    fun cycleSpeed() {
        val speed = PlaybackSpeeds.next(_state.value.speed)
        player?.setSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    // Dragging the waveform: pause while dragging, so the sound doesn't stutter, then carry on.
    fun scrubStart() {
        scrubbing = true
        playAfterScrub = _state.value.playing
        if (playAfterScrub) pause()
    }

    fun scrubTo(position: Float) {
        _state.update { it.copy(positionMillis = Waveform.millisAt(position).coerceIn(0, it.durationMillis)) }
    }

    fun scrubEnd() {
        scrubbing = false
        seekTo(_state.value.positionMillis)
        if (playAfterScrub) play()
    }

    fun renamed(newName: String) = _state.update { it.copy(name = newName) }

    fun delete() {
        pause()
        val name = _state.value.name
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                player?.release()
                player = null
                store.delete(name)
            }
            if (ok) _state.update { it.copy(deleted = true) }
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        player?.release()
        player = null
        super.onCleared()
    }
}

/** What the options screen did to the recording. */
sealed interface OptionsResult {
    data class Renamed(val newName: String) : OptionsResult
    data object Deleted : OptionsResult
}

/** Plays a recording: the waveform to drag through, skip back and forward, and speed. */
class PlayerScreen(
    sealedActivity: SealedLightActivity,
    private val name: String,
) : LightScreen<Unit, PlayerViewModel>(sealedActivity) {

    override val viewModelClass: Class<PlayerViewModel>
        get() = PlayerViewModel::class.java

    override fun createViewModel() = PlayerViewModel(RecordingStore(lightContext.filesDir, ::mediaDuration), name)

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        LaunchedEffect(state.deleted) { if (state.deleted) goBack() }
        MemoTheme {
            if (state.failed) {
                MessageScreen(title = state.name, message = "This recording can't be played.", onBack = { goBack() })
            } else {
                PlayerContent(
                    state = state,
                    onBack = { goBack() },
                    onOptions = {
                        viewModel.pause()
                        navigateTo({ OptionsScreen(it, state.name) }) { result ->
                            when (result) {
                                is OptionsResult.Renamed -> viewModel.renamed(result.newName)
                                OptionsResult.Deleted -> viewModel.delete()
                            }
                        }
                    },
                    onTogglePlay = viewModel::togglePlay,
                    onSkip = viewModel::skip,
                    onSeekFraction = { viewModel.seekTo((it * state.durationMillis).toLong()) },
                    onSpeed = viewModel::cycleSpeed,
                    onScrubStart = viewModel::scrubStart,
                    onScrub = viewModel::scrubTo,
                    onScrubEnd = viewModel::scrubEnd,
                )
            }
        }
    }
}

const val SKIP_MILLIS = 15_000L

@Composable
internal fun PlayerContent(
    state: PlayerState,
    onBack: () -> Unit,
    onOptions: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkip: (Long) -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSpeed: () -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
) {
    val unit = 1f.gridUnitsAsDp()
    val totalSamples = Waveform.samplesFor(state.durationMillis)
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = backButton(onBack),
            center = LightTopBarCenter.Text(state.name),
            rightButton = LightBarButton.LightIcon(icon = LightIcons.ELLIPSES, onClick = onOptions, contentDescription = "Rename, share or delete"),
        )
        WaveformView(
            levels = state.levels,
            position = Waveform.positionOf(state.positionMillis),
            totalSamples = totalSamples,
            onScrubStart = onScrubStart,
            onScrub = onScrub,
            onScrubEnd = onScrubEnd,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = unit * 0.5f),
        )
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = unit * 1.5f)) {
            LightTouchableProgressBar(
                colors = LightThemeTokens.colors,
                progress = if (state.durationMillis > 0) state.positionMillis.toFloat() / state.durationMillis else 0f,
                onValueChange = onSeekFraction,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = unit * 0.5f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeText(text = formatDuration(state.positionMillis), style = LightThemeTokens.typography.detail)
                Row(modifier = Modifier.weight(1f), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    LightText(
                        text = PlaybackSpeeds.label(state.speed),
                        variant = LightTextVariant.Button,
                        modifier = Modifier.lightClickable(onClick = onSpeed).padding(horizontal = unit, vertical = unit * 0.25f),
                    )
                }
                TimeText(text = formatDuration(state.durationMillis), style = LightThemeTokens.typography.detail, lighten = true)
            }
        }
        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(icon = LightIcons.SKIP_BACKWARD_FIFTEEN, onClick = { onSkip(-SKIP_MILLIS) }, contentDescription = "Back 15 seconds"),
                LightBarButton.LightIcon(
                    icon = if (state.playing) LightIcons.PAUSE else LightIcons.PLAY,
                    onClick = onTogglePlay,
                    contentDescription = if (state.playing) "Pause" else "Play",
                ),
                LightBarButton.LightIcon(icon = LightIcons.SKIP_FORWARD_FIFTEEN, onClick = { onSkip(SKIP_MILLIS) }, contentDescription = "Forward 15 seconds"),
            ),
            topPadding = unit * 0.5f,
            modifier = Modifier.padding(bottom = unit),
        )
    }
}
