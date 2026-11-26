import {readFileSync,writeFileSync} from 'node:fs'
import {execFileSync} from 'node:child_process'
import {randomUUID} from 'node:crypto'
const reportPath=process.argv[2]
if(!reportPath)throw new Error('Usage: node evals/stream.mjs evals/reports/answer-run.json')
const source=JSON.parse(readFileSync(reportPath,'utf8'))
if(!source.completedAt)throw new Error('Wait for the answer run to finish before timing streaming')
const status=JSON.parse(execFileSync('npx',['--yes','supabase@3.3.0','status','-o','json'],{encoding:'utf8',stdio:['ignore','pipe','ignore']}))
if(!['localhost','127.0.0.1','[::1]'].includes(new URL(status.API_URL).hostname))throw new Error('Local auth required')
const account=JSON.parse(readFileSync(process.env.TEST_ACCOUNT_FILE||'.cache/support-demo.json','utf8')).client
const login=await fetch(status.API_URL+'/auth/v1/token?grant_type=password',{method:'POST',redirect:'error',headers:{apikey:status.PUBLISHABLE_KEY||status.ANON_KEY,'Content-Type':'application/json'},body:JSON.stringify(account),signal:AbortSignal.timeout(15000)})
if(!login.ok)throw new Error(`Sign-in failed: ${login.status}`)
const token=(await login.json()).access_token
const start=performance.now()
const response=await fetch(`http://localhost:8080/api/v1/workspaces/${source.workspace}/questions/stream`,{method:'POST',redirect:'error',signal:AbortSignal.timeout(420000),headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:JSON.stringify({requestKey:randomUUID(),conversationId:randomUUID(),question:'What is the current annual subscription refund window?'})})
if(!response.ok)throw new Error(`Stream request failed: ${response.status}`)
const result={testedAt:new Date().toISOString(),firstStatusMs:null,firstVerifiedClaimMs:null,finalAnswerMs:null,verifiedClaims:0,abstained:null,limitation:'Single local request; status events are not answer tokens'}
const decoder=new TextDecoder();let pending=''
for await(const bytes of response.body){
 pending+=decoder.decode(bytes,{stream:true}).replace(/\r\n/g,'\n')
 let end
 while((end=pending.indexOf('\n\n'))>=0){
  const block=pending.slice(0,end);pending=pending.slice(end+2)
  const lines=block.split('\n');const event=lines.find(l=>l.startsWith('event:'))?.slice(6).trim();const text=lines.filter(l=>l.startsWith('data:')).map(l=>l.slice(5).trimStart()).join('\n')
  if(!text)continue
  const data=JSON.parse(text),elapsed=Math.round(performance.now()-start)
  if(event==='status'&&result.firstStatusMs===null)result.firstStatusMs=elapsed
  if(event==='claim'){result.firstVerifiedClaimMs??=elapsed;result.verifiedClaims++}
  if(event==='answer'){result.finalAnswerMs=elapsed;result.abstained=data.answer.abstained}
  if(event==='error')throw new Error(`Stream error: ${data.message||'request failed'}`)
 }
}
if(result.finalAnswerMs===null)throw new Error('Stream ended without a final answer')
writeFileSync('evals/reports/local-stream.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2))
