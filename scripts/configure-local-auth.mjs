import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
const status = JSON.parse(execFileSync('npx', ['--yes', 'supabase@3.3.0', 'status', '-o', 'json'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }))
const publicKey = status.PUBLISHABLE_KEY || status.ANON_KEY
if (!status.API_URL || !publicKey) throw new Error('Start local Supabase before configuring the app.')
function update(path, values) {
  let content = existsSync(path) ? readFileSync(path, 'utf8') : ''
  for (const [key, value] of Object.entries(values)) {
    const pattern = new RegExp(`^${key}=.*$`, 'm')
    const line = `${key}=${value}`
    content = pattern.test(content) ? content.replace(pattern, line) : `${content}\n${line}`
  }
  writeFileSync(path, content.trim() + '\n', { mode: 0o600 })
}
const browser = { VITE_SUPABASE_URL: status.API_URL, VITE_SUPABASE_PUBLISHABLE_KEY: publicKey }
update('.env', { ...browser, SUPABASE_AUTH_ISSUER: `${status.API_URL}/auth/v1`, SUPABASE_JWKS_URL: `${status.API_URL.replace('127.0.0.1', 'host.docker.internal').replace('localhost', 'host.docker.internal')}/auth/v1/.well-known/jwks.json` })
update('frontend/.env.local', { ...browser, VITE_SUPPORT_ORIGIN: 'http://localhost:5174' })
update('support-frontend/.env.local', { ...browser, VITE_CUSTOMER_ORIGIN: 'http://localhost:5173' })
console.log('Local auth configured in ignored env files. Only the public API key was copied.')
