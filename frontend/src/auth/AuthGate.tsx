import {JoinInvitation} from './JoinInvitation'
import { useEffect, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import type { Session } from '@supabase/supabase-js'
import { authRedirect, supabase } from './client'
import './auth.css'

import { supportMode, endSupportView } from '../support/transport'
import { OrganizationSetup } from './OrganizationSetup'
import { IdentityContext } from './identity'
import type { Identity } from './identity'

function AuthFrame({ children }: { children: ReactNode }) {
  return <div className="auth-layout"><aside className="auth-story"><a className="brand" href="/"><span className="brand-mark" aria-hidden="true">a.</span>Answerbase</a><div><div className="eyebrow">YOUR TEAM'S KNOWLEDGE</div><h1>Good answers<br />start with evidence.</h1><p>Bring your documents together. Ask a question. Go straight to the source.</p></div><p className="auth-footnote">A shared place for knowledge you can trust.</p></aside><main className="auth-main">{children}</main></div>
}

const incomingInvitation=window.location.hash.match(/^#join\/([A-Za-z0-9_-]{43})$/)?.[1]
if(incomingInvitation)sessionStorage.setItem('answerbase-invitation',incomingInvitation)

export function AuthGate({ children }: { children: ReactNode }) {
  const [invitation,setInvitation]=useState(()=>incomingInvitation||sessionStorage.getItem('answerbase-invitation'))
  useEffect(()=>{const sync=()=>{const token=window.location.hash.match(/^#join\/([A-Za-z0-9_-]{43})$/)?.[1];if(token){sessionStorage.setItem('answerbase-invitation',token);setInvitation(token)}};window.addEventListener('hashchange',sync);return()=>window.removeEventListener('hashchange',sync)},[])
  const [session, setSession] = useState<Session | null>(null)
  const [loading, setLoading] = useState(Boolean(supabase))
  const [recovery, setRecovery] = useState(false)
  const [identity, setIdentity] = useState<Identity | null>(null)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    if (!supabase) return
    let active = true
    const { data: { subscription } } = supabase.auth.onAuthStateChange((event, next) => {
      if (!active) return
      setSession(next)
      setIdentity(null)
      setError('')
      setLoading(false)
      if (event === 'PASSWORD_RECOVERY') setRecovery(true)
      if (event === 'SIGNED_OUT') setRecovery(false)
    })
    supabase.auth.getSession().then(({ data, error: authError }) => {
      if (!active) return
      if (authError) setError('Your sign-in link or session could not be restored. Please sign in again.')
      setSession(data.session)
      setLoading(false)
    }).catch(() => { if (active) { setLoading(false); setError('We could not restore your session. Please try again.') } })
    return () => { active = false; subscription.unsubscribe() }
  }, [])

  useEffect(() => {
    if (!session || recovery) return
    const controller = new AbortController()
    let active = true
    const timer = window.setTimeout(() => controller.abort(), 10000)
    fetch(`${import.meta.env.VITE_API_BASE_URL || ''}/api/v1/me`, { headers: { Authorization: `Bearer ${session.access_token}` }, signal: controller.signal })
      .then(async response => {
        if (!response.ok) throw new Error(response.status === 401 ? 'Your session could not be verified. Please sign out and sign in again.' : 'We could not open your workspace. Please try again.')
        return response.json() as Promise<Identity>
      }).then(data => { if (active) setIdentity(data) })
      .catch(cause => { if (active) setError(cause.name === 'AbortError' ? 'The connection timed out. Please try again.' : cause.message) })
      .finally(() => window.clearTimeout(timer))
    return () => { active = false; controller.abort(); window.clearTimeout(timer) }
  }, [session, recovery, retry])

  if (loading) return <AuthFrame><p role="status">Restoring your session…</p></AuthFrame>
  if (!supabase) return <AuthFrame><h1>Welcome to Answerbase</h1><p className="auth-description">Sign-in is not available yet. Please check back shortly.</p></AuthFrame>
  if (recovery && session) return <AuthFrame><AuthForm recovery onRecovered={() => setRecovery(false)} /></AuthFrame>
  if (!session) return <AuthFrame><AuthForm initialError={error} /></AuthFrame>
  if (!identity) return <AuthFrame><h1>Welcome back</h1>{error ? <><p role="alert" className="auth-error">{error}</p><button className="button primary" onClick={() => { setError(''); setIdentity(null); setRetry(value => value + 1) }}>Try again</button><SignOut /></> : <p role="status" className="auth-description">Opening your workspace…</p>}</AuthFrame>
  if(invitation)return <JoinInvitation token={invitation}/>;
  if (!identity.organization) return <OrganizationSetup onCreated={() => { setIdentity(null); setRetry(value => value + 1) }} />
  return <IdentityContext.Provider value={identity}>{children}</IdentityContext.Provider>
}

export function SignOut() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function signOut() {
    if (supportMode) { endSupportView(); return }
    setBusy(true); setError('')
    try {
      const result = await supabase!.auth.signOut({ scope: 'local' })
      if (result.error) throw result.error
      window.history.replaceState(null, '', window.location.pathname)
      window.dispatchEvent(new PopStateEvent('popstate'))
    } catch { setError('Could not sign out. Please try again.') }
    finally { setBusy(false) }
  }
  return <div className="signout"><button className="button secondary" onClick={signOut} disabled={busy}>{supportMode ? 'End customer view' : busy ? 'Signing out…' : 'Sign out'}</button>{error && <p role="alert" className="auth-error">{error}</p>}</div>
}

function AuthForm({ recovery = false, onRecovered, initialError = '' }: { recovery?: boolean; onRecovered?: () => void; initialError?: string }) {
  const [mode, setMode] = useState<'login' | 'register' | 'forgot'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(initialError)
  const [notice, setNotice] = useState('')
  const title = recovery ? 'Choose a new password' : mode === 'register' ? 'Create your account' : mode === 'forgot' ? 'Reset your password' : 'Welcome back'
  useEffect(() => { document.title = `${title} · Answerbase` }, [title])

  function switchMode(next: typeof mode) { setMode(next); setError(''); setNotice(''); setPassword(''); setShowPassword(false) }
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy) return
    setBusy(true); setError(''); setNotice('')
    try {
      if (recovery) {
        const { error: authError } = await supabase!.auth.updateUser({ password })
        if (authError) throw authError
        setPassword(''); onRecovered?.()
      } else if (mode === 'register') {
        const { data, error: authError } = await supabase!.auth.signUp({ email: email.trim(), password, options: { emailRedirectTo: authRedirect } })
        if (authError) throw authError
        setPassword('')
        if (!data.session) setNotice('Check your inbox for a confirmation link. If this address already has an account, sign in or reset your password. Open the link in this browser.')
      } else if (mode === 'forgot') {
        const { error: authError } = await supabase!.auth.resetPasswordForEmail(email.trim(), { redirectTo: authRedirect })
        if (authError) throw authError
        setNotice('If an account exists for this address, you’ll receive a password reset link. Open it in this browser.')
      } else {
        const { error: authError } = await supabase!.auth.signInWithPassword({ email: email.trim(), password })
        if (authError) throw authError
        setPassword('')
      }
    } catch (cause) {
      const code = (cause as { code?: string }).code
      setError(code === 'invalid_credentials' ? 'The email or password is incorrect.' : code === 'email_not_confirmed' ? 'Confirm your email before signing in. Check your inbox for the confirmation link.' : code === 'weak_password' ? 'Choose a stronger password with at least 12 characters.' : code === 'over_email_send_rate_limit' || code === 'over_request_rate_limit' ? 'Too many attempts. Please wait before trying again.' : 'We couldn’t complete that request. Check your connection and try again.')
    } finally { setBusy(false) }
  }
  async function resend() {
    if (!email.trim() || busy) return
    setBusy(true); setError('')
    try {
      const { error: authError } = await supabase!.auth.resend({ type: 'signup', email: email.trim(), options: { emailRedirectTo: authRedirect } })
      if (authError) throw authError
      setNotice('If this account is awaiting confirmation, a new link is on its way. Check your inbox.')
    } catch { setError('Unable to resend right now. Please wait and try again.') }
    finally { setBusy(false) }
  }
  return <div className="auth-form-wrap"><div className="eyebrow">ANSWERBASE</div><h1>{title}</h1><p className="auth-description">{recovery ? 'Use at least 12 characters to keep your account secure.' : mode === 'register' ? 'Your team’s knowledge, one question away.' : mode === 'forgot' ? 'We’ll email you a link to choose a new password.' : 'Sign in to your knowledge workspace.'}</p>
    <form onSubmit={submit} className="auth-form">
      {!recovery && <div><label htmlFor="auth-email">Email address</label><input id="auth-email" type="email" name="email" autoComplete="email" required maxLength={254} value={email} onChange={event => setEmail(event.target.value)} disabled={busy} /></div>}
      {(recovery || mode !== 'forgot') && <div><label htmlFor="auth-password">{recovery ? 'New password' : 'Password'}</label><div className="password-field"><input id="auth-password" name="password" type={showPassword ? 'text' : 'password'} autoComplete={recovery || mode === 'register' ? 'new-password' : 'current-password'} minLength={recovery || mode === 'register' ? 12 : 1} required value={password} onChange={event => setPassword(event.target.value)} disabled={busy} aria-describedby={recovery || mode === 'register' ? 'password-help' : undefined} /><button type="button" onClick={() => setShowPassword(value => !value)} aria-label={showPassword ? 'Hide password' : 'Show password'}>{showPassword ? 'Hide' : 'Show'}</button></div>{(recovery || mode === 'register') && <p className="field-help" id="password-help">At least 12 characters. A longer passphrase works well.</p>}</div>}
      {error && <p className="auth-error" role="alert">{error}</p>}{notice && <p className="auth-notice" role="status">{notice}</p>}
      <button className="button primary auth-submit" type="submit" disabled={busy}>{busy ? 'Please wait…' : recovery ? 'Save new password' : mode === 'register' ? 'Create account' : mode === 'forgot' ? 'Send reset link' : 'Sign in'}</button>
    </form>
    {!recovery && <div className="auth-links">{mode === 'login' && <button type="button" disabled={busy} onClick={() => switchMode('forgot')}>Forgot password?</button>}<p>{mode === 'login' ? 'New to Answerbase?' : 'Already have an account?'} <button type="button" disabled={busy} onClick={() => switchMode(mode === 'login' ? 'register' : 'login')}>{mode === 'login' ? 'Create an account' : 'Sign in'}</button></p>{(notice && mode === 'register' || error.includes('Confirm your email')) && <button type="button" disabled={busy || !email.trim()} onClick={resend}>Resend confirmation email</button>}</div>}
  </div>
}
