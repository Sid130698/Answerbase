import {execFileSync} from 'node:child_process'
import {setTimeout} from 'node:timers/promises'

// Only service status and aggregate counts leave this process. Never log prompts, users or secrets.
const interval = Number(process.env.MONITOR_INTERVAL_MS || 60000)
if (!Number.isFinite(interval) || interval < 1000) throw new Error('Invalid monitoring interval')
const sql = `SELECT json_build_object(
 'failedIngestion', (SELECT count(*) FROM answerbase.ingestion_jobs WHERE state='failed'),
 'failedIndexing', (SELECT count(*) FROM answerbase.index_jobs WHERE state='failed'),
 'stalledIngestion', (SELECT count(*) FROM answerbase.ingestion_jobs WHERE state='queued' AND available_at<now()-interval '5 minutes'),
 'expiredAnswers', (SELECT count(*) FROM answerbase.questions WHERE status='running' AND lease_until<now()),
 'exhaustedAnswers', (SELECT count(*) FROM answerbase.question_retries WHERE terminal OR expires_at<now() OR (attempts>=3 AND lease_until<now())),
 'failedObjectDeletions', (SELECT count(*) FROM answerbase.object_deletions WHERE attempts>0),
 'answerP95Ms', (SELECT percentile_cont(.95) WITHIN GROUP (ORDER BY (trace->>'totalMs')::double precision) FROM answerbase.questions WHERE status='complete' AND created_at>now()-interval '1 hour')
)`
const endpoints = [
 ['api',process.env.MONITOR_API_URL || 'http://api:8080/actuator/health/readiness'],
 ['worker',process.env.MONITOR_WORKER_URL || 'http://worker:8081/actuator/health/readiness'],
 ['embedding',process.env.MONITOR_MODEL_URL || 'http://ollama:11434/api/tags'],
 ['reranker',process.env.MONITOR_RERANKER_URL || 'http://reranker:8092/health'],
]
let previous = '', lastDelivery = 0
async function check() {
 const checks = await Promise.all(endpoints.map(async ([service,url]) => {
  try { const response=await fetch(url,{signal:AbortSignal.timeout(5000)}); await response.body?.cancel(); return {service,ok:response.ok} }
  catch {return {service,ok:false}}
 }))
 let counts = {}, databaseOk = true
 try { counts=JSON.parse(execFileSync('psql',['-XAt','-v','ON_ERROR_STOP=1','-c',sql],{encoding:'utf8',timeout:10000,stdio:['ignore','pipe','pipe']})) }
 catch {databaseOk=false}
 const alerts=checks.filter(c=>!c.ok).map(c=>`${c.service} unavailable`)
 if(!databaseOk) alerts.push('database diagnostics unavailable')
 for(const [key,value] of Object.entries(counts)) if(value>0 && key!=='answerP95Ms')alerts.push(`${key}: ${value}`)
 if(counts.answerP95Ms>8000)alerts.push('answer p95 exceeds 8 seconds')
 const result={checkedAt:new Date().toISOString(),checks,databaseOk,counts,alerts}
 console.log(JSON.stringify(result))
 const signature=JSON.stringify(alerts)
 const changed=signature!==previous
 const remind=alerts.length>0 && Date.now()-lastDelivery>=3600000
 if(process.env.MONITOR_WEBHOOK_URL && (changed||remind)) {
  const url=new URL(process.env.MONITOR_WEBHOOK_URL)
  if(url.protocol!=='https:')throw new Error('Alert webhook must use HTTPS')
  try {
   const response=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({text:`Answerbase: ${alerts.length ? alerts.join('; ') : 'services recovered'}`}),signal:AbortSignal.timeout(5000)})
   await response.body?.cancel()
   if(!response.ok)throw new Error('delivery failed')
   previous=signature;lastDelivery=Date.now()
  } catch {console.error(JSON.stringify({event:'alert_delivery_failed'}))}
 } else if(!process.env.MONITOR_WEBHOOK_URL) previous=signature
 return alerts.length===0
}
do {
 const healthy=await check()
 if(process.argv.includes('--once')) {process.exitCode=healthy?0:1;break}
 await setTimeout(interval)
} while(true)
