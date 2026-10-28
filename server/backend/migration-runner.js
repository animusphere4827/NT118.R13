import {readFile, readdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';

export async function applyMigrations(db) {
  await db.query('SELECT pg_advisory_xact_lock(118,1)');
  await db.query(await readFile(new URL('./schema.sql', import.meta.url), 'utf8'));
  await db.query('ALTER TABLE schema_migrations ADD COLUMN IF NOT EXISTS checksum CHAR(64)');
  await db.query('INSERT INTO schema_migrations(version) VALUES(1) ON CONFLICT DO NOTHING');
  const directory = new URL('./migrations/', import.meta.url);
  const files = (await readdir(directory)).filter(name => /^\d+_.*\.sql$/.test(name))
    .sort((a,b) => Number(a.split('_')[0]) - Number(b.split('_')[0]));
  const versions = new Set([1]);
  for (const file of files) {
    const version = Number(file.split('_')[0]);
    if (versions.has(version)) throw new Error('Duplicate migration version: ' + version);
    versions.add(version);
    const sql = await readFile(new URL(file,directory), 'utf8');
    const checksum = createHash('sha256').update(sql.replace(/\r\n/g,'\n')).digest('hex');
    const {rows:[applied]} = await db.query('SELECT checksum FROM schema_migrations WHERE version=$1',[version]);
    if (applied) {
      if (applied.checksum && applied.checksum !== checksum) throw new Error('Applied migration was changed: ' + file);
      if (!applied.checksum) await db.query('UPDATE schema_migrations SET checksum=$1 WHERE version=$2',[checksum,version]);
      continue;
    }
    await db.query(sql);
    await db.query('INSERT INTO schema_migrations(version,checksum) VALUES($1,$2)',[version,checksum]);
  }
}
