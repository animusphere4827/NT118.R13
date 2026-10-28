import test from 'node:test';
import assert from 'node:assert/strict';
import {validateContent,loadContent} from '../scripts/validate-content.js';

const fresh=()=>structuredClone(loadContent());
const has=(result,fragment)=>result.errors.some(e=>e.includes(fragment));

test('bộ nội dung hiện tại đạt mọi tiêu chí L1.1 và L1.2',()=>{
 const r=validateContent(fresh());
 assert.deepEqual(r.errors,[]);
 assert.ok(r.totals.words>=300);
 assert.ok(r.totals.topics>=10);
 assert.equal(r.totals.tests,2);
});

test('báo lỗi khi đề thiếu câu Part 5',()=>{
 const c=fresh();c.tests[0].part5.pop();
 assert.ok(has(validateContent(c),'Part 5 cần 30 câu'));
});

test('báo lỗi khi đáp án nằm ngoài A–D',()=>{
 const c=fresh();c.tests[1].part5[0].answer='E';
 assert.ok(has(validateContent(c),'phải là A–D'));
});

test('báo lỗi khi câu Part 6 không có chỗ trống tương ứng trong đoạn văn',()=>{
 const c=fresh();c.tests[0].part6[0].passage.content=c.tests[0].part6[0].passage.content.replace('(131)','');
 assert.ok(has(validateContent(c),'không có chỗ trống (131)'));
});

test('báo lỗi khi từ vựng bị trùng hoặc thiếu trường bắt buộc',()=>{
 const c=fresh();
 const words=c.vocabulary.topics[0].words;
 words.push([...words[0]]);
 words.push(['', 'noun', '/x/', 'nghĩa', 'Example.']);
 const r=validateContent(c);
 assert.ok(has(r,'trùng với'));
 assert.ok(has(r,'thiếu word'));
});

test('báo lỗi khi nội dung còn lorem ipsum',()=>{
 const c=fresh();c.tests[0].part7[0].passages[0].content='Lorem ipsum dolor sit amet.';
 assert.ok(has(validateContent(c),'nội dung giữ chỗ'));
});

test('báo lỗi khi vị trí đáp án đúng bị dồn vào một chữ cái',()=>{
 const c=fresh();
 for(const q of c.tests[0].part5){const i='ABCD'.indexOf(q.answer);[q.options[0],q.options[i]]=[q.options[i],q.options[0]];q.answer='A';}
 assert.ok(has(validateContent(c),'bị lệch'));
});

test('báo lỗi khi bộ luyện tham chiếu bài ngữ pháp không tồn tại',()=>{
 const c=fresh();c.practice.sets[0].questions[0].lesson='G-KHONG-CO';
 assert.ok(has(validateContent(c),'không tồn tại'));
});
