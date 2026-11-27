// Disposable CI only. Creates a confirmed fixture through local Supabase's admin API.
import {execFileSync,spawn} from 'node:child_process'
import {mkdirSync,writeFileSync,readFileSync,openSync} from 'node:fs'
import {randomUUID} from 'node:crypto'
if(process.env.CI!=='true')throw new Error('This bootstrap is only for disposable CI runners')
const run=(cmd,args)=>execFileSync(cmd,args,{stdio:'inherit'})
mkdirSync('.cache',{recursive:true})
run(process.execPath,['scripts/init-local-auth.mjs'])
run('npx',['--yes','supabase@3.3.0','start','-x','studio,imgproxy,inbucket,storage-api,realtime,postgres-meta,logflare,vector,supavisor,edge-runtime'])
run(process.execPath,['scripts/configure-local-auth.mjs'])
run('docker',['compose','up','-d','--wait','db'])
run('./backend/mvnw',['-f','backend/pom.xml','-B','-ntp','-DskipTests','package'])
const status=JSON.parse(execFileSync('npx',['--yes','supabase@3.3.0','status','-o','json'],{encoding:'utf8',stdio:['ignore','pipe','ignore']}))
if(!['127.0.0.1','localhost'].includes(new URL(status.API_URL).hostname))throw new Error('Local auth only')
const account={email:`evaluation-${randomUUID()}@example.test`,password:randomUUID()+randomUUID()}
const create=await fetch(status.API_URL+'/auth/v1/admin/users',{method:'POST',headers:{apikey:status.SERVICE_ROLE_KEY,Authorization:`Bearer ${status.SERVICE_ROLE_KEY}`,'Content-Type':'application/json'},body:JSON.stringify({...account,email_confirm:true})})
if(!create.ok)throw new Error('Could not provision local evaluation identity: '+create.status)
writeFileSync('.cache/support-demo.json',JSON.stringify({client:account}),{mode:0o600})
const env={...process.env,SUPABASE_AUTH_ISSUER:status.API_URL+'/auth/v1',SUPABASE_JWKS_URL:status.API_URL+'/auth/v1/.well-known/jwks.json',SUPABASE_PUBLISHABLE_KEY:status.PUBLISHABLE_KEY||status.ANON_KEY,OLLAMA_URL:'http://localhost:11434',GENERATION_PROVIDER:'ollama',GENERATION_MODEL:'llama3.1:8b',RETRIEVAL_RERANK:'false',DOCUMENT_STORAGE_ROOT:process.cwd()+'/.cache/ci-documents'}
for(const module of ['api','worker']){const log=openSync(`.cache/${module}.log`,'a',0o600);const child=spawn('java',['-jar',`backend/${module}/target/answerbase-${module}-0.1.0-SNAPSHOT.jar`],{env,detached:true,stdio:['ignore',log,log]});child.unref()}
let ready=false
for(let i=0;i<90;i++){try{ready=(await fetch('http://localhost:8080/actuator/health/readiness')).ok}catch{}if(ready)break;await new Promise(r=>setTimeout(r,1000))}
if(!ready)throw new Error('CI API did not become ready; inspect private runner logs')
const login=await fetch(status.API_URL+'/auth/v1/token?grant_type=password',{method:'POST',headers:{apikey:status.PUBLISHABLE_KEY||status.ANON_KEY,'Content-Type':'application/json'},body:JSON.stringify(account)})
if(!login.ok)throw new Error('CI sign-in failed')
const token=(await login.json()).access_token
const org=await fetch('http://localhost:8080/api/v1/organizations',{method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:JSON.stringify({name:'CI evaluation'})})
if(!org.ok)throw new Error('CI organization setup failed: '+org.status)
console.log('Isolated local evaluation services and identity are ready.')
