import {seedCourse} from '../backend/course-seed.js';
import {pool} from '../backend/db.js';

try {
  console.log('Course lessons added:', (await seedCourse()).added);
} finally {
  await pool.end();
}
