import {readFile} from 'node:fs/promises';
import {pool,query} from './db.js';
try{const sql=await readFile(new URL('./schema.sql',import.meta.url),'utf8');for(const statement of sql.split(';').map(x=>x.trim()).filter(Boolean))await query(statement);await query('INSERT IGNORE INTO schema_migrations(version) VALUES(1)');console.log('Schema v1 ready');}finally{await pool.end();}
