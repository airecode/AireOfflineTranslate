# Google Play store listing — Aire Offline Translate

Copy to paste into Play Console, plus the answers for the forms that block release. Anything in
`<angle brackets>` is a decision only you can make.

---

## App name (30 characters max)

```
Aire Offline Translate
```

22 characters.

## Short description (80 characters max)

```
Face-to-face translation that runs on your phone. No account, no cloud.
```

70 characters.

## Full description (4000 characters max)

```
Aire Offline Translate turns your phone into a translator you can put on the table between two people.

The screen splits in two, with the top half rotated 180° so it faces whoever is sitting across from you. Each person gets their own microphone, their own language, and reads their own half the right way up. Speak, and your words appear on your side while the translation appears on theirs — and is read aloud in their language.

EVERY TRANSLATION HAPPENS ON YOUR PHONE

The app runs Google's Gemma model directly on your device. Your conversations are not sent to a server, because there is no server. Put the phone in aeroplane mode and it still works — that is the simplest way to check.

There is no account to create, no sign-in, no analytics, and no advertising.

WAYS TO TRANSLATE

• Speak — tap the microphone, talk, tap stop
• Camera — point at a sign or a menu and drag a box around the text you want
• Photo — pick an image from your gallery
• Type — for when speaking out loud is not an option

Point the camera at a menu and only what you frame gets read, so a single dish can be translated without the rest of the page coming with it.

18 LANGUAGES

English, Arabic, Chinese (Simplified and Traditional), Filipino, French, German, Hebrew, Hindi, Japanese, Korean, Malay, Russian, Spanish, Tamil, Thai, Turkish, Vietnamese.

The app itself is translated into English, Chinese (Simplified), Japanese, Spanish and Arabic.

BEFORE YOU INSTALL — PLEASE READ

This app downloads a 2.59 GB model the first time you use it, over Wi-Fi. That download is what makes offline translation possible, and the app cannot translate anything until it finishes. Make sure you have the space and a Wi-Fi connection.

It needs Android 13 or newer and a 64-bit device. Around 2.6 GB of memory is held while the model is loaded, so it is happiest on a phone with 6 GB of RAM or more. The model is released automatically when you switch away for a while, or whenever the system needs the memory back.

Speech recognition uses your phone's own speech service. The app asks it to work offline, and it will if you have an offline language pack installed for the language you are speaking. Without one, that part may use the network even though the translation does not.

FREE AND OPEN SOURCE

The app is free, has no paid features, and nothing is locked behind a purchase. The complete source code is published under the Apache License 2.0, so you can read exactly what it does with your conversations rather than take our word for it.

If it has been useful you can buy the developer a coffee from inside the app. That is entirely optional and unlocks nothing.
```

Roughly 2,500 characters, comfortably inside the limit.

---

## Categorisation

| Field | Value |
|---|---|
| App or game | App |
| Category | Tools |
| Tags | Translation, Productivity, Utilities |
| Free or paid | **Free** — the tips are in-app products, not a price |
| Contains ads | **No** |
| In-app purchases | **Yes** — tips, `<lowest tip price>` to `<highest tip price>` per item |

## Contact details

| Field | Value |
|---|---|
| Email | `<your-contact-email>` — shown publicly on the listing, so consider one that is not your personal address |
| Website | `<optional — your GitHub repository works>` |
| Privacy policy | `<URL where you host PRIVACY.md>` |

**The privacy policy must be a public URL**, not a file attached to the listing. Options: GitHub
Pages, or the raw file URL from your repository.

---

## Data safety form

The app collects nothing, so almost every answer is "no". Answer honestly rather than copying
blindly — but these match what the code actually does.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable — no data is collected |
| Do you provide a way for users to request that their data is deleted? | Not applicable — uninstalling removes everything |

Why "no" is the right answer:

- **Audio** is passed to the device's own speech recognition service. The app does not collect,
  store or transmit it. The system service is a separate app with its own disclosure.
- **Photos and camera images** are processed in memory to extract text, then discarded. Nothing is
  saved or uploaded.
- **Text** you type or speak is translated on-device and never leaves the phone.
- **Crash diagnostics** are written to a file on the device and shown to you. There is no crash
  reporting service and nothing is uploaded.
- **Tips** are processed by Google Play. The app never sees payment details.

If the form asks about the model download: it is a file download from a public CDN that carries no
user data and no identifiers.

---

## Content rating questionnaire

Answer as a utility app with no user-generated content, no social features, no ads, no violence, no
gambling and no data collection. Expect an "Everyone" / PEGI 3 rating.

The one question worth pausing on is whether users can share content or communicate with each other.
They cannot — there is no network feature at all, and two people using the phone are in the same
room.

---

## Screenshots

Play requires at least 2 phone screenshots; up to 8 is better. Take them on your Pixel with the
model installed, so the panels have real translations in them.

Worth capturing:

1. The split screen mid-conversation with real text in both halves — this is the whole idea in one
   image, so make it the first one.
2. The camera scanner with the box framing text on a real sign or menu.
3. The language pair selection.
4. The model manager, which makes the 2.59 GB download honest before install rather than after.

Avoid the empty first-run state — it is the least representative screen in the app.

---

## In-app products

Create six **consumable** one-time products. The IDs must match the app exactly; the display names
are what Play shows on the purchase sheet, so they should match what the app promises.

| Product ID | Display name | Price |
|---|---|---|
| `donate_1` | Buy me a biscuit | `<your price>` |
| `donate_5` | Buy me a coffee | `<your price>` |
| `donate_10` | Buy me lunch | `<your price>` |
| `donate_25` | Buy me dinner | `<your price>` |
| `donate_50` | Buy me a feast | `<your price>` |
| `donate_100` | Buy me a buffet | `<your price>` |

Description for each: something like "A voluntary tip for the developer. Unlocks no features."

Leaving these named "Donation" in Play Console while the app says "buy me a coffee" is the sort of
mismatch that gets flagged: collecting donations through in-app purchase is only allowed for
registered nonprofits, whereas tipping a developer is fine.

---

## Before you can submit

- [ ] Upload key created, `keystore.properties` filled in, `bundleRelease` produces a signed `.aab`
- [ ] Privacy policy hosted at a public URL
- [ ] At least 2 phone screenshots
- [ ] App icon 512×512 PNG and feature graphic 1024×500 PNG
- [ ] Data safety form completed
- [ ] Content rating questionnaire completed
- [ ] Target audience declared
- [ ] Six in-app products created and activated
- [ ] Countries selected
