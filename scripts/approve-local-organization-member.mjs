// Local development approval only. Never infers membership from email text or domain.
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
const [organization, user, role = 'member'] = process.argv.slice(2)
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
if (!uuid.test(organization || '') || !uuid.test(user || '') || !['member', 'admin'].includes(role)) {
  throw new Error('Usage: node scripts/approve-local-organization-member.mjs ORGANIZATION_UUID SUPABASE_USER_UUID [member|admin]')
}
const cwd = fileURLToPath(new URL('..', import.meta.url))
const verified = execFileSync('docker', ['exec', 'supabase_db_answerbase-auth', 'psql', '-U', 'postgres', '-d', 'postgres', '-At', '-v', 'ON_ERROR_STOP=1', '-c', `SELECT count(*) FROM auth.users WHERE id='${user}' AND email_confirmed_at IS NOT NULL AND is_anonymous IS NOT TRUE`], { encoding: 'utf8', cwd }).trim()
if (verified !== '1') throw new Error('The local Supabase account must exist and have a confirmed email before approval.')
const sql = `BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended('${user}'::text, 0));
DO $$ BEGIN
IF EXISTS (SELECT 1 FROM answerbase.users WHERE id='${user}' AND organization_id<>'${organization}') THEN
  RAISE EXCEPTION 'Account already belongs to another organization';
END IF;
END $$;
INSERT INTO answerbase.users (id, organization_id, organization_role)
VALUES ('${user}', '${organization}', '${role}') ON CONFLICT (id) DO NOTHING;
COMMIT;`
execFileSync('docker', ['compose', 'exec', '-T', 'db', 'psql', '-U', 'answerbase', '-d', 'answerbase', '-v', 'ON_ERROR_STOP=1'], { input: sql, cwd, stdio: ['pipe', 'pipe', 'pipe'] })
console.log('Account approved for the organization. Existing roles are unchanged; workspace access still requires membership.')
