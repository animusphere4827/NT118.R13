/**
 * Kiểm tra bộ nội dung trong server/content trước khi import vào database.
 * Chạy trong thư mục server: node scripts/validate-content.js (không cần database, không có dependency).
 * Thoát với mã 1 nếu có lỗi; cảnh báo không làm thất bại.
 */
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {dirname,join} from 'node:path';

const root=join(dirname(fileURLToPath(import.meta.url)),'..','content');
const QUESTION_TYPES=['WORD_FORM','VERB_TENSE','PREPOSITION','CONJUNCTION','PRONOUN','VOCABULARY','COLLOCATION','SENTENCE_INSERTION','MAIN_IDEA','DETAIL','INFERENCE','VOCAB_IN_CONTEXT','NOT_TRUE','PURPOSE','OTHER'];
const PASSAGE_TYPES=['EMAIL','LETTER','MEMO','NOTICE','ADVERTISEMENT','ARTICLE','FORM','SCHEDULE','INVOICE','CHAT','WEB_PAGE','REVIEW','OTHER'];
const POS=['noun','verb','adjective','adverb','preposition','conjunction','phrase'];
const DIFFICULTY=['EASY','MEDIUM','HARD'];
const LABELS=['A','B','C','D'];
const TEST_PART_COUNTS={5:30,6:16,7:54};
const PART7_BY_PASSAGES={1:29,2:10,3:15};
const PLACEHOLDER=/lorem ipsum|\bTODO\b|\bTBD\b|xxx/i;

export function validateContent(content){
 const errors=[],warnings=[];
 const err=(where,msg)=>errors.push(`${where}: ${msg}`);
 const warn=(where,msg)=>warnings.push(`${where}: ${msg}`);
 const text=(where,field,value,max)=>{
  if(typeof value!=='string'||!value.trim()){err(where,`thiếu ${field}`);return false;}
  if(PLACEHOLDER.test(value))err(where,`${field} chứa nội dung giữ chỗ`);
  if(max&&value.length>max)err(where,`${field} dài hơn ${max} ký tự`);
  return true;
 };

 const vocab=content.vocabulary;
 const seenWords=new Map();let wordCount=0;
 const topicNames=new Set(),topicKeys=new Set();
 for(const [ti,t] of (vocab.topics||[]).entries()){
  const tw=`vocabulary.topics[${ti}]`;
  text(tw,'key',t.key);text(tw,'name',t.name,150);
  if(topicKeys.has(t.key))err(tw,`trùng key chủ đề ${t.key}`);topicKeys.add(t.key);
  if(topicNames.has(t.name))err(tw,`trùng tên chủ đề ${t.name}`);topicNames.add(t.name);
  for(const [wi,w] of (t.words||[]).entries()){
   const where=`${t.key}#${wi+1}`;
   if(!Array.isArray(w)||w.length!==5){err(where,'mỗi từ phải là [word, part_of_speech, ipa, meaning_vi, example_sentence]');continue;}
   const [word,pos,ipa,meaning,example]=w;
   text(where,'word',word,150);text(where,'meaning_vi',meaning);text(where,'example_sentence',example);
   if(!POS.includes(pos))err(where,`loại từ "${pos}" không hợp lệ`);
   if(typeof ipa!=='string'||!/^\/[^/]+\/$/.test(ipa))err(where,`IPA "${ipa}" phải nằm trong /.../`);
   const k=String(word).toLowerCase();
   if(seenWords.has(k))err(where,`từ "${word}" trùng với ${seenWords.get(k)}`);else seenWords.set(k,where);
   const stem=k.split(/[\s-]/)[0].replace(/(e|y|ing|ed|s)$/,'').slice(0,4);
   if(typeof example==='string'&&!example.toLowerCase().includes(stem))warn(where,`câu ví dụ có thể không chứa từ "${word}"`);
   if(typeof example==='string'&&!/[.?!]$/.test(example.trim()))warn(where,'câu ví dụ nên kết thúc bằng dấu câu');
   wordCount++;
  }
 }
 if(wordCount<300)err('vocabulary',`cần ít nhất 300 từ, hiện có ${wordCount}`);
 if(topicKeys.size<10)err('vocabulary',`cần ít nhất 10 chủ đề, hiện có ${topicKeys.size}`);

 const lessonKeys=new Set();let lessonCount=0;
 for(const [ti,t] of (content.grammar.topics||[]).entries()){
  const tw=`grammar.topics[${ti}]`;text(tw,'name',t.name,150);
  for(const l of t.lessons||[]){
   const where=`grammar ${l.key||'(không key)'}`;
   text(where,'key',l.key);text(where,'title',l.title,200);text(where,'content',l.content);
   if(!DIFFICULTY.includes(l.difficulty))err(where,`difficulty "${l.difficulty}" không hợp lệ`);
   if(lessonKeys.has(l.key))err(where,'trùng key bài học');lessonKeys.add(l.key);lessonCount++;
  }
 }
 if(!lessonCount)err('grammar','chưa có bài ngữ pháp nào');

 const questionKeys=new Set();
 const checkQuestion=(where,q,part,passageText)=>{
  if(questionKeys.has(where))err(where,'trùng mã câu hỏi');questionKeys.add(where);
  text(where,'q',q.q);text(where,'explanation',q.explanation);
  if(!QUESTION_TYPES.includes(q.type))err(where,`question_type "${q.type}" không hợp lệ`);
  if(q.difficulty!==undefined&&!DIFFICULTY.includes(q.difficulty))err(where,`difficulty "${q.difficulty}" không hợp lệ`);
  if(q.lesson!==undefined&&!lessonKeys.has(q.lesson))err(where,`lesson "${q.lesson}" không tồn tại`);
  if(!Array.isArray(q.options)||q.options.length!==4){err(where,'phải có đúng 4 phương án');return;}
  q.options.forEach((o,i)=>text(where,`phương án ${LABELS[i]}`,o));
  if(new Set(q.options.map(o=>String(o).trim().toLowerCase())).size!==4)err(where,'có phương án trùng nhau');
  if(!LABELS.includes(q.answer))err(where,`đáp án "${q.answer}" phải là A–D`);
  if(part===5&&!q.q.includes('_____'))err(where,'câu Part 5 phải có chỗ trống _____');
  if(part===6&&passageText!==undefined&&!passageText.includes(`(${q.no})`))err(where,`đoạn văn không có chỗ trống (${q.no})`);
  if(q.type==='SENTENCE_INSERTION'&&part===7&&passageText!==undefined&&!/\[1\]/.test(passageText))err(where,'câu chèn câu nhưng đoạn văn không có vị trí [1]–[4]');
 };
 const checkPassage=(where,p)=>{
  if(!PASSAGE_TYPES.includes(p.type))err(where,`passage_type "${p.type}" không hợp lệ`);
  text(where,'content',p.content);
  if(p.title&&p.title.length>200)err(where,'title đoạn văn dài hơn 200 ký tự');
 };
 const answerSpread=(where,qs,report)=>{
  const c={A:0,B:0,C:0,D:0};qs.forEach(q=>c[q.answer]++);
  const shares=Object.values(c).map(n=>n/qs.length);
  if(Math.min(...shares)<0.15||Math.max(...shares)>0.35)report(where,`vị trí đáp án đúng bị lệch, dễ đoán mẹo: ${JSON.stringify(c)}`);
 };

 const tests=content.tests||[];
 if(tests.length<2)err('reading',`cần ít nhất 2 đề, hiện có ${tests.length}`);
 const testKeys=new Set();
 for(const t of tests){
  const tw=`test ${t.key}`;
  text(tw,'title',t.title,200);
  if(testKeys.has(t.key))err(tw,'trùng key đề');testKeys.add(t.key);
  if(t.time_limit!==4500)err(tw,'đề đầy đủ phải có time_limit 4500 giây (75 phút)');
  const all=[];const counts={5:0,6:0,7:0};const p7={1:0,2:0,3:0};
  for(const q of t.part5||[]){checkQuestion(`${t.key}-${q.no}`,q,5);all.push(q);counts[5]++;}
  for(const [gi,g] of (t.part6||[]).entries()){
   const gw=`${t.key} part6[${gi}]`;text(gw,'instructions',g.instructions);
   if(!g.passage){err(gw,'Part 6 cần đúng 1 đoạn văn');continue;}
   checkPassage(gw,g.passage);
   if(!(g.questions||[]).length)err(gw,'nhóm không có câu hỏi');
   for(const q of g.questions||[]){checkQuestion(`${t.key}-${q.no}`,q,6,g.passage.content);all.push(q);counts[6]++;}
  }
  for(const [gi,g] of (t.part7||[]).entries()){
   const gw=`${t.key} part7[${gi}]`;text(gw,'instructions',g.instructions);
   const n=(g.passages||[]).length;
   if(n<1||n>3){err(gw,`Part 7 cần 1–3 đoạn văn, hiện có ${n}`);continue;}
   g.passages.forEach((p,i)=>checkPassage(`${gw}.passages[${i}]`,p));
   const joined=g.passages.map(p=>p.content).join('\n');
   if((g.questions||[]).length<2)err(gw,'nhóm Part 7 cần ít nhất 2 câu');
   if(n>1&&g.questions.length!==5)err(gw,`nhóm ${n} đoạn văn nên có 5 câu, hiện có ${g.questions.length}`);
   for(const q of g.questions||[]){checkQuestion(`${t.key}-${q.no}`,q,7,joined);all.push(q);counts[7]++;p7[n]++;}
  }
  for(const part of [5,6,7])if(counts[part]!==TEST_PART_COUNTS[part])err(tw,`Part ${part} cần ${TEST_PART_COUNTS[part]} câu, hiện có ${counts[part]}`);
  for(const n of [1,2,3])if(p7[n]!==PART7_BY_PASSAGES[n])err(tw,`Part 7 nhóm ${n} đoạn cần ${PART7_BY_PASSAGES[n]} câu, hiện có ${p7[n]}`);
  const nos=all.map(q=>q.no);
  nos.forEach((no,i)=>{if(no!==101+i)err(tw,`số câu phải liên tục 101–200, vị trí ${i+1} là ${no}`);});
  answerSpread(tw,all,err);
 }

 const parts=new Set();
 for(const s of content.practice.sets||[]){
  const sw=`practice ${s.key}`;text(sw,'title',s.title,200);parts.add(s.part);
  if(![5,6,7].includes(s.part))err(sw,`part ${s.part} không hợp lệ`);
  if(!(s.time_limit>=60&&s.time_limit<=14400))err(sw,'time_limit phải trong khoảng 60–14400 giây');
  let n=0;
  if(s.part===5){for(const q of s.questions||[]){checkQuestion(`${s.key}-${q.no}`,q,5);n++;}if(s.groups)err(sw,'bộ Part 5 không dùng groups');}
  else for(const [gi,g] of (s.groups||[]).entries()){
   const gw=`${sw} groups[${gi}]`;
   const passages=s.part===6?[g.passage].filter(Boolean):g.passages||[];
   if(s.part===6&&passages.length!==1)err(gw,'Part 6 cần đúng 1 đoạn văn');
   if(s.part===7&&(passages.length<1||passages.length>3))err(gw,'Part 7 cần 1–3 đoạn văn');
   passages.forEach((p,i)=>checkPassage(`${gw}.passages[${i}]`,p));
   const joined=passages.map(p=>p.content).join('\n');
   for(const q of g.questions||[]){checkQuestion(`${s.key}-${q.no}`,q,s.part,joined);n++;}
  }
  if(n<5)err(sw,`bộ luyện cần ít nhất 5 câu, hiện có ${n}`);
  if(n>=20)answerSpread(sw,[...(s.questions||[]),...(s.groups||[]).flatMap(g=>g.questions||[])],warn);
 }
 for(const p of [5,6,7])if(!parts.has(p))err('practice',`thiếu bộ luyện cho Part ${p}`);

 const totals={words:wordCount,topics:topicKeys.size,lessons:lessonCount,tests:tests.length,questions:questionKeys.size};
 return {errors,warnings,totals};
}

export function loadContent(dir=root){
 const read=f=>JSON.parse(readFileSync(join(dir,f),'utf8'));
 return {vocabulary:read('vocabulary.json'),grammar:read('grammar.json'),tests:[read('reading/test-1.json'),read('reading/test-2.json')],practice:read('reading/practice.json')};
}

if(process.argv[1]&&fileURLToPath(import.meta.url)===process.argv[1]){
 const {errors,warnings,totals}=validateContent(loadContent());
 for(const w of warnings)console.log(`CẢNH BÁO ${w}`);
 for(const e of errors)console.log(`LỖI ${e}`);
 console.log(`Tổng: ${totals.words} từ / ${totals.topics} chủ đề, ${totals.lessons} bài ngữ pháp, ${totals.tests} đề, ${totals.questions} câu hỏi Reading. ${errors.length} lỗi, ${warnings.length} cảnh báo.`);
 process.exitCode=errors.length?1:0;
}
