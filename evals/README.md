# Prototype evaluation

`cases.json` contains synthetic questions that check whether the local agent:

- Retrieves the expected reference for the six topics in its small catalog.
- Abstains when no catalog source covers a question.
- Applies the prototype's fixed boundaries for diagnosis, medication dosing,
  and a small set of urgent phrases.

Run with `python3 scripts/evaluate.py`. No model, API key, network, or extra
package is required.

Passing these checks tests wiring and retrieval only. It does not show that an
answer is medically correct, that emergency recognition is comprehensive, or
that a model is clinically safe. The cases are synthetic and are not training
data. Keep future training data separate from independently held-out evaluation questions,
review model/data licenses, and do not add real people's health information.

## Android retrieval regression suite

`android_retrieval_cases.json` adds 40 development cases covering body-only false matches, vague queries, selected non-health phrases, source ordering and exclusion, multi-topic questions, Hindi/Hinglish aliases and selected safety phrases. Run `python3 scripts/evaluate_android.py` for 50 cases total. This runner compiles the actual Android Java engine, language helper and retrieval class, and uses the bundled English catalog plus the same localized keywords supplied by MainActivity.

These 50 cases were used while developing 0.8.7, so they are a regression set, not independent held-out evidence. They do not test the Android UI, follow-up wrapper, generated model output, clinician correctness or comprehensive emergency coverage. The existing ten shared cases predate the update, but they have also been used during development and should not be treated as a clean held-out clinical benchmark.

The baseline can be reproduced without checking out or changing the working tree:

```sh
python3 scripts/evaluate_android.py --revision 15967d06dc3eb07ee455f524a868028b183b54f1 --report dist/diagnostics/retrieval-baseline-0.8.6.json
python3 scripts/evaluate_android.py --report dist/diagnostics/retrieval-0.8.7.json
```

The baseline intentionally returns a nonzero exit code when its cases fail. Source/case results from both runs are recorded in `results/android-retrieval-0.8.7.json`. A future clinician-reviewed, multi-turn evaluation must use separate questions, record the exact comparison model and configuration, and evaluate safety, citation support, uncertainty and useful task completion. No such medical evaluation or GPT comparison has been run.
