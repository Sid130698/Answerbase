import {readFileSync,writeFileSync} from 'node:fs'
import {createHash} from 'node:crypto'
import {pathToFileURL} from 'node:url'
export function parseCsv(text){
 text=text.replace(/^\uFEFF/,'')
 const rows=[];let row=[],cell='',quoted=false
 for(let i=0;i<text.length;i++){
  const ch=text[i]
  if(ch==='"'){if(quoted&&text[i+1]==='"'){cell+='"';i++}else quoted=!quoted}
  else if(ch===','&&!quoted){row.push(cell);cell=''}
  else if(ch==='\n'&&!quoted){row.push(cell.replace(/\r$/,''));if(row.some(c=>c.trim()))rows.push(row);row=[];cell=''}
  else cell+=ch
 }
 if(quoted)throw new Error('Unclosed CSV quote')
 if(cell||row.length){row.push(cell.replace(/\r$/,''));rows.push(row)}
 return rows
}
export function compareHuman(judge,csv){
 if(!judge.completedAt||judge.summary.errors||judge.reviewSample.length<Math.ceil(judge.summary.graded*.2))throw new Error('A complete judge run and its full 20% sample are required')
 const [header,...rows]=parseCsv(csv)
 const expected=['id','human_correctness_0_or_1','human_faithfulness_0_or_1','human_citation_accuracy_0_to_1','reviewer','notes']
 if(JSON.stringify(header)!==JSON.stringify(expected))throw new Error('Unexpected review CSV columns')
 if(rows.length!==judge.reviewSample.length||new Set(rows.map(r=>r[0])).size!==rows.length)throw new Error('Missing or duplicate review rows')
 const grades=rows.map(([id,c,f,a,reviewer,notes])=>{
  if(!judge.reviewSample.includes(id)||!['0','1'].includes(c)||!['0','1'].includes(f)||!reviewer?.trim())throw new Error(`Incomplete human grade: ${id}`)
  const machine=judge.rows.find(r=>r.id===id)
  const accuracy=a.trim()===''?null:Number(a)
  if(machine.totalCitations===0?accuracy!==null:accuracy===null||!Number.isFinite(accuracy)||accuracy<0||accuracy>1)throw new Error(`Invalid human citation grade: ${id}`)
  return {id,correctness:Number(c),faithfulness:Number(f),citationAccuracy:accuracy,reviewer:reviewer.trim(),notes:notes||'',machine}
 })
 const mean=values=>values.length?values.reduce((a,b)=>a+b,0)/values.length:null
 return {reviewed:grades.length,correctnessAgreement:mean(grades.map(g=>Number(g.correctness===g.machine.correctness))),faithfulnessAgreement:mean(grades.map(g=>Number(g.faithfulness===g.machine.faithfulness))),citationAgreementWithinOnePoint:mean(grades.filter(g=>g.citationAccuracy!==null).map(g=>Number(Math.abs(g.citationAccuracy-g.machine.citationAccuracy)<=.010000001))),humanSampleCorrectness:mean(grades.map(g=>g.correctness)),grades:grades.map(({machine,...human})=>human),limitation:'Human sample metrics apply only to the reviewed sample; they do not make AI-authored candidate labels into a hand-written gold set.'}
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
 const [judgePath,csvPath]=process.argv.slice(2)
 if(!judgePath||!csvPath)throw new Error('Usage: node evals/human-review.mjs JUDGE.json REVIEW.csv')
 const text=readFileSync(judgePath,'utf8'),result=compareHuman(JSON.parse(text),readFileSync(csvPath,'utf8'))
 const output=judgePath.replace(/\.json$/,'')+'.human.json'
 writeFileSync(output,JSON.stringify({reviewedAt:new Date().toISOString(),judgeHash:createHash('sha256').update(text).digest('hex'),...result},null,2))
 const {grades,...summary}=result;console.log(JSON.stringify({report:output,...summary},null,2))
}
