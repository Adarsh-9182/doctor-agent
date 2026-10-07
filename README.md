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
- Run the core behavior tests using only the Python standard library:

```sh
python3 -m unittest discover -s tests -v
```

## Roadmap

1. Measure retrieval and response quality on clinician-reviewed, synthetic
   cases before expanding health coverage.
2. Add opt-in encrypted local profiles, source provenance and user-controlled
   memory. Add a mobile client only after an API and threat model are reviewed.
3. Evaluate open models and optional LoRA adapters on a held-out set. Training
   requires rights-cleared data; synthetic cases test software behavior but do
   not demonstrate clinical competence.
4. Add reviewed voice input, document import and connected device data.
5. Consider patient-specific assessment only with qualified clinical review,
   jurisdiction-specific regulatory assessment and a monitored pilot.

## Project boundaries

The included reference catalog has explicit source links and short summaries.
It is an initial prototype library, not a complete medical knowledge base.
Model output is checked for source IDs and blocked treatment claims, but those
checks cannot establish that an answer is clinically safe. An open model is
free to download, not necessarily free to run or automatically cleared for
commercial use. Review each model and data license before distribution.
