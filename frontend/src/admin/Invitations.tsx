import { useEffect, useState } from "react";
import { request } from "../api";
import { useIdentity } from "../auth/identity";
type Invite = {
  id: string;
  email: string;
  workspace_role: string;
  expires_at: string;
  accepted_at: string | null;
  revoked_at: string | null;
};
export function Invitations({ workspace }: { workspace: string }) {
  const identity = useIdentity();
  const canInvite = ["owner", "admin"].includes(
    identity.organization?.role || "",
  );
  const [email, setEmail] = useState(""),
    [role, setRole] = useState("viewer"),
    [rows, setRows] = useState<Invite[]>([]),
    [link, setLink] = useState(""),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false),
    [loading, setLoading] = useState(true),
    [refresh, setRefresh] = useState(0);
  const base = `/workspaces/${workspace}/invitations`;
  useEffect(() => {
    if (!canInvite) return;
    let active = true;
    request(base)
      .then((r) => r.json())
      .then((list) => {
        if (active) setRows(list);
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
  }, [base, canInvite, refresh]);
  if (!canInvite)
    return (
      <p>
        Ask an organization owner or admin to invite new colleagues. You can
        assign existing organization members below.
      </p>
    );
  return (
    <section className="source-detail">
      <h2>Invite a colleague</h2>
      <p>
        You approve this person joining your organization and this workspace.
        They must sign in with the confirmed email below. Share the private link
        with that person; no email delivery service is configured.
      </p>
      <form
        className="retention-form"
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          setMessage("");
          setLink("");
          try {
            const invite = await (
              await request(base, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email, role }),
              })
            ).json();
            setLink(`${location.origin}/#join/${invite.token}`);
            setEmail("");
            setRefresh((n) => n + 1);
          } catch (error) {
            setMessage(
              error instanceof Error
                ? error.message
                : "Could not create invitation",
            );
          } finally {
            setBusy(false);
          }
        }}
      >
        <label htmlFor="invite-email">Company email</label>
        <input
          id="invite-email"
          type="email"
          maxLength={320}
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <label htmlFor="invite-role">Workspace role</label>
        <select
          id="invite-role"
          value={role}
          onChange={(e) => setRole(e.target.value)}
        >
          {["viewer", "editor", "admin"].map((value) => (
            <option key={value}>{value}</option>
          ))}
        </select>
        <button className="button primary" disabled={busy}>
          Create invitation
        </button>
      </form>
      {link && (
        <div>
          <label htmlFor="invite-link">
            Private invitation link (shown once)
          </label>
          <input
            id="invite-link"
            readOnly
            value={link}
            onFocus={(e) => e.target.select()}
          />
          <p>Expires in seven days. Copy and share it securely.</p>
        </div>
      )}
      {loading ? (
        <p role="status">Loading invitations…</p>
      ) : rows.length === 0 ? (
        <p>No invitations yet.</p>
      ) : (
        <ul>
          {rows.map((row) => (
            <li key={row.id}>
              <span>
                {row.email} · {row.workspace_role} ·{" "}
                {row.accepted_at
                  ? "Accepted"
                  : row.revoked_at
                    ? "Revoked"
                    : new Date(row.expires_at) < new Date()
                      ? "Expired"
                      : "Pending"}
              </span>
              {!row.accepted_at && !row.revoked_at && (
                <button
                  className="text-link"
                  disabled={busy}
                  onClick={async () => {
                    setBusy(true);
                    try {
                      await request(`${base}/${row.id}`, { method: "DELETE" });
                      setRefresh((n) => n + 1);
                    } catch (e) {
                      setMessage(
                        e instanceof Error ? e.message : "Could not revoke",
                      );
                    } finally {
                      setBusy(false);
                    }
                  }}
                >
                  Revoke invitation
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      <button className="text-link" onClick={() => setRefresh((n) => n + 1)}>
        Refresh invitations
      </button>
      <p role="status">{message}</p>
    </section>
  );
}
