import test from 'node:test'
import assert from 'node:assert/strict'
import {parseRubricScore} from './rubric.mjs'
test('rubric rejects out-of-range grades and missing citation grades instead of inflating averages',()=>{
 const grade={correctness:1,faithfulness:0,citationAccuracy:.5,reason:'One citation does not support the claim.'}
 assert.deepEqual(parseRubricScore(JSON.stringify(grade),true),grade)
 for(const invalid of [{correctness:100},{correctness:'1'},{faithfulness:2},{citationAccuracy:null},{citationAccuracy:2},{reason:''}])assert.throws(()=>parseRubricScore(JSON.stringify({...grade,...invalid}),true),/Invalid/)
 assert.throws(()=>parseRubricScore(JSON.stringify(grade),false),/Invalid/)
 assert.equal(parseRubricScore(JSON.stringify({...grade,citationAccuracy:null}),false).citationAccuracy,null)
})
