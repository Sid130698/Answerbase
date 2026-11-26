import test from 'node:test'
import assert from 'node:assert/strict'
import {parseCsv,compareHuman} from './human-review.mjs'
const header='id,human_correctness_0_or_1,human_faithfulness_0_or_1,human_citation_accuracy_0_to_1,reviewer,notes\n'
const judge={completedAt:'test',summary:{graded:5,errors:0},reviewSample:['a'],rows:[{id:'a',correctness:1,faithfulness:1,citationAccuracy:.5,totalCitations:2}]}
test('human review rejects blank/cherry-picked grades and measures actual agreement',()=>{
 assert.throws(()=>compareHuman(judge,header+'a,,,,,\n'),/Incomplete/)
 assert.throws(()=>compareHuman(judge,header+'b,1,1,0.5,Reviewer,\n'),/Incomplete/)
 assert.throws(()=>compareHuman(judge,header+'a,1,1,,Reviewer,\n'),/Invalid/)
 const result=compareHuman(judge,header+'a,0,1,0.5,Reviewer,"Wrong answer, valid quotes"\n')
 assert.equal(result.correctnessAgreement,0);assert.equal(result.faithfulnessAgreement,1);assert.equal(result.citationAgreementWithinOnePoint,1);assert.equal(result.grades[0].notes,'Wrong answer, valid quotes')
 assert.throws(()=>compareHuman({...judge,summary:{graded:5,errors:1}},header),/complete/)
})
test('CSV supports quoted commas, escaped quotes and multiline notes',()=>{
 assert.deepEqual(parseCsv('a,"b,c","d""e"\r\nf,"g\nh",i\n'),[['a','b,c','d"e'],['f','g\nh','i']])
 assert.throws(()=>parseCsv('a,"b'),/Unclosed/)
})
