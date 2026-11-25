import { generateKeyPairSync, randomUUID } from 'node:crypto'
import { existsSync, writeFileSync } from 'node:fs'
const path = new URL('../supabase/signing_keys.json', import.meta.url)
if (!existsSync(path)) {
  const { privateKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' })
  writeFileSync(path, JSON.stringify([{ ...privateKey.export({ format: 'jwk' }), kid: randomUUID(), use: 'sig', alg: 'ES256', key_ops: ['sign', 'verify'] }]), { mode: 0o600 })
}
console.log('Local auth signing key ready (gitignored).')
