import 'dotenv/config';
import mysql from 'mysql2/promise';
if(!process.env.DATABASE_URL) throw new Error('DATABASE_URL is required');
const u=new URL(process.env.DATABASE_URL);
export const pool=mysql.createPool({host:u.hostname,port:Number(u.port||3306),user:decodeURIComponent(u.username),password:decodeURIComponent(u.password),database:u.pathname.slice(1),connectionLimit:10,decimalNumbers:true,timezone:'Z',charset:'utf8mb4',...(process.env.DB_SSL==='true'?{ssl:{rejectUnauthorized:true}}:{})});
// MySQL TIMESTAMP conversion must match the driver's UTC decoding on every host.
pool.on('connection',connection=>connection.query("SET time_zone = '+00:00'"));
export async function query(sql,params=[],db=pool){const [rows]=await db.execute(sql,params);return rows;}
export async function transaction(fn){const c=await pool.getConnection();try{await c.beginTransaction();const result=await fn(c);await c.commit();return result;}catch(e){await c.rollback();throw e;}finally{c.release();}}
