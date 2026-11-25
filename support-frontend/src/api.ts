import { createClient } from '@supabase/supabase-js'
const url = import.meta.env.VITE_SUPABASE_URL
const key = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY
export const auth = url && key ? createClient(url, key, { auth: { storageKey: 'answerbase-staff-session', storage: window.sessionStorage, persistSession: true, detectSessionInUrl: false, autoRefreshToken: true } }) : null
export const customerOrigin = new URL(import.meta.env.VITE_CUSTOMER_ORIGIN || 'http://localhost:5173').origin
export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const { data } = await auth!.auth.getSession()
  if (!data.session) throw new Error('Sign in to continue.')
  const response = await fetch(`/api/v1/support${path}`, { ...init, signal: AbortSignal.timeout(15000), headers: { 'Content-Type': 'application/json', ...init?.headers, Authorization: `Bearer ${data.session.access_token}` } })
  if (!response.ok) { const problem = await response.json().catch(() => null); throw new Error(problem?.detail || 'Staff request failed. Please try again.') }
  return response.status === 204 ? undefined as T : response.json()
}
