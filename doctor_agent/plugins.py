"""Versioned data-only plugins. Manifests never execute Python, shell or network IO."""
from __future__ import annotations

import json
import re
import threading
from pathlib import Path
from urllib.parse import urlsplit

OPERATIONS = {"reference_search", "visit_brief", "checklist"}
PERMISSIONS = {"reference_search": ["read:question"], "visit_brief": ["read:question", "read:notes"], "checklist": ["read:question"]}
MAX_PLUGIN_BYTES = 32_000
ID = re.compile(r"[a-z][a-z0-9-]{2,39}\Z")


class PluginError(ValueError):
    pass


def text(value, name, maximum=500):
    if not isinstance(value, str) or not value.strip() or len(value) > maximum:
        raise PluginError(f"Invalid {name}.")
    if any(ord(char) < 32 and char not in "\n\t" for char in value):
        raise PluginError(f"Invalid {name}.")
    return value.strip()


def validate_manifest(value):
    if not isinstance(value, dict) or set(value) - {"schema_version", "id", "name", "description", "version", "operation", "permissions", "triggers", "items", "references"}:
        raise PluginError("Unsupported plugin fields. Plugins contain data, never executable commands.")
    if value.get("schema_version") != 1 or isinstance(value.get("schema_version"), bool):
        raise PluginError("Unsupported plugin schema version.")
    identifier = text(value.get("id"), "plugin ID", 40)
    if not ID.fullmatch(identifier):
        raise PluginError("Plugin IDs use lowercase letters, digits and hyphens.")
    operation = value.get("operation")
    if not isinstance(operation, str) or operation not in OPERATIONS:
        raise PluginError("Unsupported operation. Arbitrary code and network tools are not allowed.")
    if value.get("permissions") != PERMISSIONS[operation]:
        raise PluginError("Permissions must match the operation's actual access.")
    result = {"schema_version": 1, "id": identifier, "name": text(value.get("name"), "name", 80), "description": text(value.get("description"), "description", 300), "version": text(value.get("version"), "version", 30), "operation": operation, "permissions": PERMISSIONS[operation]}
    triggers = value.get("triggers", [])
    if not isinstance(triggers, list) or len(triggers) > 12:
        raise PluginError("Plugins may declare up to 12 literal trigger phrases.")
    result["triggers"] = [text(item, "trigger", 80).casefold() for item in triggers]
    items = value.get("items", [])
    if not isinstance(items, list) or len(items) > 12:
        raise PluginError("Plugins may contain up to 12 checklist items.")
    result["items"] = [text(item, "checklist item", 240) for item in items]
    references = value.get("references", [])
    if not isinstance(references, list) or len(references) > 12:
        raise PluginError("Plugins may contain up to 12 references.")
    result["references"] = []
    for reference in references:
        if not isinstance(reference, dict) or set(reference) != {"id", "title", "source", "url", "text", "keywords"}:
            raise PluginError("Every reference needs attribution, URL, text and keywords.")
        url = text(reference["url"], "reference URL", 500)
        parsed = urlsplit(url)
        if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
            raise PluginError("Reference links must use HTTPS without credentials.")
        keywords = reference["keywords"]
        if not isinstance(keywords, list) or len(keywords) > 20:
            raise PluginError("Invalid reference keywords.")
        result["references"].append({**{key: text(reference[key], key, 1600 if key == "text" else 150) for key in ("id", "title", "source", "text")}, "url": url, "keywords": [text(word, "keyword", 60) for word in keywords]})
    if operation == "reference_search" and not result["references"] and identifier != "health-library":
        raise PluginError("A source plugin must supply references.")
    if operation == "checklist" and not result["items"]:
        raise PluginError("A checklist plugin needs items.")
    # Check normalized size too, rather than relying on HTTP framing alone.
    if len(json.dumps(result).encode()) > MAX_PLUGIN_BYTES:
        raise PluginError("Plugin manifest is too large.")
    return result


class PluginRegistry:
    def __init__(self, bundled: Path, installed: Path):
        self.bundled = bundled
        self.installed = installed
        self.lock = threading.RLock()

    def catalog(self):
        with self.lock:
            result = {}
            for directory, builtin in ((self.bundled, True), (self.installed, False)):
                if not directory.exists():
                    continue
                for path in sorted(directory.glob("*.json")):
                    if path.is_symlink() or path.stat().st_size > MAX_PLUGIN_BYTES:
                        continue
                    try:
                        item = validate_manifest(json.loads(path.read_text(encoding="utf-8")))
                    except (ValueError, OSError):
                        continue
                    if item["id"] not in result:
                        result[item["id"]] = {**item, "builtin": builtin, "review_status": "bundled engineering content" if builtin else "user-installed; not clinically reviewed"}
            return result

    def list(self):
        return list(self.catalog().values())

    def install(self, manifest):
        item = validate_manifest(manifest)
        with self.lock:
            catalog = self.catalog()
            if len(catalog) >= 32:
                raise PluginError("At most 32 plugins can be installed. Remove an unused custom plugin first.")
            if item["id"] in catalog:
                raise PluginError("This plugin ID is already installed; remove a custom plugin before replacing it.")
            self.installed.mkdir(parents=True, exist_ok=True, mode=0o700)
            target = self.installed / f"{item['id']}.json"
            try:
                with target.open("x", encoding="utf-8") as output:
                    json.dump(item, output, ensure_ascii=False)
                target.chmod(0o600)
            except FileExistsError as error:
                raise PluginError("Plugin already exists.") from error
            return {**item, "builtin": False, "review_status": "user-installed; not clinically reviewed"}

    def remove(self, identifier):
        if not isinstance(identifier, str) or not ID.fullmatch(identifier):
            raise PluginError("Invalid plugin ID.")
        with self.lock:
            item = self.catalog().get(identifier)
            if not item or item["builtin"]:
                raise PluginError("Only installed custom plugins can be removed.")
            (self.installed / f"{identifier}.json").unlink()


def tool_schema(plugin):
    return {"type": "function", "function": {"name": plugin["id"].replace("-", "_"), "description": f"{plugin['name']}: {plugin['description']}", "parameters": {"type": "object", "properties": {}, "additionalProperties": False}}}


def execute(plugin, question, notes, retrieve):
    """Inputs are selected by the host, never arbitrary model-supplied PHI or paths."""
    operation = plugin["operation"]
    if operation == "reference_search":
        if plugin["id"] == "health-library":
            references = retrieve(question)
        else:
            words = set(re.findall(r"\w{3,}", question.casefold()))
            references = [item for item in plugin["references"] if words.intersection(re.findall(r"\w{3,}", " ".join([item["title"], *item["keywords"]]).casefold()))][:3]
        return {"text": "\n\n".join(f"{item['title']}: {item['text']}" for item in references) or "No matching source in this plugin.", "sources": [{**item, "plugin_id": plugin["id"], "review_status": plugin["review_status"]} for item in references], "artifact": None}
    if operation == "visit_brief":
        body = "Draft for your clinician to review\n\nYour question (recorded verbatim):\n" + json.dumps(question, ensure_ascii=False)
        body += "\n\nYour notes (recorded verbatim, not clinically verified):\n" + (json.dumps(notes, ensure_ascii=False) if notes else "No notes supplied.")
        body += "\n\nQuestions for the visit:\n" + "\n".join(f"- {item}" for item in plugin["items"])
        body += "\n\nThis is an organizational draft, not a diagnosis, prescription or clinician-signed record. Review before sharing."
        return {"text": "I prepared a factual visit-brief draft from your question and the notes you explicitly shared with this plugin. Review the artifact before sharing.", "sources": [], "artifact": {"id": plugin["id"], "title": "Doctor visit brief", "text": body, "kind": "visit-brief", "status": "draft"}}
    body = plugin["name"] + "\n\n" + "\n".join(f"- [ ] {item}" for item in plugin["items"])
    body += "\n\nUser-installed content; not clinically reviewed." if not plugin["builtin"] else "\n\nPreparation checklist; discuss medical decisions with your clinician."
    return {"text": f"Prepared the {plugin['name']} checklist. Review its content in the artifact.", "sources": [], "artifact": {"id": plugin["id"], "title": plugin["name"], "text": body, "kind": "checklist", "status": "draft"}}
