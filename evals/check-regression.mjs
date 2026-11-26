import {readFileSync} from 'node:fs'
import {createHash} from 'node:crypto'
import {regressions} from './metrics.mjs'
const [baselinePath,currentPath,baselineJudgePath,currentJudgePath]=process.argv.slice(2)
if(!baselinePath||!currentPath)throw new Error('Usage: node evals/check-regression.mjs BASELINE CURRENT [BASELINE_JUDGE CURRENT_JUDGE]')
const beforeText=readFileSync(baselinePath,'utf8'),afterText=readFileSync(currentPath,'utf8')
const before=JSON.parse(beforeText),after=JSON.parse(afterText)
const failures=regressions(before,after)
if(Boolean(baselineJudgePath)!==Boolean(currentJudgePath))throw new Error('Both judge reports are required')
if(baselineJudgePath){
 const b=JSON.parse(readFileSync(baselineJudgePath)),a=JSON.parse(readFileSync(currentJudgePath))
 if(b.answerReportHash!==createHash('sha256').update(beforeText).digest('hex')||a.answerReportHash!==createHash('sha256').update(afterText).digest('hex'))throw new Error('Judge report does not grade the supplied answer report')
 if(b.judgeModel!==a.judgeModel||b.rubricVersion!==a.rubricVersion||b.datasetHash!==a.datasetHash)throw new Error('Judge configurations differ')
 if(b.summary.errors||a.summary.errors||b.summary.graded!==before.rows.length||a.summary.graded!==after.rows.length)throw new Error('Incomplete judge report')
 for(const metric of ['llmJudgeCorrectness','llmJudgeFaithfulness','llmJudgeCitationAccuracy'])if(b.summary[metric]!=null&&(a.summary[metric]==null||b.summary[metric]-a.summary[metric]>.030000001))failures.push(metric)
}
if(failures.length)throw new Error(`Regression >3 percentage points: ${failures.join(', ')}`)
console.log('Comparable machine metrics passed the three-point regression gate. Human review remains a separate requirement.')
