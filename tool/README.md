# Memo

A voice recorder for the Light Phone III (and later the Light Flip).

- **Record:** tap the microphone. Pause and resume as often as you like; stop (or the back arrow) saves. A line drawing of the sound scrolls past a centre line as you speak, like Google Recorder's.
- **Play:** tap a recording. Drag the waveform sideways (or tap the progress line) to move through it, skip 15 seconds back or forward, and tap the speed (1×, 1.25×, 1.5×, 2×, 0.5×, 0.75×) to change it.
- **Rename, share, delete:** the ••• button on a recording.
- **Names:** a new recording is named after when it started, such as `2026.10.30 1405` (year.month.day, 24-hour time). There's no colon in the time because colons aren't allowed in file names on Windows.
- **The screen stays on** while Memo is open, whatever the phone's screen timeout is.
- **Storage:** About shows how much space is free. Below 10% free, Memo shows a full-screen warning. At 2% it won't start a recording and stops one in progress (keeping what was recorded), so the phone keeps working.

## Privacy and sharing

Recordings always stay on the phone. Memo has no internet permission, no accounts, ads or tracking.

Light phones have no share menu, so recordings are shared through **Light's Tool Manager**: with the phone and a computer on the same Wi-Fi, open the Tool Manager in the computer's browser and choose **Memo → Recordings** to download them (or to add `.m4a` files, which then appear in Memo's list). The files go straight from the phone to the computer.

## Limits

- **Leaving Memo stops a recording** and saves it. That includes switching the screen off with the power button. Android doesn't let apps in the background hear the microphone unless they run a foreground service, and Light's tools can't.
- **No transcription.** Android's speech recognizer isn't available to Light tools, and no speech library is on Light's list of allowed libraries. Light's SDK also has no way to send text to the Notes tool.

## Light's Tool Guidelines

How Memo meets each of Light's [Tool Guidelines](../TOOL_GUIDELINES.md):

| Guideline | Memo |
|---|---|
| Intentionality | One job: record and play back. No feeds, notifications or anything that asks to be checked. |
| Privacy | No internet permission, analytics or tracking. Recordings leave the phone only when the person downloads them with Light's Tool Manager; About and Share say so. |
| No advertising | None. |
| Open source | MIT ([LICENSE](LICENSE)). |
| SDK compliance | Built with Light's SDK and checked against the version Light's build server uses (v0.2.0). Microphone access goes through LightOS (`checkPermission`, `rememberPermissionRequestLauncher`); sharing through the SDK's Tool Manager support. |
| Design | Light's components throughout: top and bottom bars, icons, text styles, lists, progress line, text entry and full-screen notice, in the person's light or dark theme. The waveform and the timers are drawn in Light's colours and typeface. |
| Longevity & maintenance | Works entirely offline. No dependencies beyond Light's SDK; standard .m4a audio any computer can play. |
| Legal | No third-party services or content. |

## Files

All in `src/main/kotlin/com/zachgrosser/memo/`:

| File | What it does |
|---|---|
| `Models.kt` | Memo's rules, with no Android code: default names, cleaning typed names, storage levels (10% and 2%), formatting sizes and times, playback speeds, and the waveform's scale and file format. |
| `RecordingStore.kt` | Where recordings live: listing, saving a finished recording, renaming and deleting (audio and waveform together). |
| `Audio.kt` | The Android parts: recording with `MediaRecorder` (mono AAC .m4a, about 0.5 MB a minute), playing with `MediaPlayer`, reading lengths, and free space (`StatFs`). |
| `MemoEntryPoint.kt` | Tells Light's Tool Manager about the Recordings folder. |
| `HomeScreen.kt` | The list of recordings, and the low-storage warning when Memo opens. |
| `RecordScreen.kt` | Asking for the microphone, recording, pause and resume, the storage checks, and saving. |
| `PlayerScreen.kt` | Playback: waveform scrubbing, the progress line, ±15 seconds, speed. |
| `OptionsScreens.kt` | Rename (Light's keyboard), Share (how to use the Tool Manager), Delete (asks first). |
| `AboutScreen.kt` | Privacy, storage space and version. |
| `MemoUi.kt` | Shared pieces: the theme (which keeps the screen on), list rows, notices and the waveform drawing. |
| `MemoPreviews.kt` | Every screen at both Light screen sizes, for Android Studio's preview pane. |

## Where files are kept

Inside Memo's private storage on the phone:

| Path | What |
|---|---|
| `files/shared/recordings/<name>.m4a` | The recordings. `files/shared` is what the Tool Manager can see. |
| `files/waveforms/<name>.wave` | Each recording's waveform: one byte (0–255) per tenth of a second. Recordings added from a computer have none and show a row of dots. |
| `files/in-progress.m4a` | The recording being made. It's moved into `recordings` when it stops, so the Tool Manager never shows a half-written file. If Memo is closed mid-recording, it's discarded the next time Memo opens. |

## Permissions

- `RECORD_AUDIO`: the microphone. LightOS asks the first time a recording starts.
- `tool-manager-provider` (a Light capability): lets the Tool Manager see `files/shared`.

## License

MIT, see [LICENSE](LICENSE).
