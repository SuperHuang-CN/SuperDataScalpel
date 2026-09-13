import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, symlink, link, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { WorkspaceFiles } from '../dist/files.js';
import { BridgeStorage } from '../dist/storage.js';

test('personal tools isolate same-name files, reject traversal, links, oversized and binary text', async () => {
  const root = await mkdtemp(join(tmpdir(), 'dsh-files-'));
  // macOS /var is itself an alias; production roots are already canonical.
  const { realpath } = await import('node:fs/promises'); const canonical = await realpath(root);
  const a = new WorkspaceFiles(join(canonical, 'alice')), b = new WorkspaceFiles(join(canonical, 'bob'));
  try {
    await Promise.all([a.write('note.txt', 'only Alice'), b.write('note.txt', 'only Bob')]);
    assert.equal((await a.read('note.txt')).text, 'only Alice'); assert.equal((await b.read('note.txt')).text, 'only Bob');
    for (const path of ['../bob/note.txt', '/etc/passwd', 'nested/../../bob', 'x\\y', './note.txt']) {
      await assert.rejects(a.read(path), e => e.code === 'BRIDGE_FILE_PATH_REJECTED');
      await assert.rejects(a.write(path, 'changed'), e => e.code === 'BRIDGE_FILE_PATH_REJECTED');
    }
    await symlink(b.root, join(a.root, 'shortcut')); await link(join(b.root, 'note.txt'), join(a.root, 'linked.txt'));
    for (const path of ['shortcut/note.txt', 'linked.txt']) {
      await assert.rejects(a.read(path), e => e.code === 'BRIDGE_FILE_PATH_REJECTED');
      await assert.rejects(a.write(path, 'changed'), e => e.code === 'BRIDGE_FILE_PATH_REJECTED');
    }
    await a.write('notes/nested.txt', '你好'); assert.equal((await a.read('notes/nested.txt')).text, '你好');
    await assert.rejects(a.write('large.txt', 'a'.repeat(131073))); await assert.rejects(a.write('binary', '\0'));
    const entries = (await a.read('')).entries; assert.ok(!entries.some(e => e.name === 'shortcut'));
  } finally { await rm(root, { recursive: true, force: true }); }
});
test('phase one and both users open distinct domains; knowing foreign IDs grants nothing', async () => {
  const names = [], stores = new Map();
  const ctx = { storageDomain: { async open(def) {
    names.push(def.name); const sessions = new Map(); stores.set(def.name, sessions);
    return { table: () => sessions, close() {} };
  } } };
  const [first, a, b] = await Promise.all([BridgeStorage.open(ctx), BridgeStorage.open(ctx, 'alice'), BridgeStorage.open(ctx, 'bob')]);
  assert.equal(new Set(names).size, 3);
  a.sessions.set('alice-session', { sessionId: 'alice-session' });
  assert.equal(a.own('alice-session').sessionId, 'alice-session');
  assert.throws(() => b.own('alice-session'), e => e.status === 404); assert.throws(() => first.own('alice-session'), e => e.status === 404);
});
