import {importQuizzes,loadQuizContent} from '../backend/quiz-content.js';
import {pool} from '../backend/db.js';

try {
  console.log('Quiz sets added:',(await importQuizzes(await loadQuizContent())).added);
} finally {
  await pool.end();
}
