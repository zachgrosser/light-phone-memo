package com.zachgrosser.memo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightProgressBar
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val SOURCE_URL = "https://github.com/zachgrosser/light-phone-memo"

data class AboutState(
    val storage: StorageInfo? = null,
    val recordingCount: Int = 0,
    val recordingBytes: Long = 0,
)

class AboutScreen(sealedActivity: SealedLightActivity) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        var state by remember { mutableStateOf(AboutState()) }
        LaunchedEffect(Unit) {
            state = withContext(Dispatchers.IO) {
                val files = recordingStore(lightContext.filesDir).files()
                AboutState(
                    storage = storageInfo(lightContext.filesDir),
                    recordingCount = files.size,
                    recordingBytes = files.sumOf { it.length() },
                )
            }
        }
        MemoTheme { AboutContent(state, onBack = { goBack() }) }
    }
}

@Composable
internal fun AboutContent(state: AboutState, onBack: () -> Unit) {
    val unit = 1f.gridUnitsAsDp()
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(leftButton = backButton(onBack), center = LightTopBarCenter.Text("About"))
        LightScrollView(modifier = Modifier.weight(1f).fillMaxWidth().padding(start = unit)) {
            ListHeading("Privacy")
            Note(
                "Recordings always stay on this phone. Memo has no internet access: nothing is uploaded, " +
                    "and there are no accounts, ads or tracking.",
            )
            Note(
                "To copy a recording to a computer, use Light's Tool Manager over your own Wi-Fi " +
                    "(a recording's Share option explains how).",
            )

            ListHeading("Storage")
            state.storage?.let { storage ->
                Note(storage.describe())
                Box(modifier = Modifier.padding(end = unit, top = unit * 0.25f, bottom = unit * 0.75f)) {
                    LightProgressBar(colors = LightThemeTokens.colors, progress = 1f - storage.freeFraction)
                }
                Note(
                    when (state.recordingCount) {
                        0 -> "No recordings yet."
                        1 -> "1 recording, using ${formatBytes(state.recordingBytes)}."
                        else -> "${state.recordingCount} recordings, using ${formatBytes(state.recordingBytes)}."
                    },
                )
                Note("Memo warns when 10% of storage is left, and stops recording at 2% so the phone keeps working.")
            }

            ListHeading("Memo")
            Note("Version ${BuildConfig.VERSION_NAME}. Open source under the MIT License: $SOURCE_URL")
            Spacer(Modifier.height(unit))
        }
    }
}
