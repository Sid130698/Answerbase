import {api,json,sleep} from './local-client.mjs'
import {readFileSync,writeFileSync,mkdirSync,readdirSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {randomUUID,createHash} from 'node:crypto'
const revision='3f489816f1fcde506cae1427b280d744ab382621'
const repo='.cache/gitlab-handbook'
mkdirSync('.cache',{recursive:true});mkdirSync('evals/reports',{recursive:true})
try{execFileSync('git',['-C',repo,'rev-parse','HEAD'],{stdio:'ignore'})}catch{execFileSync('git',['clone','--filter=blob:none','--sparse','https://gitlab.com/gitlab-com/content-sites/handbook.git',repo],{stdio:'inherit'})}
execFileSync('git',['-C',repo,'fetch','origin',revision],{stdio:'ignore'})
execFileSync('git',['-C',repo,'checkout','--detach',revision],{stdio:'ignore'})
execFileSync('git',['-C',repo,'sparse-checkout','set','content/handbook/support'],{stdio:'ignore'})
const license=readFileSync(repo+'/LICENSE','utf8')
if(!license.includes('Permission is hereby granted, free of charge'))throw new Error('Review changed corpus license')
const files=readdirSync(repo+'/content/handbook/support',{recursive:true}).filter(p=>p.endsWith('.md')).sort()
const file='.cache/handbook-workspace.json'
let state
try{state=JSON.parse(readFileSync(file))}catch(e){if(e.code!=='ENOENT')throw e;state={revision,key:randomUUID(),documents:{}}}
if(state.revision!==revision)throw new Error('Use a new benchmark workspace for a changed snapshot')
const save=()=>writeFileSync(file,JSON.stringify(state,null,2),{mode:0o600})
save()
if(!state.workspace){state.workspace=(await api('/workspaces',{...json({name:'GitLab public support handbook'}),headers:{'Content-Type':'application/json','Idempotency-Key':state.key}})).id;save()}
const path='/workspaces/'+state.workspace
for(const [index,name] of ['LICENSE.txt',...files].entries()){
 if(state.documents[name])continue
 const source=name==='LICENSE.txt'?license:readFileSync(repo+'/content/handbook/support/'+name,'utf8')
 const text=source+'\n\nSource: GitLab B.V. public handbook, snapshot '+revision+'. Original: https://gitlab.com/gitlab-com/content-sites/handbook/-/blob/'+revision+'/content/handbook/support/'+name+'\nLicense notice is retained in LICENSE.txt in this workspace.\n'
 const body=new FormData();body.set('file',new Blob([text]),name.replaceAll('/','--').slice(-190))
 state.documents[name]={...await api(path+'/documents',{method:'POST',headers:{'Idempotency-Key':randomUUID()},body}),sha256:createHash('sha256').update(text).digest('hex')};save()
 if(index%50===0)console.log(`Public handbook uploaded ${index+1}/${files.length+1}`)
}
let pending=true
for(let attempt=0;attempt<1080;attempt++){
 // Per-document status reads avoid library pagination hiding late failures.
 let indexed=0,failed=0
 for(const doc of Object.values(state.documents)){
  const versions=await api(path+'/documents/'+doc.documentId+'/versions');const v=versions.find(v=>v.id===doc.id)
  if(v.indexStatus==='indexed')indexed++
  if(v.status==='failed'||v.indexStatus==='failed')failed++
 }
 console.log(JSON.stringify({indexed,total:files.length+1,failed}))
 if(failed)throw new Error('Public handbook processing failed; inspect the source failure')
 if(indexed===files.length+1){pending=false;break}
 await sleep(30000)
}
if(pending)throw new Error('Indexing remains queued; rerun resumes')
const searches=[]
for(const question of ['What is the emergency support response time?','How should support engineers handle customer escalations?','How does the support team work with feature requests?']){
 const started=Date.now(), result=await api(path+'/search',json({question}))
 searches.push({question,elapsedMs:Date.now()-started,result})
}
writeFileSync('evals/reports/handbook.json',JSON.stringify({completedAt:new Date().toISOString(),revision,license,source:'https://gitlab.com/gitlab-com/content-sites/handbook',documents:files.length+1,workspace:state.workspace,searches,humanReview:'pending; retrieval smoke results are not semantic gold scores'},null,2))
