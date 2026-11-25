import { supportMode } from '../support/transport'
import { createClient } from '@supabase/supabase-js'

const url = import.meta.env.VITE_SUPABASE_URL?.trim()
const key = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY?.trim()
// Public project configuration only. Never put a service-role key in VITE_* variables.
export const supabase = !supportMode && url && key ? createClient(url, key, {
  auth: { flowType: 'pkce', detectSessionInUrl: true, persistSession: true, autoRefreshToken: true },
}) : null
export const authRedirect = `${window.location.origin}/`
