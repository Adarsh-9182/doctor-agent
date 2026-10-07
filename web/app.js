const form = document.querySelector("#chat-form");
const input = document.querySelector("#question");
const messages = document.querySelector("#messages");
const send = document.querySelector("#send");
const engineLabel = document.querySelector("#engine-label");
const history = [];

function addMessage(role, text, sources = [], mode = "") {
  const article = document.createElement("article");
  article.className = `message ${role === "user" ? "user-message" : "assistant-message"}`;
  const avatar = document.createElement("span");
  avatar.className = "avatar";
  avatar.setAttribute("aria-hidden", "true");
  avatar.textContent = role === "user" ? "●" : "✳";
  const body = document.createElement("div");
  body.className = "message-body";
  const paragraph = document.createElement("p");
  paragraph.textContent = text;
  body.append(paragraph);
  if (mode) {
    const meta = document.createElement("span");
    meta.className = "message-meta";
    meta.textContent = mode.replaceAll("-", " ").toUpperCase();
    body.append(meta);
  }
  if (sources.length) {
    const list = document.createElement("div");
    list.className = "sources";
    for (const source of sources) {
      const link = document.createElement("a");
      link.href = source.url;
      link.target = "_blank";
      link.rel = "noopener noreferrer";
      link.textContent = `↗ ${source.source}`;
      list.append(link);
    }
    body.append(list);
  }
  if (role === "assistant" && "speechSynthesis" in window) {
    const tools = document.createElement("div");
    tools.className = "message-tools";
    const speak = document.createElement("button");
    speak.type = "button";
    speak.textContent = "▶ Read aloud";
    speak.addEventListener("click", () => {
      window.speechSynthesis.cancel();
      window.speechSynthesis.speak(new SpeechSynthesisUtterance(text));
    });
    tools.append(speak);
    body.append(tools);
  }
  article.append(avatar, body);
  messages.append(article);
  messages.scrollTop = messages.scrollHeight;
}

function addTyping() {
  const article = document.createElement("article");
  article.className = "message assistant-message";
  article.id = "typing";
  const avatar = document.createElement("span");
  avatar.className = "avatar";
  avatar.textContent = "✳";
  const dots = document.createElement("span");
  dots.className = "typing";
  dots.innerHTML = "<i></i><i></i><i></i>";
  article.append(avatar, dots);
  messages.append(article);
  messages.scrollTop = messages.scrollHeight;
}

async function ask(question) {
  const text = question.trim();
  if (!text || send.disabled) return;
  addMessage("user", text);
  history.push({ role: "user", text });
  input.value = "";
  input.style.height = "auto";
  send.disabled = true;
  addTyping();
  try {
    const response = await fetch("/api/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ question: text, history: history.slice(-4) }),
    });
    const result = await response.json();
    document.querySelector("#typing")?.remove();
    if (!response.ok) throw new Error(result.error || "The request could not be answered.");
    addMessage("assistant", result.text, result.sources, result.mode);
    history.push({ role: "assistant", text: result.text });
  } catch (error) {
    document.querySelector("#typing")?.remove();
    addMessage("assistant", error.message || "I couldn't complete that. Please try again.");
  } finally {
    send.disabled = false;
    input.focus();
  }
}

form.addEventListener("submit", (event) => {
  event.preventDefault();
  ask(input.value);
});
input.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    form.requestSubmit();
  }
});
input.addEventListener("input", () => {
  input.style.height = "auto";
  input.style.height = `${Math.min(input.scrollHeight, 120)}px`;
});
document.querySelectorAll("[data-prompt]").forEach((button) => {
  button.addEventListener("click", () => ask(button.dataset.prompt));
});
fetch("/api/status")
  .then((response) => response.json())
  .then((status) => {
    engineLabel.textContent = status.model ? "Local model + references" : "Reference mode · no API key";
  })
  .catch(() => {
    engineLabel.textContent = "Local only";
  });
