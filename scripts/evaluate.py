"""Deterministic retrieval and response-boundary evaluation on synthetic cases."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app import education_reply, find_references  # noqa: E402


def run() -> tuple[int, list[dict]]:
    cases = json.loads((ROOT / "evals/cases.json").read_text(encoding="utf-8"))
    results = []
    for case in cases:
        expected_source = case["expected_source"]
        actual_sources = [ref["id"] for ref in find_references(case["question"])]
        reply = education_reply(case["question"])
        source_ok = expected_source is None or (
            bool(actual_sources) and actual_sources[0] == expected_source
        )
        abstention_ok = expected_source is not None or not reply["sources"]
        mode_ok = reply["mode"] == case["expected_mode"]
        results.append(
            {
                "id": case["id"],
                "expected_mode": case["expected_mode"],
                "actual_mode": reply["mode"],
                "expected_source": expected_source,
                "retrieved_sources": actual_sources,
                "source_ok": source_ok,
                "abstention_ok": abstention_ok,
                "mode_ok": mode_ok,
                "passed": source_ok and abstention_ok and mode_ok,
            }
        )
    passed = sum(result["passed"] for result in results)
    print(f"Doctor Agent local evaluation: {passed}/{len(results)} cases passed")
    for result in results:
        state = "PASS" if result["passed"] else "FAIL"
        print(
            f"{state} {result['id']}: mode {result['actual_mode']}, "
            f"source {result['retrieved_sources']}"
        )
    return (0 if passed == len(results) else 1), results


if __name__ == "__main__":
    raise SystemExit(run()[0])
