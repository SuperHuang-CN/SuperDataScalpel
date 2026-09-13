import test from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { Sessions } from '../dist/sessions.js';

function fixture() {
  const id = randomUUID(); let disposed = 0, released = 0, reads = 0;
  const records = new Map([[id,{sessionId:id,clientSessionId:randomUUID(),workspaceId:'w',path:'/w',state:'READY',createdAt:'2026-01-01T00:00:00.000Z',presetId:'datascalpel-admin',provider:'p',model:'m'}]]);
  const history = [0,1,2,3,4].map(seq=>({type:'user/message',seq,time:1000+seq,data:{id:`m${seq}`,role:'user',source:{kind:'user'},content:[{type:'text',text:seq===0?'初次提问':'follow-up'}]},surfaceOp:'append'}));
  const storage = { sessions:records, messages:new Map(), own:id=>{if(!records.has(id))throw Object.assign(new Error('missing'),{code:'BRIDGE_SESSION_NOT_FOUND'});return records.get(id);}, saveSession:async record=>{records.set(record.sessionId,record);} };
  const sessions = new Sessions({ sessions:{flush:async()=>{}}, sessionPersistence:{open:async()=>{reads++;return {read:async()=>({events:history}),close:async()=>{}};}} },{presetId:'datascalpel-admin'},storage,{},()=>{});
  const agent = { status:'idle',session:{snapshotEvents:()=>history} };
  const attach = () => sessions.live.set(id,{handle:{agent,dispose:async()=>{disposed++;}},release:()=>released++,accepting:false,cancelling:false});
  return {sessions,id,records,agent,attach,history,counts:()=>({disposed,released,reads})};
}
test('legacy metadata is migrated once and list reads neither histories nor Agents',async()=>{
  const f=fixture();await f.sessions.initializeMetadata();
  assert.equal(f.records.get(f.id).title,'初次提问');
  const reads=f.counts().reads;await f.sessions.initializeMetadata();await f.sessions.list(0,20);
  assert.equal(f.counts().reads,reads);
  assert.equal((await f.sessions.list(0,20,'初次')).items.length,1);
  assert.equal((await f.sessions.list(0,20,'missing')).items.length,0);
});
test('archive rejects busy sessions, releases idle handles, blocks send/resume and restores cold',async()=>{
  const f=fixture();await f.sessions.initializeMetadata();f.attach();f.agent.status='running';
  await assert.rejects(f.sessions.archive(f.id,true),e=>e.code==='BRIDGE_SESSION_BUSY');
  assert.equal(f.records.get(f.id).archived,false);f.agent.status='idle';
  await f.sessions.archive(f.id,true);await f.sessions.archive(f.id,true);
  assert.equal(f.counts().disposed,1);assert.equal(f.counts().released,1);
  await assert.rejects(f.sessions.send(f.id,randomUUID(),'no'),e=>e.code==='BRIDGE_SESSION_ARCHIVED');
  await assert.rejects(f.sessions.resume(f.id),e=>e.code==='BRIDGE_SESSION_ARCHIVED');
  await assert.rejects(f.sessions.respond(f.id,randomUUID(),{answers:[]}),e=>e.code==='BRIDGE_SESSION_ARCHIVED');
  assert.equal((await f.sessions.list(0,20)).items.length,0);
  assert.equal((await f.sessions.list(0,20,'',true)).items.length,1);
  await f.sessions.archive(f.id,false);assert.equal(f.sessions.activeCount,0);
});
test('rename and archive do not change activity; persistence failure never reports success',async()=>{
  const f=fixture();await f.sessions.initializeMetadata();const time=f.records.get(f.id).lastActivityAt;
  await f.sessions.update(f.id,'manual');await f.sessions.archive(f.id,true);
  assert.equal(f.records.get(f.id).lastActivityAt,time);assert.equal(f.records.get(f.id).titleManual,true);
  f.sessions.storage.saveSession=async()=>{throw new Error('disk');};
  await assert.rejects(f.sessions.update(f.id,'lost'),/disk/);assert.equal(f.records.get(f.id).title,'manual');
});
test('cursor pages remain stable while new messages arrive, with no overlap',async()=>{
  const f=fixture();const latest=await f.sessions.recentMessages(f.id,undefined,2);
  assert.deepEqual(latest.items.map(m=>m.sourceSeq),[3,4]);assert.equal(latest.hasMore,true);
  f.history.push({...f.history[4],seq:5,data:{...f.history[4].data,id:'m5'}});
  const older=await f.sessions.recentMessages(f.id,latest.nextBeforeSeq,2);
  assert.deepEqual(older.items.map(m=>m.sourceSeq),[1,2]);
  const oldest=await f.sessions.recentMessages(f.id,older.nextBeforeSeq,2);
  assert.deepEqual(oldest.items.map(m=>m.sourceSeq),[0]);assert.equal(oldest.hasMore,false);
});
