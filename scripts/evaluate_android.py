"""Run synthetic retrieval/boundary cases against the real Java Android engine.

Requires JDK 17, but no Android SDK or emulator. This checks software behavior,
not clinical competence.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / "native/android/app/src/main/java/org/doctoragent/mobile/EducationEngine.java"


def java_tool(name: str) -> str:
    for directory in (
        Path(os.environ.get("JAVA_HOME", "/nonexistent")) / "bin",
        Path("/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin"),
    ):
        if (directory / name).exists():
            return str(directory / name)
    found = shutil.which(name)
    if not found:
        raise SystemExit("JDK 17 is required. Set JAVA_HOME to your Java installation.")
    return found


def main() -> None:
    catalog = json.loads((ROOT / "knowledge.json").read_text())
    cases = json.loads((ROOT / "evals/cases.json").read_text())
    cases.extend([
        {"id": "urgent-precedes-dose", "question": "Chest pain after an overdose: what dose should I take?", "expected_mode": "urgent-care", "expected_source": None},
        {"id": "stopwords-only", "question": "Can you please help me?", "expected_mode": "not-covered", "expected_source": None},
        {"id": "punctuation-case", "question": "WATER!!! hydration?", "expected_mode": "reference-only", "expected_source": "medlineplus-water"},
        {"id": "repeated-query", "question": "sleep sleep sleep", "expected_mode": "reference-only", "expected_source": "medlineplus-sleep"},
    ])
    quote = lambda value: json.dumps(value, ensure_ascii=True)
    sources = ",\n".join(
        "new EducationEngine.Source(%s, %s, List.of(%s), %s)" % (
            quote(source["id"]), quote(source["title"]),
            ", ".join(quote(keyword) for keyword in source["keywords"]), quote(source["text"]),
        ) for source in catalog
    )
    checks = "\n".join(
        "check(%s, %s, %s, %s);" % (
            quote(case["id"]), quote(case["question"]), quote(case["expected_mode"]),
            quote(case["expected_source"]) if case["expected_source"] else "null",
        ) for case in cases
    )
    code = """package org.doctoragent.mobile;
import java.util.List;
public class NativeEvaluation {
    static final List<EducationEngine.Source> CATALOG = List.of(%s);
    static void check(String id, String question, String mode, String source) {
        EducationEngine.Answer answer = EducationEngine.answer(question, CATALOG);
        if (!mode.equals(answer.mode)) throw new AssertionError(id + ": wrong mode " + answer.mode);
        if (source == null && !answer.sourceIds.isEmpty()) throw new AssertionError(id + ": unexpected sources");
        if (source != null && (answer.sourceIds.isEmpty() || !source.equals(answer.sourceIds.get(0)))) throw new AssertionError(id + ": wrong top source " + answer.sourceIds);
        if (answer.text.trim().isEmpty()) throw new AssertionError(id + ": empty answer");
        System.out.println("PASS " + id);
    }
    public static void main(String[] args) {
        %s
        System.out.println("Android Java engine: %d/%d synthetic cases passed; not clinical validation.");
    }
}
""" % (sources, checks, len(cases), len(cases))
    with tempfile.TemporaryDirectory(prefix="doctor-agent-java-") as temp:
        directory = Path(temp)
        fixture = directory / "NativeEvaluation.java"
        fixture.write_text(code)
        subprocess.run([java_tool("javac"), "-encoding", "UTF-8", "-d", temp, str(ENGINE), str(fixture)], check=True)
        subprocess.run([java_tool("java"), "-cp", temp, "org.doctoragent.mobile.NativeEvaluation"], check=True)


if __name__ == "__main__":
    main()
