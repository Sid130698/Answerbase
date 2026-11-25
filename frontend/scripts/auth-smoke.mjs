// Local-only integration test. Creates a synthetic user and captures emails in Mailpit.
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { randomUUID } from 'node:crypto'
import { createClient } from '@supabase/supabase-js'
const env = Object.fromEntries(readFileSync(new URL('../.env.local', import.meta.url), 'utf8').split('\n').filter(line => line && !line.startsWith('#')).map(line => { const at = line.indexOf('='); return [line.slice(0, at), line.slice(at + 1)] }))
const url = env.VITE_SUPABASE_URL
assert.equal(new URL(url).hostname, '127.0.0.1', 'This script only runs against local Supabase')
assert.equal(new URL(url).port, '55321')
const count = Number(process.argv[2] || 1)
assert.ok(Number.isInteger(count) && count >= 1 && count <= 5, 'Provide an account count from 1 to 5')
async function exerciseAccount() {
  const client = createClient(url, env.VITE_SUPABASE_PUBLISHABLE_KEY, { auth: { flowType: 'pkce', autoRefreshToken: false, detectSessionInUrl: false } })
  const email = `auth-test-${randomUUID()}@example.test`
  const password = `Test-${randomUUID()}!`
  const changedPassword = `Reset-${randomUUID()}!`
  const signup = await client.auth.signUp({ email, password, options: { emailRedirectTo: 'http://localhost:5173/' } })
  assert.ifError(signup.error)
  assert.equal(signup.data.session, null, 'Signup requires email confirmation')
  assert.ok((await client.auth.signInWithPassword({ email, password })).error, 'Unconfirmed login is rejected')
  async function confirmEmail(subject) {
    let message
    for (let attempt = 0; attempt < 15; attempt++) {
      const inbox = await (await fetch('http://127.0.0.1:55324/api/v1/messages')).json()
      const found = inbox.messages?.find(item => item.To?.some(to => to.Address === email) && item.Subject.toLowerCase().includes(subject))
      if (found) { message = await (await fetch(`http://127.0.0.1:55324/api/v1/message/${found.ID}`)).json(); break }
      await new Promise(resolve => setTimeout(resolve, 300))
    }
    assert.ok(message, `Expected ${subject} email in Mailpit`)
    const link = message.HTML.match(/href="([^"]*\/auth\/v1\/verify[^"]*)"/)?.[1]?.replaceAll('&amp;', '&')
    assert.ok(link, 'Verification link exists')
    assert.equal(new URL(link).hostname, '127.0.0.1')
    const response = await fetch(link, { redirect: 'manual' })
    const location = new URL(response.headers.get('location'))
    const code = location.searchParams.get('code')
    assert.ok(code, 'Confirmation redirects with a PKCE code')
    const exchange = await client.auth.exchangeCodeForSession(code)
    assert.ifError(exchange.error)
    return exchange.data.session
  }
  await confirmEmail('confirm')
  assert.ifError((await client.auth.signOut({ scope: 'local' })).error)
  assert.ok((await client.auth.signInWithPassword({ email, password: 'incorrect-password' })).error)
  const login = await client.auth.signInWithPassword({ email, password })
  assert.ifError(login.error)
  const identity = await fetch('http://localhost:5173/api/v1/me', { headers: { Authorization: `Bearer ${login.data.session.access_token}` } })
  assert.equal(identity.status, 200)
  assert.equal((await identity.json()).email, email)
  assert.equal((await fetch('http://localhost:5173/api/v1/me')).status, 401)
  assert.ifError((await client.auth.refreshSession()).error)
  assert.ifError((await client.auth.signOut({ scope: 'local' })).error)
  assert.equal((await client.auth.getSession()).data.session, null)
  assert.ifError((await client.auth.resetPasswordForEmail(email, { redirectTo: 'http://localhost:5173/' })).error)
  await confirmEmail('reset')
  assert.ifError((await client.auth.updateUser({ password: changedPassword })).error)
  await client.auth.signOut({ scope: 'local' })
  assert.ok((await client.auth.signInWithPassword({ email, password })).error)
  assert.ifError((await client.auth.signInWithPassword({ email, password: changedPassword })).error)
  await client.auth.signOut({ scope: 'local' })
  return { email, password: changedPassword, id: login.data.user.id }
}
const workspaceIds = []
const accounts = []
for (let index = 0; index < count; index++) {
  accounts.push(await exerciseAccount())
  console.log(`PASS account ${index + 1}/${count}: signup, confirmation, rejected invalid login, API identity, refresh, logout, password recovery`)
}
assert.equal(new Set(accounts.map(account => account.id)).size, count, 'Each account has a distinct identity')
// Reuse one SDK client, as a browser does when different users sign in and out.
const shared = createClient(url, env.VITE_SUPABASE_PUBLISHABLE_KEY, { auth: { autoRefreshToken: false, detectSessionInUrl: false } })
for (let round = 0; round < 2; round++) {
  for (const account of [...accounts].reverse()) {
    assert.equal((await shared.auth.getSession()).data.session, null)
    const { data, error } = await shared.auth.signInWithPassword({ email: account.email, password: account.password })
    assert.ifError(error)
    assert.equal(data.user.id, account.id)
    const response = await fetch('http://localhost:5173/api/v1/me', { headers: { Authorization: `Bearer ${data.session.access_token}` } })
    assert.equal(response.status, 200)
    const identity = await response.json()
    assert.equal(identity.id, account.id)
    assert.equal(identity.email, account.email)
    if (round === 0) assert.equal(identity.organization, null)
    else assert.equal(identity.organization.id, account.organizationId)
    const headers = { Authorization: `Bearer ${data.session.access_token}`, 'Content-Type': 'application/json' }
    const endpoint = 'http://localhost:5173/api/v1/workspaces'
    if (round === 0) {
      assert.equal((await fetch(endpoint, { headers })).status, 403)
      const orgBody = JSON.stringify({ name: `Company ${account.id}` })
      const organizationResponse = await fetch('http://localhost:5173/api/v1/organizations', { method: 'POST', headers, body: orgBody })
      assert.equal(organizationResponse.status, 200)
      const organization = await organizationResponse.json()
      account.organizationId = organization.id
      assert.equal(organization.role, 'owner')
      const retry = await fetch('http://localhost:5173/api/v1/organizations', { method: 'POST', headers, body: orgBody })
      assert.deepEqual(await retry.json(), organization)
      const second = await fetch('http://localhost:5173/api/v1/organizations', { method: 'POST', headers, body: JSON.stringify({ name: 'Other company' }) })
      assert.equal(second.status, 409)
      const creationHeaders = { ...headers, 'Idempotency-Key': randomUUID() }
      const body = JSON.stringify({ name: 'Auth smoke workspace' })
      const created = await fetch(endpoint, { method: 'POST', headers: creationHeaders, body })
      assert.equal(created.status, 201)
      const workspace = await created.json()
      assert.equal(workspace.role, 'admin')
      assert.equal(workspace.organizationId, account.organizationId)
      workspaceIds.push({ id: workspace.id, owner: account.id })
      const replay = await fetch(endpoint, { method: 'POST', headers: creationHeaders, body })
      assert.equal(replay.status, 200)
      assert.deepEqual(await replay.json(), workspace)
    }
    const list = await fetch(endpoint, { headers })
    assert.equal(list.status, 200)
    const rows = await list.json()
    assert.equal(rows.length, 1)
    assert.equal(rows[0].id, workspaceIds.find(workspace => workspace.owner === account.id).id)
    for (const workspace of workspaceIds) {
      const read = await fetch(`${endpoint}/${workspace.id}`, { headers })
      assert.equal(read.status, workspace.owner === account.id ? 200 : 404)
    }
    const refreshToken = data.session.refresh_token
    assert.ifError((await shared.auth.signOut({ scope: 'local' })).error)
    assert.equal((await shared.auth.getSession()).data.session, null)
    assert.ok((await shared.auth.refreshSession({ refresh_token: refreshToken })).error, 'Signed-out refresh token must be revoked')
    assert.equal((await fetch('http://localhost:5173/api/v1/me')).status, 401)
  }
  console.log(`PASS switching round ${round + 1}: correct identity, workspace creation/retry/isolation, empty session after logout, revoked refresh tokens, anonymous API access denied`)
}
console.log(`PASS: ${count} distinct accounts; all authentication and account-switching checks passed.`)

// Exercise explicit local approval independently of self-service organization creation.
const joining = await exerciseAccount()
execFileSync(process.execPath, [fileURLToPath(new URL('../../scripts/approve-local-organization-member.mjs', import.meta.url)), accounts[0].organizationId, joining.id], { stdio: 'pipe' })
const ownerLogin = await shared.auth.signInWithPassword({ email: accounts[0].email, password: accounts[0].password })
assert.ifError(ownerLogin.error)
const ownerHeaders = { Authorization: `Bearer ${ownerLogin.data.session.access_token}`, 'Content-Type': 'application/json' }
const joiningClient = createClient(url, env.VITE_SUPABASE_PUBLISHABLE_KEY, { auth: { autoRefreshToken: false, detectSessionInUrl: false } })
const joiningLogin = await joiningClient.auth.signInWithPassword({ email: joining.email, password: joining.password })
assert.ifError(joiningLogin.error)
const joiningHeaders = { Authorization: `Bearer ${joiningLogin.data.session.access_token}` }
const workspacesUrl = 'http://localhost:5173/api/v1/workspaces'
assert.deepEqual(await (await fetch(workspacesUrl, { headers: joiningHeaders })).json(), [])
const ownWorkspace = workspaceIds.find(workspace => workspace.owner === accounts[0].id)
const membershipUrl = `${workspacesUrl}/${ownWorkspace.id}/members/${joining.id}`
assert.equal((await fetch(membershipUrl, { method: 'PUT', headers: ownerHeaders, body: JSON.stringify({ role: 'viewer' }) })).status, 204)
const joinedWorkspaces = await (await fetch(workspacesUrl, { headers: joiningHeaders })).json()
assert.equal(joinedWorkspaces.length, 1)
assert.equal(joinedWorkspaces[0].role, 'viewer')
assert.equal(joinedWorkspaces[0].organizationId, accounts[0].organizationId)
assert.equal((await fetch(membershipUrl, { method: 'DELETE', headers: ownerHeaders })).status, 204)
assert.equal((await fetch(`${workspacesUrl}/${ownWorkspace.id}`, { headers: joiningHeaders })).status, 404)
if (accounts.length > 1) {
  assert.throws(() => execFileSync(process.execPath, [fileURLToPath(new URL('../../scripts/approve-local-organization-member.mjs', import.meta.url)), accounts[1].organizationId, joining.id], { stdio: 'pipe' }))
}
await shared.auth.signOut({ scope: 'local' })
await joiningClient.auth.signOut({ scope: 'local' })
console.log('PASS: approved company join, explicit workspace grant/revoke, and rejected cross-company reassignment.')
