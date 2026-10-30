# Memo

A voice recorder for the Light Phone III.

Tap the microphone to record. You can pause and carry on as often as you like, and stop (or back) saves. While you record, the sound scrolls past as a row of bars, a bit like Google's Recorder app.

Tap a recording to play it. Drag the bars to move around, skip 15 seconds either way, or tap the speed to change it (0.5× to 2×). The ••• button renames, shares or deletes it.

New recordings are named after the time they started, like `2026.10.30 1405`.

The screen stays on while Memo is open, so a long recording isn't cut short by the screen timeout. If the phone gets down to 10% free space Memo warns you, and at 2% it stops recording (and saves what it has) so the phone keeps working. About shows how much space is left.

## Privacy

Recordings stay on your phone. Memo doesn't have internet access, and there are no accounts, ads or tracking.

Light phones don't have a share menu, so sharing goes through Light's Tool Manager: with the phone and a computer on the same Wi-Fi, open the Tool Manager on the computer and go to **Memo → Recordings** to download them. Files go straight from the phone to the computer.

## Things it can't do

- Keep recording in the background. Android only lets apps use the microphone in the background with a foreground service, which Light tools can't have, so leaving Memo or turning the screen off stops and saves the recording.
- Transcribe. Android's speech recognition isn't available to Light tools, and there's no speech library on Light's allowed list.

## Light's Tool Guidelines

| | |
|---|---|
| Intentionality | Records and plays back. No feeds or notifications. |
| Privacy | No internet permission, analytics or tracking. Recordings only leave the phone if you download them with the Tool Manager, and About says so. |
| No advertising | None. |
| Open source | MIT, see [LICENSE](LICENSE). |
| SDK compliance | Built with Light's SDK, and built against v0.2.0 (the version Light's build server uses). The microphone permission goes through LightOS, and sharing uses the SDK's Tool Manager support. |
| Design | Light's own components: top and bottom bars, icons, text styles, lists, text entry, progress bar and full-screen notice, in light or dark. The bars and timers use Light's colours and font. |
| Longevity | Works offline, depends only on Light's SDK, and saves plain .m4a files. |
| Legal | No third-party services or content. |

## The code

In `src/main/kotlin/com/zachgrosser/memo/`:

- `Models.kt`: naming, storage levels, time and size formatting, speeds and the waveform scale. No Android code, so it's unit tested.
- `RecordingStore.kt`: listing, saving, renaming and deleting recordings. Also unit tested.
- `Audio.kt`: `MediaRecorder` (mono AAC), `MediaPlayer` and free space.
- `HomeScreen.kt`, `RecordScreen.kt`, `PlayerScreen.kt`, `OptionsScreens.kt`, `AboutScreen.kt`: the screens.
- `MemoUi.kt`: shared UI, including the waveform.
- `MemoEntryPoint.kt`: adds the Recordings folder to the Tool Manager.
- `MemoPreviews.kt`: every screen at Light Phone III and Light Flip sizes.

Files on the phone, in Memo's private storage:

- `files/shared/recordings/<name>.m4a`: the recordings. `files/shared` is what the Tool Manager can see.
- `files/waveforms/<name>.wave`: the bars for each recording, one byte per 100 ms. Files added from a computer don't have one and show dots instead.
- `files/in-progress.m4a`: the recording being made, moved into `recordings` when it stops.

## License

MIT, see [LICENSE](LICENSE).
