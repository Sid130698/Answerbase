// Measure complete answers against the already-ingested 50k-chunk workspace.
import {api,json,sleep} from '../evals/local-client.mjs'
import {readFileSync,writeFileSync} from 'node:fs'
import {randomUUID} from 'node:crypto'
const scale=JSON.parse(readFileSync('evals/reports/ingestion-scale.json'))
if(scale.indexed!==2000||scale.vectors!==50000)throw new Error('Complete the real ingestion scale run first')
const rows=[]
for(let record=1;record<=10;record++){
 const started=Date.now()
 const answer=await api(`/workspaces/${scale.workspace}/questions`,json({requestKey:randomUUID(),conversationId:randomUUID(),question:`What is the routing code for record ${record} section 1?`}));
 const text=answer.answer.claims.map(c=>c.text).join(' ')
 rows.push({record,latencyMs:answer.latencyMs,wallMs:Date.now()-started,abstained:answer.answer.abstained,exactCodeProxy:text.includes(`AB-${record}-1`),answer})
 console.log(`Scale answer ${record}/10: ${answer.latencyMs}ms`)
 await sleep(Math.max(0,6500-(Date.now()-started)))
}
const times=rows.map(r=>r.latencyMs).sort((a,b)=>a-b)
writeFileSync('evals/reports/scale-answers.json',JSON.stringify({completedAt:new Date().toISOString(),documents:2000,chunks:50000,concurrency:1,cases:rows.length,p50Ms:times[4],p95Ms:times[9],exactCodeProxy:rows.filter(r=>r.exactCodeProxy).length,limitations:['Ten synthetic routing-code questions; not a semantic quality benchmark','Sequential generation; separate search report measures concurrency four'],rows},null,2))
