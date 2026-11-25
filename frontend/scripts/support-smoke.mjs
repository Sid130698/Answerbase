// Synthetic local end-to-end API test; no browser automation or external email delivery.
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { createClient } from '@supabase/supabase-js'
const root = fileURLToPath(new URL('../../', import.meta.url))
const env = Object.fromEntries(readFileSync(new URL('../.env.local', import.meta.url), 'utf8').split('\n').filter(line => line && !line.startsWith('#')).map(line => { const at = line.indexOf('='); return [line.slice(0, at), line.slice(at + 1)] }))
assert.equal(env.VITE_SUPABASE_URL, 'http://127.0.0.1:55321', 'Local test only')
async function account(label) {
  const client = createClient(env.VITE_SUPABASE_URL, env.VITE_SUPABASE_PUBLISHABLE_KEY, { auth: { flowType: 'pkce', autoRefreshToken: false, detectSessionInUrl: false } })
  const email = `${label}-${randomUUID()}@example.test`
  const password = `Local-${randomUUID()}!`
  const signup = await client.auth.signUp({ email, password, options: { emailRedirectTo: 'http://localhost:5173/' } })
  assert.ifError(signup.error)
  let mail
  for (let attempt = 0; attempt < 20; attempt++) {
    const inbox = await (await fetch('http://127.0.0.1:55324/api/v1/messages')).json()
    const found = inbox.messages?.find(item => item.To?.some(to => to.Address === email))
    if (found) { mail = await (await fetch(`http://127.0.0.1:55324/api/v1/message/${found.ID}`)).json(); break }
    await new Promise(resolve => setTimeout(resolve, 250))
  }
  assert.ok(mail)
  const link = mail.HTML.match(/href="([^"]*\/auth\/v1\/verify[^"]*)"/)?.[1]?.replaceAll('&amp;', '&')
  assert.equal(new URL(link).origin, 'http://127.0.0.1:55321')
  const verify = await fetch(link, { redirect: 'manual' })
  const code = new URL(verify.headers.get('location')).searchParams.get('code')
  const confirmed = await client.auth.exchangeCodeForSession(code)
  assert.ifError(confirmed.error)
  return { client, email, password, id: confirmed.data.user.id, token: confirmed.data.session.access_token }
}
async function request(base, path, token, method = 'GET', body, headers = {}) {
  return fetch(base + path, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers }, ...(body ? { body: JSON.stringify(body) } : {}) })
}
const customerBase = 'http://localhost:5173'
const staffBase = 'http://localhost:5174'
const [customer, staff] = await Promise.all([account('support-client'), account('answerbase-staff')])
const companyResponse = await request(customerBase, '/api/v1/organizations', customer.token, 'POST', { name: 'Support demo client' })
assert.equal(companyResponse.status, 200)
const company = await companyResponse.json()
const workspaceResponse = await request(customerBase, '/api/v1/workspaces', customer.token, 'POST', { name: 'Support demo workspace' }, { 'Idempotency-Key': randomUUID() })
assert.equal(workspaceResponse.status, 201)
const workspace = await workspaceResponse.json()
assert.equal((await request(staffBase, '/api/v1/support/me', customer.token)).status, 403)
assert.equal((await request(staffBase, '/api/v1/support/me', staff.token)).status, 403)
execFileSync(process.execPath, [root + 'scripts/provision-local-staff.mjs', staff.id, 'platform_admin'], { cwd: root, stdio: 'pipe' })
assert.equal((await request(staffBase, '/api/v1/support/me', staff.token)).status, 200)
assert.equal((await request(customerBase, '/api/v1/organizations', staff.token, 'POST', { name: 'Forbidden staff company' })).status, 403)
assert.equal((await request(customerBase, '/api/v1/support/me', staff.token)).status, 404, 'Customer proxy does not expose support routes')
assert.equal((await request(staffBase, '/api/v1/workspaces', customer.token)).status, 404, 'Staff proxy does not expose customer routes')
const started = await request(staffBase, '/api/v1/support/sessions', staff.token, 'POST', { organizationId: company.id, reason: 'LOCAL-TEST customer context and isolation' })
assert.equal(started.status, 200)
const session = await started.json()
const sessionPath = `/api/v1/support/sessions/${session.id}`
const snapshot = await request(staffBase, sessionPath, staff.token)
assert.equal(snapshot.status, 200)
assert.ok((await snapshot.json()).workspaces.some(space => space.id === workspace.id))
async function grant() {
  const response = await request(staffBase, sessionPath + '/delegations', staff.token, 'POST', { userId: customer.id })
  assert.equal(response.status, 200)
  return response.json()
}
const first = await grant()
const delegated = (path, value, method = 'GET', body) => request(customerBase, '/api/v1/delegated' + path, null, method, body, { 'X-Support-Grant': value.token, 'Idempotency-Key': randomUUID() })
assert.equal((await (await delegated('/me', first)).json()).id, customer.id)
assert.ok((await (await delegated('/workspaces', first)).json()).some(space => space.id === workspace.id))
assert.equal((await delegated('/workspaces', first, 'POST', { name: 'Created through delegated customer view' })).status, 201)
const second = await grant()
assert.equal((await delegated('/me', first)).status, 401, 'Switching/reissuing revokes the old grant')
assert.equal((await request(staffBase, '/api/v1/support/me', second.token)).status, 401, 'Grant cannot act as staff bearer token')
const history = await (await request(staffBase, '/api/v1/support/audit', staff.token)).json()
assert.ok(history.some(row => row.actor_id === staff.id && row.target_user_id === customer.id && row.action === 'delegated.workspace.create'))
assert.ok(!JSON.stringify(history).includes(second.token), 'Audit contains no raw grant')
assert.equal((await request(staffBase, sessionPath, staff.token, 'DELETE')).status, 204)
assert.equal((await delegated('/me', second)).status, 401, 'Ending session revokes delegated requests')
const customerPage = await fetch(customerBase)
assert.ok(customerPage.headers.get('content-security-policy').includes('http://localhost:5174'))
const staffPage = await fetch(staffBase)
assert.ok(staffPage.headers.get('content-security-policy').includes("frame-ancestors 'none'"))
mkdirSync(root + '.cache', { recursive: true })
writeFileSync(root + '.cache/support-demo.json', JSON.stringify({ supportUrl: staffBase, staff: { email: staff.email, password: staff.password, id: staff.id }, client: { email: customer.email, password: customer.password, id: customer.id, organizationId: company.id } }, null, 2) + '\n', { mode: 0o600 })
await staff.client.auth.signOut({ scope: 'local' })
await customer.client.auth.signOut({ scope: 'local' })
console.log('PASS: independent proxies, staff-only login, identity separation, delegated customer operations, switch/end revocation, audit attribution, framing headers.')
console.log('Synthetic local demo credentials saved to ignored .cache/support-demo.json (no access tokens stored).')
