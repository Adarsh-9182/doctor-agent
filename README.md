# Doctor Agent

A free, local-first health companion prototype. It answers supported general
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

The repo also contains an early native macOS app source and a native Android project. They provide general education, not clinical care. Android additionally includes an optional local daily check-in journal; neither app includes voice input. The Android debug APK was built successfully on October 7, 2026. Installation and UI behavior on a physical Android phone remain to be checked.

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

The native Android app uses the device's offline English speech voice when one is available. It disables Listen when no offline English voice is installed. The app requests no internet or microphone permission; source links open in the user's browser. The PWA remains available from the link above using Android Chrome's **Install app** action.

#### Android daily check-in (0.3.0)

Open **Daily check-in** to record sleep hours, a self-rated energy score, and an optional goal. Each save requires selecting the local-save checkbox. There is one entry per local calendar date; another save replaces today's entry. Up to 30 daily entries are retained, and the seven most recent are displayed. These entries are a personal journal, not an assessment or personalized medical advice, and are not passed into chat.

Journal data is encrypted with AES-GCM using an Android Keystore key and stored in the app's private no-backup directory. App backup is disabled. **Delete all saved check-ins** removes the journal after confirmation; clearing chat does not clear the journal. Uninstalling the app also removes its local data. This version has no reminders or account sync. The updated APK must still be exercised on a physical phone; a successful build alone does not establish runtime behavior or clinical safety.

#### Android companion interface (0.4.0)

- **Home:** a daily greeting, saved-check-in status, and shortcuts for nutrition, sleep, hydration, and movement.
- **Chat:** readable source summaries, clearly labelled care boundaries, explicit clipboard copying, offline read-aloud, and stop-reading controls. Clearing chat requires confirmation.
- **Library:** all bundled summaries and links to original sources, with an error message when a browser cannot open a link.
- **You:** journal management, privacy information, voice availability, and chat controls.

The journal pre-fills today's saved entry and can show all retained entries. Chat and its draft are kept in memory during activity configuration changes, such as rotation; they are not restored after process termination. A session retains at most 80 messages. Copying text explicitly places it on the system clipboard, marked sensitive on supported Android versions. Source retrieval remains the default; optional generative drafts are described below. The Mac app and PWA have not received this interface update.

#### Optional local AI drafts (0.5.0)

In **You**, choose **Import a model file**, select a CPU-compatible `.litertlm` file, then enable **Add local AI drafts**. No weights are bundled or downloaded automatically. Review the model's own terms before acquiring or importing it. This runtime supports arm64-v8a and x86_64; device/model compatibility and performance have not been measured on the user's phone. A model must be supplied before actual inference can be exercised.

Import copies a file of up to 3 GB into private no-backup storage using a temporary file and atomic replacement. This limit is a storage guard, not a promise that a 3 GB model will run. The model can be removed separately from the journal. The APK is larger because it now includes native inference libraries. The app still has no internet or microphone permission; file providers and external browsers have their own network behavior.

For questions matched to source summaries, the source answer appears first. If enabled, the CPU runtime attempts a separate AI draft using only those summaries and a bounded question. A separate opt-in switch includes up to three recent user questions, capped at 240 characters each. Journal entries and previous model outputs are never supplied. Each generation uses a fresh conversation, a 2048-token context budget, and a 256-token output limit. The switches default off and reset on activity recreation. Source retrieval does not resolve conversational references such as “what about that?” yet.

The draft is labelled unverified and shown beside its source context, not as a verified answer. Urgent-care, medication-boundary, and uncovered-topic responses bypass generation. A basic output filter rejects certain medication/diagnostic language, URLs, empty responses, and oversized drafts; it does not prove factual grounding or clinical safety. Inference failures retain the source answer. Stop, a new question, clearing chat, or leaving the foreground invalidates pending drafts and requests cancellation. A 60-second timer also requests cancellation; loading/native code may not stop immediately. Runtime crashes and excessive memory use remain possible with incompatible files.

LiteRT-LM is pinned to 0.18.0. Its released license and third-party notices are bundled in `app/src/main/legalAssets/`, included in the APK, and readable offline from **You → Open-source licenses**. See the [official runtime documentation](https://developers.google.com/edge/litert-lm/android). The APK has been compiled, but model import, native generation, timing, cancellation, and UI behavior still require device verification. No clinical validation is claimed.

The native Android response engine can be evaluated without the SDK using Java 17 and Python:

```sh
python3 scripts/evaluate_android.py
```

This runs the shared ten synthetic retrieval/boundary cases plus four additional Java-engine cases against the actual source catalog. Passing them verifies the covered software behaviors, not clinical safety, model accuracy or Android UI behavior.
