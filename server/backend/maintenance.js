import {query, transaction} from './db.js';

export async function cleanupExpiredRecords(batchSize = 500) {
  if (!Number.isInteger(batchSize) || batchSize < 1 || batchSize > 5000) throw new Error('Invalid cleanup batch size');
  return transaction(async db => {
    const sessions = await query(
      `DELETE FROM auth_sessions WHERE id IN (
        SELECT id FROM auth_sessions
        WHERE expires_at<=now() OR revoked_at<now()-interval '1 day'
        ORDER BY expires_at LIMIT ? FOR UPDATE SKIP LOCKED
      ) RETURNING id`,[batchSize],db
    );
    const resets = await query(
      `DELETE FROM password_resets WHERE email IN (
        SELECT email FROM password_resets
        WHERE GREATEST(expires_at,COALESCE(reset_expires_at,expires_at))<now()
        ORDER BY expires_at LIMIT ? FOR UPDATE SKIP LOCKED
      ) RETURNING email`,[batchSize],db
    );
    return {sessions:sessions.length,password_resets:resets.length};
  });
}

export function startMaintenance() {
  let running = false;
  const run = async () => {
    if (running) return;
    running = true;
    try { await cleanupExpiredRecords(); }
    catch (error) { console.error('Maintenance failed:',error.code || error.name); }
    finally { running = false; }
  };
  const timer = setInterval(run,15*60*1000).unref();
  void run();
  return () => clearInterval(timer);
}
