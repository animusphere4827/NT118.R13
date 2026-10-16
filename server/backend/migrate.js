import {readFile} from 'node:fs/promises';
import {pool} from './db.js';
try{const sql=await readFile(new URL('./schema.sql',import.meta.url),'utf8');await pool.query(sql);await pool.query('INSERT INTO schema_migrations(version) VALUES(1) ON CONFLICT DO NOTHING');console.log('PostgreSQL schema v1 ready');}finally{await pool.end();}
