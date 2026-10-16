"use strict";
const $ = (selector) => document.querySelector(selector);
const form = $("#chat-form"), input = $("#question"), messages = $("#messages");
const send = $("#send"), stop = $("#stop"), intro = $("#intro"), engineLabel = $("#engine-label");
const history = [], entries = [], sessions = [];
let activeSession = null, request = null, revision = 0, activeRun = null;
let plugins = [], selectedPlugins = new Set();
const mobile = matchMedia("(max-width:680px)");
const reducedMotion = matchMedia("(prefers-reduced-motion:reduce)");
const sidebar = $("#sidebar"), menu = $("#menu-button"), backdrop = $("#backdrop"), workspace = $("#workspace");
function setMenu(open) {
  const modal = open && mobile.matches;
  sidebar.classList.toggle("open", modal); backdrop.hidden = !modal;
  menu.setAttribute("aria-expanded", String(modal)); workspace.inert = modal;
  sidebar.inert = mobile.matches && !modal;
  if (modal) $("#close-menu").focus(); else if (mobile.matches) menu.focus();
}
menu.addEventListener("click", () => setMenu(true));
$("#close-menu").addEventListener("click", () => setMenu(false));
backdrop.addEventListener("click", () => setMenu(false));
mobile.addEventListener("change", () => setMenu(false));
sidebar.inert = mobile.matches;
document.addEventListener("keydown", (event) => {
  if (!sidebar.classList.contains("open")) return;
  if (event.key === "Escape") { event.preventDefault(); setMenu(false); }
  if (event.key === "Tab") {
    const nodes = [...sidebar.querySelectorAll("a,button")].filter(node => !node.disabled && node.getClientRects().length);
    const first = nodes[0], last = nodes[nodes.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  }
});
$("#theme-button").addEventListener("click", () => {
  const dark = document.documentElement.dataset.theme !== "dark";
  document.documentElement.dataset.theme = dark ? "dark" : "light";
  $("#theme-button").setAttribute("aria-label", `Switch to ${dark ? "light" : "dark"} theme`);
});
function scrollToLatest() { messages.lastElementChild?.scrollIntoView({ behavior: reducedMotion.matches ? "instant" : "smooth", block: "nearest" }); }
function tool(label, action) {
  const button = document.createElement("button"); button.type = "button"; button.textContent = label;
  button.addEventListener("click", action); return button;
}
function renderMessage(entry) {
  const {role, text, sources = [], mode = "", steps = [], artifacts = []} = entry;
  const article = document.createElement("article"); article.className = `message ${role === "user" ? "user-message" : "assistant-message"}`;
  const avatar = document.createElement("span"); avatar.className = "avatar"; avatar.setAttribute("aria-hidden", "true"); avatar.textContent = "✳";
  const body = document.createElement("div"); body.className = "message-body";
  const paragraph = document.createElement("p"); paragraph.textContent = text; body.append(paragraph);
  if (mode) {
    const meta = document.createElement("span"); meta.className = "message-meta";
    meta.textContent = mode.includes("model") || mode.includes("draft") ? "AI DRAFT · NOT VERIFIED" : mode.replaceAll("-", " ").toUpperCase(); body.append(meta);
  }
  if (sources.length) {
    const details = document.createElement("details"); details.className = "sources";
    const summary = document.createElement("summary"); summary.textContent = `↗ Sources · ${sources.length}`; details.append(summary);
    for (const source of sources) {
      let url; try { url = new URL(source.url); } catch { continue; } if (url.protocol !== "https:") continue;
      const link = document.createElement("a"); link.href = url.href; link.target = "_blank"; link.rel = "noopener noreferrer";
      link.textContent = `${source.title || source.source} ↗`; details.append(link);
      if (source.review_status) { const review = document.createElement("small"); review.className = "source-review"; review.textContent = source.review_status; details.append(review); }
      if (source.text) { const snippet = document.createElement("p"); snippet.textContent = `Bundled summary: ${source.text}`; details.append(snippet); }
    }
    body.append(details);
  }
  if (steps.length) {
    const details = document.createElement("details"); details.className = "run-activity";
    const summary = document.createElement("summary"); summary.textContent = `Agent activity · ${steps.length} steps`; details.append(summary);
    const list = document.createElement("ol");
    for (const step of steps) { const item = document.createElement("li"); item.textContent = `${step.label} · ${step.status}`; list.append(item); }
    details.append(list); body.append(details);
  }
  for (const artifact of artifacts) {
    if (typeof artifact.text !== "string") continue;
    const card = document.createElement("section"); card.className = "artifact-card";
    const heading = document.createElement("h3"); heading.textContent = `${artifact.title} · draft`;
    const content = document.createElement("pre"); content.textContent = artifact.text;
    const download = tool("Download reviewed draft", () => {
      const url = URL.createObjectURL(new Blob([artifact.text], {type:"text/plain;charset=utf-8"}));
      const anchor = document.createElement("a"); anchor.href = url; anchor.download = "doctor-agent-draft.txt";
      anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
    });
    card.append(heading, content, download); body.append(card);
  }
  const tools = document.createElement("div"); tools.className = "message-tools";
  tools.append(tool("Copy", async (event) => {
    try { await navigator.clipboard.writeText(text); event.target.textContent = "Copied"; }
    catch { event.target.textContent = "Select text to copy"; }
  }));
  if (role === "assistant" && "speechSynthesis" in window) {
    tools.append(tool("▶ Read aloud", () => { speechSynthesis.cancel(); speechSynthesis.speak(new SpeechSynthesisUtterance(text)); }));
    tools.append(tool("■ Stop reading", () => speechSynthesis.cancel()));
  }
  body.append(tools); article.append(avatar, body); messages.append(article);
}
function addMessage(role, text, sources = [], mode = "", extra = {}) {
  intro.hidden = true;
  const entry = {role, text, sources, mode, ...extra}; entries.push(entry);
  if (entries.length > 80) { entries.shift(); messages.firstElementChild?.remove(); }
  renderMessage(entry); scrollToLatest();
}
function addTyping() {
  const article = document.createElement("article"); article.className = "message assistant-message"; article.id = "typing";
  article.setAttribute("role", "status"); article.setAttribute("aria-label", "Preparing response");
  const avatar = document.createElement("span"); avatar.className = "avatar"; avatar.textContent = "✳";
  const dots = document.createElement("span"); dots.className = "typing"; dots.setAttribute("aria-hidden", "true");
  for (let i = 0; i < 3; i++) dots.append(document.createElement("i")); article.append(avatar, dots); messages.append(article); scrollToLatest();
}
async function api(path, body, signal) {
  const response = await fetch(path, {method:"POST", credentials:"same-origin", headers:{"Content-Type":"application/json", "X-Doctor-Agent":"local-web"}, body:JSON.stringify(body), signal:signal ? AbortSignal.any([signal, AbortSignal.timeout(15000)]) : AbortSignal.timeout(15000)});
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "The request could not be completed.");
  return data;
}
function cancelRequest() {
  revision++; request?.abort(); request = null;
  if (activeRun) { api("/api/agent/cancel", activeRun).catch(() => {}); activeRun = null; }
  $("#typing")?.remove(); send.disabled = false; stop.hidden = true;
}
function showActivity(steps) {
  const typing = $("#typing"); if (!typing) return;
  let list = typing.querySelector("ol");
  if (!list) { list = document.createElement("ol"); list.className = "live-activity"; typing.append(list); }
  list.replaceChildren();
  for (const step of steps.slice(-4)) { const item = document.createElement("li"); item.textContent = step.label; list.append(item); }
}
async function ask(question) {
  const text = question.trim(); if (!text || request) return;
  const context = history.filter(turn => turn.role === "user").slice(-3).map(turn => ({role:"user", text:turn.text.slice(0,1000)}));
  addMessage("user", text); history.push({role:"user", text}); if (history.length > 8) history.splice(0, history.length - 8);
  input.value = ""; input.style.height = "auto"; send.disabled = true; stop.hidden = false;
  const token = ++revision, controller = new AbortController(); request = controller; addTyping();
  try {
    const ticket = await api("/api/agent/run", {question:text, history:context, enabled_plugins:[...selectedPlugins], notes:$("#share-notes").checked ? $("#care-notes").value : "", share_notes:$("#share-notes").checked, use_model:$("#use-model").checked}, controller.signal);
    if (token !== revision) { api("/api/agent/cancel", {id:ticket.id,token:ticket.token}).catch(() => {}); return; }
    activeRun = {id:ticket.id,token:ticket.token};
    let state;
    for (let attempt = 0; attempt < 240; attempt++) {
      state = await api("/api/agent/status", activeRun, controller.signal);
      if (token !== revision) return;
      showActivity(state.steps || []);
      if (state.status !== "running") break;
      await new Promise(resolve => setTimeout(resolve, 250));
    }
    if (state.status === "running") throw new Error("The agent reached its response deadline. Stop it and try a smaller request.");
    $("#typing")?.remove();
    if (state.status === "failed") throw new Error(state.error || "The agent could not finish.");
    if (state.status === "cancelled") { addMessage("assistant", "Agent run cancelled. No answer was saved.", [], "cancelled"); return; }
    const result = state.result;
    if (!result || typeof result.text !== "string") throw new Error("The app returned an unreadable response.");
    addMessage("assistant", result.text, Array.isArray(result.sources) ? result.sources : [], result.mode || "", {steps:state.steps || [], artifacts:result.artifacts || []});
    history.push({role:"assistant", text:result.text});
    if (history.length > 8) history.splice(0, history.length - 8);
  } catch (error) {
    if (token !== revision || error.name === "AbortError") return;
    if (activeRun) api("/api/agent/cancel", activeRun).catch(() => {});
    $("#typing")?.remove(); addMessage("assistant", error.message || "I couldn’t complete that. Please try again.", [], "request-error");
  } finally {
    if (token === revision) { activeRun = null; request = null; send.disabled = false; stop.hidden = true; input.focus(); }
  }
}
stop.addEventListener("click", () => { cancelRequest(); addMessage("assistant", "Stop requested. Host tool execution is cancelled when the request reaches the local server; an in-flight local-model call may take up to its timeout to return.", [], "stopped"); });
function renderSessions() {
  const list = $("#chat-list"); list.replaceChildren();
  if (!sessions.length) { const empty = document.createElement("p"); empty.className = "empty-history"; empty.textContent = "Keep a conversation here to return to it during this session."; list.append(empty); }
  for (const session of sessions) {
    const row = document.createElement("div"); row.className = `chat-item${session.id === activeSession ? " active" : ""}`;
    const open = tool(session.title, () => {
      if ((entries.length || input.value) && !confirm("Replace the current conversation and unsent draft? Use Keep chat first to retain a session copy.")) return;
      cancelRequest(); window.speechSynthesis?.cancel(); entries.splice(0); history.splice(0); messages.replaceChildren(); input.value = ""; input.style.height = "auto";
      for (const entry of session.entries) { const copy = structuredClone(entry); entries.push(copy); renderMessage(copy); history.push({role:copy.role,text:copy.text}); }
      selectedPlugins = new Set((session.enabledPlugins || []).filter(id => plugins.some(plugin => plugin.id === id))); renderPlugins();
      $("#care-notes").value = ""; $("#share-notes").checked = false; $("#use-model").checked = false;
      activeSession = session.id; intro.hidden = entries.length > 0; renderSessions(); setMenu(false); scrollToLatest();
    }); open.className = "chat-open"; open.title = session.title;
    const remove = tool("×", () => { if (!confirm("Remove this session copy? The open conversation stays visible.")) return;
      sessions.splice(sessions.indexOf(session), 1); if (activeSession === session.id) activeSession = null; renderSessions(); });
    remove.className = "chat-delete"; remove.setAttribute("aria-label", `Remove session chat: ${session.title}`); row.append(open, remove); list.append(row);
  }
}
$("#save-chat").addEventListener("click", () => {
  if (!entries.some(e => e.role === "user")) { $("#session-note").textContent = "Start a conversation first. Session copies disappear on reload."; return; }
  if (request) { $("#session-note").textContent = "Wait for the response, or stop it before keeping a copy."; return; }
  let session = sessions.find(e => e.id === activeSession);
  if (!session && sessions.length >= 20) { $("#session-note").textContent = "20 session chats kept. Remove a copy before keeping another."; return; }
  if (!session) { session = {id:crypto.randomUUID(),title:entries.find(e => e.role === "user").text.replace(/\s+/g," ").slice(0,60)}; sessions.unshift(session); }
  session.entries = structuredClone(entries); session.enabledPlugins = [...selectedPlugins]; activeSession = session.id; renderSessions(); $("#session-note").textContent = "Session copy updated. Reloading clears all chats.";
});
$("#new-chat").addEventListener("click", () => {
  if ((entries.length || input.value) && !confirm("Start a new chat? Use Keep chat first to retain the current conversation during this session.")) return;
  cancelRequest(); window.speechSynthesis?.cancel(); entries.splice(0); history.splice(0); messages.replaceChildren(); input.value = ""; input.style.height = "auto";
  selectedPlugins = new Set(plugins.filter(plugin => plugin.builtin).map(plugin => plugin.id)); renderPlugins();
  $("#care-notes").value = ""; $("#share-notes").checked = false; $("#use-model").checked = false;
  activeSession = null; intro.hidden = false; renderSessions(); setMenu(false); input.focus();
});
form.addEventListener("submit", event => { event.preventDefault(); ask(input.value); });
input.addEventListener("keydown", event => { if (event.key === "Enter" && !event.shiftKey && !event.isComposing) { event.preventDefault(); form.requestSubmit(); } });
input.addEventListener("input", () => { input.style.height = "auto"; input.style.height = `${Math.min(input.scrollHeight,150)}px`; });
document.querySelectorAll("[data-prompt]").forEach(button => button.addEventListener("click", () => {
  if (input.value.trim() && !confirm("Replace your unsent question with this topic?")) return;
  input.value = button.dataset.prompt; input.dispatchEvent(new Event("input")); setMenu(false); input.focus();
}));
function renderPlugins() {
  $("#plugin-count").textContent = selectedPlugins.size;
  const list = $("#plugins-list"); list.replaceChildren();
  for (const plugin of plugins) {
    const card = document.createElement("section"); card.className = "plugin-card";
    const label = document.createElement("label"); label.className = "option";
    const checkbox = document.createElement("input"); checkbox.type = "checkbox"; checkbox.checked = selectedPlugins.has(plugin.id);
    checkbox.addEventListener("change", () => { checkbox.checked ? selectedPlugins.add(plugin.id) : selectedPlugins.delete(plugin.id); $("#plugin-count").textContent = selectedPlugins.size; });
    label.append(checkbox, document.createTextNode(`${plugin.name} · ${plugin.version}`));
    const description = document.createElement("p"); description.textContent = plugin.description;
    const permissions = document.createElement("small"); permissions.textContent = `Access: ${plugin.permissions.join(", ")} · ${plugin.review_status}`;
    card.append(label, description, permissions);
    if (!plugin.builtin) card.append(tool("Remove plugin", async () => {
      if (!confirm(`Remove ${plugin.name} from this local installation? Existing chat artifacts remain in this session.`)) return;
      try { await api("/api/plugins/remove", {id:plugin.id}); selectedPlugins.delete(plugin.id); await loadPlugins(); }
      catch (error) { $("#plugin-status").textContent = error.message; }
    }));
    list.append(card);
  }
}
async function loadPlugins(initial = false) {
  const response = await fetch("/api/plugins", {cache:"no-store"});
  if (!response.ok) throw new Error("Could not load plugins. Start the local app and retry.");
  const data = await response.json(); plugins = data.plugins;
  selectedPlugins = initial ? new Set(plugins.filter(plugin => plugin.builtin).map(plugin => plugin.id)) : new Set([...selectedPlugins].filter(id => plugins.some(plugin => plugin.id === id)));
  renderPlugins();
}
$("#plugins-button").addEventListener("click", () => { setMenu(false); $("#plugins-dialog").showModal(); });
$("#close-plugins").addEventListener("click", () => $("#plugins-dialog").close());
$("#install-plugin").addEventListener("click", async () => {
  const button = $("#install-plugin"); button.disabled = true;
  try {
    const manifest = JSON.parse($("#plugin-manifest").value);
    await api("/api/plugins/install", {manifest}); await loadPlugins();
    $("#plugin-status").textContent = "Installed, currently disabled. Review its permissions and enable it for this conversation to use it.";
    $("#plugin-manifest").value = "";
  } catch (error) { $("#plugin-status").textContent = error instanceof SyntaxError ? "The manifest is not valid JSON." : error.message; }
  finally { button.disabled = false; }
});
$("#example-plugin").addEventListener("click", () => {
  $("#plugin-manifest").value = JSON.stringify({schema_version:1,id:"appointment-checklist",name:"Appointment checklist",description:"Make a checklist of documents and questions to bring to an appointment.",version:"1.0.0",operation:"checklist",permissions:["read:question"],triggers:["checklist","appointment bag"],items:["Bring copies of reports you want to discuss.","Write down your main questions.","Bring your own current medicine and allergy list.","Ask the clinic about appointment preparation instructions."]}, null, 2);
});
loadPlugins(true).catch(error => { $("#plugin-status").textContent = error.message; $("#plugin-count").textContent = "offline"; });
fetch("/api/status").then(response => response.json()).then(status => {
  engineLabel.textContent = status.model ? `${status.model_name} · Local model configured` : "Tool mode · No model or API key required";
  $("#use-model").disabled = !status.model;
  $("#model-status").textContent = status.model ? "Optional local AI stays off until you enable it. It receives the current question, recent user questions and permitted tool results on this device." : "No local model configured. Tools, sources and visit briefs work without one. Configure DOCTOR_AGENT_MODEL_URL to enable model planning.";
}).catch(() => { engineLabel.textContent = "Local app unavailable · Start app.py"; });
