import { useEffect, useState } from "react";
import { request } from "../api";
import { SignOut } from "./AuthGate";
export function JoinInvitation({ token }: { token: string }) {
  useEffect(()=>{document.title="Join your team · Answerbase"},[]);
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  return (
    <main className="auth-main">
      <h1>Join your team</h1>
      <p>
        Accept the invitation using the confirmed email your administrator
        invited. An account can belong to only one organization.
      </p>
      {error && <p role="alert">{error}</p>}
      <button
        className="button primary"
        disabled={busy}
        onClick={async () => {
          setBusy(true);
          setError("");
          try {
            const result = await (
              await request("/invitations/accept", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ token }),
              })
            ).json();
            sessionStorage.removeItem("answerbase-invitation");
            location.replace(`/?workspace=${result.workspaceId}#ask`);
          } catch (e) {
            setError(
              e instanceof Error ? e.message : "Could not accept invitation",
            );
          } finally {
            setBusy(false);
          }
        }}
      >
        {busy ? "Joining…" : "Accept invitation"}
      </button>
      <button
        className="button secondary"
        disabled={busy}
        onClick={() => {
          sessionStorage.removeItem("answerbase-invitation");
          location.replace("/");
        }}
      >
        Cancel
      </button>
      <SignOut />
    </main>
  );
}
