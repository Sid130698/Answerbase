import { useState, type FormEvent } from 'react'
import { supabase } from './client'
import { SignOut } from './AuthGate'

export function OrganizationSetup({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy) return
    setBusy(true); setError('')
    try {
      const { data } = await supabase!.auth.getSession()
      if (!data.session) throw new Error('Please sign in again.')
      const response = await fetch(`${import.meta.env.VITE_API_BASE_URL || ''}/api/v1/organizations`, {
        method: 'POST', signal: AbortSignal.timeout(15000),
        headers: { Authorization: `Bearer ${data.session.access_token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: name.trim() }),
      })
      if (!response.ok) {
        const problem = await response.json().catch(() => null)
        throw new Error(problem?.detail || 'Could not create your organization. Please try again.')
      }
      onCreated()
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Please try again.') }
    finally { setBusy(false) }
  }
  return <div className="workspace-onboarding">
    <header><span className="brand">Answerbase</span><SignOut /></header>
    <main>
      <div className="eyebrow">YOUR ORGANIZATION</div>
      <h1>Create your organization</h1>
      <p>Set up a private home for your company’s workspaces. You’ll become its owner.</p>
      <form onSubmit={submit}>
        <label htmlFor="organization-name">Organization name</label>
        <input id="organization-name" autoFocus required maxLength={100} disabled={busy} value={name} onChange={event => setName(event.target.value)} placeholder="e.g. Acme" />
        {error && <p role="alert" className="workspace-error">{error}</p>}
        <div className="workspace-actions"><button className="button primary" disabled={busy || !name.trim()}>{busy ? 'Creating…' : 'Create organization'}</button></div>
      </form>
      <h2 className="organization-join-heading">Joining an existing company?</h2>
      <p>Ask your company administrator to approve your account instead of creating another organization. Each account belongs to one organization.</p>
      <button className="button secondary" disabled={busy} onClick={onCreated}>Check my access</button>
    </main>
  </div>
}
