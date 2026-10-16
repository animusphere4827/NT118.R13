import 'dotenv/config';
import pg from 'pg';
if(!process.env.DATABASE_URL) throw new Error('DATABASE_URL is required');
pg.types.setTypeParser(20,v=>Number(v));
pg.types.setTypeParser(1700,v=>Number(v));
export const pool=new pg.Pool({connectionString:process.env.DATABASE_URL,max:10,connectionTimeoutMillis:5000,options:'-c TimeZone=UTC',...(process.env.DB_SSL==='true'?{ssl:{rejectUnauthorized:true}}:{})});
const toPg=sql=>{let i=0;return sql.replace(/\?/g,()=>`$${++i}`);};
export async function query(sql,params=[],db=pool){const r=await db.query(toPg(sql),params);return r.rows;}
export async function insert(sql,params=[],db=pool){const [row]=await query(`${sql} RETURNING id`,params,db);return row.id;}
export async function transaction(fn){const c=await pool.connect();let rollbackError;try{await c.query('BEGIN');const result=await fn(c);await c.query('COMMIT');return result;}catch(e){try{await c.query('ROLLBACK');}catch(error){rollbackError=error;}throw e;}finally{c.release(rollbackError);}}
