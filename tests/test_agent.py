import json
import unittest
from pathlib import Path
from unittest.mock import patch

import app


class AgentCoreTests(unittest.TestCase):
    def test_retrieves_relevant_nutrition_reference(self):
        refs = app.find_references("What is a balanced nutrition meal?")
        self.assertTrue(refs)
        self.assertTrue(any("nutrition" in ref["title"].lower() for ref in refs))

    def test_abstains_when_library_has_no_relevant_topic(self):
        result = app.education_reply("Explain a car engine's timing belt")
        self.assertEqual(result["mode"], "not-covered")
        self.assertEqual(result["sources"], [])

    def test_does_not_provide_medication_dosing(self):
        result = app.education_reply("What dose should I take?")
        self.assertEqual(result["mode"], "professional-care")
        self.assertIn("can't diagnose", result["text"])

    def test_urgent_terms_get_fixed_escalation_without_model(self):
        with patch.object(app, "MODEL_URL", "http://127.0.0.1:8080/v1/chat/completions"):
            with patch.object(app, "local_model_answer", side_effect=AssertionError("model must not run")):
                result = app.education_reply("I have chest pain")
        self.assertEqual(result["mode"], "urgent-care")

    def test_model_host_must_be_loopback(self):
        self.assertTrue(app.model_is_loopback("http://127.0.0.1:8080/v1/chat/completions"))
        self.assertTrue(app.model_is_loopback("http://localhost:8080/v1/chat/completions"))
        self.assertFalse(app.model_is_loopback("https://example.com/v1/chat/completions"))

    def test_model_output_blocked_if_it_makes_treatment_claim(self):
        with patch.object(app, "MODEL_URL", "http://127.0.0.1:8080/v1/chat/completions"):
            with patch.object(app, "local_model_answer", return_value="Take 2 pills daily."):
                result = app.education_reply("How can I sleep better?")
        self.assertEqual(result["mode"], "reference-only")

    def test_reference_catalog_has_attribution_and_working_url_shape(self):
        for reference in app.KNOWLEDGE:
            with self.subTest(reference=reference["id"]):
                self.assertTrue(reference["source"])
                self.assertTrue(reference["url"].startswith("https://"))
                self.assertTrue(reference["text"])


if __name__ == "__main__":
    unittest.main()
