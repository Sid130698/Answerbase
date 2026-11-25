import { useEffect, useState } from "react";
import { request } from "../api";
import type { Workspace } from "../workspaces/WorkspaceGate";
import "./query-log.css";
type Row = {
  id: string;
  question: string;
  status: string;
  abstained: boolean;
  created_at: string;
  model: string;
  input_tokens: number;
  output_tokens: number;
  cost_usd: number | null;
  latency_ms: number | null;
  vote: number | null;
  reason: string | null;
  corrected: boolean;
};
type Detail = Row & {
  answer: {
    abstained: boolean;
    claims: {
      text: string;
      citations: { passageId: string; quote: string }[];
    }[];
  } | null;
  evidence: { id: string; title: string; content: string }[] | null;
  trace: Record<string, unknown> | null;
  note: string | null;
  corrected_answer: string | null;
};
type Usage = {
  questions: number;
  completed: number;
  abstained: number;
  failed: number;
  input_tokens: number;
  output_tokens: number;
  known_cost_usd: number;
  unpriced_queries: number;
  p50_ms: number | null;
  p95_ms: number | null;
};
const seconds = (value: number | null) =>
  value === null ? "—" : `${(value / 1000).toFixed(1)} s`;
export function QueryLog({ workspace }: { workspace: Workspace }) {
  const params = new URLSearchParams(location.search);
  const [offset, setOffset] = useState(() =>
      Math.max(
        0,
        Math.min(10000, Number(params.get("queryPage") || 0) * 25 || 0),
      ),
    ),
    [unanswered, setUnanswered] = useState(params.get("unanswered") === "true");
  const [rows, setRows] = useState<Row[]>([]),
    [usage, setUsage] = useState<Usage | null>(null),
    [detail, setDetail] = useState<Detail | null>(null),
    [correction, setCorrection] = useState(""),
    [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [message, setMessage] = useState(""),
    [refresh, setRefresh] = useState(0);
  const base = `/workspaces/${workspace.id}`;
  useEffect(() => {
    let active = true;
    // Refresh state accompanies this asynchronous request; retain previous results while loading.
    // oxlint-disable-next-line react/set-state-in-effect
    setLoading(true);
    setError("");
    const url = new URL(location.href);
    url.searchParams.set("queryPage", String(offset / 25));
    url.searchParams.set("unanswered", String(unanswered));
    history.replaceState(null, "", url);
    Promise.all([
      request(
        `${base}/query-log?offset=${offset}&unanswered=${unanswered}`,
      ).then((r) => r.json()),
      request(`${base}/usage`).then((r) => r.json()),
    ])
      .then(([list, stats]) => {
        if (active) {
          setRows(list);
          setUsage(stats);
        }
      })
      .catch((e) => {
        if (active) setError(e.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [base, offset, unanswered, refresh]);
  async function open(id: string) {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const data = await (await request(`${base}/query-log/${id}`)).json();
      setDetail(data);
      setCorrection(data.corrected_answer || "");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not open question");
    } finally {
      setBusy(false);
    }
  }
  async function exportCsv() {
    setBusy(true);
    try {
      const response = await request(`${base}/unanswered.csv`);
      const url = URL.createObjectURL(await response.blob());
      const a = document.createElement("a");
      a.href = url;
      a.download = "unanswered.csv";
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Export failed");
    } finally {
      setBusy(false);
    }
  }
  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!detail) return;
    setBusy(true);
    setError("");
    try {
      await request(`${base}/query-log/${detail.id}/correction`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ answer: correction }),
      });
      setMessage(
        "Correction saved to the evaluation candidate queue. It does not replace the original answer.",
      );
      setRefresh((n) => n + 1);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save correction");
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="query-admin">
      <div className="eyebrow">WORKSPACE ADMINISTRATION</div>
      <h1>Questions and quality</h1>
      <p>Review answers, reported problems and usage in {workspace.name}.</p>
      <button
        className="button secondary"
        disabled={loading || busy}
        onClick={() => setRefresh((n) => n + 1)}
      >
        Refresh
      </button>
      {error && (
        <p role="alert" className="source-error">
          {error}{" "}
          <button
            className="text-link"
            onClick={() => setRefresh((n) => n + 1)}
          >
            Retry
          </button>
        </p>
      )}
      {loading && <p role="status">Loading questions…</p>}
      {usage && (
        <section aria-label="Last seven days">
          <h2>Last seven days</h2>
          <dl className="usage-summary">
            <div>
              <dt>Questions</dt>
              <dd>{usage.questions}</dd>
            </div>
            <div>
              <dt>Completed requests</dt>
              <dd>{usage.completed}</dd>
            </div>
            <div>
              <dt>Abstentions / failures</dt>
              <dd>
                {usage.abstained} / {usage.failed}
              </dd>
            </div>
            <div>
              <dt>Input / output tokens</dt>
              <dd>
                {usage.input_tokens} / {usage.output_tokens}
              </dd>
            </div>
            <div>
              <dt>Median / 95th percentile</dt>
              <dd>
                {seconds(usage.p50_ms)} / {seconds(usage.p95_ms)}
              </dd>
            </div>
            <div>
              <dt>Known API charges</dt>
              <dd>${Number(usage.known_cost_usd).toFixed(4)}</dd>
            </div>
          </dl>
          <p className="field-help">
            {usage.unpriced_queries} completed queries have unknown pricing.
            Local-model API charges are zero; hardware and electricity are
            excluded.
          </p>
        </section>
      )}
      <div className="query-actions">
        <label>
          <input
            type="checkbox"
            checked={unanswered}
            onChange={(e) => {
              setUnanswered(e.target.checked);
              setOffset(0);
            }}
          />{" "}
          Unanswered only
        </label>
        <button
          className="button secondary"
          disabled={busy}
          onClick={() => void exportCsv()}
        >
          Export unanswered CSV
        </button>
        <span className="field-help">Latest 1,000 unanswered questions</span>
      </div>
      {!loading && !error && rows.length === 0 ? (
        <p>No questions in this view yet.</p>
      ) : (
        <div
          className="query-table"
          role="region"
          aria-label="Question history"
          tabIndex={0}
        >
          <table>
            <caption>Question history</caption>
            <thead>
              <tr>
                <th>Question</th>
                <th>Result</th>
                <th>Time</th>
                <th>Feedback</th>
                <th>Asked</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td>
                    <button
                      className="text-link"
                      disabled={busy}
                      onClick={() => void open(row.id)}
                    >
                      {row.question}
                    </button>
                  </td>
                  <td>
                    {row.status === "complete"
                      ? row.abstained
                        ? "Abstained"
                        : "Answered"
                      : row.status}
                    {row.corrected ? " · Corrected" : ""}
                  </td>
                  <td>{seconds(row.latency_ms)}</td>
                  <td>{row.vote === 1 ? "Helpful" : row.reason || "—"}</td>
                  <td>{new Date(row.created_at).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div className="query-actions">
        <button
          className="button secondary"
          disabled={offset === 0 || loading}
          onClick={() => setOffset((n) => Math.max(0, n - 25))}
        >
          Previous questions
        </button>
        <button
          className="button secondary"
          disabled={rows.length < 25 || loading || offset >= 10000}
          onClick={() => setOffset((n) => n + 25)}
        >
          Next questions
        </button>
      </div>
      {detail && (
        <section className="query-detail" aria-label="Question detail">
          <button className="text-link" onClick={() => setDetail(null)}>
            Close question
          </button>
          <h2>{detail.question}</h2>
          <p>
            {detail.model || "No model response"} · {detail.input_tokens} input
            / {detail.output_tokens} output tokens
          </p>
          {detail.answer?.abstained && <p>No supported answer was found.</p>}
          {detail.answer?.claims.map((claim, i) => (
            <section key={i}>
              <p>{claim.text}</p>
              {claim.citations.map((cite, j) => (
                <blockquote key={j}>
                  <p>{cite.quote}</p>
                  <cite>
                    {detail.evidence?.find((e) => e.id === cite.passageId)
                      ?.title || "Source unavailable"}
                  </cite>
                </blockquote>
              ))}
            </section>
          ))}
          {detail.note && <p>Feedback: {detail.note}</p>}
          <details>
            <summary>Timing breakdown</summary>
            <dl>
              {Object.entries(detail.trace || {})
                .filter(([key]) => key.endsWith("Ms"))
                .map(([key, value]) => (
                  <div key={key}>
                    <dt>{key.replace(/Ms$/, "").replace(/([A-Z])/g, " $1")}</dt>
                    <dd>{seconds(Number(value))}</dd>
                  </div>
                ))}
            </dl>
          </details>
          {detail.status === "complete" && (
            <form onSubmit={save}>
              <label htmlFor="corrected-answer">
                Reviewed answer for evaluation
              </label>
              <textarea
                id="corrected-answer"
                rows={5}
                required
                maxLength={8000}
                value={correction}
                onChange={(e) => setCorrection(e.target.value)}
              />
              <p className="field-help">
                Add a factual correction with source references. A saved
                candidate still needs review before becoming an evaluation
                baseline.
              </p>
              <button
                className="button primary"
                disabled={busy || !correction.trim()}
              >
                Save evaluation candidate
              </button>
            </form>
          )}
          <p role="status">{message}</p>
        </section>
      )}
    </section>
  );
}
