import {api,json,sleep} from './local-client.mjs'
import {readFileSync,writeFileSync,mkdirSync,renameSync} from 'node:fs'
import {randomUUID} from 'node:crypto'
import {execFileSync} from 'node:child_process'
const count=Number(process.env.SCALE_DOCUMENTS||2000)
if(!Number.isInteger(count)||count<1||count>2000)throw new Error('SCALE_DOCUMENTS must be 1..2000')
mkdirSync('.cache',{recursive:true});mkdirSync('evals/reports',{recursive:true})
const file=`.cache/ingestion-scale-${count}.json`
let state
try{state=JSON.parse(readFileSync(file,'utf8'))}catch(e){if(e.code!=='ENOENT')throw e;state={key:randomUUID(),startedAt:new Date().toISOString(),documents:[]}}
const save=()=>{writeFileSync(file+'.tmp',JSON.stringify(state),{mode:0o600});renameSync(file+'.tmp',file)}
save()
if(!state.workspace){state.workspace=(await api('/workspaces',{...json({name:`Ingestion benchmark ${count} documents`}),headers:{'Content-Type':'application/json','Idempotency-Key':state.key}})).id;save()}
const path='/workspaces/'+state.workspace
for(let n=state.documents.length;n<count;n++){
 const text=Array.from({length:25},(_,section)=>`# Procedure ${n+1}.${section+1}\n\nRecord ${n+1} section ${section+1}: routing code AB-${n+1}-${section+1}. `+('The support operator checks the active customer agreement, records the incident, and verifies approval before changing an account. '.repeat(10))).join('\n\n')
 const body=new FormData();body.set('file',new Blob([text]),`procedure-${n+1}.md`)
 const doc=await api(path+'/documents',{method:'POST',headers:{'Idempotency-Key':`${state.key.slice(0,24)}${String(n).padStart(12,'0')}`},body})
 state.documents.push({id:doc.documentId,version:doc.id});save()
 if((n+1)%100===0)console.log(`Uploaded ${n+1}/${count}`)
}
// Read-only aggregate measurement of the same real API/worker workload; no fixture rows inserted with SQL.
const query=`SELECT json_build_object('documents',count(*),'indexed',count(*) FILTER(WHERE index_status='indexed'),'failed',count(*) FILTER(WHERE status='failed' OR index_status='failed'),'chunks',(SELECT count(*) FROM answerbase.document_chunks WHERE workspace_id='${state.workspace}'),'vectors',(SELECT count(*) FROM answerbase.document_chunks WHERE workspace_id='${state.workspace}' AND embedding IS NOT NULL)) FROM answerbase.document_versions WHERE workspace_id='${state.workspace}'`
let progress
for(let tries=0;tries<1440;tries++){
 progress=JSON.parse(execFileSync('docker',['compose','exec','-T','db','psql','-XAt','-v','ON_ERROR_STOP=1','-U','answerbase','-d','answerbase','-c',query],{encoding:'utf8'}))
 console.log(JSON.stringify(progress))
 if(progress.failed)throw new Error('Real ingestion/index failure; inspect workspace before retrying')
 if(progress.indexed===count)break
 await sleep(10000)
}
if(progress.indexed!==count)throw new Error('Scale run has not completed; rerun resumes this workspace')
const searches=[]
for(let wave=0;wave<5;wave++){
 const result=await Promise.all(Array.from({length:4},async(_,i)=>{const started=Date.now();const response=await api(path+'/search',json({question:`What is the routing code for record ${1+wave*4+i} section 1?`}));return {elapsedMs:Date.now()-started,passages:response.passages.length}}))
 searches.push(...result)
}
const times=searches.map(s=>s.elapsedMs).sort((a,b)=>a-b)
const report={startedAt:state.startedAt,completedAt:new Date().toISOString(),workspace:state.workspace,...progress,realEmbeddings:true,syntheticDocuments:true,concurrency:4,searches:searches.length,p50Ms:times[Math.ceil(times.length*.5)-1],p95Ms:times[Math.ceil(times.length*.95)-1],limits:['Synthetic repeated procedures measure pipeline capacity, not semantic accuracy','Search timings include real embedding and retrieval; generation is measured separately']}
writeFileSync('evals/reports/ingestion-scale.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2))
