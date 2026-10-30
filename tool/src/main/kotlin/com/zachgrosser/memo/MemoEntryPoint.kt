package com.zachgrosser.memo

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.toolmanager.ClientLeafNode
import com.thelightphone.toolmanager.ClientToolManifest
import com.thelightphone.toolmanager.FileBrowserSpec

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
