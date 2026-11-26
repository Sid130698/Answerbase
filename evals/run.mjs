// Local-only evaluation: never send fixture credentials to a public endpoint.
import {readFileSync,writeFileSync,mkdirSync,renameSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {createHash,randomUUID} from 'node:crypto'
import {retrievalScore,answerScore,summarize,regressions} from './metrics.mjs'
const data=JSON.parse(readFileSync(new URL('./cases.json',import.meta.url)))
const files=[...new Set(data.cases.flatMap(c=>c.gold.map(g=>g.file)))].concat('archive.md','attack.md')
const corpus=Object.fromEntries(files.map(file=>[file,readFileSync(new URL(`./corpus/${file}`,import.meta.url),'utf8')]))
const datasetHash=createHash('sha256').update(JSON.stringify({data,corpus})).digest('hex')
const mode=process.env.EVAL_MODE||'retrieval'
if(!['retrieval','answers'].includes(mode))throw new Error('EVAL_MODE must be retrieval or answers')
const variant=process.env.EVAL_VARIANT||'hybrid-rerank'
if(!['vector','hybrid','hybrid-rerank','small-chunks','no-context'].includes(variant))throw new Error('Unknown EVAL_VARIANT')
const config={mode,variant,retrievalMode:variant==='vector'?'vector':'hybrid',candidates:30,topK:8,rerank:!['vector','hybrid'].includes(variant),generationModel:'default',chunkChars:variant==='small-chunks'?600:1800,contextLine:variant!=='no-context',redactPii:true,dailyAttempts:1000}
const selected=process.env.EVAL_IDS?.split(',')
const cases=data.cases.filter(c=>!selected||selected.includes(c.id))
if(!cases.length || selected?.some(id=>!data.cases.some(c=>c.id===id)))throw new Error('Invalid EVAL_IDS')
const base=process.env.API_URL||'http://localhost:8080/api/v1'
const checkLocal=url=>{if(!['localhost','127.0.0.1','[::1]'].includes(new URL(url).hostname))throw new Error('Evaluation runner only allows loopback hosts')}
checkLocal(base)
const status=JSON.parse(execFileSync('npx',['--yes','supabase@3.3.0','status','-o','json'],{encoding:'utf8',stdio:['ignore','pipe','ignore']}))
checkLocal(status.API_URL)
const account=JSON.parse(readFileSync(process.env.TEST_ACCOUNT_FILE||'.cache/support-demo.json','utf8')).client
let token,expires=0
async function login(){
 if(Date.now()<expires)return
 const response=await fetch(`${status.API_URL}/auth/v1/token?grant_type=password`,{method:'POST',redirect:'error',headers:{apikey:status.PUBLISHABLE_KEY||status.ANON_KEY,'Content-Type':'application/json'},body:JSON.stringify(account),signal:AbortSignal.timeout(15000)})
 if(!response.ok)throw new Error(`Local sign-in failed: ${response.status}`)
 const session=await response.json();token=session.access_token;expires=Date.now()+(session.expires_in-60)*1000
}
async function api(path,init={}){
 await login()
 const response=await fetch(base+path,{...init,redirect:'error',signal:AbortSignal.timeout(420000),headers:{Authorization:`Bearer ${token}`,...init.headers}})
 if(!response.ok)throw new Error(`${path}: HTTP ${response.status}`)
 const text=await response.text();return text?JSON.parse(text):null
}
const json=body=>({method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
const sleep=ms=>new Promise(r=>setTimeout(r,ms))
mkdirSync('.cache',{recursive:true});mkdirSync('evals/reports',{recursive:true})
const identity=await api('/me')
const scope=createHash('sha256').update(JSON.stringify({base,userId:identity.id,datasetHash,variant})).digest('hex').slice(0,20)
const manifestPath=`.cache/evaluation-${scope}.json`
let manifest
try{manifest=JSON.parse(readFileSync(manifestPath,'utf8'))}catch(e){if(e.code!=='ENOENT')throw e;manifest={workspaceKey:randomUUID(),documents:{}}}
const saveManifest=()=>{writeFileSync(manifestPath+'.tmp',JSON.stringify(manifest,null,2),{mode:0o600});renameSync(manifestPath+'.tmp',manifestPath)}
saveManifest()
if(!manifest.workspace){manifest.workspace=(await api('/workspaces',{...json({name:`Evaluation ${variant} ${datasetHash.slice(0,6)}`}),headers:{'Content-Type':'application/json','Idempotency-Key':manifest.workspaceKey}})).id;saveManifest()}
const path=`/workspaces/${manifest.workspace}`
const {mode:unusedMode,variant:unusedVariant,...policy}=config
await api(`${path}/settings`,{...json(policy),method:'PUT'})
for(const [file,content] of Object.entries(corpus)){
 manifest.documents[file]??={key:randomUUID()};saveManifest()
 const item=manifest.documents[file]
 if(!item.version){const body=new FormData();body.set('file',new Blob([content]),file);const version=await api(`${path}/documents`,{method:'POST',headers:{'Idempotency-Key':item.key},body});Object.assign(item,{version:version.id,id:version.documentId});saveManifest()}
 let indexed=false
 for(let i=0;i<120;i++){const versions=await api(`${path}/documents/${item.id}/versions`);const v=versions.find(v=>v.id===item.version);if(v?.indexStatus==='indexed'){indexed=true;break}if(v?.status==='failed'||v?.indexStatus==='failed')throw new Error(`Fixture processing failed: ${file}`);await sleep(1000)}
 if(!indexed)throw new Error(`Fixture indexing timed out: ${file}`)
}
const documents=Object.fromEntries(Object.entries(manifest.documents).map(([file,item])=>[file,item.id]))
const report={schemaVersion:1,startedAt:new Date().toISOString(),datasetHash,config,authorship:data.authorship,workspace:manifest.workspace,documents,rows:[],summary:{}}
const output=process.env.EVAL_REPORT||`evals/reports/${Date.now()}-${mode}-${variant}.json`
const checkpoint=()=>{report.summary=summarize(report.rows);writeFileSync(output+'.tmp',JSON.stringify(report,null,2));renameSync(output+'.tmp',output)}
let lastQuestionAt=0
async function ask(test,conversationId){
 await sleep(Math.max(0,6500-(Date.now()-lastQuestionAt)));lastQuestionAt=Date.now()
 return api(`${path}/questions`,json({requestKey:randomUUID(),conversationId,question:test.question}))
}
for(const test of cases){
 const row={id:test.id,category:test.category,question:test.question}
 try{
  const parent=data.cases.find(c=>c.id===test.parent)
  if(mode==='retrieval'){
   const start=Date.now();const result=await api(`${path}/search`,json({question:[parent?.question,test.question].filter(Boolean).join(' ')}))
   Object.assign(row,{elapsedMs:Date.now()-start,retrieval:retrievalScore(test,result.passages,documents),search:result})
  }else{
   const conversationId=randomUUID();if(parent)row.parentResult=await ask(parent,conversationId)
   const start=Date.now();const result=await ask(test,conversationId)
   Object.assign(row,{elapsedMs:result.latencyMs,wallMs:Date.now()-start,result,retrieval:retrievalScore(test,result.evidence,documents),answer:answerScore(test,result)})
  }
 }catch(error){row.error=error.message}
 report.rows.push(row);checkpoint();console.log(`${report.rows.length}/${cases.length} ${test.id}: ${row.error||'recorded'}`)
}
report.completedAt=new Date().toISOString();checkpoint()
// Blank human grades: must be filled by a reviewer, never inferred from lexical scores.
writeFileSync(output.replace(/\.json$/, '')+'.review.csv','id,correctness_0_or_1,faithfulness_0_or_1,citation_accuracy_0_to_1,reviewer,notes\n'+report.rows.map(r=>`${r.id},,,,,`).join('\n')+'\n')
console.log(JSON.stringify({report:output,summary:report.summary},null,2))
if(process.env.EVAL_BASELINE){const failures=regressions(JSON.parse(readFileSync(process.env.EVAL_BASELINE)),report);if(failures.length)throw new Error(`Regression >3 percentage points: ${failures.join(', ')}`)}
if(report.summary.errors)process.exitCode=1
