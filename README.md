# Doctor Agent

A free, local-first AI health companion prototype for broader health education and care preparation. It answers supported general
health education questions from a small, source-linked library. It can speak
answers using the browser's built-in speech synthesis. An optional local
OpenAI-compatible model can write grounded explanations; no paid model API is
required.

This prototype does **not** diagnose, prescribe, choose medicine doses, or
replace professional care. It is not clinically validated. If you may be in
immediate danger, contact local emergency services or a qualified professional.
Do not enter real personal health information into this prototype.

## Run it

Requires Python 3.11 or newer. No packages, keys, accounts, database or paid
service are needed.

```sh
python3 app.py
```

Open <http://127.0.0.1:8000>. The server binds to loopback only. Questions stay
on this computer. Without a local model, answers come from the curated library
or the agent says the library does not cover the topic.

### Optional local model

For example, install [Ollama](https://ollama.com/) and download the Qwen3 4B
model (about 2.5 GB). Model weights are free to download under their published
license; downloading them uses internet and disk space. Then run Doctor Agent
with Ollama's local OpenAI-compatible endpoint:

```sh
ollama pull qwen3:4b
DOCTOR_AGENT_MODEL_URL=http://127.0.0.1:11434/v1/chat/completions \
DOCTOR_AGENT_MODEL_NAME=qwen3:4b python3 app.py
```

Ollama exposes an OpenAI-compatible local chat endpoint; the companion refuses
non-loopback model URLs. [Ollama endpoint docs](https://ollama.com/blog/openai-compatibility),
[Qwen3 4B license](https://huggingface.co/Qwen/Qwen3-4B). Hardware requirements
depend on the device. The retrieval-only mode works without a model.

## What works

- Search a curated set of general wellness and nutrition references.
- Receive answers with links to the original public sources.
- Keep each chat in browser memory for the current session only.
- Optionally hear the answer using browser speech synthesis.
- Optionally connect a local OpenAI-compatible model for grounded wording.
- Run a 10-case synthetic retrieval/boundary evaluation with `python3 scripts/evaluate.py`.
- Run the core behavior tests using only the Python standard library:

```sh
python3 -m unittest discover -s tests -v
```

## Roadmap

1. Expand the small public-source library and build a clinician-reviewed
   evaluation set before making answers more detailed.
2. Test retrieval, safety boundaries, offline behavior and on-device model
   performance on a range of phones and browsers.
3. Consider opt-in local health context only after a threat model, encryption
   design and clear user controls are reviewed.
4. Add voice input, document import and device health data only with explicit
   consent and a clear account of what each browser or device shares.
5. Consider patient-specific assessment only with qualified clinical review,
   jurisdiction-specific regulatory assessment and a monitored pilot.

## Project boundaries

The included reference catalog has explicit source links and short summaries.
It is an initial prototype library, not a complete medical knowledge base.
Model output is checked for source IDs and blocked treatment claims, but those
checks cannot establish that an answer is clinically safe. An open model is
free to download, not necessarily free to run or automatically cleared for
commercial use. Review each model and data license before distribution.

## Install the phone web app

The standalone mobile web app lives in `mobile/`; it does not use or modify the NutritiScan website. Build its deployable folder with:

```sh
python3 scripts/build_mobile.py
python3 -m http.server 8000 --directory dist/mobile
```

Then open `http://127.0.0.1:8000` on the same computer to preview. For phone installation, the included GitHub Pages workflow builds and publishes the app over HTTPS at `https://adarsh-9182.github.io/doctor-agent/` after Pages is enabled for this repository. On a phone, open that address and use the browser's **Add to Home Screen** / **Install app** action. The cached app shell and source library work offline after the first visit.

The optional in-browser model is downloaded only after selecting **Enable on-device AI**. It requires WebGPU, a compatible browser, internet for the first download, and substantial device memory/storage. The app does not send chat questions to a model server. Model output remains an unvalidated draft and may be wrong; use the linked source material and a qualified clinician for personal concerns. Retrieval-only source answers remain available when the model is unavailable.

The PWA model is MLC's prebuilt SmolLM2 1.7B Instruct Q4F32 model. The upstream model card lists Apache-2.0; review the [model card](https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct) and the [WebLLM model configuration](https://github.com/mlc-ai/web-llm/blob/main/src/config.ts) before redistribution. These model weights and WebLLM runtime files are downloaded from their upstream hosts after the user opts in; the chat text is passed to the local browser engine, not included in those download requests.

The repo also contains an early native macOS app source and a native Android project. They provide general education, not clinical care. Android additionally includes an optional local daily check-in journal and optional on-device voice dictation; the Mac app does not include voice input. Android builds have succeeded, but installation and UI behavior on a physical phone remain to be checked.

### Native Mac app

On a Mac with Swift command-line tools, build a universal arm64/x86_64 app bundle:

```sh
bash scripts/build_mac_app.sh
```

The app bundle is created at `dist/Doctor Agent.app`. It reads the bundled source catalog locally and does not need the Python server. Its default source-only mode requires no model download. To opt in to conversational wording on this Mac, install Ollama and run:

```sh
ollama pull qwen3:4b
```

Then enable **Use local Qwen3 model (Ollama)** in the app. Doctor Agent sends the question and selected public-source excerpts only to the Ollama endpoint on `127.0.0.1`; if the local model is unavailable or fails the output check, it shows the source-only answer. Model output is not clinically validated. The ad-hoc signed app bundle is for local use; it is not notarized for general distribution.

### Native Android project

The Android app source is in `native/android/` and uses Android platform UI APIs plus Google's LiteRT-LM 0.18.0 for optional local inference. Android SDK and JDK 21 are required to build it. Prepare the shared source catalog and build the debug APK with:

```sh
bash scripts/build_android.sh
```

Or open `native/android/` in Android Studio, let Gradle sync, and build/install the debug APK. JDK 21, SDK platform 35, and build-tools 35.0.0 are installed in this workspace. The project requires SDK platform 35 and Android build-tools; set `ANDROID_HOME` if the SDK is installed somewhere other than `~/Library/Android/sdk`. Set `JAVA_HOME` to a compatible JDK 21 installation; the script detects Homebrew JDK 21 on Macs. Each Gradle build copies the shared root source catalog into generated app assets automatically.

The build produces `native/android/app/build/outputs/apk/debug/app-debug.apk`. A local copy is available at `dist/Doctor-Agent-android-debug.apk`. This is a debug-signed development build, not a Play Store release. Transfer it to a supported 64-bit Android 8.0 or newer device, open the file, and allow installation from the transferring app if prompted. With a USB-connected device and USB debugging authorized, it can also be installed with:

```sh
~/Library/Android/sdk/platform-tools/adb install -r dist/Doctor-Agent-android-debug.apk
```

The native Android app uses the device's offline English speech voice when one is available. It disables Listen when no offline English voice is installed. The app has no internet permission. Version 0.6.0 declares microphone permission for optional dictation, requested at runtime only after choosing Speak; source links open in the user's browser. The PWA remains available from the link above using Android Chrome's **Install app** action.

#### Android daily check-in (0.3.0)

Open **Daily check-in** to record sleep hours, a self-rated energy score, and an optional goal. Each save requires selecting the local-save checkbox. There is one entry per local calendar date; another save replaces today's entry. Up to 30 daily entries are retained, and the seven most recent are displayed. These entries are a personal journal, not an assessment or personalized medical advice, and are not passed into chat.

Journal data is encrypted with AES-GCM using an Android Keystore key and stored in the app's private no-backup directory. App backup is disabled. **Delete all saved check-ins** removes the journal after confirmation; clearing chat does not clear the journal. Uninstalling the app also removes its local data. There is no account sync; version 0.7.0 adds optional local reminders. The updated APK must still be exercised on a physical phone; a successful build alone does not establish runtime behavior or clinical safety.

#### Android companion interface (0.4.0)

- **Home:** a daily greeting, saved-check-in status, and shortcuts for nutrition, sleep, hydration, and movement.
- **Chat:** readable source summaries, clearly labelled care boundaries, explicit clipboard copying, offline read-aloud, and stop-reading controls. Clearing chat requires confirmation.
- **Library:** all bundled summaries and links to original sources, with an error message when a browser cannot open a link.
- **You:** journal management, privacy information, voice availability, and chat controls.

The journal pre-fills today's saved entry and can show all retained entries. Chat and its draft are kept in memory during activity configuration changes, such as rotation; an unsaved session and its draft are not restored after process termination. Version 0.8.6 adds explicitly saved snapshots, which can be reopened from History. A session retains at most 80 messages. Copying text explicitly places it on the system clipboard, marked sensitive on supported Android versions. Source retrieval remains the default; optional generative drafts are described below. The Mac app and PWA have not received this interface update.

#### Optional local AI drafts (0.5.0)

In the standard build, choose **Import a model file** in **You**, select a CPU-compatible `.litertlm` file, then enable **Add local AI drafts**. The standard APK contains no weights and never downloads them automatically. The current version also offers a separate APK with a pinned starter model included, described below. Review the model's own terms before acquiring or importing it. This runtime supports arm64-v8a and x86_64; device/model compatibility and performance have not been measured on the user's phone.

Import copies a file of up to 3 GB into private no-backup storage using a temporary file and atomic replacement. This limit is a storage guard, not a promise that a 3 GB model will run. The model can be removed separately from the journal. The APK is larger because it now includes native inference libraries. The app has no internet permission; version 0.6.0 adds optional microphone access as described below. File providers and external browsers have their own network behavior.

For questions matched to source summaries, the source answer appears first. Hindi and Hinglish topic lookup and six curated Hindi summaries are included in 0.8.4, along with bounded translated emergency/medication phrases. Coverage is limited to the documented phrases; Hindi output, voice recognition, and clinical correctness have not been evaluated. If enabled, the CPU runtime attempts a separate AI draft using the current source summaries and a bounded question. A separate opt-in switch includes up to three recent source-backed exchanges: each question is capped at 240 characters, with complete source summaries within a 450-character title/body budget. Summaries that do not fit are omitted and marked. Emergency and medication-boundary exchanges are excluded. Version 0.8.0 optionally supplies a saved name and habit goal when the user separately authorizes profile sharing. Journal entries and previous model outputs are never supplied. Each generation uses a fresh conversation, a 2048-token context budget, and a 256-token output limit. AI/recent-context switches default off and reset on activity recreation. Explicit English and selected Hindi follow-up phrases reuse the preceding source-backed topic, with the current safety boundary checked first. Missing topics trigger clarification and editable topic suggestions. Broader conversational reference resolution remains incomplete.

The draft is labelled unverified and shown beside its source context, not as a verified answer. Urgent-care, medication-boundary, and uncovered-topic responses bypass generation. A basic output filter rejects certain medication/diagnostic language, URLs, empty responses, and oversized drafts; it does not prove factual grounding or clinical safety. Inference failures retain the source answer. Stop, a new question, clearing chat, or leaving the foreground invalidates pending drafts and requests cancellation. A 60-second timer also requests cancellation; loading/native code may not stop immediately. Runtime crashes and excessive memory use remain possible with incompatible files.

LiteRT-LM is pinned to 0.18.0. Its released license and third-party notices are bundled in `app/src/main/legalAssets/`, included in the APK, and readable offline from **You → Open-source licenses**. See the [official runtime documentation](https://developers.google.com/edge/litert-lm/android). The APK has been compiled, but model import, native generation, timing, cancellation, and UI behavior still require device verification. No clinical validation is claimed.

#### Optional on-device voice dictation (0.6.0)

In Chat, tap **Speak** to request microphone permission. After granting access, tap **Speak** again to start a single dictation session. The app calls Android's explicit on-device recognition API on Android 12+ only when that service is available. It never falls back to the network recognizer. English uses the device's English locale, or `en-IN` when the device locale is another language. The phone must have a suitable offline recognition language pack; service availability alone does not guarantee that pack is installed.

Only final recognized text becomes a draft; it is never sent automatically. An existing typed draft offers Append, Replace, or Cancel. The app does not save audio or partial results. Dictation stops on cancellation, a 20-second timeout, leaving Chat, sending/clearing a question, reading an answer aloud, or losing activity focus. You can manage microphone permission from **You**. Denied permission, missing services/languages, and recognizer errors preserve typing. Speech recognition depends on the system provider; the app requests the on-device path rather than claiming control over that provider's internals.

The APK has been compiled; no microphone, speech-provider, or device UI testing has been performed for this feature. Testing remains deferred at the user's request. See the [Android SpeechRecognizer documentation](https://developer.android.com/reference/android/speech/SpeechRecognizer).

#### Optional daily reminder (0.7.0)

In **You**, choose a reminder time and enable the daily reminder. Reminders default off. Android 13+ requests notification permission only on enable; blocked app/channel notifications are reported in the UI, with a link to system notification settings. Turning the reminder off cancels its pending alarm and visible reminder notification. Deleting journal entries does not change the reminder setting.

The scheduler uses one local inexact alarm, then schedules the next local calendar date when it fires. It requests no exact-alarm access. Notification text is generic and contains no journal values; tapping it opens the check-in dialog. Only the enabled flag, preferred time, and next due timestamp are stored in ordinary private preferences. No journal or AI context is read by the reminder receiver.

Reboot, clock/timezone changes, and app updates attempt to reschedule an enabled reminder; returning to the app attempts to restore its saved due time. A saved due time less than eight hours late may be delivered after reopening; older missed reminders are skipped. Device force-stop, notification settings, battery policies, and system scheduling can prevent or delay delivery. This is an optional journal prompt, not a medication or emergency alarm. See the [Android alarm documentation](https://developer.android.com/develop/background-work/services/alarms).

The manifest now declares `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED` alongside optional microphone access. No internet permission, cloud push, exact-alarm permission, or foreground service has been added. Compilation succeeded; scheduled delivery, reboot behavior, permission handling, and notification navigation have not been tested on a phone. Product testing remains deferred at the user's request.

#### Android companion (0.8.9)

Two debug APKs are produced locally:

- `dist/Doctor-Agent-android-0.8.9-debug.apk`: standard build, without weights.
- `dist/Doctor-Agent-android-0.8.9-with-model-debug.apk`: includes the pinned Qwen3 0.6B mixed-INT4 starter. No manual model import is needed. Open **You**, enable local AI drafts, then ask a covered topic such as sleep or nutrition in **Chat**.

Version 0.8.9 redesigns the chat interface with a lavender visual system, Chat as the Android opening screen, an animated navigation drawer with encrypted saved-chat shortcuts, a compact composer, grouped answer actions and expandable source evidence. Native Mac and local web apps now have sidebars, session chat snapshots and response-stop controls; desktop session copies disappear when the app closes or the page reloads. The web UI adds light/dark theme switching, responsive navigation, keyboard focus handling and motion preferences. The installable web app receives matching styling and editable topic prompts. This UI update does not establish feature or clinical parity with ChatGPT. See [0.8.9 design scope](docs/releases/0.8.9-preview.md).

Version 0.8.8 adds a **Care** workspace for user-authored symptom and visit notes, with review before adding to Chat. The form stays in memory across tab changes and rotation; only explicitly saved chats persist. It does not infer symptoms, generate a clinical assessment, or automatically share notes with the model. Home and Chat now start with symptoms, reports, medicines, mental health and conditions. The library has 14 linked summaries, including doctor visits, lab-result literacy, medicines, mental health, diabetes, blood pressure, vaccines and pain, with English/Hindi summaries. Report upload, personal result interpretation and clinical diagnosis are not implemented. See [0.8.8 scope and limitations](docs/releases/0.8.8-preview.md).

Version 0.8.7 introduced curated topic routing and field-weighted BM25 source ranking, keeping explicitly requested topics in mention order. Body-only word overlap cannot activate a source. Broad queries prompt clarification, and selected non-health phrases are excluded. It also preserves Devanagari combining marks in safety normalization and adds expandable bundled source summaries beneath citations. The same 50 synthetic development cases improve from 30/50 on 0.8.6 to 50/50; these cases were used during development, not held out or clinician reviewed, and no GPT comparison was performed. See [retrieval changes, reproducible evaluation and limitations](docs/releases/0.8.7-preview.md).

Version 0.8.6 adds optional encrypted chat history. In Chat, **Save** explicitly stores a snapshot of the current messages, source links and labelled AI drafts; the unsent input is excluded. **Update** replaces that saved snapshot after more messages. Nothing is saved automatically. **History** opens saved conversations and offers rename, individual deletion and delete-all controls. Titles initially use the first question. **New** starts a separate session after a leave confirmation; saved copies remain until explicitly deleted. Saved chat data uses a separate Android Keystore key, AES-GCM and an atomic file in private no-backup storage. Limits are 20 chats, 80 messages per snapshot and 2 MiB total plaintext; reaching a limit prompts the user to delete an older saved chat rather than silently evicting it. Saved source summaries are historical snapshots, not freshly checked evidence. Reopening a chat can restore source-backed follow-up context; the existing separate opt-in still controls whether recent exchanges are passed into local AI. Chats are never loaded in bulk into AI context. See [history changes and validation limits](docs/releases/0.8.6-preview.md).

Version 0.8.5 refreshes the chat UI with source-led status, health-topic starter chips, distinct user/assistant bubbles and short reduced-motion-aware transitions. Starter prompts are editable drafts and are never sent automatically. See [interface changes and validation limits](docs/releases/0.8.5-preview.md). Version 0.8.4 adds bounded Hindi/Hinglish topic retrieval and safety phrases with curated Hindi summaries. See [changes and validation limits](docs/releases/0.8.4-preview.md). Version 0.8.3 polishes card styling, navigation feedback and short page and message transitions. See [interface changes and validation limits](docs/releases/0.8.3-preview.md). The companion conversation updates are described in [0.8.2](docs/releases/0.8.2-preview.md).

Version 0.8.1 fixes a chat-template mismatch between the pinned starter model and LiteRT-LM 0.18.0. A local draft was generated in the ARM64 API 37 emulator; physical-phone performance and medical correctness remain unverified. See [runtime evidence and limitations](docs/releases/0.8.1-preview.md).

The starter weighs 497,516,544 bytes. Its immutable repository revision, SHA-256, source URL, and Apache-2.0 license are recorded in `models/starter-model.json`. The model license is bundled and readable from **You → Open-source licenses**. The [upstream model card](https://huggingface.co/litert-community/Qwen3-0.6B) publishes CPU examples; those benchmarks are not measurements of this app or the user's phone. The starter is a general language model, not a medically validated or health-fine-tuned model.

The included model is copied into private no-backup storage on first AI generation, with a checksum check and atomic replacement. The first copy requires at least 550 MiB free at that point; APK installation and overall model memory need additional space. CPU inference can require several GB of RAM. The bundled weights remain part of the APK even after removing the private copy; install the standard build to omit those weights. Neither build enables AI by default, and neither app has internet permission.

**Companion memory** optionally stores a preferred name and one everyday habit goal using a separate encrypted file/key. Home can display them. A separate checkbox authorizes those fields in local AI drafts; the sharing choice persists with the saved profile. Saving or deleting preferences invalidates pending drafts. Deleting preferences leaves the journal and current chat unchanged. It does not erase text already copied to the clipboard or previously shown in chat.

**Add companion to home screen** asks the launcher to pin a widget; unsupported launchers can use their widget picker instead. Its Chat and Check-in buttons open the app directly. The widget contains no personal values, has no periodic refresh, and performs no background inference or microphone capture. It provides access rather than a continuously running model.

Rebuild the model-included package with Python 3.11+, curl, JDK 21, and the Android SDK:

```sh
bash scripts/build_android_companion.sh
```

This downloads the pinned weights to ignored `dist/models/`, checks their size/hash, and packages them. Gradle uses a 3 GB maximum heap for the large asset. The build script cleans generated output before packaging; this avoids retaining deleted model bytes in an incremental APK when switching build modes. Standard builds do not retain the bundled asset.

To install on one USB-connected Android phone with USB debugging enabled and this Mac authorized:

```sh
bash scripts/install_android.sh
```

Or pass an explicit APK path. The installer updates the app without clearing data and opens it. No phone was connected during this build. Both packaging and the downloaded file's integrity have been checked; model execution, widget behavior, reminders, voice, and usability on a real phone remain unverified. Product testing is still deferred at the user's request.

The native Android response engine can be evaluated without the SDK using Java 17 and Python:

```sh
python3 scripts/evaluate_android.py
```

This runs the shared ten synthetic retrieval/boundary cases plus 40 Android regression cases against the actual source catalog and localized keywords. It compiles the Java engine and its real helper classes. Passing verifies the covered software behaviors, not clinical safety, model accuracy or Android UI behavior. Use `--report dist/diagnostics/retrieval.json` to save JSON results, or `--revision <commit>` to evaluate an earlier engine and catalog against the current case set.
