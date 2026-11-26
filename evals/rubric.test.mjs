import test from 'node:test'
import assert from 'node:assert/strict'
import {parseRubricScore,parseCountedRubricScore} from './rubric.mjs'
test('rubric rejects out-of-range grades and missing citation grades instead of inflating averages',()=>{
 const grade={correctness:1,faithfulness:0,citationAccuracy:.5,reason:'One citation does not support the claim.'}
 assert.deepEqual(parseRubricScore(JSON.stringify(grade),true),grade)
 for(const invalid of [{correctness:100},{correctness:'1'},{faithfulness:2},{citationAccuracy:null},{citationAccuracy:2},{reason:''}])assert.throws(()=>parseRubricScore(JSON.stringify({...grade,...invalid}),true),/Invalid/)
 assert.throws(()=>parseRubricScore(JSON.stringify(grade),false),/Invalid/)
 assert.equal(parseRubricScore(JSON.stringify({...grade,citationAccuracy:null}),false).citationAccuracy,null)
})

test('citation accuracy is calculated from bounded integer counts, never an invented model percentage',()=>{
 const raw=JSON.stringify({correctness:1,faithfulness:0,supportedCitations:2,reason:'Two of three citations support their claims.'})
 assert.equal(parseCountedRubricScore(raw,3).citationAccuracy,2/3)
 assert.throws(()=>parseCountedRubricScore(raw,1),/Invalid/)
 assert.throws(()=>parseCountedRubricScore(raw.replace('"supportedCitations":2','"supportedCitations":0.8'),3),/Invalid/)
 assert.equal(parseCountedRubricScore(raw.replace('"supportedCitations":2','"supportedCitations":0'),0).citationAccuracy,null)
})
