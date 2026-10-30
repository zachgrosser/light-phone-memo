package com.zachgrosser.memo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class HomeState(
    val loaded: Boolean = false,
    val recordings: List<Recording> = emptyList(),
    val storage: StorageInfo? = null,
    /** True while the full-screen low-storage notice is showing. */
    val showLowStorage: Boolean = false,
)

/** Shown once each time Memo starts, so the warning isn't repeated on every visit to the list. */
private var lowStorageNoticeShown = false

class HomeViewModel(private val store: RecordingStore, private val filesDir: File) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    init {
        // A recording still "in progress" means Memo was closed mid-recording; it can't be played.
        viewModelScope.launch(Dispatchers.IO) { store.discardInProgress() }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        // Back from recording, renaming or deleting, or from the Tool Manager changing files.
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val (recordings, storage) = withContext(Dispatchers.IO) { store.list() to storageInfo(filesDir) }
            val warn = storage.level != StorageLevel.Ok && !lowStorageNoticeShown
            if (warn) lowStorageNoticeShown = true
            _state.update {
                it.copy(loaded = true, recordings = recordings, storage = storage, showLowStorage = it.showLowStorage || warn)
            }
        }
    }

    fun closeLowStorage() = _state.update { it.copy(showLowStorage = false) }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, HomeViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    override fun createViewModel() =
        HomeViewModel(RecordingStore(lightContext.filesDir, ::mediaDuration), lightContext.filesDir)

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        MemoTheme {
            HomeContent(
                state = state,
                onOpen = { name -> navigateTo({ PlayerScreen(it, name) }) },
                onRecord = { navigateTo({ RecordScreen(it) }) },
                onAbout = { navigateTo({ AboutScreen(it) }) },
            )
            if (state.showLowStorage && state.storage != null) {
                FullScreenNotice(lowStorageMessage(state.storage!!), onClose = viewModel::closeLowStorage)
            }
        }
    }
}

fun lowStorageMessage(storage: StorageInfo): String =
    if (storage.level == StorageLevel.Full) {
        "Phone storage is almost full: ${storage.describe()}.\n\nMemo can't record until some space is freed."
    } else {
        "Phone storage is running low: ${storage.describe()}.\n\nMemo stops recording when 2% is left, so the phone keeps working."
    }

@Composable
internal fun HomeContent(
    state: HomeState,
    onOpen: (String) -> Unit,
    onRecord: () -> Unit,
    onAbout: () -> Unit,
) {
    val unit = 1f.gridUnitsAsDp()
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(center = LightTopBarCenter.Text("Memo"))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LightScrollView(modifier = Modifier.fillMaxSize().padding(start = unit)) {
                when {
                    !state.loaded -> Unit
                    state.recordings.isEmpty() -> Note("No recordings yet. Tap the microphone to start one.")
                    else -> state.recordings.forEach { recording ->
                        ListRow(
                            title = recording.name,
                            onClick = { onOpen(recording.name) },
                            trailing = recording.durationMillis?.let(::formatDuration) ?: "–",
                        )
                    }
                }
                Spacer(Modifier.height(unit))
            }
        }
        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(icon = LightIcons.SETTINGS, onClick = onAbout, contentDescription = "About and storage"),
                LightBarButton.LightIcon(icon = LightIcons.MICROPHONE, onClick = onRecord, contentDescription = "Record"),
                null,
            ),
            modifier = Modifier.padding(bottom = unit),
        )
    }
}
