import {test} from 'node:test';
import assert from 'node:assert/strict';
import bcrypt from 'bcryptjs';
import {hashPassword,verifyPassword} from '../backend/passwords.js';

test('new password hashes distinguish passwords after the bcrypt 72-byte limit',async()=>{
  const first='A'.repeat(90)+'first';
  const second='A'.repeat(90)+'second';
  const stored=await hashPassword(first);
  assert.equal(await verifyPassword(first,stored),true);
  assert.equal(await verifyPassword(second,stored),false);
  const unicode='Mật-khẩu-'.repeat(10);
  assert.equal(await verifyPassword(unicode,await hashPassword(unicode)),true);
});

test('legacy bcrypt accounts remain readable without accepting truncated long passwords',async()=>{
  const password='Legacy-password-123';
  assert.equal(await verifyPassword(password,await bcrypt.hash(password,12)),true);
  assert.equal(await verifyPassword('A'.repeat(90),await bcrypt.hash('A'.repeat(90),12)),false);
  assert.equal(await verifyPassword(password,null),false);
});
