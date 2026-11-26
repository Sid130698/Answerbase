export const normalize = value => String(value).replace(/\s+/g, ' ').trim().toLowerCase()
export function retrievalScore(test, passages, documents) {
  if (!test.gold.length) return null
  const ranks = test.gold.map(gold => passages.findIndex(p => p.documentId === documents[gold.file] && normalize(p.content).includes(normalize(gold.quote))) + 1)
  const first = Math.min(...ranks.filter(rank => rank > 0))
  return {hitAt5: Number(first <= 5), reciprocalRank: Number.isFinite(first) ? 1 / first : 0,
    allGoldAt5: Number(ranks.every(rank => rank > 0 && rank <= 5))}
}
export function answerScore(test, result) {
  const citations = result.answer.claims.flatMap(c => c.citations)
  const valid = citations.filter(c => c.quote.trim().length >= 8 && result.evidence.some(p => p.id === c.passageId && normalize(p.content).includes(normalize(c.quote))))
  const text = normalize(result.answer.claims.map(c => c.text).join(' '))
  return {abstained: result.answer.abstained, expectedAbstention: test.expectedAbstention,
    expectedTermsMatchProxy: test.expectedAbstention ? null : Number(!result.answer.abstained && test.expectedTerms.every(term => text.includes(normalize(term)))),
    citationCount: citations.length, literalValidCitations: valid.length}
}
const average = values => values.length ? values.reduce((a,b) => a+b,0)/values.length : null
const percentile = (values, p) => values.length ? [...values].sort((a,b)=>a-b)[Math.ceil(values.length*p)-1] : null
export function summarize(rows) {
  const retrieval = rows.filter(r=>r.retrieval).map(r=>r.retrieval)
  const answers = rows.filter(r=>r.answer).map(r=>r.answer)
  const citations = answers.reduce((sum,a)=>sum+a.citationCount,0)
  const latencies = rows.filter(r=>r.result).map(r=>r.elapsedMs)
  return {cases:rows.length, errors:rows.filter(r=>r.error).length, retrievalCases:retrieval.length,
    hitAt5:average(retrieval.map(r=>r.hitAt5)),mrr:average(retrieval.map(r=>r.reciprocalRank)),allGoldAt5:average(retrieval.map(r=>r.allGoldAt5)),
    generationCases:answers.length, expectedTermsMatchProxy:average(answers.filter(a=>!a.expectedAbstention).map(a=>a.expectedTermsMatchProxy)),
    abstentionRecall:average(answers.filter(a=>a.expectedAbstention).map(a=>Number(a.abstained))),
    abstentionPrecision:average(answers.filter(a=>a.abstained).map(a=>Number(a.expectedAbstention))),
    falseAbstentionRate:average(answers.filter(a=>!a.expectedAbstention).map(a=>Number(a.abstained))),
    literalCitationIntegrity:citations ? answers.reduce((sum,a)=>sum+a.literalValidCitations,0)/citations:null,
    p50Ms:percentile(latencies,.5),p95Ms:percentile(latencies,.95),
    inputTokens:rows.reduce((s,r)=>s+(r.result?.inputTokens||0),0),outputTokens:rows.reduce((s,r)=>s+(r.result?.outputTokens||0),0),
    knownApiCostUsd:rows.reduce((s,r)=>s+(r.result?.costUsd||0),0),unpricedAnswers:rows.filter(r=>r.result && r.result.costUsd==null).length,
    semanticCorrectness:null,faithfulness:null,humanCitationAccuracy:null}
}
export function regressions(baseline,current) {
  if (baseline.datasetHash !== current.datasetHash || JSON.stringify(baseline.config)!==JSON.stringify(current.config) || JSON.stringify(baseline.rows.map(r=>r.id))!==JSON.stringify(current.rows.map(r=>r.id))) throw new Error('Incomparable reports: dataset, configuration and case order must match')
  if (current.summary.errors || baseline.summary.errors) throw new Error('Cannot accept a report with request failures')
  const failures=[]
  for(const metric of ['hitAt5','mrr','allGoldAt5','expectedTermsMatchProxy','abstentionRecall','abstentionPrecision','literalCitationIntegrity']) {
    const before=baseline.summary[metric],after=current.summary[metric]
    if(before!=null && (after==null || before-after > .030000001))failures.push(metric)
  }
  if(baseline.summary.falseAbstentionRate!=null && (current.summary.falseAbstentionRate==null || current.summary.falseAbstentionRate-baseline.summary.falseAbstentionRate > .030000001))failures.push('falseAbstentionRate')
  return failures
}
