"""Synthetic regression evaluation of the actual Java Android retrieval engine.

This checks software behavior, not clinical competence or a comparison to GPT.
Requires JDK 17+, but no Android SDK, model or emulator.
"""
from __future__ import annotations
import argparse
import json
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "native/android/app/src/main/java/org/doctoragent/mobile"

def java_tool(name: str) -> str:
    for directory in (
        Path(os.environ.get("JAVA_HOME", "/nonexistent")) / "bin",
        Path("/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin"),
        Path("/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin"),
    ):
        if (directory / name).exists():
            return str(directory / name)
    found = shutil.which(name)
    if not found:
        raise SystemExit("JDK 17+ is required. Set JAVA_HOME to your Java installation.")
    return found

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, help="Write JSON results for these synthetic cases")
    parser.add_argument("--revision", help="Evaluate source and catalog from a Git revision against the current cases")
    args = parser.parse_args()
    def read_file(path: str) -> str:
        if args.revision:
            return subprocess.check_output(["git", "show", f"{args.revision}:{path}"], cwd=ROOT, text=True)
        return (ROOT / path).read_text()
    catalog = json.loads(read_file("knowledge.json"))
    localized = {s["id"]: s for s in json.loads(read_file("knowledge_hi.json"))}
    cases = json.loads((ROOT / "evals/cases.json").read_text())
    cases += json.loads((ROOT / "evals/android_retrieval_cases.json").read_text())
    quote = lambda value: json.dumps(value, ensure_ascii=True)
    definitions = []
    for source in catalog:
        keywords = source["keywords"] + localized.get(source["id"], {}).get("keywords", [])
        fields = [quote(source["id"]), quote(source["title"]),
                  "List.of(" + ", ".join(map(quote, keywords)) + ")", quote(source["text"])]
        if "retrieval_terms" in source:
            fields.append("List.of(" + ", ".join(map(quote, source["retrieval_terms"])) + ")")
        definitions.append("new EducationEngine.Source(" + ", ".join(fields) + ")")
    checks = "\n".join("check(%s, %s);" % (quote(c["id"]), quote(c["question"])) for c in cases)
    code = """package org.doctoragent.mobile;
import java.util.List;
public class NativeEvaluation {
    static final List<EducationEngine.Source> CATALOG = List.of(%s);
    static void check(String id, String question) {
        EducationEngine.Answer answer = EducationEngine.answer(question, CATALOG);
        System.out.println("RESULT\\t" + id + "\\t" + answer.mode + "\\t"
                + String.join(",", answer.sourceIds) + "\\t" + !answer.text.trim().isEmpty());
    }
    public static void main(String[] args) { %s }
}
""" % (",\n".join(definitions), checks)
    with tempfile.TemporaryDirectory(prefix="doctor-agent-java-") as temp:
        fixture = Path(temp) / "NativeEvaluation.java"
        fixture.write_text(code)
        dependencies = []
        for name in ("EducationEngine.java", "LocalLanguage.java", "EvidenceRetriever.java"):
            path = "native/android/app/src/main/java/org/doctoragent/mobile/" + name
            if args.revision:
                result = subprocess.run(["git", "show", f"{args.revision}:{path}"], cwd=ROOT,
                                        capture_output=True, text=True)
                if result.returncode != 0:
                    if name == "EvidenceRetriever.java":
                        continue
                    raise RuntimeError(f"Could not read {name} from {args.revision}")
                dependency = Path(temp) / name
                dependency.write_text(result.stdout)
            else:
                dependency = JAVA / name
                if name == "EvidenceRetriever.java" and not dependency.exists():
                    continue
            dependencies.append(dependency)
        subprocess.run([java_tool("javac"), "-encoding", "UTF-8", "-d", temp,
                        *map(str, dependencies), str(fixture)], check=True)
        output = subprocess.run([java_tool("java"), "-cp", temp, "org.doctoragent.mobile.NativeEvaluation"],
                                check=True, capture_output=True, text=True).stdout
    actual = {}
    for line in output.splitlines():
        marker, case_id, mode, source_ids, nonempty = line.split("\t")
        if marker != "RESULT" or case_id in actual:
            raise RuntimeError("Invalid Java evaluation output")
        actual[case_id] = (mode, source_ids.split(",") if source_ids else [], nonempty == "true")
    if len(actual) != len(cases):
        raise RuntimeError("Missing evaluation results or duplicate case IDs")
    results = []
    known_ids = {source["id"] for source in catalog}
    for case in cases:
        mode, sources, nonempty = actual[case["id"]]
        expected = case.get("expected_source")
        reasons = []
        if mode != case["expected_mode"]:
            reasons.append("mode")
        if expected and (not sources or sources[0] != expected):
            reasons.append("top_source")
        if expected is None and case["expected_mode"] != "reference-only" and sources:
            reasons.append("unexpected_sources")
        if not set(case.get("required_sources", [])).issubset(sources):
            reasons.append("missing_sources")
        if set(case.get("excluded_sources", [])) & set(sources):
            reasons.append("irrelevant_sources")
        if not nonempty or len(sources) > 3 or not set(sources).issubset(known_ids):
            reasons.append("invalid_answer")
        result = {"id": case["id"], "expected_mode": case["expected_mode"], "actual_mode": mode,
                  "expected_source": expected, "source_ids": sources, "passed": not reasons, "failures": reasons}
        results.append(result)
        print(f"{'PASS' if not reasons else 'FAIL'} {case['id']}: {mode}, {sources}" +
              (f" ({', '.join(reasons)})" if reasons else ""))
    passed = sum(result["passed"] for result in results)
    print(f"Android Java engine: {passed}/{len(results)} synthetic cases passed; not clinical validation.")
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps({"suite": "android-retrieval-regression", "revision": args.revision or "working-tree", "passed": passed,
                                          "total": len(results), "results": results}, indent=2) + "\n")
    return 0 if passed == len(results) else 1

if __name__ == "__main__":
    raise SystemExit(main())
