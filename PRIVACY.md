# Privacy Policy — Aire Offline Translate

**Last updated: 10 August 2026**

Aire Offline Translate ("the app") is an on-device translator for face-to-face conversations. This
policy describes exactly what the app does with your information. It is short because the app does
very little.

## The short version

The app has no accounts, no servers, no analytics, no advertising and no tracking of any kind. It
does not collect, store or transmit personal information. Your conversations, photos and recordings
stay on your phone.

There is one thing on this page that deserves your attention, and it is the section on speech
recognition below.

## What the app does not do

- It does not create an account or ask for any identifying information.
- It does not have a server. There is nowhere for your data to be sent.
- It contains no analytics, telemetry, advertising or tracking libraries of any kind.
- It does not read your contacts, location, files or any other app's data.

## Translation

Translation is performed entirely on your device by a Gemma model that you download once. Your text
is never sent anywhere for translation. This remains true with the phone in aeroplane mode, which is
the simplest way to verify the claim for yourself.

## The model download

On first use the app downloads the translation model — about 2.59 GB — from Hugging Face
(`huggingface.co`, `hf.co`, or `hf-mirror.com` if the first is unreachable). This is an ordinary
file download over Wi-Fi.

The app sends no identifying information with the request. As with any download, the server that
serves the file will see your IP address and is subject to its own privacy policy. This is the only
network connection the app makes for its own purposes.

Once the download finishes, the app never needs a network connection again.

## Speech recognition — please read this

When you speak into the app, the audio is handled by **your device's own speech recognition
service**, not by the app. On most Android phones this is Google's speech service.

The app asks that service to process your speech **on the device** rather than in the cloud. Whether
it can honour that request depends on your phone: if an offline recognition pack is installed for
the language you are speaking, recognition happens locally and no audio leaves your device. **If no
offline pack is installed for that language, the system service may send the audio to its own
servers to transcribe it**, where it is handled under that service's privacy policy, not this one.

The app never stores your audio, never keeps a copy, and never sends audio anywhere itself. But it
cannot control what your device's speech service does with it.

If this matters to you, install offline speech recognition for your languages in
**Settings → System → Languages & input → On-device speech recognition** (the exact path varies by
phone). Typing or the camera avoid speech recognition entirely.

Spoken output uses your device's text-to-speech engine in the same way, and the same reasoning
applies.

## Camera and photos

The camera is used only while the scanning screen is open, and only to read text you point it at.
Photos you choose from your gallery are read only to extract their text.

In both cases the image is processed on your device and discarded. The app does not save images, does
not keep a copy, and does not upload them anywhere.

## What is stored on your device

- The translation model file you downloaded.
- Your chosen language pair, so it is remembered next time.
- A diagnostic file written only if the app crashes, containing the technical details of the crash.
  It is stored on your device, shown to you on the next launch, and never sent anywhere.

Uninstalling the app removes all of it.

## Tips

If you choose to tip the developer, the payment is processed entirely by Google Play. The app never
sees your payment details — it receives only a confirmation from Play that a purchase completed.
Tips are voluntary, unlock no features, and change nothing about how the app works.

Google Play's handling of the transaction is covered by Google's own privacy policy.

## Permissions and why they exist

| Permission | Why |
|---|---|
| Microphone | To hear what you say, so it can be transcribed and translated. Used only while recording. |
| Camera | To read text you point the camera at. Used only while the scanning screen is open. |
| Internet | To download the translation model once, and to process tips through Google Play. |

The app requests no other permissions. The camera is marked optional, so the app installs and works
on devices without one.

## Children

The app is not directed at children and collects no information from anyone, including children.

## Changes to this policy

If this policy changes, the date at the top will change with it. Since the app collects nothing,
material changes are unlikely.

## Source code

Aire Offline Translate is open source under the Apache License 2.0. If you would rather verify the
claims on this page than take them on trust, you can read the code.

## Contact

Questions about this policy: `<your-contact-email>`
