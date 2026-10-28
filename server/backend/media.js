import {randomUUID} from 'node:crypto';
import {mkdir, readFile, writeFile, unlink} from 'node:fs/promises';
import path from 'node:path';
import multer from 'multer';
import sharp from 'sharp';
import rateLimit from 'express-rate-limit';
import {S3Client, PutObjectCommand, GetObjectCommand, DeleteObjectCommand} from '@aws-sdk/client-s3';
import {auth, publicUser} from './sessions.js';
import {query, transaction} from './db.js';

const directory = path.resolve(process.env.MEDIA_LOCAL_DIR || 'storage/avatars');
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const upload = multer({
  storage:multer.memoryStorage(),
  limits:{fileSize:2*1024*1024,files:1,fields:0,parts:1},
  fileFilter(req,file,done) {
    const valid = ['image/jpeg','image/png','image/webp'].includes(file.mimetype);
    done(valid?null:Object.assign(new Error('Chỉ nhận ảnh JPEG, PNG hoặc WebP'),{status:422}),valid);
  }
});
let s3;
let s3Region;
const client = () => {if(!s3||s3Region!==process.env.AWS_REGION){s3?.destroy();s3Region=process.env.AWS_REGION;s3=new S3Client({region:s3Region});}return s3;};
const useS3 = () => {const driver=process.env.MEDIA_DRIVER||'local';if(!['local','s3'].includes(driver))throw new Error('Invalid MEDIA_DRIVER');return driver==='s3';};

function bucket() {
  if (!process.env.S3_BUCKET || !process.env.AWS_REGION) {
    throw Object.assign(new Error('Máy chủ chưa cấu hình lưu trữ S3'),{status:503});
  }
  return process.env.S3_BUCKET;
}

async function put(key,buffer) {
  if (useS3()) {
    await client().send(new PutObjectCommand({Bucket:bucket(),Key:key,Body:buffer,ContentType:'image/webp',ServerSideEncryption:'AES256'}));
  } else {
    await mkdir(directory,{recursive:true});
    await writeFile(path.join(directory,path.basename(key)),buffer,{flag:'wx'});
  }
}

export async function deleteStoredAvatar(key) {
  if (useS3()) await client().send(new DeleteObjectCommand({Bucket:bucket(),Key:key}));
  else await unlink(path.join(directory,path.basename(key))).catch(error=>{if(error.code!=='ENOENT')throw error;});
}

export function registerMediaRoutes(app) {
  app.post('/api/auth/me/avatar',auth,rateLimit({windowMs:15*60000,limit:20}),upload.single('avatar'),async(req,res)=>{
    if (!req.file) return res.status(422).json({error:'Cần chọn ảnh đại diện'});
    let buffer;
    try {
      const decoded = sharp(req.file.buffer,{limitInputPixels:16000000,animated:false});
      const metadata = await decoded.metadata();
      if(!['jpeg','png','webp'].includes(metadata.format)||metadata.pages>1)throw new Error('Unsupported image content');
      buffer = await decoded
        .rotate().resize(512,512,{fit:'cover',withoutEnlargement:true}).webp({quality:80}).toBuffer();
    } catch {
      return res.status(422).json({error:'Nội dung ảnh không hợp lệ'});
    }
    const origin = new URL(process.env.APP_ORIGIN || 'http://localhost:3000');
    if (process.env.NODE_ENV==='production' && origin.protocol!=='https:') throw new Error('APP_ORIGIN must use HTTPS');
    const id = randomUUID();
    const key = 'avatars/'+id+'.webp';
    await put(key,buffer);
    let result;
    try {
      result = await transaction(async db=>{
        await query('SELECT id FROM users WHERE id=? FOR UPDATE',[req.user.id],db);
        const [old] = await query('SELECT object_key FROM avatar_assets WHERE user_id=?',[req.user.id],db);
        await query(
          'INSERT INTO avatar_assets(id,user_id,object_key) VALUES(?,?,?) ON CONFLICT(user_id) DO UPDATE SET id=EXCLUDED.id,object_key=EXCLUDED.object_key,created_at=now()',
          [id,req.user.id,key],db
        );
        const [user] = await query('UPDATE users SET avatar_url=? WHERE id=? RETURNING *',[origin.origin+'/api/media/avatars/'+id+'.webp',req.user.id],db);
        return {user,old};
      });
    } catch (error) {
      await deleteStoredAvatar(key).catch(()=>{});
      throw error;
    }
    if (result.old) await deleteStoredAvatar(result.old.object_key).catch(error=>console.error('Old avatar cleanup failed:',error.code || error.name));
    res.json({avatar_url:result.user.avatar_url,user:publicUser(result.user)});
  });

  app.get('/api/media/avatars/:file',async(req,res)=>{
    const id = req.params.file.replace(/\.webp$/,'');
    if (!uuid.test(id) || req.params.file!==id+'.webp') return res.sendStatus(404);
    const [asset] = await query('SELECT object_key FROM avatar_assets WHERE id=?',[id]);
    if (!asset) return res.sendStatus(404);
    try {
      const buffer = useS3()
        ? await (await client().send(new GetObjectCommand({Bucket:bucket(),Key:asset.object_key}))).Body.transformToByteArray()
        : await readFile(path.join(directory,path.basename(asset.object_key)));
      res.set({'Content-Type':'image/webp','Cache-Control':'public, max-age=3600','X-Content-Type-Options':'nosniff'}).send(Buffer.from(buffer));
    } catch (error) {
      if (error.code==='ENOENT' || error.name==='NoSuchKey') return res.sendStatus(404);
      throw error;
    }
  });
}
