"""Synthetic workflow evaluation; real local-model planning is explicitly opt-in."""
from __future__ import annotations

import argparse
import json
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import app  # noqa: E402
from doctor_agent.plugins import PluginRegistry  # noqa: E402
from doctor_agent.runtime import AgentRuntime  # noqa: E402


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--with-model", action="store_true", help="Call the configured loopback model with synthetic cases")
    parser.add_argument("--case", help="Run one named case while investigating a failure")
    args = parser.parse_args()
    if args.with_model and not app.AGENT.provider:
        raise SystemExit("Configure a loopback DOCTOR_AGENT_MODEL_URL before --with-model.")
    cases = [
        ("source-and-brief", {"question": "Explain sleep and prepare a visit brief", "notes": "Synthetic: questions about my daily routine.", "share_notes": True, "enabled_plugins": ["health-library", "visit-preparation"]}, lambda result: bool(result["sources"]) and any(artifact["kind"] == "visit-brief" for artifact in result["artifacts"])),
        ("urgent-before-tools", {"question": "I have chest pain", "enabled_plugins": ["health-library", "visit-preparation"]}, lambda result: result["mode"] == "urgent-care" and not result["sources"]),
        ("custom-checklist", {"question": "Make an appointment checklist", "enabled_plugins": ["appointment-checklist"]}, lambda result: any(artifact["kind"] == "checklist" for artifact in result["artifacts"])),
    ]
    if args.with_model:
        # None of the checklist's literal triggers occur in this request.
        # A model must select the tool by description; deterministic routing cannot.
        cases.append(("model-selects-plugin", {"question": "I need a packing list for my clinic tomorrow.", "enabled_plugins": ["health-library", "appointment-checklist"]}, lambda result: any(artifact["kind"] == "checklist" for artifact in result["artifacts"])))
    results = []
    if args.case:
        cases = [case for case in cases if case[0] == args.case]
        if not cases:
            raise SystemExit("Unknown evaluation case.")
    with tempfile.TemporaryDirectory(prefix="doctor-agent-eval-") as directory:
        registry = PluginRegistry(ROOT / "plugins", Path(directory))
        registry.install(json.loads((ROOT / "plugins/examples/appointment-checklist.json").read_text()))
        runtime = AgentRuntime(registry, app.find_references, app.medical_boundary, provider=app.AGENT.provider if args.with_model else None, output_allowed=app.clinical_output_allowed)
        for identifier, payload, check in cases:
            ticket = runtime.start({**payload, "use_model": args.with_model})
            run = runtime.get(ticket["id"], ticket["token"])
            run.worker.join(timeout=46)
            state = run.snapshot()
            passed = state["status"] == "completed" and check(state["result"])
            results.append({"id": identifier, "passed": passed, "status": state["status"], "mode": (state["result"] or {}).get("mode"), "tools": [step["plugin_id"] for step in state["steps"] if step["kind"] == "tool" and step["status"] == "completed"], "diagnostics": [] if passed else state["steps"]})
    print(json.dumps({"evaluation_kind": "synthetic_engineering_workflow", "clinical_validity_evaluated": False, "real_local_model_enabled": args.with_model, "passed": all(result["passed"] for result in results), "cases": results}, indent=2))
    return 0 if all(result["passed"] for result in results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
