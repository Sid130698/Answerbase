// Local disposable identity: email confirmation is provisioned only in local Supabase.
import {readFileSync,writeFileSync,existsSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {randomBytes,randomUUID} from 'node:crypto'
const status=JSON.parse(execFileSync('npx',['--yes','supabase@3.3.0','status','-o','json'],{encoding:'utf8',stdio:['ignore','pipe','ignore']}))
const key=status.PUBLISHABLE_KEY||status.ANON_KEY
const owner=JSON.parse(readFileSync('.cache/support-demo.json','utf8')).client
async function auth(path,body){const r=await fetch(`${status.API_URL}/auth/v1${path}`,{method:'POST',headers:{apikey:key,'Content-Type':'application/json'},body:JSON.stringify(body)});if(!r.ok)throw new Error(`Local auth failed: ${r.status}`);return r.json()}
let recipient
if(existsSync('.cache/invitation-smoke.json'))recipient=JSON.parse(readFileSync('.cache/invitation-smoke.json','utf8'))
else{recipient={email:`invite-smoke-${randomUUID()}@example.test`,password:randomBytes(24).toString('base64url')};const created=await auth('/signup',recipient);recipient.id=created.user?.id||created.id;if(!/^[a-f0-9-]{36}$/.test(recipient.id))throw new Error('Invalid local fixture identity');execFileSync('docker',['exec','supabase_db_answerbase-auth','psql','-U','postgres','-d','postgres','-v','ON_ERROR_STOP=1','-c',`UPDATE auth.users SET email_confirmed_at=now() WHERE id='${recipient.id}'`],{stdio:'ignore'});writeFileSync('.cache/invitation-smoke.json',JSON.stringify(recipient),{mode:0o600})}
const ownerToken=(await auth('/token?grant_type=password',owner)).access_token
const recipientToken=(await auth('/token?grant_type=password',recipient)).access_token
async function api(token,path,body){const r=await fetch('http://localhost:8080/api/v1'+path,{method:body?'POST':'GET',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});if(!r.ok)throw new Error(`Local API failed: ${r.status}`);return r.json()}
const workspace=(await api(ownerToken,'/workspaces')).find(w=>w.role==='admin')
const invite=await api(ownerToken,`/workspaces/${workspace.id}/invitations`,{email:recipient.email,role:'viewer'})
const joined=await api(recipientToken,'/invitations/accept',{token:invite.token})
if(joined.workspaceId!==workspace.id)throw new Error('Wrong workspace returned')
const members=await api(ownerToken,`/workspaces/${workspace.id}/members`)
if(!members.some(m=>m.userId===recipient.id&&m.role==='viewer'&&m.email===recipient.email))throw new Error('Membership was not established')
console.log('Local invitation smoke passed: confirmed recipient joined the approved organization/workspace as viewer.')
