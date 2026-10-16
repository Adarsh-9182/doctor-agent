# Doctor Agent · implementation and delivery roadmap

Updated 16 October 2026. Target repository:
[Adarsh-9182/doctor-agent](https://github.com/Adarsh-9182/doctor-agent), inspected
at `8069d3a`. The NutritiScan platform is a separate repository.

## What “Claude-like doctor agent” means here

A conversational health assistant with understandable tool use, follow-ups,
installable capabilities, inspectable sources and useful artifacts. The model
plans within host-enforced permissions. Health education and preparation are
the initial intended uses; the software does not impersonate a licensed doctor.
Model intelligence depends on the configured model and the quality of evidence;
interface similarity cannot establish Claude-level capability or clinical skill.

## Delivered runtime slice

- One bounded supervisor with deterministic grounding and an optional local
  model tool loop; source-only functionality remains available without weights.
- Background run start/status/stop, operational step activity, run-token
  isolation and bounded memory/concurrency/tool/model budgets.
- Data-only versioned plugins: bundled health library and visit preparation;
  custom checklist/reference/draft manifests with explicit capabilities.
- Actual install/inspect/enable/run/remove UI. Custom plugins start disabled;
  each conversation chooses its own enabled set.
- Separately opted-in notes, quoted factual visit briefs, draft downloads,
  source attribution and unreviewed custom-content labels.
- Current-question safety screen before tools/inference, with a limited set
  of English/Hindi/Hinglish urgent and medication phrases.
- Local-origin/Host checks, JSON mutation guard, no model redirects/proxies,
  source fallback, cancellation and provider-failure regressions.
- HTTP end-to-end smoke and a GitHub Actions runtime verification workflow.

This slice upgrades the Python/local-web agent. The existing standalone native
Android, Mac and static PWA engines do not automatically gain these plugins.

## Ordered milestones

| Stage | Outcome | Acceptance evidence / dependency |
| --- | --- | --- |
| A · delivered locally | Agent runtime, plugin lifecycle, sources and visit artifacts | Unit tests, actual HTTP workflow and browser walkthrough |
| B · next | Model evaluation and richer conversation state | Held-out tool-selection, ambiguous follow-up and source-support cases; source vs model-draft evaluation; realistic latency/memory tests |
| C | Evidence retrieval plugin | Approved source policy/licensing; source dates and passages; claim-to-passage checks; relevance/conflict/abstention/injection evaluations |
| D | NutritiScan record plugin | Authenticated account-scoped bridge; confirmed facts only; point-of-use consent checks; no credentials inside model prompts; cross-account and revoked-consent tests |
| E | Reviewed connector/MCP runtime | Official protocol adapter, connection identity, per-tool scopes, payload preview before external disclosure, encrypted secrets and timeout/stop/retry policy; first synthetic connector receipt |
| F | Durable personal workspace | Identity/recovery, encrypted opt-in conversations and patient memory, corrections/provenance/deletion/export; no silent retention |
| G | Native parity | Share the stable runtime contract with Mac/Android; device workflow, offline capability decisions and signed releases |
| H | Supervised clinical pilot | Clinical/intended-use/privacy/security review; held-out multilingual cases; measured task success, correction rate and appropriate care escalation |
| I | Hosted product | Production auth, tenancy, backups/restore, monitoring, support ownership, quotas/cost control and optional billing; deployment/rollback receipt |

Clinical diagnosis or treatment recommendations require a separately defined
intended use, qualified clinical validation and applicable professional review.
They are not enabled by adding a system prompt or passing synthetic tests.

## Immediate high-value workflows

1. **Understand + prepare:** “Explain sleep and prepare a visit brief.” Retrieve
   bundled sources, organize explicitly shared fictional notes, inspect activity
   and download the reviewed draft.
2. **Add a capability:** install the example appointment checklist, observe it
   disabled, enable it and request a checklist artifact. Remove the plugin.
3. **Respect a boundary:** ask about a medicine dose or an urgent symptom.
   Return the fixed response before any model/tool call.
4. **Failure/cancel:** stop a model turn, deny a plugin, remove a plugin between
   calls or disconnect the model. No unauthorized follow-on tool runs; sources
   survive provider failure where available.

## Development and local model

```sh
python3 app.py
python3 -m unittest discover -s tests -v
python3 scripts/evaluate.py
python3 scripts/smoke_agent.py
node --check web/app.js
```

For a local model already installed in Ollama, run Ollama with cloud forwarding
disabled, then set the loopback endpoint and that installed model name:

```sh
OLLAMA_NO_CLOUD=1 ollama serve
DOCTOR_AGENT_MODEL_URL=http://127.0.0.1:11434/v1/chat/completions \
DOCTOR_AGENT_MODEL_NAME=qwen3.5:4b python3 app.py
```

Enable **Use the configured local model** explicitly in the UI. A configured
URL is labelled as configured, not as a successful health check. The provider
uses a short output budget and disables optional thinking via the compatibility
field to fit the local request deadline; unsupported model/runtime combinations
fall back to sources. No model download happens as part of starting the agent.

See [PLUGINS.md](PLUGINS.md) for the exact contract, privacy limits and a complete
installable example. Future hosted/Claude-provider support is a separate adapter
and consent boundary; no cloud API key is needed or sent by this implementation.

## Verification receipt · 16 October 2026

- 26 standard-library unit tests passed, including denied tools/arguments,
  note-sharing consent, fixed urgent/medication boundaries, cancellation,
  concurrency, cached calls, tool budgets and provider-failure behavior.
- Existing retrieval/boundary evaluation: 10/10 synthetic cases passed.
- New deterministic workflow evaluation: 3/3 passed.
- Actual HTTP smoke passed plugin install/disabled/enable/run/remove, visit
  artifacts, source citations, run-token isolation and origin/Host guards.
- Real local Qwen3.5 4B via Ollama: 4/4 synthetic workflow cases passed,
  including model invocation of a checklist tool without its literal trigger.
  A short source-grounded generative draft was also observed in Safari.
- Safari walkthrough verified the plugin dialog, custom installation remaining
  disabled, explicit enablement, live tool activity, source answers and draft
  artifacts. JavaScript syntax and Git whitespace checks passed.

These checks establish the covered software behavior. They are not held-out
clinical evaluation, comprehensive multilingual safety testing, a Claude
capability comparison or proof of native-client parity. Provider drafts can be
rejected or time out; fallback preserves the actual source/tool result.
