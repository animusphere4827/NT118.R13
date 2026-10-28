import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID,createHash} from 'node:crypto';
import {SMTPServer} from 'smtp-server';
import sharp from 'sharp';
import request from 'supertest';
import {app} from '../backend/app.js';
import {query,pool,transaction} from '../backend/db.js';
import {cleanupExpiredRecords} from '../backend/maintenance.js';
import {applyMigrations} from '../backend/migration-runner.js';

const api = request(app);
after(()=>pool.end());

test('B1.3 OTP, profile, upload, maintenance and admin statistics',async t=>{
  const tag = randomUUID().slice(0,8);
  const email = 'recovery-'+tag+'@example.com';
  const password = 'Before-'+randomUUID();
  const nextPassword = 'After-'+randomUUID();
  const messages = [];
  const server = new SMTPServer({
    secure:false,authOptional:true,disabledCommands:['AUTH','STARTTLS'],
    onData(stream,session,done) {
      let body = '';
      stream.on('data',chunk=>{body+=chunk;});
      stream.on('end',()=>{messages.push(body);done();});
    }
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  process.env.SMTP_HOST='127.0.0.1';
  process.env.SMTP_PORT=String(server.server.address().port);
  process.env.SMTP_FROM='no-reply@example.com';
  process.env.SMTP_SECURE='false';
  const otp = ()=>messages.at(-1).split('\r\n\r\n').slice(1).join('\r\n\r\n').match(/\b\d{6}\b/)[0];
  const forgot = value=>api.post('/api/auth/password/forgot').send({email:value});
  const verify = code=>api.post('/api/auth/password/verify').send({email,otp:code});
  const reset = (token,password)=>api.post('/api/auth/password/reset').send({reset_token:token,password});
  const login = password=>api.post('/api/auth/login').send({email,password});
  let token;
  let admin;
  let avatarId;
  let deck;
  let notification;

  try {
    assert.equal((await api.post('/api/auth/register').send({email,password,full_name:'Recovery learner'})).status,201);
    token=(await login(password)).body.token;
    const adminLogin=await api.post('/api/auth/login').send({email:process.env.ADMIN_EMAIL,password:process.env.ADMIN_PASSWORD});
    assert.equal(adminLogin.status,200);
    admin=adminLogin.body.token;

    await t.test('OTP uses real SMTP, generic response, cooldown and one-time verification',async()=>{
      const sent=await forgot(email);
      const unknown=await forgot('unknown-'+tag+'@example.com');
      assert.equal(sent.status,202);
      assert.deepEqual(sent.body,unknown.body);
      assert.equal(messages.length,1);
      const code=otp();
      assert.equal((await forgot(email)).status,202);
      assert.equal(messages.length,1);
      const [row]=await query('SELECT otp_hash FROM password_resets WHERE email=?',[email]);
      assert.notEqual(row.otp_hash,code);
      const wrong=code==='000000'?'111111':'000000';
      assert.equal((await verify(wrong)).status,400);
      const result=await verify(code);
      assert.equal(result.status,200);
      assert.equal((await verify(code)).status,400);
      const resetToken=result.body.reset_token;
      assert.equal((await reset(resetToken,nextPassword)).status,204);
      assert.equal((await reset(resetToken,nextPassword)).status,400);
      assert.equal((await api.get('/api/auth/me').auth(token,{type:'bearer'})).status,401);
      assert.equal((await login(password)).status,401);
      const loggedIn=await login(nextPassword);
      assert.equal(loggedIn.status,200);
      token=loggedIn.body.token;
    });

    await t.test('profile validates fields, prevents privilege changes and uploads only decoded images',async()=>{
      const body={full_name:'Updated learner',target_score:400};
      const result=await api.put('/api/auth/me').auth(token,{type:'bearer'}).send(body);
      assert.equal(result.status,200);
      assert.equal(result.body.full_name,body.full_name);
      assert.ok(!('password_hash' in result.body));
      assert.equal((await api.put('/api/auth/me').auth(token,{type:'bearer'}).send({...body,role:'ADMIN'})).status,422);
      assert.equal((await api.put('/api/auth/me').auth(token,{type:'bearer'}).send({...body,target_score:999})).status,422);
      const image=await sharp({create:{width:20,height:20,channels:3,background:'#416857'}}).png().toBuffer();
      const uploaded=await api.post('/api/auth/me/avatar').auth(token,{type:'bearer'}).attach('avatar',image,{filename:'avatar.png',contentType:'image/png'});
      assert.equal(uploaded.status,200,JSON.stringify(uploaded.body));
      avatarId=uploaded.body.avatar_url;
      const path=new URL(avatarId).pathname;
      assert.equal((await api.get(path)).status,200);
      assert.equal((await api.get(path)).headers['content-type'],'image/webp');
      assert.equal((await api.post('/api/auth/me/avatar').auth(token,{type:'bearer'}).attach('avatar',Buffer.from('not an image'),{filename:'bad.png',contentType:'image/png'})).status,422);
      assert.equal((await api.post('/api/auth/me/avatar').auth(token,{type:'bearer'}).attach('avatar',Buffer.alloc(2*1024*1024+1),{filename:'large.png',contentType:'image/png'})).status,413);
    });

    await t.test('OTP expiry and attempt ceiling are enforced',async()=>{
      await query('DELETE FROM password_resets WHERE email=?',[email]);
      await forgot(email);
      await query("UPDATE password_resets SET created_at=now()-interval '10 minutes',expires_at=now()-interval '1 minute' WHERE email=?",[email]);
      assert.equal((await verify(otp())).status,400);
      await query('DELETE FROM password_resets WHERE email=?',[email]);
      await forgot(email);
      const wrong=otp()==='000000'?'111111':'000000';
      await query('UPDATE password_resets SET attempts=4 WHERE email=?',[email]);
      assert.equal((await verify(wrong)).status,429);
      assert.equal((await verify(otp())).status,429);
    });

    await t.test('cleanup deletes expired sessions and cascades refresh hashes without touching active sessions',async()=>{
      const [user]=await query('SELECT id FROM users WHERE email=?',[email]);
      const id=randomUUID();
      const hash=createHash('sha256').update(id).digest('hex');
      await query("INSERT INTO auth_sessions(id,user_id,created_at,expires_at) VALUES(?,?,now()-interval '2 days',now()-interval '1 day')",[id,user.id]);
      await query('INSERT INTO auth_refresh_tokens(token_hash,session_id) VALUES(?,?)',[hash,id]);
      const result=await cleanupExpiredRecords(5000);
      assert.ok(result.sessions>=1);
      assert.equal((await query('SELECT * FROM auth_refresh_tokens WHERE token_hash=?',[hash])).length,0);
      assert.equal((await api.get('/api/auth/me').auth(token,{type:'bearer'})).status,200);
    });

    await t.test('admin CRUD and detailed statistics use real rows',async()=>{
      const userId=(await query('SELECT id FROM users WHERE email=?',[email]))[0].id;
      const created=await api.post('/api/admin/flashcard_decks').auth(admin,{type:'bearer'}).send({user_id:userId,name:'Admin deck '+tag});
      assert.equal(created.status,201,JSON.stringify(created.body));
      deck=created.body.id;
      const card=await api.post('/api/admin/flashcards').auth(admin,{type:'bearer'}).send({deck_id:deck,front_text:'schedule',back_text:'lịch trình'});
      assert.equal(card.status,201,JSON.stringify(card.body));
      assert.equal((await api.put('/api/admin/flashcards/'+card.body.id).auth(admin,{type:'bearer'}).send({deck_id:deck,front_text:'schedule',back_text:'lịch trình mới',is_pinned:true})).status,200);
      const sent=await api.post('/api/admin/notifications').auth(admin,{type:'bearer'}).send({user_id:userId,title:'Test notification'});
      assert.equal(sent.status,201);
      notification=sent.body.id;
      const report=await api.get('/api/admin/dashboard?days=7').auth(admin,{type:'bearer'});
      assert.equal(report.status,200);
      assert.equal(report.body.daily.length,7);
      assert.equal(report.body.part_performance.length,3);
      assert.ok(report.body.security.active_sessions>=1);
      assert.equal((await api.get('/api/admin/dashboard?days=10000').auth(admin,{type:'bearer'})).status,422);
      assert.equal((await api.post('/api/admin/notifications').auth(token,{type:'bearer'}).send({user_id:userId,title:'Denied'})).status,403);
      assert.equal((await api.delete('/api/admin/notifications/'+notification).auth(admin,{type:'bearer'})).status,204);
      notification=null;
      assert.equal((await api.delete('/api/admin/flashcard_decks/'+deck).auth(admin,{type:'bearer'})).status,204);
      deck=null;
    });

    await t.test('JSON syntax failures do not reflect request secrets',async()=>{
      const secret='should-never-be-echoed';
      const result=await api.post('/api/auth/login').set('Content-Type','application/json').send('{"password":"'+secret+'",');
      assert.equal(result.status,400);
      assert.ok(!JSON.stringify(result.body).includes(secret));
    });
  } finally {
    if(notification)await query('DELETE FROM notifications WHERE id=?',[notification]);
    if(deck)await query('DELETE FROM flashcard_decks WHERE id=?',[deck]);
    await query('DELETE FROM password_resets WHERE email LIKE ?',['%'+tag+'%']);
    await query("DELETE FROM audit_logs WHERE resource IN ('flashcard_decks','flashcards','notifications') AND record_id IN (SELECT id FROM flashcard_decks WHERE user_id=(SELECT id FROM users WHERE email=?))",[email]);
    await query('DELETE FROM users WHERE email=?',[email]);
    await new Promise(resolve=>server.close(resolve));
  }
});

test('migration repairs a version-one legacy database and preserves rows',async()=>{
  const schema='legacy_'+randomUUID().replaceAll('-','');
  await transaction(async db=>{
    await db.query('CREATE SCHEMA '+schema);
    try {
      await db.query('SET LOCAL search_path TO '+schema);
      await db.query('CREATE TABLE schema_migrations(version INTEGER PRIMARY KEY)');
      await db.query('INSERT INTO schema_migrations VALUES(1)');
      await db.query("CREATE TABLE users(id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,email VARCHAR(254) NOT NULL UNIQUE,password_hash VARCHAR(255) NOT NULL,full_name VARCHAR(150) NOT NULL,avatar_url VARCHAR(2048),role VARCHAR(10) NOT NULL DEFAULT 'STUDENT',status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',target_score INTEGER NOT NULL DEFAULT 300,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now())");
      await db.query("INSERT INTO users(email,password_hash,full_name) VALUES('legacy@example.com','hash','Legacy')");
      await applyMigrations(db);
      await applyMigrations(db);
      const checksum = (await db.query('SELECT checksum FROM schema_migrations WHERE version=2')).rows[0].checksum;
      await db.query("UPDATE schema_migrations SET checksum=repeat('0',64) WHERE version=2");
      await assert.rejects(applyMigrations(db),/Applied migration was changed/);
      await db.query('UPDATE schema_migrations SET checksum=$1 WHERE version=2',[checksum]);
      const {rows}=await db.query("SELECT email,firebase_uid FROM users");
      assert.equal(rows.length,1);
      assert.equal(rows[0].email,'legacy@example.com');
      assert.equal((await db.query('SELECT COUNT(*)::int AS n FROM schema_migrations')).rows[0].n,4);
    } finally {
      await db.query('SET LOCAL search_path TO public');
      await db.query('DROP SCHEMA '+schema+' CASCADE');
    }
  });
});
