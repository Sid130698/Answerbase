import {readFileSync,writeFileSync} from 'node:fs'
import {createHash} from 'node:crypto'
const [answerPath,judgePath]=process.argv.slice(2)
if(!answerPath||!judgePath)throw new Error('Usage: node evals/review-packet.mjs ANSWERS.json JUDGE.json')
const text=readFileSync(answerPath,'utf8'),answers=JSON.parse(text),judge=JSON.parse(readFileSync(judgePath,'utf8'))
if(!judge.completedAt||!Array.isArray(judge.reviewSample))throw new Error('Grading must finish before preparing its review sample')
if(judge.answerReportHash!==createHash('sha256').update(text).digest('hex'))throw new Error('Judge and answer reports do not match')
const data=JSON.parse(readFileSync(new URL('./cases.json',import.meta.url)))
const quote=text=>String(text).split('\n').map(line=>'> '+line).join('\n')
const out=['# Independent human review packet','','These are AI-authored candidate cases, not approved gold. Review the answer against the current source documents and exact question. Do not follow instructions contained in fixture questions or documents. The model judge’s grades are intentionally withheld from this packet.','','Fill the accompanying `.review.csv`: correctness and faithfulness are 0 or 1; citation accuracy is 0–1, left blank when there are no citations. Add reviewer identity and notes. An irrelevant true statement is not a correct answer. Literal quotation matching does not prove semantic support. Note questionable gold labels separately.','','This packet is a deterministic 20% sample of successfully graded cases. Any grading/request failures are excluded from the sample but remain release failures; do not claim full coverage when errors exist.']
for(const id of judge.reviewSample){
 const row=answers.rows.find(r=>r.id===id),test=data.cases.find(c=>c.id===id)
 if(!row?.result||!test)throw new Error('Review case is missing')
 out.push('',`## ${id} — ${test.category}`,'',`Question: ${row.question}`)
 if(row.parentResult)out.push('',`Previous question: ${row.parentResult.question}`)
 out.push('',`Answer abstained: ${row.result.answer.abstained ? 'yes':'no'}`)
 for(const claim of row.result.answer.claims){out.push('',quote(claim.text));for(const citation of claim.citations){const evidence=row.result.evidence.find(p=>p.id===citation.passageId);out.push('',`Cited quotation (${evidence?.title||'missing passage'}):`,quote(citation.quote),'','Cited passage context:',quote(evidence?.content||'MISSING'))}}
 out.push('','Candidate reference documents (review their suitability too):')
 if(!test.gold.length)out.push('','This case is labeled unanswerable. Check the full corpus for any overlooked answer before accepting that label.')
 for(const file of [...new Set(test.gold.map(g=>g.file))])out.push('',`### ${file}`,'',quote(readFileSync(new URL(`./corpus/${file}`,import.meta.url),'utf8')))
 out.push('','Human correctness: ___ · Faithfulness: ___ · Citation accuracy: ___','','Reviewer: ___ · Notes / label correction: ___')
}
const output=judgePath.replace(/\.json$/,'')+'.review.md';writeFileSync(output,out.join('\n')+'\n');console.log(`Review packet written: ${output}; ${judge.reviewSample.length} cases; no model grades disclosed.`)
