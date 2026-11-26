import {execFileSync} from 'node:child_process'
import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
mkdirSync('evals/reports',{recursive:true})
const start=Date.now()
const output=execFileSync('docker',['compose','exec','-T','db','psql','-X','-q','-A','-t','-v','ON_ERROR_STOP=1','-U','answerbase','-d','answerbase_test'],{input:readFileSync(new URL('./scale.sql',import.meta.url)),encoding:'utf8',maxBuffer:8*1024*1024,timeout:600000})
writeFileSync('evals/reports/local-scale-plan.txt',output)
const timings=[...output.matchAll(/"Execution Time": ([\d.]+)/g)].map(m=>Number(m[1]))
if(timings.length!==2 || !output.includes('"chunks" : 50000'))throw new Error('Incomplete scale evidence')
const report={testedAt:new Date().toISOString(),documents:2000,chunks:50000,dimensions:768,syntheticVectors:true,concurrency:1,vectorSqlMs:timings[0],keywordSqlMs:timings[1],totalFixtureAndQueriesMs:Date.now()-start,nonmemberDenied:true,rolledBack:true,limitations:['No real embedding, ingestion throughput, reranker or generation measured','Single run; not p50/p95','Shares local hardware with other services; not a capacity guarantee']}
writeFileSync('evals/reports/local-scale.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2))
