# Aire Offline Translate

A face-to-face conversation translator for Android that runs **entirely on the device**. Speech
recognition, translation and speech synthesis all happen locally — after the model is downloaded,
no text, audio or image ever leaves the phone.

Translation runs on **Gemma 4** via Google's LiteRT-LM runtime.

---

## What it does

The screen is split into two halves, the upper one rotated 180° so it faces the person across the
table. Each half shows its own language and has its own microphone, replay and copy controls.

- **Speak** — tap the microphone, speak, tap stop. The transcript appears on the speaker's half,
  the translation on the listener's, and is read aloud in the listener's language.
- **Type** — a keyboard button on the lower half for text input.
- **Photo** — pick an image and Gemma reads the text in it and translates it. Uses the model's own
  vision encoder, so every script the model knows is covered.
- **Replay / copy** — per half, so either person can hear a line again or copy it. Copy is a
  button rather than text selection because the platform's selection UI does not respect the
  rotated half.
- **Cancel** — the microphone becomes a red stop square while recording, and a red cross while
  translating or speaking, which abandons the run.

18 languages: English, Arabic, Chinese (Simplified and Traditional), Filipino, French, German,
Hebrew, Hindi, Japanese, Korean, Malay, Russian, Spanish, Tamil, Thai, Turkish, Vietnamese.

The UI itself is localised into English, Chinese (Simplified), Japanese, Spanish and Arabic, and
follows the phone's language setting.

---

## Device requirements

This is a demanding app. A 4-billion-parameter model running locally is not free.

| | |
|---|---|
| **Android** | 13 (API 33) or newer |
| **CPU** | `arm64-v8a`. 32-bit ARM is **not supported** — LiteRT-LM ships no `armeabi-v7a` binary |
| **RAM** | 8 GB or more for Gemma 4 E4B. E2B, the default, runs on less |
| **Storage** | 2.6–3.7 GB for the model, plus headroom during download |
| **Services** | Google speech recognition and text-to-speech, with language packs installed for the languages you use |

### Accelerator support

Which backend works is only knowable at runtime, so the app tries them in order and keeps the
first that can actually execute a kernel:

1. `GOOGLE_TENSOR` — Pixel devices
2. `GPU` — Snapdragon, MediaTek and anything else exposing OpenCL
3. `CPU` — works everywhere, but time-to-first-token is measured in seconds

**Pixel devices have no OpenCL driver**, so the GPU backend fails there with "Can not find OpenCL
library on this device" — hence the Tensor-specific path being tried first on that hardware.

Loading is validated by running a real one-token generation, not just `initialize()`, because
LiteRT-LM defers kernel compilation and a backend that cannot run will still initialise cleanly.

---

## Models

Nothing is bundled — the smallest build is 2.59 GB, far past Google Play's limits. Models are
downloaded on demand from Hugging Face and can be deleted from the in-app model manager.

| Variant | Size | Notes |
|---|---|---|
| Gemma 4 E2B | 2.59 GB | **Default.** Smaller and faster, for devices with less memory |
| Gemma 4 E4B | 3.66 GB | Best quality. Needs 8 GB RAM or more |

Each has four download sources tried in order. Note these are **not independent origins** — they
all terminate at the same Hugging Face CDN. They cover a blocked domain, a failed DNS lookup or a
moved branch, not an outage at the source.

A download that stops delivering bytes for 45 seconds is treated as a dead source and switched,
because `DownloadManager` reports no error for a stalled connection.

**Model weights are licensed separately from this source. See [NOTICE](NOTICE).**

---

## Build requirements

| | |
|---|---|
| **JDK** | 17 or newer. The JBR bundled with Android Studio works |
| **Android Studio** | Any version with AGP 9.x support |
| **Android Gradle Plugin** | 9.3.1 |
| **Gradle** | 9.5.0 (via the wrapper — no separate install needed) |
| **Kotlin** | 2.2.10, supplied by AGP's built-in Kotlin |
| **compileSdk / targetSdk** | 37 |
| **minSdk** | 33 |

Two build details are easy to trip over:

- **AGP 9 has built-in Kotlin.** Do **not** apply `org.jetbrains.kotlin.android` — only the
  Compose compiler plugin, pinned to the same version AGP bundles (2.2.10).
- **`kotlinx-coroutines` is pinned to 1.11.0 deliberately.** `litertlm-android:0.15.0` declares
  1.9.0 in its POM but its bytecode calls `SendChannel.close$default` as a static on the
  interface, which only exists from 1.11.0. Resolving 1.9.0 makes translation die with
  `NoSuchMethodError` the moment a stream completes. Do not let this drift back down.

---

## Building the APK

### Command line

```bash
git clone <your-repo-url>
cd gemma
```

Point Gradle at a JDK if it is not already on your `PATH`:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
```

Then build:

```bash
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk` (~78 MB, debug-signed).

For a release build:

```bash
./gradlew assembleRelease
```

Release output is **unsigned** by default. To produce an installable release you need your own
keystore and a `signingConfig` — see [Signing](#signing) below.

### Android Studio

Open the project folder, let Gradle sync, then **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

### Installing

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy the APK to the device and open it, with "Install unknown apps" enabled for whichever app
opens it.

If you already have model weights locally, you can skip the in-app download by pushing them
directly — the app checks this path at startup:

```bash
adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.aire.translate/files/models/
```

---

## Signing

`build.gradle.kts` has no release signing config. Create a keystore, then add one — and keep the
keystore and its passwords **out of version control**. `.gitignore` already excludes `*.jks`,
`*.keystore`, `keystore.properties` and `signing.properties`.

A leaked upload key lets someone publish builds that Google Play accepts as yours. It is the one
genuinely catastrophic secret in an Android repository.

---

## Donations

The heart icon opens a donation sheet backed by Google Play Billing. The products are consumable
one-time purchases and must exist in Play Console under exactly these IDs:

```
donate_1   donate_5   donate_10   donate_25   donate_50   donate_100
```

Prices shown in the app come from Play, localised to the user's currency — the numbers above only
name the products.

**Billing cannot be tested from a sideloaded APK.** The app must be installed from a Play track,
so the sheet shows an explanatory message off-Play rather than an empty list.

---

## Project layout

```
app/src/main/java/com/example/myapplication/
├── MainActivity.kt                  Activity, permissions, photo picker, dialogs
└── translate/
    ├── Language.kt                  The 18 languages, endonyms, prompts
    ├── ImageLoader.kt               Downscale + EXIF rotation for photo input
    ├── CrashReporter.kt             Persists uncaught exceptions for the next launch
    ├── billing/DonationBilling.kt   Play Billing
    ├── speech/                      SpeechToText, Speaker (TTS)
    ├── translator/
    │   ├── Translator.kt            Interface + prompts
    │   ├── LiteRtTranslator.kt      Gemma via LiteRT-LM, backend fallback
    │   ├── StubTranslator.kt        Fake engine mimicking real timings
    │   ├── ModelVariant.kt          E4B / E2B catalogue and download sources
    │   ├── ModelLocation.kt         On-disk paths
    │   └── ModelDownloader.kt       Download, source switching, delete
    └── ui/                          Compose screen, ViewModel, dialogs, theme
```

The `Translator` interface is the seam. Everything above it — UI, state machine, speech — is
independent of how translation happens, which is what lets the app run against `StubTranslator`
with no model present.

---

## Known limitations

- **The Kotlin package is still `com.example.myapplication`** while the `applicationId` is
  `com.aire.translate`. Cosmetic, but visible in stack traces.
- **Speech depends on Google's language packs.** Languages without an installed pack fail with
  "Speech model for this language isn't installed". The model's own audio encoder is present in
  the weights and would remove this dependency, but is not yet wired up.
- **Text selection on the rotated half** does not work properly — the platform's selection handles
  and toolbar ignore the rotation. Use the copy button.
- **Vision runs on CPU**, so photo translation has a noticeable delay before the first token.

---

## Licence

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).

Model weights are **not** covered by that licence and are licensed separately.
