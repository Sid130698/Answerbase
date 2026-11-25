// Local-only bootstrap. Never provisions a hosted account or prints auth keys.
import {execFileSync,spawn} from 'node:child_process'
import {existsSync,mkdirSync,openSync,readFileSync,writeFileSync,copyFileSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
process.chdir(fileURLToPath(new URL('..',import.meta.url)))
const run=(program,args)=>execFileSync(program,args,{stdio:'inherit'})
const ready=async url=>{try{return (await fetch(url,{signal:AbortSignal.timeout(2000)})).ok}catch{return false}}
try{execFileSync('docker',['info'],{stdio:'ignore'})}catch{throw new Error('Start Docker before running local setup.')}
try{execFileSync('ollama',['--version'],{stdio:'ignore'})}catch{throw new Error('Install Ollama from https://ollama.com before running local setup.')}
mkdirSync('.cache',{recursive:true})
if(!existsSync('.env'))copyFileSync('.env.example','.env')
run(process.execPath,['scripts/init-local-auth.mjs'])
try{execFileSync('npx',['--yes','supabase@3.3.0','status','-o','json'],{stdio:'ignore'})}catch{run('npx',['--yes','supabase@3.3.0','start'])}
run(process.execPath,['scripts/configure-local-auth.mjs'])
if(!await ready('http://localhost:11434/api/tags')) {
 const log=openSync('.cache/ollama.log','a',0o600)
 const child=spawn('ollama',['serve'],{detached:true,stdio:['ignore',log,log]});child.unref()
 let started=false
 for(let i=0;i<30;i++){if(await ready('http://localhost:11434/api/tags')){started=true;break}await new Promise(r=>setTimeout(r,1000))}
 if(!started)throw new Error('Ollama did not start. Check .cache/ollama.log.')
}
const tags=await(await fetch('http://localhost:11434/api/tags')).json()
for(const model of ['nomic-embed-text:latest','llama3.1:8b']) if(!tags.models.some(m=>m.name===model))run('ollama',['pull',model])
// Preserve unrelated settings, but explicitly select local inference for this local-only command.
const rerank=await ready('http://localhost:8092/health')
const values={GENERATION_PROVIDER:'ollama',GENERATION_MODEL:'llama3.1:8b',OLLAMA_URL:'http://host.docker.internal:11434',RETRIEVAL_RERANK:String(rerank),RERANKER_URL:'http://host.docker.internal:8092'}
let env=readFileSync('.env','utf8')
for(const [key,value] of Object.entries(values)){const re=new RegExp(`^${key}=.*$`,'m');env=re.test(env)?env.replace(re,`${key}=${value}`):env+`\n${key}=${value}`}
writeFileSync('.env',env.trim()+'\n',{mode:0o600})
run('docker',['compose','up','--build','-d','--wait'])
console.log(`\nAnswerbase: http://localhost:5173\nStaff console: http://localhost:5174\nLocal confirmation email: http://localhost:55324\nModels: local Ollama; reranking ${rerank?'enabled':'disabled (start the optional local reranker to enable)'}.\nRegister in the customer UI, confirm via local email, then create your organization and workspace.\nStaff access is separately provisioned; ordinary customer registration never grants staff access.`)
