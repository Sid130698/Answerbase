import { useEffect, useState } from "react";
import { request } from "../api";
type Policy = {
  retrievalMode: string;
  candidates: number;
  topK: number;
  rerank: boolean;
  generationModel: string;
  chunkChars: number;
  contextLine: boolean;
  redactPii: boolean;
  dailyAttempts: number;
};
export function ModelSettings({ workspace }: { workspace: string }) {
  const [policy, setPolicy] = useState<Policy | null>(null),
    [models, setModels] = useState<string[]>([]),
    [defaultModel, setDefaultModel] = useState(""),
    [message, setMessage] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [refresh, setRefresh] = useState(0);
  const base = `/workspaces/${workspace}/settings`;
  useEffect(() => {
    let active = true;
    request(base)
      .then((r) => r.json())
      .then((data) => {
        if (active) {
          setPolicy(data.policy);
          setModels(data.models);
          setDefaultModel(data.defaultModel);
          setError("");
        }
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, [base, refresh]);
  return (
    <section className="source-detail">
      <h2>Answer and retrieval settings</h2>
      {error && (
        <p role="alert">
          {error}{" "}
          <button
            className="text-link"
            onClick={() => setRefresh((n) => n + 1)}
          >
            Retry
          </button>
        </p>
      )}
      {!policy && !error ? (
        <p role="status">Loading settings…</p>
      ) : (
        policy && (
          <form
            className="retention-form"
            onSubmit={async (e) => {
              e.preventDefault();
              setBusy(true);
              setError("");
              setMessage("");
              try {
                await request(base, {
                  method: "PUT",
                  headers: { "Content-Type": "application/json" },
                  body: JSON.stringify(policy),
                });
                setMessage(
                  "Settings saved. Existing source passages are unchanged.",
                );
              } catch (error) {
                setError(
                  error instanceof Error ? error.message : "Could not save",
                );
              } finally {
                setBusy(false);
              }
            }}
          >
            <label htmlFor="model-choice">Answer model</label>
            <select
              id="model-choice"
              value={policy.generationModel}
              onChange={(e) =>
                setPolicy({ ...policy, generationModel: e.target.value })
              }
            >
              {models.map((model) => (
                <option key={model} value={model}>
                  {model === "default" ? `Default (${defaultModel})` : model}
                </option>
              ))}
            </select>
            <label htmlFor="retrieval-mode">Search method</label>
            <select
              id="retrieval-mode"
              value={policy.retrievalMode}
              onChange={(e) =>
                setPolicy({ ...policy, retrievalMode: e.target.value })
              }
            >
              <option value="hybrid">Hybrid: semantic and keyword</option>
              <option value="vector">Semantic only</option>
              <option value="keyword">Keyword only</option>
            </select>
            <label htmlFor="candidate-count">Candidate passages</label>
            <input
              id="candidate-count"
              type="number"
              min={1}
              max={100}
              required
              value={policy.candidates}
              onChange={(e) =>
                setPolicy({ ...policy, candidates: Number(e.target.value) })
              }
            />
            <label htmlFor="context-count">Passages used for an answer</label>
            <input
              id="context-count"
              type="number"
              min={1}
              max={Math.min(20, policy.candidates)}
              required
              value={policy.topK}
              onChange={(e) =>
                setPolicy({ ...policy, topK: Number(e.target.value) })
              }
            />
            <label className="group-member">
              <input
                type="checkbox"
                checked={policy.rerank}
                onChange={(e) =>
                  setPolicy({ ...policy, rerank: e.target.checked })
                }
              />
              Rerank candidates with the configured local model
            </label>
            <label htmlFor="chunk-size">Target passage size (characters)</label>
            <input
              id="chunk-size"
              type="number"
              min={600}
              max={4000}
              required
              value={policy.chunkChars}
              onChange={(e) =>
                setPolicy({ ...policy, chunkChars: Number(e.target.value) })
              }
            />
            <p className="field-help">
              Applies to future text processing. Existing document versions keep
              their passages.
            </p>
            <label className="group-member">
              <input
                type="checkbox"
                checked={policy.contextLine}
                onChange={(e) =>
                  setPolicy({ ...policy, contextLine: e.target.checked })
                }
              />
              Include section headings when building embeddings
            </label>
            <p className="field-help">
              Applies to future indexing; rebuild a document's index to apply it
              to an existing version.
            </p>
            <label className="group-member">
              <input
                type="checkbox"
                checked={policy.redactPii}
                onChange={(e) =>
                  setPolicy({ ...policy, redactPii: e.target.checked })
                }
              />
              Mask common personal identifiers in generation prompts
            </label>
            <p className="field-help">
              Masks email addresses, phone-like numbers and US Social Security
              number patterns. It is not comprehensive detection. Authorized
              original sources remain unchanged.
            </p>
            <label htmlFor="daily-attempts">
              Daily question-attempt budget
            </label>
            <input
              id="daily-attempts"
              type="number"
              min={1}
              max={10000}
              required
              value={policy.dailyAttempts}
              onChange={(e) =>
                setPolicy({ ...policy, dailyAttempts: Number(e.target.value) })
              }
            />
            <p className="field-help">
              Resets at 00:00 UTC. This limits requests, not dollar spend;
              provider charges need separately verified pricing.
            </p>
            <button className="button primary" disabled={busy}>
              Save answer settings
            </button>
            <p role="status">{message}</p>
          </form>
        )
      )}
    </section>
  );
}
