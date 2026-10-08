"use strict";
const $ = (selector) => document.querySelector(selector);
const form = $("#chat-form"), input = $("#question"), messages = $("#messages");
const send = $("#send"), stop = $("#stop"), intro = $("#intro"), engineLabel = $("#engine-label");
const history = [], entries = [], sessions = [];
let activeSession = null, request = null, revision = 0;
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
  const {role, text, sources = [], mode = ""} = entry;
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
      if (source.text) { const snippet = document.createElement("p"); snippet.textContent = `Bundled summary: ${source.text}`; details.append(snippet); }
    }
    body.append(details);
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
function addMessage(role, text, sources = [], mode = "") {
  intro.hidden = true;
  const entry = {role, text, sources, mode}; entries.push(entry);
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
function cancelRequest() {
  revision++; request?.abort(); request = null; $("#typing")?.remove(); send.disabled = false; stop.hidden = true;
}
async function ask(question) {
  const text = question.trim(); if (!text || request) return;
  addMessage("user", text); history.push({role:"user", text}); if (history.length > 8) history.splice(0, history.length - 8);
  input.value = ""; input.style.height = "auto"; send.disabled = true; stop.hidden = false;
  const token = ++revision, controller = new AbortController(); request = controller; addTyping();
  try {
    const response = await fetch("/api/chat", {method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({question:text, history:history.slice(-4)}), signal:controller.signal});
    const result = await response.json(); if (token !== revision) return; $("#typing")?.remove();
    if (!response.ok) throw new Error(result.error || "The request could not be answered.");
    if (typeof result.text !== "string") throw new Error("The app returned an unreadable response. Please try again.");
    addMessage("assistant", result.text, Array.isArray(result.sources) ? result.sources : [], result.mode || "");
    history.push({role:"assistant", text:result.text});
  } catch (error) {
    if (token !== revision || error.name === "AbortError") return;
    $("#typing")?.remove(); addMessage("assistant", error.message || "I couldn’t complete that. Please try again.", [], "request-error");
  } finally {
    if (token === revision) { request = null; send.disabled = false; stop.hidden = true; input.focus(); }
  }
}
stop.addEventListener("click", () => { cancelRequest(); addMessage("assistant", "Response stopped in this browser. You can send another question.", [], "stopped"); });
function renderSessions() {
  const list = $("#chat-list"); list.replaceChildren();
  if (!sessions.length) { const empty = document.createElement("p"); empty.className = "empty-history"; empty.textContent = "Keep a conversation here to return to it during this session."; list.append(empty); }
  for (const session of sessions) {
    const row = document.createElement("div"); row.className = `chat-item${session.id === activeSession ? " active" : ""}`;
    const open = tool(session.title, () => {
      if ((entries.length || input.value) && !confirm("Replace the current conversation and unsent draft? Use Keep chat first to retain a session copy.")) return;
      cancelRequest(); window.speechSynthesis?.cancel(); entries.splice(0); history.splice(0); messages.replaceChildren(); input.value = ""; input.style.height = "auto";
      for (const entry of session.entries) { const copy = structuredClone(entry); entries.push(copy); renderMessage(copy); history.push({role:copy.role,text:copy.text}); }
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
  session.entries = structuredClone(entries); activeSession = session.id; renderSessions(); $("#session-note").textContent = "Session copy updated. Reloading clears all chats.";
});
$("#new-chat").addEventListener("click", () => {
  if ((entries.length || input.value) && !confirm("Start a new chat? Use Keep chat first to retain the current conversation during this session.")) return;
  cancelRequest(); window.speechSynthesis?.cancel(); entries.splice(0); history.splice(0); messages.replaceChildren(); input.value = ""; input.style.height = "auto";
  activeSession = null; intro.hidden = false; renderSessions(); setMenu(false); input.focus();
});
form.addEventListener("submit", event => { event.preventDefault(); ask(input.value); });
input.addEventListener("keydown", event => { if (event.key === "Enter" && !event.shiftKey && !event.isComposing) { event.preventDefault(); form.requestSubmit(); } });
input.addEventListener("input", () => { input.style.height = "auto"; input.style.height = `${Math.min(input.scrollHeight,150)}px`; });
document.querySelectorAll("[data-prompt]").forEach(button => button.addEventListener("click", () => {
  if (input.value.trim() && !confirm("Replace your unsent question with this topic?")) return;
  input.value = button.dataset.prompt; input.dispatchEvent(new Event("input")); setMenu(false); input.focus();
}));
fetch("/api/status").then(response => response.json()).then(status => { engineLabel.textContent = status.model ? "Local model configured · Private" : "Source mode · No API key"; }).catch(() => { engineLabel.textContent = "Local app unavailable · Start app.py"; });
