import {readFileSync,writeFileSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
const status=JSON.parse(execFileSync('npx',['--yes','supabase@3.3.0','status','-o','json'],{encoding:'utf8',stdio:['ignore','pipe','ignore']}))
const account=JSON.parse(readFileSync(process.env.TEST_ACCOUNT_FILE || '.cache/support-demo.json','utf8')).client
const auth=await fetch(`${status.API_URL}/auth/v1/token?grant_type=password`,{method:'POST',headers:{apikey:status.PUBLISHABLE_KEY||status.ANON_KEY,'Content-Type':'application/json'},body:JSON.stringify({email:account.email,password:account.password})})
if(!auth.ok)throw new Error(`Local sign-in failed: ${auth.status}`)
const token=(await auth.json()).access_token
const base=process.env.API_URL||'http://localhost:8080/api/v1'
async function api(path,init={}){const response=await fetch(base+path,{...init,headers:{Authorization:`Bearer ${token}`,...init.headers}});if(!response.ok)throw new Error(`${path}: ${response.status} ${await response.text()}`);return response.status===204?null:response.json()}
const workspace=(await api('/workspaces')).find(w=>w.role==='admin')
if(!workspace)throw new Error('Test account needs an admin workspace')
const body=new FormData();body.set('file',new Blob(['# Refund policy\n\nAnnual subscriptions may be refunded within 30 days of purchase. Monthly subscriptions may be refunded within 7 days.\n\n# Support hours\n\nSupport is available Monday through Friday, 09:00 to 17:00 UTC.']),'retrieval-smoke-policy.md')
const version=await api(`/workspaces/${workspace.id}/documents`,{method:'POST',headers:{'Idempotency-Key':'45512813-bc27-4141-8cd5-1543608df5dc'},body})
let indexed=false
for(let attempt=0;attempt<90;attempt++) {const versions=await api(`/workspaces/${workspace.id}/documents/${version.documentId}/versions`);if(versions.find(v=>v.id===version.id)?.indexStatus==='indexed'){indexed=true;break}await new Promise(r=>setTimeout(r,1000))}
if(!indexed)throw new Error('Document did not index within 90 seconds')
const result=await api(`/workspaces/${workspace.id}/search`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({question:'How many days do I have to get an annual subscription refund?'})})
if(!result.passages.some(p=>p.documentId===version.documentId&&p.content.includes('30 days')))throw new Error('Gold passage missing')
const report={testedAt:new Date().toISOString(),workspace:workspace.id,document:version.documentId,version:version.id,model:result.embeddingModel,embeddingMs:result.embeddingMs,retrievalMs:result.retrievalMs,rerankMs:result.rerankMs,passageCount:result.passages.length,goldPassageRetrieved:true}
writeFileSync('.cache/retrieval-smoke.json',JSON.stringify(report,null,2))
console.log(JSON.stringify(report,null,2))
