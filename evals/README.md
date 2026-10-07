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
data. Keep any future training set separate from this held-out evaluation set,
review model/data licenses, and do not add real people's health information.
