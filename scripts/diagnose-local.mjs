import {execFileSync} from 'node:child_process'
const checks=await Promise.all([
 ['api','http://localhost:8080/actuator/health/readiness'],
 ['auth','http://localhost:55321/auth/v1/health'],
 ['ollama','http://localhost:11434/api/tags'],
 ['optional-reranker','http://localhost:8092/health'],
].map(async([service,url])=>{try{const response=await fetch(url,{signal:AbortSignal.timeout(5000)});return{service,reachable:response.ok,status:response.status}}catch{return{service,reachable:false}}}))
const sql=`SELECT json_build_object(
'answerP95Ms',(SELECT percentile_cont(0.95) WITHIN GROUP(ORDER BY (trace->>'totalMs')::double precision) FROM answerbase.questions WHERE status='complete' AND created_at>now()-interval '24 hours'),
'ingestionFailureRate',(SELECT count(*) FILTER(WHERE status='failed')::numeric/nullif(count(*),0) FROM answerbase.document_versions WHERE status IN ('processed','failed') AND created_at>now()-interval '24 hours'),
'ingestionFailed',(SELECT count(*) FROM answerbase.ingestion_jobs WHERE state='failed'),
'indexFailed',(SELECT count(*) FROM answerbase.index_jobs WHERE state='failed'),
'ingestionQueued',(SELECT count(*) FROM answerbase.ingestion_jobs WHERE state='queued'),
'indexQueued',(SELECT count(*) FROM answerbase.index_jobs WHERE state='queued'),
'oldestIngestionSeconds',(SELECT EXTRACT(EPOCH FROM now()-min(available_at))::int FROM answerbase.ingestion_jobs WHERE state='queued'),
'expiredQuestionLeases',(SELECT count(*) FROM answerbase.questions WHERE status='running' AND lease_until<now()),
'pendingObjectDeletions',(SELECT count(*) FROM answerbase.object_deletions),
'retryingObjectDeletions',(SELECT count(*) FROM answerbase.object_deletions WHERE attempts>0)
);`
let queues
try{queues=JSON.parse(execFileSync('docker',['compose','exec','-T','db','psql','-X','-A','-t','-v','ON_ERROR_STOP=1','-U','answerbase','-d','answerbase','-c',sql],{encoding:'utf8',stdio:['ignore','pipe','pipe']}))}catch{queues={error:'Cannot read local queue diagnostics'}}
const alerts=[]
for(const check of checks)if(!check.reachable&&check.service!=='optional-reranker')alerts.push(`${check.service} unavailable`)
if(queues.error)alerts.push(queues.error)
for(const key of ['ingestionFailed','indexFailed','expiredQuestionLeases','retryingObjectDeletions'])if(queues[key]>0)alerts.push(`${key}: ${queues[key]}`)
if(queues.oldestIngestionSeconds>300)alerts.push('Ingestion backlog older than five minutes')
if(queues.answerP95Ms>8000)alerts.push('Complete-answer p95 exceeds the 8-second target over the last 24 hours')
if(queues.ingestionFailureRate>.05)alerts.push('Ingestion failure rate exceeds 5% over the last 24 hours')
console.log(JSON.stringify({checkedAt:new Date().toISOString(),checks,queues,alerts},null,2))
if(alerts.length)process.exitCode=1
