import {execFileSync} from 'node:child_process'
import {readFileSync,statSync} from 'node:fs'
const file=process.env.DEPLOY_ENV_FILE||'.env.production'
if((statSync(file).mode&0o077)!==0)throw new Error('Deployment env file must be private: chmod 600 '+file)
const env=Object.fromEntries(readFileSync(file,'utf8').split('\n').filter(l=>l.trim()&&!l.trim().startsWith('#')).map(line=>{const i=line.indexOf('=');return[line.slice(0,i),line.slice(i+1)]}))
for(const key of ['POSTGRES_PASSWORD','CUSTOMER_HOST','SUPPORT_HOST','ACME_EMAIL','VITE_SUPABASE_URL','VITE_SUPABASE_PUBLISHABLE_KEY','SUPABASE_AUTH_ISSUER','SUPABASE_JWKS_URL','DOCUMENT_STORAGE_URL','DOCUMENT_STORAGE_SERVICE_KEY','GROQ_API_KEY']){
 if(!env[key]||/replace-|PROJECT|example\.(com|org)/.test(env[key]))throw new Error('Set deployment value: '+key)
}
if(env.POSTGRES_PASSWORD.length<24)throw new Error('Use a database password of at least 24 characters')
for(const [host,origin] of [['CUSTOMER_HOST','CUSTOMER_ORIGIN'],['SUPPORT_HOST','SUPPORT_ORIGIN']]){
 if(!/^[a-zA-Z0-9.-]+$/.test(env[host])||env[origin]!==`https://${env[host]}`)throw new Error('Set exact HTTPS hostname/origin: '+host)
}
if(env.CUSTOMER_HOST===env.SUPPORT_HOST)throw new Error('Staff and customer origins must differ')
for(const key of ['VITE_SUPABASE_URL','SUPABASE_AUTH_ISSUER','SUPABASE_JWKS_URL','DOCUMENT_STORAGE_URL'])if(!env[key].startsWith('https://'))throw new Error('HTTPS required: '+key)
const args=['compose','--env-file',file,'-f','compose.yml','-f','infra/deploy/compose.yml']
const run=(...tail)=>execFileSync('docker',[...args,...tail],{stdio:'inherit'})
run('config','--quiet')
if(process.argv.includes('--check')){console.log('Deployment values and Compose configuration validated; no resources changed.');process.exit(0)}
run('up','-d','--build','db','ollama','reranker')
run('exec','-T','ollama','ollama','pull',env.EMBEDDING_MODEL||'nomic-embed-text:latest')
run('up','-d','--build','--wait')
console.log('Services started. Verify HTTPS, auth redirects, a confirmed customer and separately provisioned staff account before inviting testers.')
