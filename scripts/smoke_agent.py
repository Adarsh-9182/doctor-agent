"""Real HTTP workflow smoke using an isolated in-memory server and temporary plugins."""
from __future__ import annotations

import json
import sys
import tempfile
import threading
import time
from http.server import ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import app  # noqa: E402
from doctor_agent.plugins import PluginRegistry  # noqa: E402
from doctor_agent.runtime import AgentRuntime  # noqa: E402


def main():
    original_plugins, original_agent = app.PLUGINS, app.AGENT
    with tempfile.TemporaryDirectory(prefix="doctor-agent-smoke-") as directory:
        app.PLUGINS = PluginRegistry(ROOT / "plugins", Path(directory) / "plugins")
        app.AGENT = AgentRuntime(app.PLUGINS, app.find_references, app.medical_boundary)
        server = ThreadingHTTPServer(("127.0.0.1", 0), app.Handler)
        server.daemon_threads = True
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        origin = f"http://127.0.0.1:{server.server_address[1]}"

        def call(path, payload=None, expected=200, headers=None):
            request = Request(origin + path, data=None if payload is None else json.dumps(payload).encode(), headers={"Content-Type": "application/json", "X-Doctor-Agent": "local-web", "Origin": origin, **(headers or {})})
            try:
                response = urlopen(request, timeout=5)
            except HTTPError as error:
                response = error
            with response:
                assert response.status == expected, (path, response.status, expected)
                body = response.read()
                return json.loads(body) if response.headers.get_content_type() == "application/json" else body

        def run(payload):
            ticket = call("/api/agent/run", payload, 202)
            for _attempt in range(100):
                state = call("/api/agent/status", {"id": ticket["id"], "token": ticket["token"]})
                if state["status"] != "running":
                    assert state["status"] == "completed", state
                    return state, ticket
                time.sleep(0.01)
            raise AssertionError("Agent did not finish")

        try:
            assert b'plugins-dialog' in call("/")
            catalog = call("/api/plugins")["plugins"]
            assert len(catalog) == 2
            manifest = json.loads((ROOT / "plugins/examples/appointment-checklist.json").read_text())
            call("/api/plugins/install", {"manifest": manifest}, 201)
            disabled, _ = run({"question": "appointment checklist", "enabled_plugins": []})
            assert disabled["result"]["artifacts"] == []
            enabled, ticket = run({"question": "appointment checklist", "enabled_plugins": ["appointment-checklist"]})
            assert enabled["result"]["artifacts"][0]["kind"] == "checklist"
            call("/api/agent/status", {"id": ticket["id"], "token": "another-browser"}, 404)
            answer, _ = run({"question": "Explain sleep and prepare a visit brief", "notes": "Synthetic: I want to ask about my sleep routine.", "share_notes": True, "enabled_plugins": ["health-library", "visit-preparation"]})
            assert any(source["id"] == "medlineplus-sleep" for source in answer["result"]["sources"])
            assert "Synthetic:" in answer["result"]["artifacts"][0]["text"]
            assert any(step["kind"] == "tool" for step in answer["steps"])
            emergency, _ = run({"question": "I have chest pain", "enabled_plugins": ["health-library"]})
            assert emergency["result"]["mode"] == "urgent-care"
            call("/api/plugins/install", {"manifest": manifest}, 403, {"Origin": "https://attacker.example"})
            call("/api/plugins", expected=403, headers={"Host": "attacker.example"})
            call("/api/plugins/install", {"manifest": {**manifest, "id": "unsafe-plugin", "command": "sh"}}, 400)
            call("/api/plugins/remove", {"id": "appointment-checklist"})
            assert len(call("/api/plugins")["plugins"]) == 2
            print("PASS: actual HTTP UI route, plugin install/disabled/enable/run/remove, source-linked education + visit artifact, run-token isolation, emergency boundary, CSRF and DNS-rebinding guards.")
            print("PASS: temporary plugin storage removed; no health text written to disk and no model or external service called.")
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)
            app.PLUGINS, app.AGENT = original_plugins, original_agent


if __name__ == "__main__":
    main()
