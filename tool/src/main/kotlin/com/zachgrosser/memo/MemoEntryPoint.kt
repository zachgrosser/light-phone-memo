package com.zachgrosser.memo

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.toolmanager.ClientLeafNode
import com.thelightphone.toolmanager.ClientToolManifest
import com.thelightphone.toolmanager.FileBrowserSpec

/**
 * Shows Memo's recordings in Light's Tool Manager (a page in a computer's browser, on the same
 * Wi-Fi as the phone), where they can be downloaded. Files added or removed there show up in
 * Memo's list the next time it opens.
 */
@EntryPoint
object MemoEntryPoint : LightEntryPoint {
    override fun getToolManagerManifest(): ClientToolManifest = ClientToolManifest(
        title = "Memo",
        roots = listOf(
            ClientLeafNode(
                FileBrowserSpec(
                    label = "Recordings",
                    path = RecordingStore.RECORDINGS_PATH,
                    headerText = "Voice recordings from Memo, as .m4a audio files. Download one to keep it on this computer.",
                ),
            ),
        ),
    )
}
