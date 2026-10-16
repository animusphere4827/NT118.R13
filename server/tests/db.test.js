import {test,after} from 'node:test';
import assert from 'node:assert/strict';

// Fake clients only: this suite never opens a database connection.
process.env.DATABASE_URL ||= 'postgresql://test:test@127.0.0.1:1/test';
const {pool,query,transaction}=await import('../backend/db.js');
after(async()=>pool.end());

test('query binds parameters using PostgreSQL placeholders',async()=>{
 const client={async query(sql,params){
  assert.equal(sql,'SELECT * FROM users WHERE id=$1 AND email=$2');
  assert.deepEqual(params,[42,'learner@example.com']);
  return {rows:[{id:42}]};
 }};
 assert.deepEqual(await query('SELECT * FROM users WHERE id=? AND email=?',[42,'learner@example.com'],client),[{id:42}]);
});

test('transactions commit, roll back and release their client',async t=>{
 const original=pool.connect;
 t.after(()=>{pool.connect=original;});
 for(const scenario of ['success','callback failure','commit failure','rollback failure']){
  const statements=[];
  const failure=new Error(scenario);
  let released=0,releaseError;
  pool.connect=async()=>({
   async query(sql){
    statements.push(sql);
    if(scenario==='commit failure'&&sql==='COMMIT')throw failure;
    if(scenario==='rollback failure'&&sql==='ROLLBACK')throw new Error('Connection lost');
   },
   release(error){released++;releaseError=error;}
  });
  const work=async client=>{
   await client.query('SELECT 1');
   if(scenario==='callback failure'||scenario==='rollback failure')throw failure;
   return 42;
  };
  if(scenario==='success'){
   assert.equal(await transaction(work),42);
   assert.deepEqual(statements,['BEGIN','SELECT 1','COMMIT']);
  }else{
   await assert.rejects(transaction(work),error=>error===failure);
   assert.deepEqual(statements,scenario==='commit failure'
    ?['BEGIN','SELECT 1','COMMIT','ROLLBACK']
    :['BEGIN','SELECT 1','ROLLBACK']);
  }
  assert.equal(released,1); if(scenario==='rollback failure')assert.equal(releaseError.message,'Connection lost'); else assert.equal(releaseError,undefined);
 }
});
