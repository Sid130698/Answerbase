import {execFileSync} from 'node:child_process'
import {readFileSync,statSync,mkdirSync,writeFileSync} from 'node:fs'
const file=process.env.DEPLOY_ENV_FILE||'.env.production'
if((statSync(file).mode&0o077)!==0)throw new Error('Deployment env file must be private: chmod 600 '+file)
const env=Object.fromEntries(readFileSync(file,'utf8').split('\n').filter(l=>l.trim()&&!l.trim().startsWith('#')).map(line=>{const i=line.indexOf('=');return[line.slice(0,i),line.slice(i+1)]}))
for(const key of ['POSTGRES_PASSWORD','CUSTOMER_HOST','SUPPORT_HOST','ACME_EMAIL','VITE_SUPABASE_URL','VITE_SUPABASE_PUBLISHABLE_KEY','SUPABASE_AUTH_ISSUER','SUPABASE_JWKS_URL','DOCUMENT_STORAGE_URL','DOCUMENT_STORAGE_SERVICE_KEY','GROQ_API_KEY']){
 if(!env[key]||/replace-|PROJECT|example\.(com|org)/.test(env[key]))throw new Error('Set deployment value: '+key)
}
if(env.POSTGRES_PASSWORD.length<24)throw new Error('Use a database password of at least 24 characters')
for(const [host,origin] of [['CUSTOMER_HOST','CUSTOMER_ORIGIN'],['SUPPORT_HOST','SUPPORT_ORIGIN']]){
 if(!/^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,63}$/.test(env[host])||env[origin]!==`https://${env[host]}`)throw new Error('Set exact HTTPS hostname/origin: '+host)
}
if(env.CUSTOMER_HOST===env.SUPPORT_HOST)throw new Error('Staff and customer origins must differ')
for(const key of ['VITE_SUPABASE_URL','SUPABASE_AUTH_ISSUER','SUPABASE_JWKS_URL','DOCUMENT_STORAGE_URL'])if(!env[key].startsWith('https://'))throw new Error('HTTPS required: '+key)
const project=new URL(env.VITE_SUPABASE_URL).origin
if(env.VITE_SUPABASE_URL!==project || env.DOCUMENT_STORAGE_URL!==project || env.SUPABASE_AUTH_ISSUER!==project+'/auth/v1' || env.SUPABASE_JWKS_URL!==project+'/auth/v1/.well-known/jwks.json')throw new Error('Auth, JWKS and storage must belong to the configured Supabase project')
const args=['compose','--env-file',file,'-f','compose.yml','-f','infra/deploy/compose.yml']
const run=(...tail)=>execFileSync('docker',[...args,...tail],{stdio:'inherit',env:{...process.env,COMPOSE_PARALLEL_LIMIT:'1'}})
run('config','--quiet')
if(process.argv.includes('--check')){console.log('Deployment values and Compose configuration validated; no resources changed.');process.exit(0)}
const bucket=env.DOCUMENT_STORAGE_BUCKET||'answerbase-documents'
if(!/^[a-z0-9-]+$/.test(bucket))throw new Error('Invalid private bucket name')
const bucketResponse=await fetch(project+'/storage/v1/bucket/'+bucket,{headers:{apikey:env.DOCUMENT_STORAGE_SERVICE_KEY,Authorization:'Bearer '+env.DOCUMENT_STORAGE_SERVICE_KEY},signal:AbortSignal.timeout(10000)})
if(!bucketResponse.ok)throw new Error('Private bucket preflight failed: HTTP '+bucketResponse.status)
if((await bucketResponse.json()).public!==false)throw new Error('Document bucket MUST be private')
const jwks=await fetch(env.SUPABASE_JWKS_URL,{signal:AbortSignal.timeout(10000)})
if(!jwks.ok || !(await jwks.json()).keys?.length)throw new Error('Configure an asymmetric Supabase signing key before deployment')
const models=await fetch('https://api.groq.com/openai/v1/models',{headers:{Authorization:'Bearer '+env.GROQ_API_KEY},signal:AbortSignal.timeout(10000)})
if(!models.ok || !(await models.json()).data?.some(model=>model.id===env.GENERATION_MODEL))throw new Error('Groq credentials/model preflight failed')
run('up','-d','--build','db','ollama','reranker')
run('exec','-T','ollama','ollama','pull',env.EMBEDDING_MODEL||'nomic-embed-text:latest')
run('up','-d','--build','--wait')
mkdirSync('.cache',{recursive:true,mode:0o700})
writeFileSync('.cache/deployed-revision',execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}),{mode:0o600})
console.log('Services started. Verify HTTPS, auth redirects, a confirmed customer and separately provisioned staff account before inviting testers.')
