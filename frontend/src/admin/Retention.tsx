import { useEffect, useState } from "react";
import { request } from "../api";
export function Retention({ workspace }: { workspace: string }) {
  const [days, setDays] = useState({ documentDays: 0, questionDays: 0 }),
    [pending, setPending] = useState({ pending: 0, retrying: 0 }),
    [loading, setLoading] = useState(true),
    [loaded, setLoaded] = useState(false),
    [busy, setBusy] = useState(false),
    [confirm, setConfirm] = useState(false),
    [message, setMessage] = useState(""),
    [refresh, setRefresh] = useState(0);
  const base = `/workspaces/${workspace}`;
  useEffect(() => {
    let active = true;
    Promise.all([
      request(`${base}/retention`).then((r) => r.json()),
      request(`${base}/deletions`).then((r) => r.json()),
    ])
      .then(([policy, jobs]) => {
        if (active) {
          setDays(policy);
          setLoaded(true);
          setPending(jobs);
        }
      })
      .catch((e) => {
        if (active) setMessage(e.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [base, refresh]);
  return (
    <section className="source-detail">
      <h2>Retention and deletion</h2>
      <p>
        Zero disables automatic deletion. Document age is measured from its
        newest uploaded version. Expired documents are removed with all versions
        and saved answers containing them. Question retention removes question
        history, feedback and corrections.
      </p>
      {loading ? (
        <p role="status">Loading retention policy…</p>
      ) : (
        <form
          className="retention-form"
          onSubmit={async (e) => {
            e.preventDefault();
            setBusy(true);
            setMessage("");
            try {
              await request(`${base}/retention`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(days),
              });
              setConfirm(false);
              setMessage(
                "Retention policy saved. Enabled policies run on the hourly maintenance cycle.",
              );
            } catch (error) {
              setMessage(
                error instanceof Error ? error.message : "Could not save",
              );
            } finally {
              setBusy(false);
            }
          }}
        >
          <label htmlFor="document-days">
            Delete documents after days without a new version
          </label>
          <input
            id="document-days"
            type="number"
            min={0}
            max={3650}
            required
            value={days.documentDays}
            onChange={(e) => {
              setDays({ ...days, documentDays: Number(e.target.value) });
              setConfirm(false);
            }}
          />
          <label htmlFor="question-days">Delete questions after days</label>
          <input
            id="question-days"
            type="number"
            min={0}
            max={3650}
            required
            value={days.questionDays}
            onChange={(e) => {
              setDays({ ...days, questionDays: Number(e.target.value) });
              setConfirm(false);
            }}
          />
          <label className="group-member">
            <input
              type="checkbox"
              checked={confirm}
              onChange={(e) => setConfirm(e.target.checked)}
            />
            I understand that enabled policies permanently delete matching
            content, including existing content.
          </label>
          <button className="button primary" disabled={busy || !confirm || !loaded}>
            Save retention policy
          </button>
        </form>
      )}
      <p>
        {pending.pending} original files awaiting physical deletion;{" "}
        {pending.retrying} retrying.
      </p>
      <button
        className="button secondary"
        disabled={busy}
        onClick={() => setRefresh((n) => n + 1)}
      >
        Refresh deletion status
      </button>
      <p role="status">{message}</p>
    </section>
  );
}
