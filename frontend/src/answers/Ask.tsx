import { useEffect, useRef, useState } from "react";
import type { Workspace } from "../workspaces/WorkspaceGate";
import { request } from "../api";
import "./ask.css";
import { readEvents } from "./events";
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
  const value = new URLSearchParams(location.search).get("conversation");
  return value &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
      value,
    )
    ? value
    : crypto.randomUUID();
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
    [note, setNote] = useState(""),
    [reportOpen, setReportOpen] = useState(false),
    [feedbackBusy, setFeedbackBusy] = useState(false),
    [historyLoading, setHistoryLoading] = useState(true);
  const stream = useRef<AbortController | null>(null);
  const feedbackRequest = useRef<AbortController | null>(null);
  const answerRequest = useRef<AbortController | null>(null);
  function resetFeedback() {
    feedbackRequest.current?.abort();
    setFeedbackBusy(false);
    setFeedback("");
    setReason("wrong");
    setNote("");
    setReportOpen(false);
  }
  const key = useRef(crypto.randomUUID());
  const active = useRef(true);
  const base = `/workspaces/${workspace.id}/questions`;
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      stream.current?.abort();
      feedbackRequest.current?.abort();
      answerRequest.current?.abort();
    };
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    const url = new URL(location.href);
    url.searchParams.set("conversation", conversation);
    historyReplace(url);
    request(`${base}?conversation=${conversation}`, {
      signal: controller.signal,
    })
      .then((r) => r.json())
      .then((rows) => {
        if (!controller.signal.aborted) setHistory(rows);
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(e.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setHistoryLoading(false);
      });
    return () => controller.abort();
  }, [base, conversation, refresh]);
  useEffect(() => {
    if (!history.some(item => item.status === "queued" || item.status === "running")) return;
    const timer = window.setInterval(() => setRefresh(value => value + 1), 5000);
    return () => window.clearInterval(timer);
  }, [history]);
  async function ask(e: React.FormEvent) {
    e.preventDefault();
    if (busy || !question.trim()) return;
    answerRequest.current?.abort();
    const controller = new AbortController();
    stream.current = controller;
    setBusy(true);
    setDraft([]);
    setError("");
    setAnswer(null);
    setSelected(null);
    resetFeedback();
    setStatus("Searching your workspace…");
    try {
      const response = await request(`${base}/stream`, {
        method: "POST",
        signal: controller.signal,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          requestKey: key.current,
          conversationId: conversation,
          question,
        }),
      });
      let completed = false;
      await readEvents(response, (kind, data) => {
        if (controller.signal.aborted) return;
        if (kind === "status") setStatus((data as { message: string }).message);
        if (kind === "failure")
          throw new Error((data as { message: string }).message);
        if (kind === "reset") setDraft([]);
        if (kind === "claim")
          setDraft((previous) => [...previous, data as Claim]);
        if (kind === "answer") {
          setDraft([]);
          setAnswer(data as Answer);
          completed = true;
          setStatus("");
          key.current = crypto.randomUUID();
        }
      });
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
        setHistoryLoading(true);
        setRefresh((v) => v + 1);
      }
    }
  }
  const evidence = answer?.evidence.find((p) => p.id === selected?.passageId);
  async function vote(value: number) {
    if (!answer || feedbackBusy) return;
    const controller = new AbortController();
    feedbackRequest.current = controller;
    setFeedbackBusy(true);
    try {
      await request(`${base}/${answer.id}/feedback`, {
        signal: controller.signal,
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          vote: value,
          reason: value < 0 ? reason : null,
          note: value < 0 ? note : "",
        }),
      });
      if (!controller.signal.aborted) {
        setFeedback("Feedback saved.");
        setReportOpen(false);
      }
    } catch (e) {
      if (!controller.signal.aborted)
        setFeedback(e instanceof Error ? e.message : "Could not save feedback");
    } finally {
      if (!controller.signal.aborted) setFeedbackBusy(false);
    }
  }
  return (
    <div className="ask-layout">
      <section className="question-space">
        <h1>Ask a question</h1>
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
            {!answer.answer.abstained && (
              <ul className="answer-sources" aria-label="Sources used">
                {answer.evidence
                  .filter((p) =>
                    answer.answer.claims.some((c) =>
                      c.citations.some(
                        (citation) => citation.passageId === p.id,
                      ),
                    ),
                  )
                  .filter(
                    (p, i, sources) =>
                      sources.findIndex(
                        (source) => source.versionId === p.versionId,
                      ) === i,
                  )
                  .map((p) => (
                    <li key={p.versionId}>
                      <button
                        className="text-link"
                        onClick={() =>
                          setSelected({ passageId: p.id, quote: "" })
                        }
                      >
                        {p.title}
                      </button>
                      <span>
                        {" "}
                        · Updated {new Date(p.updatedAt).toLocaleDateString()}
                      </span>
                    </li>
                  ))}
              </ul>
            )}
            <div className="answer-feedback">
              <h3>Was this useful?</h3>
              <div className="feedback-actions">
                <button
                  className="button secondary"
                  disabled={feedbackBusy}
                  onClick={() => void vote(1)}
                >
                  Helpful
                </button>
                <button
                  className="button secondary"
                  disabled={feedbackBusy}
                  aria-expanded={reportOpen}
                  aria-controls="feedback-details"
                  onClick={() => setReportOpen((v) => !v)}
                >
                  Not helpful
                </button>
              </div>
              {reportOpen && (
                <div id="feedback-details" className="feedback-details">
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
                    disabled={feedbackBusy}
                    onClick={() => void vote(-1)}
                  >
                    Report a problem
                  </button>
                </div>
              )}
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
              answerRequest.current?.abort();
              setHistoryLoading(true);
              setHistory([]);
              setError("");
              setDraft([]);
              resetFeedback();
              setConversation(crypto.randomUUID());
              setAnswer(null);
              setSelected(null);
              setQuestion("");
              key.current = crypto.randomUUID();
            }}
          >
            New conversation
          </button>
          {historyLoading ? (
            <p role="status">Loading questions…</p>
          ) : (
            history.length === 0 && (
              <p>No questions in this conversation yet.</p>
            )
          )}
          <ul>
            {history.map((item) => (
              <li key={item.id}>
                <button
                  className="text-link"
                  disabled={busy || item.status === "running" || item.status === "queued"}
                  onClick={async () => {
                    if (item.status === "failed") {
                      setQuestion(item.question);
                      setError("");
                      key.current = crypto.randomUUID();
                      document.getElementById("question")?.focus();
                      return;
                    }
                    answerRequest.current?.abort();
                    const controller = new AbortController();
                    answerRequest.current = controller;
                    setError("");
                    resetFeedback();
                    try {
                      const result = await (
                        await request(`${base}/${item.id}`, {
                          signal: controller.signal,
                        })
                      ).json();
                      if (!controller.signal.aborted) {
                        setAnswer(result);
                        setSelected(null);
                      }
                    } catch (e) {
                      if (!controller.signal.aborted)
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
                  {item.status === "queued" ? "Queued for automatic retry" : item.status === "failed"
                    ? "Could not complete · Select to retry"
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
