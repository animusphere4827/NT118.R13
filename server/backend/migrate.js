import {pool, transaction} from './db.js';
import {applyMigrations} from './migration-runner.js';

try {
  await transaction(applyMigrations);
  console.log('PostgreSQL schema ready');
} finally {
  await pool.end();
}
