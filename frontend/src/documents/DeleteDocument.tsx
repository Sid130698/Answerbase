import { useState } from "react";
import { request } from "../api";
export function DeleteDocument({
  path,
  removed,
}: {
  path: string;
  removed: () => void;
}) {
  const [open, setOpen] = useState(false),
    [confirmation, setConfirmation] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  if (!open)
    return (
      <button className="button secondary" onClick={() => setOpen(true)}>
        Delete document
      </button>
    );
  return (
    <form
      className="source-detail"
      onSubmit={async (event) => {
        event.preventDefault();
        if (confirmation !== "DELETE") return;
        setBusy(true);
        setError("");
        try {
          await request(path, { method: "DELETE" });
          removed();
        } catch (e) {
          setError(e instanceof Error ? e.message : "Deletion failed");
        } finally {
          setBusy(false);
        }
      }}
    >
      <h3>Permanently delete this document?</h3>
      <p>
        All versions, passages and embeddings will be removed. Saved answers
        containing this source will be erased, and active questions in this
        workspace will be cancelled. Original-file deletion continues in the
        background and retries if storage is unavailable.
      </p>
      <label htmlFor="delete-confirmation">Type DELETE to confirm</label>
      <input
        id="delete-confirmation"
        value={confirmation}
        onChange={(e) => setConfirmation(e.target.value)}
        autoComplete="off"
      />
      {error && <p role="alert">{error}</p>}
      <button
        className="button secondary"
        type="button"
        disabled={busy}
        onClick={() => setOpen(false)}
      >
        Keep document
      </button>
      <button
        className="button primary"
        disabled={busy || confirmation !== "DELETE"}
      >
        Permanently delete
      </button>
    </form>
  );
}
