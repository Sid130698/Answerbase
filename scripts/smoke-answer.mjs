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
const conversationId=crypto.randomUUID()
const report=[]
for(const question of ['What is the annual subscription refund window?', 'What is the CEO home address?']) {
 const start=Date.now()
 const answer=await api(`/workspaces/${workspace.id}/questions`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({requestKey:crypto.randomUUID(),conversationId,question})})
 report.push({question,abstained:answer.answer.abstained,claims:answer.answer.claims,message:answer.message,model:answer.model,latencyMs:Date.now()-start,inputTokens:answer.inputTokens,outputTokens:answer.outputTokens})
 console.log(JSON.stringify(report.at(-1),null,2))
}
writeFileSync('.cache/answer-smoke.json',JSON.stringify(report,null,2))
if(report[0].abstained || !report[0].claims.some(c=>c.text.includes('30')))throw new Error('Answerable question did not produce expected cited fact')
if(!report[1].abstained)throw new Error('Unanswerable question was not declined')
