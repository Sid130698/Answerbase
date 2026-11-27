import {api,json,sleep} from './local-client.mjs'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {createHash,randomUUID} from 'node:crypto'
import {execFileSync} from 'node:child_process'
const corpus=JSON.parse(readFileSync(new URL('./public-corpus.json',import.meta.url)))
mkdirSync('.cache/public-corpus',{recursive:true});mkdirSync('evals/reports',{recursive:true})
const file='.cache/public-corpus/workspace.json'
let state
try{state=JSON.parse(readFileSync(file))}catch(e){if(e.code!=='ENOENT')throw e;state={key:randomUUID(),documents:{}}}
const save=()=>writeFileSync(file,JSON.stringify(state,null,2),{mode:0o600})
save()
if(!state.workspace){state.workspace=(await api('/workspaces',{...json({name:'Public NIST PDF benchmark'}),headers:{'Content-Type':'application/json','Idempotency-Key':state.key}})).id;save()}
const path='/workspaces/'+state.workspace
for(const doc of corpus.documents){
 const local='.cache/public-corpus/'+doc.file
 let bytes
 try{bytes=readFileSync(local)}catch(e){if(e.code!=='ENOENT')throw e;execFileSync('curl',['--fail','--location','--silent','--show-error','--retry','3',doc.url,'-o',local]);bytes=readFileSync(local)}
 if(createHash('sha256').update(bytes).digest('hex')!==doc.sha256)throw new Error('Public source changed; review and repin its checksum: '+doc.file)
 if(!state.documents[doc.file]){const body=new FormData();body.set('file',new Blob([bytes],{type:'application/pdf'}),doc.file);state.documents[doc.file]=await api(path+'/documents',{method:'POST',headers:{'Idempotency-Key':randomUUID()},body});save()}
}
for(const [name,item] of Object.entries(state.documents)){
 let ready=false
 for(let i=0;i<720;i++){const versions=await api(path+'/documents/'+item.documentId+'/versions');const v=versions.find(v=>v.id===item.id);if(v.indexStatus==='indexed'){ready=true;break}if(v.status==='failed'||v.indexStatus==='failed')throw new Error('Public PDF failed: '+name);await sleep(10000)}
 if(!ready)throw new Error('Public PDF indexing still pending; rerun resumes')
}
const results=[]
for(const test of corpus.cases){
 const answer=await api(path+'/questions',json({requestKey:randomUUID(),conversationId:randomUUID(),question:test.question}))
 const text=answer.answer.claims.map(c=>c.text).join(' ')
 const expectedDocument=state.documents[test.file].documentId
 results.push({id:test.id,question:test.question,answer,expectedDocumentRetrieved:answer.evidence.some(p=>p.documentId===expectedDocument),hasPageReferences:answer.evidence.some(p=>p.documentId===expectedDocument&&p.page!=null),expectedTermsProxy:test.terms.every(t=>text.toLowerCase().includes(t.toLowerCase())),humanReview:'pending'})
 console.log(test.id+': recorded');await sleep(6500)
}
const report={completedAt:new Date().toISOString(),corpus,workspace:state.workspace,results}
writeFileSync('evals/reports/public-corpus.json',JSON.stringify(report,null,2));console.log('Public PDF report saved; semantic scores require human review.')
if(results.some(r=>!r.expectedDocumentRetrieved||!r.hasPageReferences))process.exitCode=1
