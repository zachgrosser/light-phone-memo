package com.zachgrosser.memo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp

sealed interface OptionsResult {
    data class Renamed(val newName: String) : OptionsResult
    data object Deleted : OptionsResult
}

class OptionsScreen(
    sealedActivity: SealedLightActivity,
    private val name: String,
) : SimpleLightScreen<OptionsResult>(sealedActivity) {

    private val store by lazy { recordingStore(lightContext.filesDir) }

    @Composable
    override fun Content() {
        var message by remember { mutableStateOf<String?>(null) }
        MemoTheme {
            OptionsContent(
                name = name,
                message = message,
                onBack = { goBack() },
                onRename = {
                    navigateTo({ TextEntryScreen(it, "Rename", name) }) { typed ->
                        when (val newName = store.rename(name, typed)) {
                            null -> message = "Couldn't rename it. Try a different name."
                            name -> Unit
                            else -> goBack(OptionsResult.Renamed(newName))
                        }
                    }
                },
                onShare = { navigateTo({ ShareScreen(it, name) }) },
                onDelete = {
                    navigateTo({ ConfirmDeleteScreen(it, name) }) { confirmed ->
                        if (confirmed) goBack(OptionsResult.Deleted)
                    }
                },
            )
        }
    }
}

@Composable
internal fun OptionsContent(
    name: String,
    message: String?,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val unit = 1f.gridUnitsAsDp()
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(leftButton = backButton(onBack), center = LightTopBarCenter.Text(name))
        LightScrollView(modifier = Modifier.weight(1f).fillMaxWidth().padding(start = unit)) {
            message?.let { Note(it) }
            ListRow(title = "Rename", onClick = onRename)
            ListRow(title = "Share", onClick = onShare, detail = "Download it to a computer")
            ListRow(title = "Delete", onClick = onDelete)
            Spacer(Modifier.height(unit))
        }
    }
}

class TextEntryScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val initialValue: String,
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val text = rememberTextFieldState(initialValue)
        val keyboardOptions = rememberKeyboardOptions()
        MemoTheme {
            LightTextInputEditor(
                title = title,
                state = text,
                keyboardOptionsFlow = keyboardOptions,
                onSubmit = { goBack(it.toString()) },
                onBack = { goBack() },
                submitLabel = "SAVE",
                singleLine = true,
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
    }
}

class ShareScreen(
    sealedActivity: SealedLightActivity,
    private val name: String,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        MemoTheme { ShareContent(name, onBack = { goBack() }) }
    }
}

@Composable
internal fun ShareContent(name: String, onBack: () -> Unit) {
    val unit = 1f.gridUnitsAsDp()
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(leftButton = backButton(onBack), center = LightTopBarCenter.Text("Share"))
        LightScrollView(modifier = Modifier.weight(1f).fillMaxWidth().padding(start = unit)) {
            Note("Copy this recording to a computer with Light's Tool Manager:")
            Note("1. Put the phone and the computer on the same Wi-Fi.")
            Note("2. Open the Tool Manager in the computer's web browser.")
            Note("3. Choose Memo, then Recordings, and download \"$name.$RECORDING_EXTENSION\".")
            Note("From there it can be sent like any other audio file. It goes straight from the phone to the computer, never through the internet.")
            Spacer(Modifier.height(unit))
        }
    }
}

class ConfirmDeleteScreen(
    sealedActivity: SealedLightActivity,
    private val name: String,
) : SimpleLightScreen<Boolean>(sealedActivity) {

    @Composable
    override fun Content() {
        MemoTheme {
            MessageScreen(
                title = "Delete",
                message = "Delete \"$name\"? It can't be brought back.",
                onBack = { goBack(false) },
                action = "DELETE",
                onAction = { goBack(true) },
            )
        }
    }
}
