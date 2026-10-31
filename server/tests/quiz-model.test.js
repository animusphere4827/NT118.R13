import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {validateQuizQuestion,gradeQuizAnswer,quizSnapshot,publicQuizQuestion} from '../backend/quiz-model.js';

const content = JSON.parse(await readFile(new URL('../content/quizzes.json',import.meta.url),'utf8'));
const samples = content.sets.map(set=>set.questions[0]);

test('all authored quiz questions have complete, valid answer keys',() => {
  for (const set of content.sets) {
    for (const question of set.questions) {
      const valid = validateQuizQuestion(question);
      const answer = valid.type==='FILL_BLANK' ? valid.solution.answers[0] : valid.solution;
      assert.equal(gradeQuizAnswer(valid,answer).is_correct,true);
    }
  }
});

test('fill answers normalize Unicode, case and whitespace without accepting substrings',() => {
  const q = samples[0];
  assert.equal(gradeQuizAnswer(q,'  ＢＹ  ').is_correct,true);
  assert.equal(gradeQuizAnswer(q,'by Friday').is_correct,false);
  assert.equal(gradeQuizAnswer(q,'at').is_correct,false);
  for (const answer of ['',null,{},['by'],'x'.repeat(501)]) assert.throws(()=>gradeQuizAnswer(q,answer));
});

test('matching is order-independent and requires a complete bijection',() => {
  const q = samples[1];
  const pairs = q.solution.pairs;
  assert.equal(gradeQuizAnswer(q,{pairs:[...pairs].reverse()}).is_correct,true);
  const wrong = pairs.map((p,i)=>({...p,right_id:pairs[(i+1)%pairs.length].right_id}));
  assert.equal(gradeQuizAnswer(q,{pairs:wrong}).is_correct,false);
  for (const input of [{pairs:pairs.slice(1)},{pairs:[pairs[0],pairs[0],pairs[2]]},
    {pairs:pairs.map(p=>({...p,right_id:'foreign'}))},{pairs,solution:q.solution}]) {
    assert.throws(()=>gradeQuizAnswer(q,input));
  }
});

test('ordering uses stable token IDs and rejects missing, repeated or foreign tokens',() => {
  const q = samples[2];
  assert.equal(gradeQuizAnswer(q,q.solution).is_correct,true);
  assert.equal(gradeQuizAnswer(q,{order:[...q.solution.order].reverse()}).is_correct,false);
  for (const order of [q.solution.order.slice(1),q.solution.order.map(()=>q.solution.order[0]),['foreign',...q.solution.order.slice(1)]]) {
    assert.throws(()=>gradeQuizAnswer(q,{order}));
  }
});

test('invalid authored keys and hidden payload fields are rejected before publication',() => {
  for (const original of samples) {
    const q = structuredClone(original);
    q.payload.solution = q.solution;
    assert.throws(()=>validateQuizQuestion(q));
  }
  const match = structuredClone(samples[1]);
  match.solution.pairs[0].right_id=match.solution.pairs[1].right_id;
  assert.throws(()=>validateQuizQuestion(match));
  const order = structuredClone(samples[2]);
  order.solution.order[0]='unknown';
  assert.throws(()=>validateQuizQuestion(order));
});

test('snapshot preserves shuffled item identities and public output excludes keys and explanations',() => {
  for (const [index,question] of samples.entries()) {
    const snapshot = quizSnapshot({id:index+1,...question});
    const publicQuestion = publicQuizQuestion(snapshot,index+1);
    assert.ok(!('solution' in publicQuestion));
    assert.ok(!('explanation' in publicQuestion));
    assert.equal(gradeQuizAnswer(snapshot,question.type==='FILL_BLANK' ? question.solution.answers[0] : question.solution).is_correct,true);
    assert.deepEqual(question,samples[index]);
  }
});
