export function parseRubricScore(raw, hasCitations) {
  const value=JSON.parse(raw)
  const citationValid=hasCitations
    ? typeof value.citationAccuracy==='number' && Number.isFinite(value.citationAccuracy) && value.citationAccuracy>=0 && value.citationAccuracy<=1
    : value.citationAccuracy===null
  if(![0,1].includes(value.correctness)||![0,1].includes(value.faithfulness)||!citationValid||typeof value.reason!=='string'||!value.reason.trim()||value.reason.length>2000)throw new Error('Invalid rubric response')
  return {correctness:value.correctness,faithfulness:value.faithfulness,citationAccuracy:value.citationAccuracy,reason:value.reason}
}

export function parseCountedRubricScore(raw, totalCitations) {
  const value=JSON.parse(raw)
  if(!Number.isInteger(totalCitations)||totalCitations<0||!Number.isInteger(value.supportedCitations)||value.supportedCitations<0||value.supportedCitations>totalCitations)throw new Error('Invalid supported-citation count')
  const grade=parseRubricScore(JSON.stringify({...value,citationAccuracy:totalCitations?value.supportedCitations/totalCitations:null}),totalCitations>0)
  return {...grade,supportedCitations:value.supportedCitations,totalCitations}
}
