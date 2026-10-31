import {finalizeExpiredReadingAttempts} from '../backend/assessments.js';
import {finalizeExpiredQuizzes} from '../backend/quizzes.js';
import {pool} from '../backend/db.js';

try {
  console.log({reading_attempts:await finalizeExpiredReadingAttempts(100),quiz_attempts:await finalizeExpiredQuizzes(100)});
} finally {
  await pool.end();
}
