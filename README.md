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

This is an installable web app, not a native iOS/Android app. Voice input, health-record storage, device health integrations, and clinical review are not included.
