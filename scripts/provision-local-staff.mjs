// Trusted developer provisioning for local Supabase only. No self-service staff grants.
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
const [user, role] = process.argv.slice(2)
if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(user || '') || !['support', 'platform_admin', 'revoke'].includes(role)) throw new Error('Usage: node scripts/provision-local-staff.mjs USER_UUID support|platform_admin|revoke')
const cwd = fileURLToPath(new URL('..', import.meta.url))
const confirmed = execFileSync('docker', ['exec', 'supabase_db_answerbase-auth', 'psql', '-U', 'postgres', '-d', 'postgres', '-At', '-v', 'ON_ERROR_STOP=1', '-c', `SELECT count(*) FROM auth.users WHERE id='${user}' AND email_confirmed_at IS NOT NULL AND is_anonymous IS NOT TRUE`], { cwd, encoding: 'utf8' }).trim()
if (confirmed !== '1') throw new Error('Provision a confirmed, dedicated Answerbase employee account first.')
const sql = `BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended('${user}'::text,0));
${role === 'revoke' ? `UPDATE answerbase.staff_access SET active=false WHERE user_id='${user}';` : `INSERT INTO answerbase.staff_access (user_id,role,active) VALUES ('${user}','${role}',true) ON CONFLICT(user_id) DO UPDATE SET role=excluded.role,active=true;`}
INSERT INTO answerbase.support_audit(actor_id,action,detail) VALUES ('${user}','staff.local_provision','Local developer CLI: ${role}; actor field identifies provisioned account, not an authenticated staff operator');
COMMIT;`
execFileSync('docker', ['compose', 'exec', '-T', 'db', 'psql', '-U', 'answerbase', '-d', 'answerbase', '-v', 'ON_ERROR_STOP=1'], { cwd, input: sql, stdio: ['pipe', 'pipe', 'pipe'] })
console.log('Local staff access updated. Customer accounts cannot be provisioned as staff.')
