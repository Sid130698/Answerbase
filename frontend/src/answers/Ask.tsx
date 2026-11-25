import { useEffect, useRef, useState } from "react";
import type { Workspace } from "../workspaces/WorkspaceGate";
import { request } from "../api";
import "./ask.css";
type Citation = { passageId: string; quote: string };
type Claim = { text: string; citations: Citation[] };
type Evidence = {
  id: string;
  documentId: string;
  versionId: string;
  ordinal: number;
  title: string;
  section: string;
  page: number | null;
  content: string;
  updatedAt: string;
  startOffset: number;
  endOffset: number;
};
type Answer = {
  id: string;
  conversationId: string;
  question: string;
  answer: { abstained: boolean; claims: Claim[] };
  evidence: Evidence[];
  message: string;
  model: string;
  latencyMs: number;
};
type History = { id: string; question: string; status: string };
function initialConversation() {
  return (
    new URLSearchParams(location.search).get("conversation") ||
    crypto.randomUUID()
  );
}
export function Ask({ workspace }: { workspace: Workspace }) {
  const [conversation, setConversation] = useState(initialConversation),
    [question, setQuestion] = useState(""),
    [busy, setBusy] = useState(false),
    [status, setStatus] = useState(""),
    [error, setError] = useState(""),
    [answer, setAnswer] = useState<Answer | null>(null),
    [selected, setSelected] = useState<Citation | null>(null),
    [history, setHistory] = useState<History[]>([]),
    [refresh, setRefresh] = useState(0),
    [draft, setDraft] = useState<Claim[]>([]),
    [feedback, setFeedback] = useState(""),
    [reason, setReason] = useState("wrong"),
    [note, setNote] = useState("");
  const key = useRef(crypto.randomUUID());
  const active = useRef(true);
  const base = `/workspaces/${workspace.id}/questions`;
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  useEffect(() => {
    const url = new URL(location.href);
    url.searchParams.set("conversation", conversation);
    historyReplace(url);
    request(`${base}?conversation=${conversation}`)
      .then((r) => r.json())
      .then((rows) => {
        if (active.current) setHistory(rows);
      })
      .catch((e) => {
        if (active.current) setError(e.message);
      });
  }, [base, conversation, refresh]);
  async function ask(e: React.FormEvent) {
    e.preventDefault();
    if (busy || !question.trim()) return;
    setBusy(true);
    setDraft([]);
    setError("");
    setAnswer(null);
    setSelected(null);
    setFeedback("");
    setStatus("Searching your workspace…");
    try {
      const response = await request(`${base}/stream`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          requestKey: key.current,
          conversationId: conversation,
          question,
        }),
      });
      if (!response.body)
        throw new Error("Streaming is not available in this browser.");
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let pending = "";
      let completed = false;
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        pending += decoder.decode(value, { stream: true });
        let boundary: number;
        while ((boundary = pending.indexOf("\n\n")) >= 0) {
          const event = pending.slice(0, boundary);
          pending = pending.slice(boundary + 2);
          const kind = event
            .split("\n")
            .find((l) => l.startsWith("event:"))
            ?.slice(6)
            .trim();
          const data = event
            .split("\n")
            .filter((l) => l.startsWith("data:"))
            .map((l) => l.slice(5))
            .join("\n");
          if (!data) continue;
          const parsed = JSON.parse(data);
          if (!active.current) {
            await reader.cancel();
            return;
          }
          if (kind === "status") setStatus(parsed.message);
          if (kind === "failure") throw new Error(parsed.message);
          if (kind === "claim") setDraft((previous) => [...previous, parsed]);
          if (kind === "answer") {
            setDraft([]);
            setAnswer(parsed);
            completed = true;
            setStatus("");
            key.current = crypto.randomUUID();
          }
        }
      }
      if (!completed)
        throw new Error(
          "The connection ended before the answer completed. Retry or check recent questions.",
        );
    } catch (e) {
      if (active.current)
        setError(e instanceof Error ? e.message : "Could not answer.");
    } finally {
      if (active.current) {
        setBusy(false);
        setRefresh((v) => v + 1);
      }
    }
  }
  const evidence = answer?.evidence.find((p) => p.id === selected?.passageId);
  async function vote(value: number) {
    if (!answer) return;
    try {
      await request(`${base}/${answer.id}/feedback`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          vote: value,
          reason: value < 0 ? reason : null,
          note,
        }),
      });
      setFeedback("Feedback saved.");
    } catch (e) {
      setFeedback(e instanceof Error ? e.message : "Could not save feedback");
    }
  }
  return (
    <div className="ask-layout">
      <section className="question-space">
        <div className="eyebrow">ASK YOUR KNOWLEDGE</div>
        <h1>Answers with evidence.</h1>
        <p className="intro">
          Ask about the documents you can access in {workspace.name}.
        </p>
        <form onSubmit={ask}>
          <div className="composer">
            <label className="visually-hidden" htmlFor="question">
              Your question
            </label>
            <textarea
              id="question"
              required
              maxLength={2000}
              rows={3}
              value={question}
              disabled={busy}
              placeholder="What would you like to know?"
              onChange={(e) => {
                setQuestion(e.target.value);
                key.current = crypto.randomUUID();
              }}
            />
            <div className="composer-footer">
              <span>Workspace sources</span>
              <button
                className="button primary"
                disabled={busy || !question.trim()}
              >
                {busy ? "Finding an answer…" : "Ask question"}
              </button>
            </div>
          </div>
        </form>
        {busy && <p role="status">{status}</p>}
        {error && (
          <p role="alert" className="source-error">
            {error}
          </p>
        )}
        {busy && !answer && draft.length > 0 && (
          <article aria-label="Verified answer in progress">
            {draft.map((claim, index) => (
              <p key={index}>{claim.text}</p>
            ))}
          </article>
        )}
        {answer && (
          <article className="answer-result" aria-label="Answer">
            <h2>{answer.question}</h2>
            {answer.answer.abstained ? (
              <p>{answer.message}</p>
            ) : (
              answer.answer.claims.map((claim, i) => (
                <p key={i}>
                  {claim.text}{" "}
                  {claim.citations.map((citation, j) => (
                    <button
                      className="citation-link"
                      key={j}
                      onClick={() => setSelected(citation)}
                      aria-label={`View citation ${i + 1}.${j + 1}`}
                    >
                      [{i + 1}.{j + 1}]
                    </button>
                  ))}
                </p>
              ))
            )}
            {answer.answer.abstained && answer.evidence.length > 0 && (
              <>
                <h3>Related sources</h3>
                <ul>
                  {answer.evidence.slice(0, 3).map((p) => (
                    <li key={p.id}>
                      <button
                        className="text-link"
                        onClick={() =>
                          setSelected({ passageId: p.id, quote: "" })
                        }
                      >
                        {p.title}
                      </button>
                    </li>
                  ))}
                </ul>
              </>
            )}
            <div className="answer-feedback">
              <h3>Was this useful?</h3>
              <button className="button secondary" onClick={() => void vote(1)}>
                Helpful
              </button>
              <label htmlFor="feedback-reason">What needs improving?</label>
              <select
                id="feedback-reason"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              >
                {["wrong", "outdated", "incomplete", "bad citation"].map(
                  (r) => (
                    <option key={r}>{r}</option>
                  ),
                )}
              </select>
              <label htmlFor="feedback-note">Optional detail</label>
              <input
                id="feedback-note"
                value={note}
                maxLength={2000}
                onChange={(e) => setNote(e.target.value)}
              />
              <button
                className="button secondary"
                onClick={() => void vote(-1)}
              >
                Report a problem
              </button>
              <p role="status">{feedback}</p>
            </div>
          </article>
        )}
        <section className="question-history">
          <h2>Recent questions</h2>
          <button
            className="text-link"
            disabled={busy}
            onClick={() => {
              setConversation(crypto.randomUUID());
              setAnswer(null);
              setSelected(null);
              setQuestion("");
              key.current = crypto.randomUUID();
            }}
          >
            New conversation
          </button>
          <ul>
            {history.map((item) => (
              <li key={item.id}>
                <button
                  className="text-link"
                  disabled={busy || item.status !== "complete"}
                  onClick={async () => {
                    try {
                      setAnswer(
                        await (await request(`${base}/${item.id}`)).json(),
                      );
                      setSelected(null);
                    } catch (e) {
                      setError(
                        e instanceof Error
                          ? e.message
                          : "Unable to open answer",
                      );
                    }
                  }}
                >
                  {item.question}
                </button>{" "}
                <small>
                  {item.status === "failed"
                    ? "Could not complete"
                    : item.status === "running"
                      ? "In progress"
                      : ""}
                </small>
              </li>
            ))}
          </ul>
        </section>
      </section>
      <aside className="evidence-panel">
        <div className="eyebrow">EVIDENCE</div>
        <h2>{evidence?.title || "Check the source"}</h2>
        {evidence ? (
          <>
            <p>
              {evidence.section}
              {evidence.page ? ` · Page ${evidence.page}` : ""}
            </p>
            <p className="field-help">
              Updated {new Date(evidence.updatedAt).toLocaleDateString()}
            </p>
            <Highlighted
              text={evidence.content}
              quote={selected?.quote || ""}
            />
            <a
              className="text-link"
              href={`?workspace=${workspace.id}&document=${evidence.documentId}&version=${evidence.versionId}&passage=${evidence.ordinal}&conversation=${conversation}#sources`}
            >
              Open source and original
            </a>
          </>
        ) : (
          <p>
            Select a numbered citation to see the exact supporting passage. Only
            sources you can access are searched.
          </p>
        )}
      </aside>
    </div>
  );
}
function historyReplace(url: URL) {
  window.history.replaceState(null, "", url);
}
function Highlighted({ text, quote }: { text: string; quote: string }) {
  const positions: number[] = [];
  let normalized = "";
  let whitespace = false;
  for (let i = 0; i < text.length; i++) {
    const space = /\s/.test(text[i]);
    if (space && whitespace) continue;
    normalized += space ? " " : text[i];
    positions.push(i);
    whitespace = space;
  }
  const needle = quote.replace(/\s+/g, " ").trim();
  const found = needle ? normalized.indexOf(needle) : -1;
  const start = found < 0 ? -1 : positions[found];
  const end = found < 0 ? -1 : positions[found + needle.length - 1] + 1;
  return (
    <pre className="cited-passage">
      {start < 0 ? (
        text
      ) : (
        <>
          {text.slice(0, start)}
          <mark>{text.slice(start, end)}</mark>
          {text.slice(end)}
        </>
      )}
    </pre>
  );
}
