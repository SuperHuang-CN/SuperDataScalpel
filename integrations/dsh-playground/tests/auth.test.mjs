import test from 'node:test';
import assert from 'node:assert/strict';
import { Logins, bridgePath, cookieId } from '../src/auth.mjs';

test('two credentials produce independent server-side identities and revoke sockets', () => {
  const logins = new Logins({ alice: 'alice-password', bob: 'bob-password' });
  assert.equal(logins.login('bob', 'alice-password'), null);
  assert.equal(logins.login('__proto__', 'alice-password'), null);
  const a = logins.login('alice', 'alice-password'), b = logins.login('bob', 'bob-password');
  assert.notEqual(a, b); assert.equal(logins.get(a).owner, 'alice'); assert.equal(logins.get(b).owner, 'bob');
  let closed = false; logins.get(a).sockets.add({ close() { closed = true; } }); logins.logout(a);
  assert.equal(logins.get(a), null); assert.equal(closed, true); assert.equal(logins.get(b).owner, 'bob');
  logins.get(b).expires = 0; assert.equal(logins.get(b), null);
});
test('proxy paths are scoped using authenticated owner, never browser-selected user or URL', () => {
  assert.equal(bridgePath('/sessions', 'alice'), '/bridge/v2/users/alice/sessions');
  for (const p of ['/users/bob/sessions', '/../../../bridge/v1/sessions', '/sessions/../bob', '//evil.test', '/events', '/files/../../etc/passwd']) assert.equal(bridgePath(p, 'alice'), null);
  assert.equal(cookieId({ headers: { cookie: 'x=1; dsh_playground=opaque; y=2' } }), 'opaque');
});
