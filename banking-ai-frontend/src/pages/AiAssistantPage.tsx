import { FormEvent, useEffect, useState } from "react";
import { chat, getHistory, clearSession } from "../api/ai";

interface ChatItem {
  role: "user" | "ai";
  message: string;
  timestamp: string;
}

function getInitialSessionId(): string {
  if (typeof window === "undefined") {
    return `web-${Date.now().toString()}`;
  }
  const existing = window.localStorage.getItem("bankingAiSessionId");
  if (existing && existing.trim().length > 0) {
    return existing;
  }
  const fresh = `web-${Date.now().toString()}`;
  window.localStorage.setItem("bankingAiSessionId", fresh);
  return fresh;
}

export function AiAssistantPage() {
  const [sessionId, setSessionId] = useState<string>(getInitialSessionId);
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState<ChatItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function loadHistory() {
      try {
        const history = await getHistory(sessionId);
        const mapped: ChatItem[] = history.map((h) => ({
          role: h.role === "assistant" ? "ai" : (h.role as "user" | "ai"),
          message: h.message,
          timestamp: new Date().toISOString()
        }));
        setMessages(mapped);
      } catch {
        // ignore if no history
      }
    }
    void loadHistory();
  }, [sessionId]);

  async function handleSend(e: FormEvent) {
    e.preventDefault();
    if (!input.trim()) return;
    const text = input.trim();
    setInput("");
    setError(null);
    const userItem: ChatItem = {
      role: "user",
      message: text,
      timestamp: new Date().toISOString()
    };
    setMessages((prev) => [...prev, userItem]);
    setLoading(true);
    try {
      const resp = await chat(text, sessionId);
      const aiItem: ChatItem = {
        role: "ai",
        message: resp.response,
        timestamp: new Date().toISOString()
      };
      setMessages((prev) => [...prev, aiItem]);
    } catch (e) {
      setError(
        e instanceof Error ? e.message : "Failed to send message"
      );
    } finally {
      setLoading(false);
    }
  }

  async function handleResetSession() {
    setError(null);
    try {
      await clearSession(sessionId);
    } catch {
      // ignore
    }
    const nextId = `web-${Date.now().toString()}`;
    if (typeof window !== "undefined") {
      window.localStorage.setItem("bankingAiSessionId", nextId);
    }
    setSessionId(nextId);
    setMessages([]);
  }

  return (
    <div className="ai-chat-container">
      <div className="card chat-box">
        <div className="card-header">
          <div>
            <div className="card-title">BankingAssist AI</div>
            <div className="card-subtitle">
              Ask AI to onboard, open accounts, move money, and review fraud
            </div>
          </div>
          <button className="btn-ghost" type="button" onClick={handleResetSession}>
            New session
          </button>
        </div>
        <div className="chat-messages">
          {messages.length === 0 && (
            <div className="muted">
              Start by asking, for example:
              <br />
              &quot;Onboard a new customer Alice Johnson...&quot;
            </div>
          )}
          {messages.map((m, idx) => (
            <div
              key={idx.toString()}
              className={`chat-message ${m.role === "user" ? "user" : "ai"}`}
            >
              <div className="chat-meta">
                <span>{m.role === "user" ? "You" : "BankingAssist AI"}</span>
                <span>{new Date(m.timestamp).toLocaleTimeString()}</span>
              </div>
              <div>{m.message}</div>
            </div>
          ))}
        </div>
        <form onSubmit={handleSend}>
          <label>Message</label>
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Describe what you want BankingAssist AI to do..."
          />
          <div className="btn-row">
            <button className="btn-primary" type="submit" disabled={loading}>
              {loading ? "Sending..." : "Send"}
            </button>
          </div>
          {error && <div className="error-text">{error}</div>}
        </form>
      </div>
      <div className="card">
        <div className="card-header">
          <div>
            <div className="card-title">Examples & Guardrails</div>
            <div className="card-subtitle">
              How the assistant uses MCP tools
            </div>
          </div>
        </div>
        <div className="muted">
          <div>Try prompts like:</div>
          <ul style={{ paddingLeft: "1.2rem", marginTop: "0.5rem" }}>
            <li>
              &quot;Onboard a new customer with these details...&quot;
            </li>
            <li>
              &quot;Open a savings account for customer CUST-001 with INR
              10,000 initial deposit&quot;
            </li>
            <li>
              &quot;Send 5,000 INR from ACC-001 to ACC-002 via IMPS and follow
              all fraud rules&quot;
            </li>
            <li>
              &quot;Show all customers with pending KYC&quot;
            </li>
          </ul>
          <div style={{ marginTop: "0.75rem" }}>
            The assistant always:
          </div>
          <ul style={{ paddingLeft: "1.2rem", marginTop: "0.25rem" }}>
            <li>Checks KYC before opening accounts</li>
            <li>Runs fraud rules before processing payments</li>
            <li>
              Keeps an audit trail of the tools it calls and decisions taken
            </li>
          </ul>
        </div>
      </div>
    </div>
  );
}

