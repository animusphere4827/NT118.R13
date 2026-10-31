import {randomInt} from 'node:crypto';
import {z} from 'zod';

const key = z.string().regex(/^[A-Za-z0-9_-]{1,40}$/);
const text = z.string().trim().min(1).max(1000);
const item = z.object({id:key,text}).strict();
const items = z.array(item).min(2).max(20).refine(rows => new Set(rows.map(row => row.id)).size===rows.length,'IDs must be unique');
const pair = z.object({left_id:key,right_id:key}).strict();
const common = {prompt:text,explanation:z.string().trim().min(1).max(10000)};
const definition = z.discriminatedUnion('type',[
  z.object({...common,type:z.literal('FILL_BLANK'),
    payload:z.object({text}).strict(),
    solution:z.object({answers:z.array(z.string().trim().min(1).max(500)).min(1).max(10)}).strict()
  }).strict(),
  z.object({...common,type:z.literal('MATCH'),
    payload:z.object({left:items,right:items}).strict(),
    solution:z.object({pairs:z.array(pair).min(2).max(20)}).strict()
  }).strict(),
  z.object({...common,type:z.literal('ORDER'),
    payload:z.object({tokens:items}).strict(),
    solution:z.object({order:z.array(key).min(2).max(20)}).strict()
  }).strict()
]);

export const normalizeFill = value => value.normalize('NFKC').trim().replace(/\s+/gu,' ').replaceAll('’',"'").toLowerCase();
const sameKeys = (actual,expected) => actual.length===expected.length &&
  new Set(actual).size===actual.length && actual.every(id => expected.includes(id));
const invalid = message => { throw Object.assign(new Error(message),{status:422}); };

export function validateQuizQuestion(input) {
  const question = definition.parse(input);
  if (question.type==='FILL_BLANK' && question.payload.text.split('_____').length!==2) invalid('Câu điền khuyết cần đúng một vị trí _____');
  if (question.type==='MATCH') {
    const pairs = question.solution.pairs;
    if ([question.payload.left,question.payload.right].some(rows=>new Set(rows.map(row=>normalizeFill(row.text))).size!==rows.length)) {
      invalid('Nội dung mục nối trong mỗi phía phải khác nhau');
    }
    if (!sameKeys(pairs.map(p=>p.left_id),question.payload.left.map(p=>p.id)) ||
        !sameKeys(pairs.map(p=>p.right_id),question.payload.right.map(p=>p.id))) {
      invalid('Mỗi mục nối phải có đúng một đáp án và đủ hai phía');
    }
  }
  if (question.type==='ORDER' && !sameKeys(question.solution.order,question.payload.tokens.map(p=>p.id))) {
    invalid('Đáp án sắp xếp phải chứa đủ ID, mỗi ID một lần');
  }
  return question;
}

export function gradeQuizAnswer(question,input) {
  if (question.type==='FILL_BLANK') {
    const answer = normalizeFill(z.string().trim().min(1).max(500).parse(input));
    return {answer,is_correct:question.solution.answers.some(value=>normalizeFill(value)===answer)};
  }
  if (question.type==='MATCH') {
    const parsed = z.object({pairs:z.array(pair).min(2).max(20)}).strict().parse(input);
    if (!sameKeys(parsed.pairs.map(p=>p.left_id),question.payload.left.map(p=>p.id)) ||
        !sameKeys(parsed.pairs.map(p=>p.right_id),question.payload.right.map(p=>p.id))) invalid('Cặp nối thiếu, trùng hoặc không thuộc câu hỏi');
    const answer = {pairs:parsed.pairs.sort((a,b)=>a.left_id.localeCompare(b.left_id))};
    const expected = new Map(question.solution.pairs.map(p=>[p.left_id,p.right_id]));
    return {answer,is_correct:answer.pairs.every(p=>expected.get(p.left_id)===p.right_id)};
  }
  const parsed = z.object({order:z.array(key).min(2).max(20)}).strict().parse(input);
  if (!sameKeys(parsed.order,question.payload.tokens.map(p=>p.id))) invalid('Thứ tự thiếu, trùng hoặc có ID ngoài câu hỏi');
  const textById=new Map(question.payload.tokens.map(token=>[token.id,token.text]));
  return {answer:parsed,is_correct:parsed.order.every((id,index)=>textById.get(id)===textById.get(question.solution.order[index]))};
}

function shuffle(items) {
  const result = [...items];
  for (let i=result.length-1;i>0;i--) {
    const j = randomInt(i+1);
    [result[i],result[j]] = [result[j],result[i]];
  }
  return result;
}

export function quizSnapshot(row) {
  const question = validateQuizQuestion({type:row.type,prompt:row.prompt,payload:row.payload,
    solution:row.solution,explanation:row.explanation});
  if (question.type==='MATCH') {
    question.payload.left = shuffle(question.payload.left);
    question.payload.right = shuffle(question.payload.right);
  }
  if (question.type==='ORDER') question.payload.tokens = shuffle(question.payload.tokens);
  return {id:row.id,...question};
}

export function publicQuizQuestion(snapshot,position) {
  return {id:snapshot.id,position,type:snapshot.type,prompt:snapshot.prompt,payload:snapshot.payload};
}

export function quizFeedback(row) {
  return {question_id:row.question_id,answer:row.answer,is_correct:row.is_correct,
    correct_answer:row.snapshot.solution,explanation:row.snapshot.explanation};
}
