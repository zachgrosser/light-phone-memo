# CLAUDE.md

Notes for Claude Code (and anyone new) working in this repo. Read this first; the guides it links to have the details.

## What this repo is

A copy of [Light's SDK](https://github.com/lightphone/light-sdk) with one tool of ours added: **Memo**, a voice recorder for the Light Phone III (and later the Light Flip), in `tool/`. Our other tools are in separate repos (Wiki in `light-phone-tools`, House in `light-phone-house`): Light builds one tool per repo.

- **Ours, edit freely:** `tool/`, `scripts/`, `.github/README.md`, `.github/workflows/tool.yml`, `.github/pull_request_template.md`, `.github/ISSUE_TEMPLATE/`, `DEVELOPING.md`, `COLLABORATING.md`, `SUBMITTING.md`, this file.
- **Light's, leave alone:** everything else (`sdk/`, `plugin/`, `lint-rules/`, `builder/`, `examples/`, `docs/`, `README.md`, Light's workflows…). It's updated by merging Light's repo, and edits there cause clashes. Suggestions for the SDK go to Light, not here.
- How Memo works, file by file, and where it keeps recordings: [`tool/README.md`](tool/README.md).

Explain changes in plain words. When the person you're working with needs to do something themselves, give the exact commands or clicks.

## Commands

**Java 17 is required.** Light's plugin and lint rules use `jvmToolchain(17)`; newer JDKs fail ("Cannot find a Java installation matching languageVersion=17", or Gradle 9 refusing Java 25). Don't accept Android Studio's AGP Upgrade Assistant: Light picks those versions.

```bash
./gradlew :tool:testDebugUnitTest -DlightSdk.toolOnly=true      # unit tests
./gradlew :tool:assembleRelease -DlightSdk.toolOnly=true -DlightSdk.abiFilters=arm64-v8a   # as Light's server builds it (R8 on)
./gradlew :tool:assembleDebug -DlightSdk.toolOnly=true          # debug APK
```

`-DlightSdk.toolOnly=true` skips Light's example tools and saves minutes.

**Emulator** (macOS; see [DEVELOPING.md §2](DEVELOPING.md#2-lights-emulator)):

```bash
scripts/emulator.sh setup LightPhone   # once: API 34 "Android Open Source" ARM64 image, installs Light's emulator as a system app
scripts/emulator.sh start              # start the virtual phone in the mode Light's emulator needs (-writable-system)
scripts/emulator.sh install            # rebuild Memo and install it
scripts/emulator.sh screen flip|lp3    # Light Flip (480×640) or Light Phone III (1080×1240) screen size
```

`install` temporarily switches `serverPackage` to the emulator's (`com.thelightphone.sdk.emulator`) and restores `com.lightos` afterwards. Memo only shows up in the emulator's tool list with **Settings → Allowed Tools → All Tools**. With the emulator running, `adb` works as usual (`adb logcat`, `adb exec-out screencap -p > shot.png`, `adb shell input text …`).

Quickest layout check: the previews in `tool/src/main/kotlin/com/zachgrosser/memo/MemoPreviews.kt` show every screen at both device sizes.

## Light's rules (the build fails, or Light rejects the tool, otherwise)

- **Light's build server only takes** `tool/build.gradle.kts`, `tool/lighttool.toml` and `tool/src/main/{kotlin,java,res,assets}`, then runs `:tool:assembleRelease -DlightSdk.unsigned=true -DlightSdk.abiFilters=arm64-v8a`. Nothing else in the repo ships. See `builder/README.md`.
- **The build server compiles against its own pinned SDK, not ours.** In October 2026 that was **v0.2.0**, older than the SDK in this repo (Wiki's first build failed on an API added later). Only use SDK APIs that exist in the server's version. To check, build `tool/` inside a `git worktree` of that tag (e.g. `v0.2.0`) with the release command above. Memo 0.1.0 was checked this way.
- **Never write an `AndroidManifest.xml`**, and don't set `applicationId`, `versionCode`, `versionName` or `namespace` in `build.gradle.kts`: the plugin generates them from `lighttool.toml`. No `buildscript {}`, `apply(...)`, `resolutionStrategy` or custom source dirs.
- **Forbidden in tool code** (checked at build time; full list in `plugin/src/main/kotlin/com/thelightphone/plugin/LightSdkPlugin.kt`, `BLOCKED_IMPORTS` and `BLOCKED_CODE_PATTERNS`): `android.app.*`, `android.content.Context`/`Intent`, `LocalContext`, `LocalView`, `LocalActivity`, `LocalLifecycleOwner`, `androidx.activity.*`, reflection (including any `.java.` chain), `getSystemService`, `startActivity`, casts to Activity/Context. Use the SDK instead: `navigateTo`/`goBack`, `lightContext.filesDir`, `checkPermission` + `rememberPermissionRequestLauncher`, and for vibration Compose's `LocalHapticFeedback` (`sealedActivity.vibrator` isn't on the build server's SDK).
- **Dependencies** must be on Light's allow-list (`plugin/src/main/resources/com/thelightphone/plugin/allowed-dependencies.txt`). Only what `:sdk:client` exposes with `api` is on the compile classpath. There is no speech-recognition library on the list, which is why Memo has no transcription.
- **`tool/lighttool.toml`:** `id = "com.zachgrosser.memo"` can never change once submitted. `serverPackage` must be `com.lightos` when committed. Raise `versionCode` by one for every build sent to Light. Memo declares `RECORD_AUDIO` and the `tool-manager-provider` capability (see below), and no `INTERNET`.
- Light's [TOOL_GUIDELINES.md](TOOL_GUIDELINES.md): intentional, private, no ads or tracking, open source, Light's design components.

## Careful areas in Memo

- **Recordings never leave the phone on their own.** Memo has no `INTERNET` permission; keep it that way. The only way out is Light's Tool Manager (a page on a computer on the same Wi-Fi), which serves `files/shared/`. The About screen and `tool/README.md` say this; keep them true.
- **Where files live (`RecordingStore.kt`):** recordings are `files/shared/recordings/<name>.m4a` (the name *is* the file name), waveforms `files/waveforms/<name>.wave`, and the recording being made is `files/in-progress.m4a`, moved into `recordings` only when it's finished so the Tool Manager never offers a half-written file. Rename and delete move both files. Files added through the Tool Manager have no waveform; the player shows dots instead.
- **Names (`Models.kt`):** the default is the start time, `YYYY.MM.DD HH.MM` in 24-hour time. The time uses a dot, not a colon, because colons can't be in file names on Windows. `RecordingNames.clean` strips characters file systems reject; clashes get " (2)". Numbers are formatted with `Locale.ROOT`. Keep `ModelsTest` and `RecordingStoreTest` passing.
- **Storage (`StorageLevel`):** a full-screen warning at 10% free; at 2% Memo won't start recording and stops one in progress (saving it), so the phone keeps working. Checked once a second while recording.
- **Leaving Memo stops the recording** (`RecordViewModel.onAppPause`): Android doesn't let background apps hear the microphone without a foreground service, which tools can't declare. That includes switching the screen off with the power button. Memo keeps the screen on (`Modifier.keepScreenOn()` in `MemoTheme`) so the screen timeout doesn't end recordings.
- **The waveform (`WaveformView` in `MemoUi.kt`):** Google Recorder style. One bar per 100 ms (`Waveform.SAMPLES_PER_SECOND`), log-scaled from `MediaRecorder.getMaxAmplitude()`. The centre line is "now"; dragging the waveform scrubs. Light's colours only (`LightThemeTokens`).
- **Screen sizes:** no orientation lock (Android letterboxes portrait-locked apps on the near-square Light Phone III screen). Size things with the SDK's `gridUnitsAsDp()`, and check both sizes in `MemoPreviews.kt`.
- **Unit tests run on the JVM without Android.** Keep logic that needs testing free of `android.*` classes (as `Models.kt` and `RecordingStore.kt` are); Android's audio classes are only in `Audio.kt`.
- **On the emulator** the microphone usually hears silence, so the waveform is a row of dots. The previews show real-looking bars.

## Open questions

- **Transcription and "send to Notes"** were asked for but aren't possible yet: Android's speech recognizer needs `Context`/`Intent` (blocked) and Google services (not on LightOS), no on-device speech library is on Light's allow-list, and the SDK has no way to hand text to another tool. Ask Light.
- **Sharing** is only through the Tool Manager; Light phones have no share menu.
- **Android's Back button skips `onBackPressed`** in Light's SDK, so system Back leaves the current screen without Memo's handling. On the recording screen that means `onAppPause`/`onCleared` save the recording; the top bar's back arrow saves too.
- **Light Flip** (ships April 2027): Light hasn't published how its keypad reaches tools or whether it has a touchscreen. Without one, the list and the player would need keypad controls. The Light Phone III's shutter button could start and stop recordings; not done yet.

## Git and GitHub

- Never commit to `main`. Work on a branch, open a pull request, and let both checks pass: **Tool** (ours: tests, release and debug builds, downloadable APKs) and **PR Check** (Light's, about 12 minutes).
- Normal pull requests: **Squash and merge**. Pull requests that bring in Light's SDK: **Create a merge commit**, never squash, or every later SDK update clashes. How to update: [DEVELOPING.md §6](DEVELOPING.md#6-getting-lights-latest-sdk).
- Releasing to Light's Tool Library: follow [SUBMITTING.md](SUBMITTING.md). Making the repo public is the owner's decision.
- Feedback and bugs: GitHub issues on this repo.
