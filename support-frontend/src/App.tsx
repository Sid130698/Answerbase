import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { Session } from '@supabase/supabase-js'
import { api, auth, customerOrigin } from './api'

type Staff = { userId: string; role: 'support' | 'platform_admin' }
type Organization = { id: string; name: string }
type SupportSession = { id: string; organizationId: string; organizationName: string; reason: string; expiresAt: string }
type Snapshot = { session: SupportSession; users: { id: string; organizationRole: string }[]; workspaces: { id: string; name: string }[]; memberships: { workspaceId: string; userId: string; role: string }[] }
type Grant = { token: string; expiresAt: string; userId: string }
type Audit = { id: number; actor_id: string; action: string; detail: string; occurred_at: string; target_user_id: string | null }
const message = (error: unknown) => error instanceof Error ? error.message : 'Something went wrong. Please try again.'

export function App() {
  const [session, setSession] = useState<Session | null>(null)
  const [staff, setStaff] = useState<Staff | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    if (!auth) { setLoading(false); return }
    let active = true
    const { data } = auth.auth.onAuthStateChange((_event, next) => { if (active) { setSession(next); setStaff(null); setLoading(false) } })
    auth.auth.getSession().then(({ data }) => { if (active) { setSession(data.session); setLoading(false) } }).catch(cause => { if (active) { setError(message(cause)); setLoading(false) } })
    return () => { active = false; data.subscription.unsubscribe() }
  }, [])
  useEffect(() => {
    if (!session) return
    let active = true
    setError('')
    api<Staff>('/me').then(value => { if (active) setStaff(value) }).catch(cause => { if (active) setError(message(cause)) })
    return () => { active = false }
  }, [session, attempt])
  async function signOut() {
    const result = await auth!.auth.signOut({ scope: 'local' })
    if (result.error) throw result.error
    setStaff(null); setSession(null); setError('')
  }
  if (staff && session) return <Console key={staff.userId} staff={staff} email={session.user.email || staff.userId} onSignOut={signOut} />
  return <div className="login"><div className="wordmark">Answerbase <span>STAFF</span></div><main>
    <p className="eyebrow">INTERNAL SUPPORT</p><h1>Sign in to help.</h1><p>For provisioned Answerbase employees only. Client accounts have no access.</p>
    {!auth ? <p role="alert">Staff sign-in is not configured.</p> : loading ? <p role="status">Restoring session…</p> : session ? <>
      <p role={error ? 'alert' : 'status'}>{error || 'Checking staff access…'}</p>
      {error && <button onClick={() => setAttempt(value => value + 1)}>Retry access check</button>}<button onClick={() => { signOut().catch(cause => setError(message(cause))) }}>Sign out</button>
    </> : <form onSubmit={async (event: FormEvent) => {
      event.preventDefault(); if (busy) return; setBusy(true); setError('')
      try { const result = await auth!.auth.signInWithPassword({ email: email.trim(), password }); if (result.error) throw result.error; setPassword('') }
      catch (cause) { setError(message(cause)) } finally { setBusy(false) }
    }}><label htmlFor="staff-email">Work email</label><input id="staff-email" type="email" autoComplete="username" required value={email} onChange={event => setEmail(event.target.value)} />
      <label htmlFor="staff-password">Password</label><input id="staff-password" type="password" autoComplete="current-password" required value={password} onChange={event => setPassword(event.target.value)} />
      {error && <p role="alert" className="error">{error}</p>}<button className="primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button></form>}
  </main></div>
}

function Console({ staff, email, onSignOut }: { staff: Staff; email: string; onSignOut: () => Promise<void> }) {
  const [organizations, setOrganizations] = useState<Organization[]>([])
  const [query, setQuery] = useState('')
  const [selectedOrg, setSelectedOrg] = useState('')
  const [reason, setReason] = useState('')
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null)
  const [target, setTarget] = useState('')
  const [grant, setGrant] = useState<Grant | null>(null)
  const [workspace, setWorkspace] = useState('')
  const [role, setRole] = useState('viewer')
  const [audit, setAudit] = useState<Audit[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const frame = useRef<HTMLIFrameElement>(null)
  const running = useRef(false)
  async function run(action: () => Promise<void>) {
    if (running.current) return
    running.current = true; setBusy(true); setError(''); setNotice('')
    try { await action() } catch (cause) { setError(message(cause)) } finally { running.current = false; setBusy(false) }
  }
  async function search() { setOrganizations(await api<Organization[]>(`/organizations?query=${encodeURIComponent(query)}`)) }
  async function end() {
    frame.current?.contentWindow?.postMessage({ type: 'answerbase:end' }, customerOrigin)
    setGrant(null)
    if (snapshot) await api(`/sessions/${snapshot.session.id}`, { method: 'DELETE' })
    setSnapshot(null); setTarget(''); setWorkspace('')
    window.history.replaceState(null, '', window.location.pathname)
  }
  useEffect(() => {
    let active = true
    const id = new URLSearchParams(window.location.search).get('session')
    if (id) api<Snapshot>(`/sessions/${encodeURIComponent(id)}`).then(value => { if (active) setSnapshot(value) }).catch(cause => { if (active) setError(message(cause)) })
    return () => { active = false }
  }, [])
  useEffect(() => {
    const receive = (event: MessageEvent) => {
      if (event.origin !== customerOrigin || event.source !== frame.current?.contentWindow) return
      if (event.data?.type === 'answerbase:ready' && grant) event.source?.postMessage({ type: 'answerbase:grant', ...grant }, { targetOrigin: customerOrigin })
      if (event.data?.type === 'answerbase:end-request') void run(end)
    }
    window.addEventListener('message', receive)
    return () => window.removeEventListener('message', receive)
  })
  useEffect(() => {
    if (!grant) return
    const timer = setTimeout(() => { setGrant(null); setNotice('Customer grant expired. Open a new customer view to continue.') }, Math.max(0, Date.parse(grant.expiresAt) - Date.now()))
    return () => clearTimeout(timer)
  }, [grant])
  useEffect(() => {
    if (!snapshot) return
    const timer = setTimeout(() => { setGrant(null); setSnapshot(null); setNotice('Support session expired. Start a new session.') }, Math.max(0, Date.parse(snapshot.session.expiresAt) - Date.now()))
    return () => clearTimeout(timer)
  }, [snapshot])
  async function openUser(user: string) {
    setGrant(null)
    const value = await api<Grant>(`/sessions/${snapshot!.session.id}/delegations`, { method: 'POST', body: JSON.stringify({ userId: user }) })
    setTarget(user); setGrant(value)
  }
  const assigned = snapshot?.memberships.filter(member => member.userId === target) || []
  return <div className="console">
    <a className="skip" href="#support-main">Skip to support controls</a>
    <header className="staff-header"><div className="wordmark">Answerbase <span>STAFF</span></div><div className="staff-identity">{email}<small>{staff.role === 'platform_admin' ? 'Platform administrator' : 'Support agent'}</small></div><button disabled={busy} onClick={() => void run(async () => { await end(); await onSignOut() })}>Sign out</button></header>
    <main id="support-main">
      <div className="page-title"><div><p className="eyebrow">CLIENT SUPPORT</p><h1>{snapshot ? snapshot.session.organizationName : 'Find the client. Understand the issue.'}</h1></div><button disabled={busy} onClick={() => void run(async () => { setAudit(await api<Audit[]>('/audit')) })}>View audit history</button></div>
      {error && <p role="alert" className="error">{error}</p>}{notice && <p role="status" className="notice">{notice}</p>}
      {!snapshot ? <section className="session-setup"><form onSubmit={event => { event.preventDefault(); void run(search) }}><label htmlFor="company-search">Company name or organization ID</label><div className="inline"><input id="company-search" value={query} maxLength={100} onChange={event => setQuery(event.target.value)} /><button disabled={busy}>{busy ? 'Working…' : 'Search'}</button></div></form>
        <form onSubmit={event => { event.preventDefault(); void run(async () => {
          const session = await api<SupportSession>('/sessions', { method: 'POST', body: JSON.stringify({ organizationId: selectedOrg, reason }) })
          const url = new URL(window.location.href); url.searchParams.set('session', session.id); window.history.replaceState(null, '', url)
          setSnapshot(await api<Snapshot>(`/sessions/${session.id}`))
        }) }}><label htmlFor="company">Client organization</label><select id="company" required value={selectedOrg} onChange={event => setSelectedOrg(event.target.value)}><option value="">Select a search result</option>{organizations.map(org => <option key={org.id} value={org.id}>{org.name} · {org.id.slice(0, 8)}</option>)}</select><p className="help">Search returns up to 100 organizations. Narrow your search if needed.</p>
        <label htmlFor="reason">Ticket reference / reason for access</label><textarea id="reason" required minLength={5} maxLength={500} value={reason} onChange={event => setReason(event.target.value)} placeholder="SUP-123 · Investigate missing workspace access" />
        <button className="primary" disabled={busy || !selectedOrg || reason.trim().length < 5}>Start 30-minute support session</button></form></section> : <>
        <div className="session-banner"><div><strong>Staff access · {snapshot.session.organizationName}</strong><p>{snapshot.session.reason} · Ends {new Date(snapshot.session.expiresAt).toLocaleTimeString()}</p><small>Signed in as {email}. Customer context: {grant ? grant.userId : 'none selected'}.</small></div><button disabled={busy} onClick={() => void run(end)}>End session / switch company</button></div>
        <div className="support-layout"><aside className="controls">
          <h2>Customer context</h2><label htmlFor="target-user">Client account ID</label><select id="target-user" value={target} disabled={busy} onChange={event => { setTarget(event.target.value); setGrant(null) }}><option value="">Choose a user</option>{snapshot.users.map(user => <option key={user.id} value={user.id}>{user.id} · {user.organizationRole}</option>)}</select>
          <p className="help">Account IDs are shown because customer profiles currently store no email directory.</p>
          <button className="primary" disabled={busy || !target} onClick={() => void run(() => openUser(target))}>Open real customer view</button>
          <h2>Workspace access</h2>{!target ? <p>Select an account to inspect membership.</p> : assigned.length ? <ul className="membership-list">{assigned.map(member => <li key={member.workspaceId}><strong>{snapshot.workspaces.find(space => space.id === member.workspaceId)?.name}</strong><span>{member.role}</span></li>)}</ul> : <p>No workspace memberships. The customer view will also have no workspace access.</p>}
          <form className="repair" onSubmit={event => { event.preventDefault(); void run(async () => {
            const path = `/sessions/${snapshot.session.id}/workspaces/${workspace}/members/${target}`
            await api(path, { method: role === 'none' ? 'DELETE' : 'PUT', ...(role !== 'none' ? { body: JSON.stringify({ role }) } : {}) })
            setSnapshot(await api<Snapshot>(`/sessions/${snapshot.session.id}`))
            if (grant) await openUser(target)
            setNotice('Workspace access updated and audited under your staff identity.')
          }) }}><h2>Staff repair</h2><p className="help">These changes use your staff authority, not the selected user’s permissions.</p>
          <label htmlFor="repair-workspace">Workspace</label><select id="repair-workspace" value={workspace} required onChange={event => setWorkspace(event.target.value)}><option value="">Choose workspace</option>{snapshot.workspaces.map(space => <option key={space.id} value={space.id}>{space.name}</option>)}</select>
          <label htmlFor="repair-role">New access</label><select id="repair-role" value={role} onChange={event => setRole(event.target.value)}><option value="viewer">Viewer</option><option value="editor">Editor</option><option value="admin">Workspace admin</option><option value="none">Remove access</option></select>
          <button disabled={busy || !target || !workspace}>Apply access change</button></form>
          <button disabled={busy} onClick={() => void run(async () => { setSnapshot(await api<Snapshot>(`/sessions/${snapshot.session.id}`)) })}>Refresh diagnostics</button>
        </aside><section className="customer-view" aria-label="Embedded customer application">
          <div className="frame-heading"><strong>Real customer application</strong><span>Customer permissions only</span></div>
          {grant ? <iframe key={grant.token} ref={frame} title={`Answerbase as customer ${grant.userId}`} src={`${customerOrigin}/?support=1`} sandbox="allow-scripts allow-same-origin allow-forms allow-downloads" referrerPolicy="no-referrer" onLoad={() => frame.current?.contentWindow?.postMessage({ type: 'answerbase:grant', ...grant }, customerOrigin)} /> : <div className="frame-empty"><h2>Choose a customer account</h2><p>The real application opens here with that user’s permissions. Staff credentials never enter this frame.</p></div>}
        </section></div>
      </>}
      {audit.length > 0 && <section className="audit"><h2>{staff.role === 'platform_admin' ? 'Platform audit history' : 'Your audit history'}</h2><p className="help">Latest 100 recorded events. Successful changes and their audit entries commit together.</p><div className="table-scroll" tabIndex={0} role="region" aria-label="Audit records"><table><thead><tr><th>Time</th><th>Staff actor</th><th>Action / target</th><th>Detail</th></tr></thead><tbody>{audit.map(row => <tr key={row.id}><td>{new Date(row.occurred_at).toLocaleString()}</td><td>{row.actor_id}</td><td>{row.action}<small>{row.target_user_id}</small></td><td>{row.detail}</td></tr>)}</tbody></table></div></section>}
    </main>
  </div>
}
