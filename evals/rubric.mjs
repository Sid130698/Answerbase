export function parseRubricScore(raw, hasCitations) {
  const value=JSON.parse(raw)
  const citationValid=hasCitations
    ? typeof value.citationAccuracy==='number' && Number.isFinite(value.citationAccuracy) && value.citationAccuracy>=0 && value.citationAccuracy<=1
    : value.citationAccuracy===null
  if(![0,1].includes(value.correctness)||![0,1].includes(value.faithfulness)||!citationValid||typeof value.reason!=='string'||!value.reason.trim()||value.reason.length>2000)throw new Error('Invalid rubric response')
  return {correctness:value.correctness,faithfulness:value.faithfulness,citationAccuracy:value.citationAccuracy,reason:value.reason}
}
