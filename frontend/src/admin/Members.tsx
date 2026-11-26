import { useEffect, useState } from "react";
import { request } from "../api";
type Member = { userId: string; email: string | null; role: string };
type Candidate = { userId: string; email: string | null };
export function Members({ workspace }: { workspace: string }) {
  const [rows, setRows] = useState<Member[]>([]),
    [eligible, setEligible] = useState<Candidate[]>([]),
    [target, setTarget] = useState(""),
    [role, setRole] = useState("viewer"),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false),
    [loading, setLoading] = useState(true),
    [refresh, setRefresh] = useState(0);
  const base = `/workspaces/${workspace}`;
  useEffect(() => {
    let active = true;
    Promise.all([
      request(`${base}/members`).then((r) => r.json()),
      request(`${base}/eligible-members`).then((r) => r.json()),
    ])
      .then(([members, people]) => {
        if (active) {
          setRows(members);
          setEligible(people);
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
  async function save(id: string, role: string | null) {
    setBusy(true);
    setMessage("");
    try {
      await request(`${base}/members/${id}`, {
        method: role ? "PUT" : "DELETE",
        headers: { "Content-Type": "application/json" },
        body: role ? JSON.stringify({ role }) : undefined,
      });
      setRefresh((n) => n + 1);
      window.dispatchEvent(new Event("workspace-membership-changed"));
      setMessage("Workspace access updated.");
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "Could not change access");
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="source-detail">
      <h2>Workspace members</h2>
      <p>
        Membership grants access only to this workspace. At least one admin must
        remain.
      </p>
      {loading ? (
        <p role="status">Loading members…</p>
      ) : (
        <ul>
          {rows.map((member) => (
            <li className="member-row" key={member.userId}>
              <span>{member.email || member.userId}</span>
              <label
                className="visually-hidden"
                htmlFor={`role-${member.userId}`}
              >
                Role for {member.email || member.userId}
              </label>
              <select
                id={`role-${member.userId}`}
                value={member.role}
                onChange={(e) =>
                  setRows((list) =>
                    list.map((item) =>
                      item.userId === member.userId
                        ? { ...item, role: e.target.value }
                        : item,
                    ),
                  )
                }
              >
                {["viewer", "editor", "admin"].map((value) => (
                  <option key={value}>{value}</option>
                ))}
              </select>
              <button
                className="button secondary"
                disabled={busy}
                onClick={() => void save(member.userId, member.role)}
              >
                Save role
              </button>
              <button
                className="text-link"
                disabled={busy}
                onClick={() => void save(member.userId, null)}
              >
                Remove access
              </button>
            </li>
          ))}
        </ul>
      )}
      <form
        className="retention-form"
        onSubmit={(e) => {
          e.preventDefault();
          void save(target, role);
        }}
      >
        <label htmlFor="existing-member">Existing organization member</label>
        <select
          id="existing-member"
          required
          value={target}
          onChange={(e) => setTarget(e.target.value)}
        >
          <option value="">Choose a colleague</option>
          {eligible
            .filter((p) => !rows.some((r) => r.userId === p.userId))
            .map((p) => (
              <option key={p.userId} value={p.userId}>
                {p.email || p.userId}
              </option>
            ))}
        </select>
        <label htmlFor="existing-role">Workspace role</label>
        <select
          id="existing-role"
          value={role}
          onChange={(e) => setRole(e.target.value)}
        >
          {["viewer", "editor", "admin"].map((value) => (
            <option key={value}>{value}</option>
          ))}
        </select>
        <button className="button primary" disabled={busy || !target}>
          Add to workspace
        </button>
      </form>
      <button className="text-link" onClick={() => setRefresh((n) => n + 1)}>
        Refresh members
      </button>
      <p role="status">{message}</p>
    </section>
  );
}
