import {app} from './app.js';
import {pool} from './db.js';
await pool.query('SELECT 1');
const server=app.listen(Number(process.env.PORT||3000),process.env.HOST||(process.env.NODE_ENV==='production'?'0.0.0.0':'127.0.0.1'),()=>console.log(`TOEIC Studio listening on ${process.env.PORT||3000}`));
for(const sig of ['SIGTERM','SIGINT'])process.on(sig,()=>server.close(async()=>{await pool.end();process.exit(0);}));
