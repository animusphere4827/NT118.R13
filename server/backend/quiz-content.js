import {readFile} from 'node:fs/promises';
import {z} from 'zod';
import {validateQuizQuestion} from './quiz-model.js';
import {query,insert,transaction} from './db.js';

const documentSchema = z.object({sets:z.array(z.object({
  code:z.string().regex(/^[a-z0-9-]{1,80}$/),title:z.string().trim().min(1).max(200),
  description:z.string().max(5000).default(''),time_limit:z.number().int().min(60).max(7200),
  questions:z.array(z.unknown()).min(1).max(50)
}).strict()).min(1).max(100)}).strict();

export function validateQuizContent(input) {
  const content = documentSchema.parse(input);
  if (new Set(content.sets.map(set=>set.code)).size!==content.sets.length) throw new Error('Duplicate quiz code');
  for (const set of content.sets) set.questions = set.questions.map(validateQuizQuestion);
  return content;
}

export async function importQuizzes(input) {
  const {sets} = validateQuizContent(input);
  return transaction(async db => {
    await db.query('SELECT pg_advisory_xact_lock(118,22)');
    let added = 0;
    for (const set of sets) {
      if ((await query('SELECT id FROM quiz_sets WHERE code=?',[set.code],db)).length) continue;
      const id = await insert(`INSERT INTO quiz_sets(code,title,description,time_limit,status)
        VALUES(?,?,?,?,'DRAFT')`,[set.code,set.title,set.description,set.time_limit],db);
      for (const [index,q] of set.questions.entries()) {
        await query(`INSERT INTO quiz_questions(quiz_set_id,position,type,prompt,payload,solution,explanation)
          VALUES(?,?,?,?,?,?,?)`,[id,index+1,q.type,q.prompt,JSON.stringify(q.payload),JSON.stringify(q.solution),q.explanation],db);
      }
      await query("UPDATE quiz_sets SET status='PUBLISHED' WHERE id=?",[id],db);
      added++;
    }
    return {added};
  });
}

export const loadQuizContent = async () => JSON.parse(await readFile(new URL('../content/quizzes.json',import.meta.url),'utf8'));
