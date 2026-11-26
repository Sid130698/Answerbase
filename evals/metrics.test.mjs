import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {retrievalScore,answerScore,summarize,regressions} from './metrics.mjs'
test('candidate dataset has the required composition and exact source anchors',()=>{
 const {cases}=JSON.parse(readFileSync(new URL('./cases.json',import.meta.url)))
 assert.equal(new Set(cases.map(c=>c.id)).size,70)
 assert.deepEqual(Object.fromEntries(['fact','synthesis','table','comparison','followup','unanswerable','adversarial'].map(k=>[k,cases.filter(c=>c.category===k).length])),{fact:20,synthesis:10,table:10,comparison:5,followup:5,unanswerable:10,adversarial:10})
 for(const c of cases){assert.equal(c.reviewStatus,'pending-human-review');assert.equal(c.expectedAbstention,c.gold.length===0);if(c.parent)assert.ok(cases.some(p=>p.id===c.parent));for(const g of c.gold)assert.ok(readFileSync(new URL(`./corpus/${g.file}`,import.meta.url),'utf8').includes(g.quote),c.id)}
})
test('retrieval requires both authorized document identity and exact gold text',()=>{
 const t={gold:[{file:'a',quote:'refund 30 days'},{file:'b',quote:'monthly 7 days'}]}
 const score=retrievalScore(t,[{documentId:'wrong',content:'refund 30 days'},{documentId:'A',content:'refund 30\n days'}],{a:'A',b:'B'})
 assert.deepEqual(score,{hitAt5:1,reciprocalRank:.5,allGoldAt5:0})
 assert.equal(retrievalScore({gold:[]},[],{}),null)
 assert.equal(retrievalScore(t,[],{a:'A',b:'B'}).reciprocalRank,0)
})
test('literal citation integrity cannot be misreported as semantic correctness',()=>{
 const result={answer:{abstained:false,claims:[{text:'Refund 30 days',citations:[{passageId:'x',quote:'refund 30 days'},{passageId:'y',quote:'invented quote'}]}]},evidence:[{id:'x',content:'refund 30 days'}]}
 const answer=answerScore({expectedAbstention:false,expectedTerms:['30']},result)
 const summary=summarize([{answer,result,elapsedMs:500}]);assert.equal(summary.literalCitationIntegrity,.5);assert.equal(summary.semanticCorrectness,null);assert.equal(summary.falseAbstentionRate,0);assert.equal(summary.abstentionRecall,null)
})
test('regression gate rejects missing results, incomparable runs and >3pp drops',()=>{
 const base={datasetHash:'x',config:{mode:'retrieval'},rows:[{id:'a'}],summary:{errors:0,hitAt5:.95}}
 assert.deepEqual(regressions(base,{...base,summary:{errors:0,hitAt5:.92}}),[])
 assert.deepEqual(regressions(base,{...base,summary:{errors:0,hitAt5:.919}}),['hitAt5'])
 assert.deepEqual(regressions(base,{...base,summary:{errors:0,hitAt5:null}}),['hitAt5'])
 assert.throws(()=>regressions(base,{...base,datasetHash:'z'}),/Incomparable/)
 assert.throws(()=>regressions(base,{...base,summary:{errors:1}}),/failures/)
})
