import {deleteStoredAvatar} from './media.js';
import {createHash, createHmac, randomBytes, randomInt, randomUUID, timingSafeEqual} from 'node:crypto';
import rateLimit from 'express-rate-limit';
import {z} from 'zod';
import {query, transaction} from './db.js';
import {auth, publicUser, clearSessionCookies} from './sessions.js';
import {hashPassword} from './passwords.js';
import {smtpTransport, sendPasswordOtp} from './mailer.js';
import {mediaUrl} from './resources.js';

const emailSchema = z.email().max(254).transform(value=>value.toLowerCase());
const resetLimit = rateLimit({windowMs:15*60000,limit:20});
const digest = value => createHash('sha256').update(value).digest('hex');
const otpHash = (email,nonce,otp) => createHmac('sha256',process.env.JWT_SECRET)
  .update(email+'|'+nonce+'|'+otp).digest('hex');
const equal = (a,b) => timingSafeEqual(Buffer.from(a,'hex'),Buffer.from(b,'hex'));
const response = {expires_in:300,resend_after:60};

function fail(status,message) {
  throw Object.assign(new Error(message),{status});
}

export function registerAccountRoutes(app) {
  app.post('/api/auth/password/forgot',resetLimit,async(req,res)=>{
    const {email} = z.object({email:emailSchema}).strict().parse(req.body);
    smtpTransport();
    const otp = String(randomInt(0,1000000)).padStart(6,'0');
    const nonce = randomUUID();
    const deliver = await transaction(async db=>{
      await query('SELECT pg_advisory_xact_lock(hashtext(?))',[email],db);
      const [previous] = await query(
        "SELECT created_at>now()-interval '60 seconds' AS cooling FROM password_resets WHERE email=? FOR UPDATE",[email],db
      );
      if (previous?.cooling) return false;
      const [user] = await query("SELECT id,password_hash FROM users WHERE email=? AND status='ACTIVE'",[email],db);
      const available = Boolean(user?.password_hash);
      await query(
        `INSERT INTO password_resets(email,user_id,nonce,otp_hash,expires_at,password_fingerprint)
         VALUES(?,?,?,?,now()+interval '5 minutes',?)
         ON CONFLICT(email) DO UPDATE SET user_id=EXCLUDED.user_id,nonce=EXCLUDED.nonce,
         otp_hash=EXCLUDED.otp_hash,attempts=0,created_at=now(),expires_at=EXCLUDED.expires_at,
         verified_at=NULL,reset_hash=NULL,reset_expires_at=NULL,password_fingerprint=EXCLUDED.password_fingerprint`,
        [email,available?user.id:null,nonce,otpHash(email,nonce,otp),available?digest(user.password_hash):null],db
      );
      return available;
    });
    if (deliver) {
      try { await sendPasswordOtp(email,otp); }
      catch (error) {
        await query('DELETE FROM password_resets WHERE email=? AND nonce=?',[email,nonce]);
        console.error('Password email failed:',error.code || error.name);

      }
    }
    res.set('Cache-Control','no-store').status(202).json(response);
  });

  app.post('/api/auth/password/verify',resetLimit,async(req,res)=>{
    const {email,otp} = z.object({email:emailSchema,otp:z.string().regex(/^\d{6}$/)}).strict().parse(req.body);
    const token = randomBytes(32).toString('hex');
    const status = await transaction(async db=>{
      const [reset] = await query('SELECT *,expires_at>now() AS active FROM password_resets WHERE email=? FOR UPDATE',[email],db);
      if (!reset || !reset.active || reset.verified_at) return 400;
      if (reset.attempts>=5) return 429;
      const valid = equal(otpHash(email,reset.nonce,otp),reset.otp_hash) && reset.user_id;
      if (!valid) {
        await query('UPDATE password_resets SET attempts=attempts+1 WHERE email=?',[email],db);
        return reset.attempts===4?429:400;
      }
      await query(
        "UPDATE password_resets SET verified_at=now(),reset_hash=?,reset_expires_at=now()+interval '5 minutes' WHERE email=?",
        [digest(token),email],db
      );
      return 200;
    });
    if (status!==200) fail(status,status===429?'Nhập sai quá nhiều lần':'Mã không hợp lệ hoặc đã hết hạn');
    res.set('Cache-Control','no-store').json({reset_token:token,expires_in:300});
  });

  app.post('/api/auth/password/reset',resetLimit,async(req,res)=>{
    const body = z.object({
      reset_token:z.string().regex(/^[a-f0-9]{64}$/),
      password:z.string().min(12).max(128)
    }).strict().parse(req.body);
    const passwordHash = await hashPassword(body.password);
    const success = await transaction(async db=>{
      const [candidate] = await query('SELECT user_id FROM password_resets WHERE reset_hash=?',[digest(body.reset_token)],db);
      if (!candidate?.user_id) return false;
      const [user] = await query('SELECT id,status,password_hash FROM users WHERE id=? FOR UPDATE',[candidate.user_id],db);
      const [reset] = await query(
        'SELECT *,reset_expires_at>now() AS active FROM password_resets WHERE reset_hash=? FOR UPDATE',
        [digest(body.reset_token)],db
      );
      if (!user || user.status!=='ACTIVE' || !reset?.active || !reset.verified_at ||
          !user.password_hash || digest(user.password_hash)!==reset.password_fingerprint) return false;
      await query('UPDATE users SET password_hash=? WHERE id=?',[passwordHash,user.id],db);
      await query('UPDATE auth_sessions SET revoked_at=COALESCE(revoked_at,now()) WHERE user_id=?',[user.id],db);
      await query('DELETE FROM password_resets WHERE email=?',[reset.email],db);
      return true;
    });
    if (!success) fail(400,'Yêu cầu đổi mật khẩu không hợp lệ hoặc đã hết hạn');
    clearSessionCookies(res);
    res.sendStatus(204);
  });

  app.put('/api/auth/me',auth,async(req,res)=>{
    const body = z.object({
      full_name:z.string().trim().min(1).max(150),
      target_score:z.number().int().min(5).max(495),
      avatar_url:mediaUrl.optional()
    }).strict().parse(req.body);
    const hasAvatar = Object.hasOwn(req.body,'avatar_url');
    const result = await transaction(async db=>{
      await query('SELECT id FROM users WHERE id=? FOR UPDATE',[req.user.id],db);
      const [asset] = await query('SELECT id,object_key FROM avatar_assets WHERE user_id=?',[req.user.id],db);
      const origin = new URL(process.env.APP_ORIGIN || 'http://localhost:3000').origin;
      const ownUrl = asset ? origin+'/api/media/avatars/'+asset.id+'.webp' : null;
      if(hasAvatar && body.avatar_url?.startsWith(origin+'/api/media/avatars/') && body.avatar_url!==ownUrl) {
        fail(422,'Ảnh đại diện không thuộc tài khoản');
      }
      const [user] = await query(
        'UPDATE users SET full_name=?,target_score=?'+(hasAvatar?',avatar_url=?':'')+' WHERE id=? RETURNING id,email,full_name,avatar_url,role,status,target_score',
        [body.full_name,body.target_score,...(hasAvatar?[body.avatar_url]:[]),req.user.id],db
      );
      const remove = hasAvatar && asset && body.avatar_url!==ownUrl;
      if(remove)await query('DELETE FROM avatar_assets WHERE user_id=?',[req.user.id],db);
      return {user,oldKey:remove?asset.object_key:null};
    });
    if(result.oldKey)await deleteStoredAvatar(result.oldKey).catch(error=>console.error('Old avatar cleanup failed:',error.code || error.name));
    res.json(publicUser(result.user));
  });
}
