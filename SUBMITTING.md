# Submitting Memo to Light

Light builds and signs community tools from a **public** GitHub repo, then reviews them for their Tool Library. Their steps are in [README.md, "Submitting Your Tool"](README.md#submitting-your-tool); this is our checklist around them.

## Before submitting

- [ ] **Merged into `main`.** Light reads the tool's package name from the default branch. (A pull request that brings in Light's SDK uses **Create a merge commit**, not squash.)
- [ ] **Latest Light SDK merged in.** Light requires it (see [DEVELOPING.md §6](DEVELOPING.md#6-getting-lights-latest-sdk)).
- [ ] **The Tool workflow is green on `main`**, especially the release build, which matches what Light's server runs.
- [ ] **Tried the release APK** on the emulator or a phone (DEVELOPING.md §5): allow the microphone, record, pause and resume, stop; leave Memo mid-recording (it should save); play, drag the waveform, ±15 s, each speed; rename (including a clash and an empty name), share, delete; download a recording from the Tool Manager; About.
- [ ] **Checked both screen sizes** in `MemoPreviews.kt` or with `scripts/emulator.sh screen flip`.
- [ ] **Version bumped** in `tool/lighttool.toml`: `versionCode` must be higher than the last build sent to Light; `versionName` is what people see (e.g. `1.0.0`).
- [ ] **`id` is `com.zachgrosser.memo`** and `serverPackage` is `com.lightos`. The id can never change after the first submission.
- [ ] **About reviewed:** recordings stay on the phone, no internet permission in `lighttool.toml`, and the storage figures look right.
- [ ] **Against Light's [Tool Guidelines](TOOL_GUIDELINES.md):** intentional (a recorder; no feeds or notifications), private (no internet access; recordings stay on the phone; disclosed), no ads, open source (MIT), built with the SDK, Light's design components.
- [ ] **We can explain every change in our own words.** Light's team reviews each submitted build before approving it.

## Make the repo public

**Settings → General → Danger Zone → Change visibility → Public.** Anyone will be able to see the code and its history. It contains no keys or passwords: the signing key in `sdk/keys/` is Light's public development key, and `local.properties` is never committed. Once public, turn on branch protection for `main` (**Settings → Branches**), and the source link on Memo's About screen starts working.

## On Light's dashboard

1. At [dashboard.thelightphone.com](https://dashboard.thelightphone.com): **Settings → Account → Developer Account**, turn developer mode on.
2. **Manage Custom Tools → Submit New Tool**, and give the repo: `https://github.com/zachgrosser/light-phone-memo`.
3. **Add at least one image.** Screenshots from the emulator work: the camera button on the emulator's side toolbar saves one. Good choices: the list, recording with a waveform (turn on the emulator's host microphone), and playback. Use test recordings with nothing private in their names.
4. On the tool's page, **Submit build** with:
   - **Git ref:** a tag such as `memo-v0.1.0`, made on `main` (`git tag memo-v0.1.0 && git push origin memo-v0.1.0`), or a commit SHA.
   - **Changelog:** a sentence or two.
5. Wait for the build. If it fails, **View details** under Builds shows why; **Retry build** reruns the same ref. When it works, **Download APK** appears and the version waits for Light's approval.
6. Light emails with feedback. Questions: tools@thelightphone.com.

## Suggested listing text

> **Memo** — A simple voice recorder. Pause and carry on, play back at any speed, and drag through a recording on its waveform. Rename and delete recordings, and copy them to your computer with the Tool Manager. Recordings stay on your phone: no internet access, ads or tracking.
