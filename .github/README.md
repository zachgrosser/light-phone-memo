# light-phone-memo

A tool for the **Light Phone III**, built on [Light's official SDK](https://github.com/lightphone/light-sdk).

## Memo

A simple voice recorder. Record, pause and carry on, then play it back at whatever speed you like, dragging through the waveform to find your place. Recordings stay on your phone; you can copy them to a computer over Wi-Fi with Light's Tool Manager.

More about how it works: [`tool/README.md`](../tool/README.md).

## Where to start

| I want to… | Read |
|---|---|
| Set up my computer, run Memo on the emulator, run the tests | [DEVELOPING.md](../DEVELOPING.md) |
| Work on this with others (branches, pull requests, reviews) | [COLLABORATING.md](../COLLABORATING.md) |
| Send Memo to Light for their Tool Library | [SUBMITTING.md](../SUBMITTING.md) |
| Know what Light accepts | Light's [TOOL_GUIDELINES.md](../TOOL_GUIDELINES.md) |
| Understand the SDK itself | Light's [README](../README.md) and [docs](../docs) |

## What's in this repo

This repo is a copy of Light's SDK with our tool added. **Our code lives in `tool/`**; everything else comes from Light and is updated from their repo.

| Folder / file | What it is | Do we edit it? |
|---|---|---|
| `tool/` | **Memo**: its code, tests, settings (`lighttool.toml`) and license | ✅ Yes |
| `scripts/emulator.sh` | Sets up and runs Light's emulator on a Mac | ✅ Yes |
| `.github/` | This page, our build workflow, pull request and issue templates | ✅ Yes |
| `DEVELOPING.md`, `COLLABORATING.md`, `SUBMITTING.md` | Our guides | ✅ Yes |
| `CLAUDE.md` | Notes Claude Code reads at the start of every session (also a good summary for people) | ✅ Yes |
| `examples/` | Light's sample tools (weather, authenticator, …) | 👀 Read for ideas |
| `docs/`, `README.md`, `TOOL_GUIDELINES.md`, `CONTRIBUTING.md` | Light's documentation | 👀 Read |
| `sdk/`, `plugin/`, `lint-rules/`, `builder/`, `signer/`, … | The SDK itself | ❌ No, it comes from Light |

> GitHub shows this file (`.github/README.md`) as the front page instead of Light's README, so updates from Light never clash with it.

Our other Light Phone tools live in their own repos: **Wiki** in [light-phone-tools](https://github.com/zachgrosser/light-phone-tools) and **House** in [light-phone-house](https://github.com/zachgrosser/light-phone-house). Light builds one tool per repo (from its `tool/` folder), so each has its own.

## Licenses

- Memo's code (`tool/`): [MIT](../tool/LICENSE).
- Light's SDK (everything else): [MIT](../LICENSE), © The Light Phone.

This project isn't made by, affiliated with or endorsed by Light.

## Who's working on this

- [@zachgrosser](https://github.com/zachgrosser)
- *(add your name here in your first pull request)*
