import {readFile, readdir} from 'node:fs/promises';
import {pool, query, transaction} from './db.js';

try {
  await transaction(async db => {
    await db.query("SELECT pg_advisory_xact_lock(118, 1)");
    const [{exists}] = await query(
      "SELECT to_regclass('public.schema_migrations') IS NOT NULL AS exists", [], db
    );
    const applied = exists
      ? new Set((await query('SELECT version FROM schema_migrations', [], db)).map(row => row.version))
      : new Set();

    if (!applied.has(1)) {
      await db.query(await readFile(new URL('./schema.sql', import.meta.url), 'utf8'));
      await query('INSERT INTO schema_migrations(version) VALUES(1)', [], db);
      console.log('Applied migration 1');
    }

    const directory = new URL('./migrations/', import.meta.url);
    const files = (await readdir(directory)).filter(file => /^\d+_.*\.sql$/.test(file)).sort();
    for (const file of files) {
      const version = Number(file.split('_')[0]);
      if (applied.has(version)) continue;
      await db.query(await readFile(new URL(file, directory), 'utf8'));
      await query('INSERT INTO schema_migrations(version) VALUES(?)', [version], db);
      console.log('Applied migration ' + version);
    }
  });
  console.log('PostgreSQL schema ready');
} finally {
  await pool.end();
}
