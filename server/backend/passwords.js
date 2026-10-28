import bcrypt from 'bcryptjs';
import {createHash} from 'node:crypto';

const prefix = '{bcrypt-sha256}';
const digest = value => createHash('sha256').update(value,'utf8').digest('base64');
const dummy = '$2b$12$FjSfpryrWrPDwJeTMoVKj.1Ii/kqJe63bu1NIGog6wrx.mvmMeXxy';

export async function hashPassword(password) {
  return prefix + await bcrypt.hash(digest(password),12);
}

export async function verifyPassword(password, stored) {
  if (stored?.startsWith(prefix)) return bcrypt.compare(digest(password),stored.slice(prefix.length));
  const valid = await bcrypt.compare(password,stored || dummy);
  return Boolean(stored && Buffer.byteLength(password,'utf8') <= 72 && valid);
}
