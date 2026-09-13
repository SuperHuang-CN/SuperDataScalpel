import test from 'node:test';
import assert from 'node:assert/strict';
import { AgentCapacity, IdentityLease } from '../dist/admin.js';
import { BridgeStorage } from '../dist/storage.js';
import { configuration } from '../dist/config.js';

test('global capacity is reserved before async creation and release is idempotent', () => {
  const capacity=new AgentCapacity(2), a=capacity.reserve(), b=capacity.reserve();
  assert.throws(()=>capacity.reserve(), /全局活跃/);a();a();
  const c=capacity.reserve();assert.throws(()=>capacity.reserve());b();c();capacity.reserve()();
});
test('identity lease expires closed within ten seconds and is explicitly renewed', t => {
  t.mock.timers.enable({ apis:['Date'],now:1000 });
  const lease=new IdentityLease();lease.require();t.mock.timers.tick(10000);assert.throws(()=>lease.require());
  lease.renew();lease.require();t.mock.timers.tick(10001);assert.equal(lease.expired,true);
  lease.revoke();lease.renew();assert.throws(()=>lease.require());
});
test('real-user domains are UUID-scoped and never collide with verification owners', async () => {
  const domains=[];const ctx={storageDomain:{open:async d=>{domains.push(d.name);return {table:()=>new Map()};}}};
  await BridgeStorage.open(ctx,'alice');await BridgeStorage.open(ctx,'admin_0123456789abcdef0123456789abcdef');
  assert.equal(new Set(domains).size,2);await assert.rejects(()=>BridgeStorage.open(ctx,'../alice'));
  assert.throws(()=>configuration({adminEnabled:true}));assert.equal(configuration({}).adminEnabled,false);
});
