// Hosted bootstrap is an operator-only CLI, never a customer API or email-domain shortcut.
import {readFileSync,statSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
const [user,role]=process.argv.slice(2)
if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(user||'')||!['support','platform_admin','revoke'].includes(role))throw new Error('Usage: node scripts/provision-staff.mjs CONFIRMED_EMPLOYEE_UUID support|platform_admin|revoke')
const file='.env.production'
if(statSync(file).mode&0o077)throw new Error('Production environment must be private (0600)')
const env=Object.fromEntries(readFileSync(file,'utf8').split('\n').filter(l=>l&&!l.startsWith('#')).map(l=>{const i=l.indexOf('=');return[l.slice(0,i),l.slice(i+1)]}))
const project=new URL(env.VITE_SUPABASE_URL)
if(project.protocol!=='https:')throw new Error('Hosted HTTPS Supabase project required')
const key=env.DOCUMENT_STORAGE_SERVICE_KEY
const response=await fetch(project.origin+'/auth/v1/admin/users/'+user,{headers:{apikey:key,Authorization:'Bearer '+key},signal:AbortSignal.timeout(15000)})
if(!response.ok)throw new Error('Could not verify hosted account: HTTP '+response.status)
const account=await response.json()
if(!account.email_confirmed_at||account.is_anonymous||account.id!==user)throw new Error('A confirmed dedicated employee account is required')
// Database constraints reject accounts already enrolled as customers.
const sql=`BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended('${user}'::text,0));
${role==='revoke'?`UPDATE answerbase.staff_access SET active=false WHERE user_id='${user}';`:`INSERT INTO answerbase.staff_access(user_id,role,active) VALUES('${user}','${role}',true) ON CONFLICT(user_id) DO UPDATE SET role=excluded.role,active=true;`}
INSERT INTO answerbase.support_audit(actor_id,action,detail) VALUES('${user}','staff.operator_provision','Trusted host operator CLI: ${role}; actor identifies target account. Host SSH audit identifies operator.');
COMMIT;`
execFileSync('docker',['compose','--env-file',file,'-f','compose.yml','-f','infra/deploy/compose.yml','exec','-T','db','sh','-c','exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],{input:sql,stdio:['pipe','pipe','pipe']})
console.log('Hosted staff grant updated. Keep this identity separate from customer accounts.')
