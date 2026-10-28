import {test,after} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import request from 'supertest';
import sharp from 'sharp';
import {S3Client,PutObjectCommand,GetObjectCommand,DeleteObjectCommand} from '@aws-sdk/client-s3';
import {app} from '../backend/app.js';
import {pool,query} from '../backend/db.js';

after(()=>pool.end());

test('S3 avatar path uses private encrypted objects and replaces the old object',async t=>{
  const email='s3-'+randomUUID()+'@example.com';
  const password='S3-password-'+randomUUID();
  const api=request(app);
  const saved={MEDIA_DRIVER:process.env.MEDIA_DRIVER,AWS_REGION:process.env.AWS_REGION,S3_BUCKET:process.env.S3_BUCKET};
  process.env.MEDIA_DRIVER='s3';
  process.env.AWS_REGION='ap-southeast-1';
  process.env.S3_BUCKET='unit-test-bucket';
  const objects=new Map();
  const operations=[];
  t.mock.method(S3Client.prototype,'send',async command=>{
    const {Bucket,Key,Body}=command.input;
    assert.equal(Bucket,'unit-test-bucket');
    assert.match(Key,/^avatars\/[0-9a-f-]{36}\.webp$/);
    operations.push(command.constructor.name);
    if(command instanceof PutObjectCommand){
      assert.equal(command.input.ServerSideEncryption,'AES256');
      assert.equal(command.input.ACL,undefined);
      objects.set(Key,Body);
      return {};
    }
    if(command instanceof GetObjectCommand)return {Body:{transformToByteArray:async()=>objects.get(Key)}};
    if(command instanceof DeleteObjectCommand){objects.delete(Key);return {};}
    throw new Error('Unexpected storage command');
  });
  try{
    await api.post('/api/auth/register').send({email,password,full_name:'S3 Learner'});
    const response=await api.post('/api/auth/login').send({email,password});
    assert.equal(response.status,200);
    const token=response.body.token;
    const image=await sharp({create:{width:5,height:5,channels:3,background:'white'}}).png().toBuffer();
    let previous;
    for(let index=0;index<2;index++){
      const uploaded=await api.post('/api/auth/me/avatar').auth(token,{type:'bearer'}).attach('avatar',image,{filename:'test.png',contentType:'image/png'});
      assert.equal(uploaded.status,200,JSON.stringify(uploaded.body));
      const path=new URL(uploaded.body.avatar_url).pathname;
      assert.equal((await api.get(path)).status,200);
      if(previous)assert.equal((await api.get(previous)).status,404);
      previous=path;
    }
    assert.equal(objects.size,1);
    assert.ok(operations.includes('DeleteObjectCommand'));
  }finally{
    await query('DELETE FROM users WHERE email=?',[email]);
    for(const[key,value]of Object.entries(saved)){if(value===undefined)delete process.env[key];else process.env[key]=value;}
  }
});
