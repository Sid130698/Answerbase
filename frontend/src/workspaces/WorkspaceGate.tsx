import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useIdentity } from '../auth/identity'
import { supportMode, delegatedFetch } from '../support/transport'
import { supabase } from '../auth/client'
import { SignOut } from '../auth/AuthGate'

export type Workspace = { id: string; organizationId: string; name: string; role: string; createdAt: string }

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  if (supportMode) {
    const response = await delegatedFetch(`/workspaces${path}`, { ...init, headers: { 'Content-Type': 'application/json', ...init?.headers } })
    if (!response.ok) { const error = await response.json().catch(() => null); throw new Error(error?.detail || 'Request failed in customer context.') }
    return response.json()
  }
  const { data } = await supabase!.auth.getSession()
  if (!data.session) throw new Error('Your session has ended. Please sign in again.')
  const response = await fetch(`${import.meta.env.VITE_API_BASE_URL || ''}/api/v1/workspaces${path}`, {
    ...init, signal: AbortSignal.timeout(15000),
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.session.access_token}`, ...init?.headers },
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new Error(problem?.detail || 'Could not load your workspace. Please try again.')
  }
  return response.json()
}

function selectedId() { return new URLSearchParams(window.location.search).get('workspace') }
function navigate(id: string | null, create = false) {
  const url = new URL(window.location.href)
  if (id) url.searchParams.set('workspace', id)
  else url.searchParams.delete('workspace')
  if (create) url.searchParams.set('createWorkspace', 'true')
  else url.searchParams.delete('createWorkspace')
  window.history.pushState(null, '', url)
  window.dispatchEvent(new PopStateEvent('popstate'))
}

export function WorkspaceGate({ children }: { children: (workspace: Workspace, selector: ReactNode) => ReactNode }) {
  const { organization } = useIdentity()
  const canCreate = organization?.role === 'owner' || organization?.role === 'admin'
  const [workspaces, setWorkspaces] = useState<Workspace[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const [id, setId] = useState(selectedId)
  const [creating, setCreating] = useState(() => new URLSearchParams(window.location.search).has('createWorkspace'))
  const [name, setName] = useState('')
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState('')
  const key = useRef(crypto.randomUUID())
  const inFlight = useRef(false)
  useEffect(() => {
    const sync = () => { setId(selectedId()); setCreating(new URLSearchParams(window.location.search).has('createWorkspace')) }
    window.addEventListener('popstate', sync)
    return () => window.removeEventListener('popstate', sync)
  }, [])
  useEffect(() => {
    let active = true
    request<Workspace[]>('').then(rows => {
      if (!active) return
      setWorkspaces(rows)
      if (!selectedId() && rows[0]) navigate(rows[0].id, new URLSearchParams(window.location.search).has('createWorkspace'))
    }).catch(err => { if (active) setError(err instanceof Error ? err.message : 'Unable to load workspaces.') })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [attempt])
  useEffect(() => { if (creating || !id) document.title = 'Create workspace · Answerbase' }, [creating, id])
  const current = workspaces.find(workspace => workspace.id === id)
  const selector = <div className="workspace-controls">
    <p className="organization-name">{organization?.name}</p>
    <label htmlFor="workspace-select">Workspace</label>
    <select id="workspace-select" value={id || ''} onChange={event => navigate(event.target.value)}>
      {workspaces.map(workspace => <option key={workspace.id} value={workspace.id}>{workspace.name}</option>)}
    </select>
    {canCreate && <button className="text-link" onClick={() => { setSaveError(''); navigate(id, true) }}>+ New workspace</button>}
  </div>
  if (!loading && !error && current && (!creating || !canCreate)) return children(current, selector)

  return <div className="workspace-onboarding">
    <header><span className="brand">Answerbase</span><SignOut /></header>
    <main>
      {loading ? <p role="status">Loading your workspaces…</p> : error ? <>
        <h1>Couldn’t load workspaces</h1><p role="alert">{error}</p>
        <button className="button primary" onClick={() => { setLoading(true); setError(''); setAttempt(value => value + 1) }}>Try again</button>
      </> : id && !current && !creating ? <>
        <h1>Workspace unavailable</h1><p>This workspace doesn’t exist or you don’t have access.</p>
        <button className="button primary" onClick={() => navigate(workspaces[0]?.id ?? null)}>Go to your workspaces</button>
      </> : !canCreate ? <>
        <h1>No workspace access yet</h1><p>You belong to {organization?.name}. Ask a workspace admin to give you access.</p>
        <button className="button primary" onClick={() => { setLoading(true); setError(''); setAttempt(value => value + 1) }}>Check access</button>
      </> : <>
        <div className="eyebrow">YOUR TEAM’S KNOWLEDGE</div>
        <h1>{workspaces.length ? 'Create a workspace' : 'Create your first workspace'}</h1>
        <p>A shared place in {organization?.name} for your team’s documents and answers. Give it a name your team will recognize.</p>
        <form onSubmit={async event => {
          event.preventDefault()
          if (inFlight.current) return
          inFlight.current = true
          setSaving(true); setSaveError('')
          try {
            const workspace = await request<Workspace>('', { method: 'POST', headers: { 'Idempotency-Key': key.current }, body: JSON.stringify({ name: name.trim() }) })
            setWorkspaces(rows => [...rows.filter(row => row.id !== workspace.id), workspace])
            setName(''); key.current = crypto.randomUUID(); navigate(workspace.id)
          } catch (err) {
            setSaveError(err instanceof Error ? err.message : 'Could not create workspace. Please try again.')
          } finally { inFlight.current = false; setSaving(false) }
        }}>
          <label htmlFor="workspace-name">Workspace name</label>
          <input id="workspace-name" autoFocus required maxLength={80} value={name} disabled={saving} placeholder="e.g. Customer support" aria-describedby="workspace-help" onChange={event => { setName(event.target.value); key.current = crypto.randomUUID(); setSaveError('') }} />
          <p id="workspace-help" className="field-help">You’ll be the admin. Only members will have access. Inviting teammates comes later.</p>
          {saveError && <p role="alert" className="workspace-error">{saveError}</p>}
          <div className="workspace-actions"><button className="button primary" disabled={saving || !name.trim()}>{saving ? 'Creating…' : 'Create workspace'}</button>
            {workspaces.length > 0 && <button type="button" className="button" disabled={saving} onClick={() => navigate(current?.id ?? workspaces[0].id)}>Cancel</button>}</div>
        </form>
      </>}
    </main>
  </div>
}
