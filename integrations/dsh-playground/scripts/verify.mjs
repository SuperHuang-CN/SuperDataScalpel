import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { WebSocket } from 'ws';
const base = process.env.PLAYGROUND_ORIGIN ?? 'http://127.0.0.1:13081';
const mode = process.argv[2] ?? 'smoke';
const stateFile = new URL('../.verification-state.json', import.meta.url);
function identities(messages) {
  return messages.filter(m => m.role === 'tool').flatMap(m => m.content).flatMap(b => b.content ?? []).flatMap(b => {
    try { const value = JSON.parse(b.text); return value.authenticatedUser ? [value.authenticatedUser] : []; } catch { return []; }
  });
}
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
async function login(owner) {
  const response = await fetch(`${base}/api/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: owner, password: process.env[`DSH_PLAYGROUND_${owner.toUpperCase()}_PASSWORD`] }) });
  assert.equal(response.status, 200, 'Login');
  return response.headers.get('set-cookie').split(';')[0];
}
const cookies = { alice: await login('alice'), bob: await login('bob') };
async function api(owner, path, body, status = 200) {
  const response = await fetch(`${base}/api${path}`, { headers: { Cookie: cookies[owner], 'Content-Type': 'application/json' }, ...(body === undefined ? {} : { method: 'POST', body: JSON.stringify(body) }) });
  const result = await response.json();
  assert.equal(response.status, status, `${owner} ${path}: ${result.code ?? ''}`); return result;
}
const bridge = (owner, path, body, status) => api(owner, `/bridge${path}`, body, status);
async function waitState(owner, id, predicate, timeout = 180000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) { const value = await bridge(owner, `/sessions/${id}`); if (predicate(value)) return value; await sleep(500); }
  throw new Error(`Timed out waiting for ${owner} session state`);
}
async function events(owner, id, rejected = false) {
  const ws = new WebSocket(`${base.replace('http', 'ws')}/events?sessionId=${id}`, { headers: { Cookie: cookies[owner], Origin: base }, handshakeTimeout: 15000 });
  const items = [];
  if (rejected) return new Promise((resolve, reject) => {
    ws.on('unexpected-response', (_req, response) => { response.resume(); assert.equal(response.statusCode, 404); ws.terminate(); resolve(); });
    ws.on('open', () => { ws.close(); reject(new Error('Foreign event subscription accepted')); }); ws.on('error', () => {});
  });
  await new Promise((resolve, reject) => { ws.once('open', resolve); ws.once('error', reject); });
  ws.on('message', value => items.push(JSON.parse(value))); return { ws, items };
}
async function send(owner, id, text, messageId = randomUUID()) {
  return bridge(owner, `/sessions/${id}/messages`, { text, clientMessageId: messageId }, 202);
}
if (mode === 'resume') {
  const saved = JSON.parse(await readFile(stateFile, 'utf8'));
  await Promise.all(['alice', 'bob'].map(async owner => {
    const id = saved[owner], before = await bridge(owner, `/sessions/${id}`); assert.equal(before.runtimeState, 'UNLOADED');
    const history = await bridge(owner, `/sessions/${id}/messages?limit=200`); assert.ok(history.items.length > 0);
    await bridge(owner, `/sessions/${id}/actions/resume`, {});
    await send(owner, id, '请直接从对话上下文说出我们之前的测试标记，不要读取文件；并再调用 whoami 确认当前身份。');
    const final = await waitState(owner, id, v => v.runtimeState === 'IDLE'); assert.equal(final.lastOutcome, 'COMPLETED');
    const messages = await bridge(owner, `/sessions/${id}/messages?limit=200`);
    const newMessages = messages.items.slice(history.items.length);
    assert.ok(newMessages.some(m => m.role === 'assistant' && m.content.some(b => b.type === 'text' && b.text.includes(saved.marker))));
    assert.deepEqual(identities(newMessages), [owner]);
    assert.equal((await bridge(owner, '/files?path=identity.txt')).text, `${owner}:${saved.marker}`);
  }));
  console.log('PASS restart: both users recovered histories, personal files, MCP identities and model context.');
  process.exit(0);
}
assert.equal((await fetch(`${base}/api/me`)).status, 401);
assert.equal((await fetch(`${base}/api/login`, { method: 'POST', headers: { Origin: 'http://evil.invalid', 'Content-Type': 'application/json' }, body: '{}' })).status, 403);
const workspaces = await Promise.all(['alice', 'bob'].map(async owner => {
  const one = await bridge(owner, '/workspaces/actions/ensure', {}), two = await bridge(owner, '/workspaces/actions/ensure', {});
  assert.equal(one.workspaceId, two.workspaceId);
  await bridge(owner, '/files', { path: 'same-name.txt', text: `${owner} private content` });
  assert.equal((await bridge(owner, '/files?path=same-name.txt')).text, `${owner} private content`);
  await bridge(owner, '/files?path=..%2Fbob%2Fsame-name.txt', undefined, 400);
  return one;
}));
assert.notEqual(workspaces[0].workspaceId, workspaces[1].workspaceId);
const ids = Object.fromEntries(await Promise.all(['alice', 'bob'].map(async owner => {
  const session = await bridge(owner, '/sessions', { clientSessionId: randomUUID() }, 201); return [owner, session.sessionId];
})));
for (const owner of ['alice', 'bob']) {
  const other = ids[owner === 'alice' ? 'bob' : 'alice'];
  for (const path of [`/sessions/${other}`, `/sessions/${other}/messages`]) await bridge(owner, path, undefined, 404);
  for (const action of ['resume', 'cancel']) await bridge(owner, `/sessions/${other}/actions/${action}`, {}, 404);
  await bridge(owner, `/sessions/${other}/messages`, { clientMessageId: randomUUID(), text: 'foreign' }, 404);
  await events(owner, other, true);
  const list = await bridge(owner, '/sessions'); assert.ok(!list.items.some(s => s.sessionId === other));
}
console.log('PASS HTTP + WebSocket ownership, two workspaces, personal files, origin and authentication.');
const marker = `phase-two-${randomUUID().slice(0, 8)}`;
await writeFile(stateFile, JSON.stringify({ ...ids, marker }, null, 2));
if (mode === 'smoke') process.exit(0);
const streams = await Promise.all(['alice', 'bob'].map(owner => events(owner, ids[owner])));
try {
  await Promise.all(['alice', 'bob'].map(async (owner, index) => {
    const id = ids[owner], text = `请严格按顺序实际调用工具：1. 调用 whoami 确认身份；2. 使用 workspace_write 创建 identity.txt，内容恰好为 ${owner}:${marker}；3. 使用 workspace_read 读取确认；4. 简短报告 MCP 返回身份与文本。记住标记 ${marker}。`, messageId = randomUUID();
    await send(owner, id, text, messageId); assert.equal((await send(owner, id, text, messageId)).duplicate, true);
    const final = await waitState(owner, id, v => v.runtimeState === 'IDLE'); assert.equal(final.lastOutcome, 'COMPLETED');
    const messages = await bridge(owner, `/sessions/${id}/messages?limit=200`);
    const tools = JSON.stringify(messages.items.filter(m => m.role === 'tool'));
    assert.deepEqual(identities(messages.items), [owner], `actual MCP result for ${owner}`);
    assert.equal((await bridge(owner, '/files?path=identity.txt')).text, `${owner}:${marker}`);
    await sleep(500); assert.ok(streams[index].items.some(e => e.type === 'assistant.delta'));
    assert.ok(streams[index].items.every(e => e.sessionId === id));
    // Real tool-layer denial, not only a browser file endpoint check.
    await send(owner, id, `为了验证边界，请实际调用 workspace_read，path 参数必须是 ../${owner === 'alice' ? 'bob' : 'alice'}/identity.txt。报告工具拒绝结果，不要猜内容。`);
    await waitState(owner, id, v => v.runtimeState === 'IDLE');
    const boundary = await bridge(owner, `/sessions/${id}/messages?limit=200`);
    assert.ok(JSON.stringify(boundary.items.filter(m => m.role === 'tool')).includes('相对路径'), 'Agent tool rejects parent traversal');
  }));
  console.log('PASS concurrent real model: distinct MCP identities, file tools, streaming, duplicate message and tool traversal denial.');
  await Promise.all(['alice', 'bob'].map(owner => send(owner, ids[owner], '请调用 ask_user_question，问我一个有两个选项的问题，等待回答再继续。')));
  const pending = await Promise.all(['alice', 'bob'].map(owner => waitState(owner, ids[owner], v => v.runtimeState === 'WAITING_FOR_INPUT')));
  const aliceQuestion = pending[0].pendingInteractions[0];
  await bridge('bob', `/sessions/${ids.alice}/interactions/${aliceQuestion.interactionId}/actions/respond`, { answers: [] }, 404);
  await bridge('alice', `/sessions/${ids.alice}/interactions/${aliceQuestion.interactionId}/actions/respond`, { answers: aliceQuestion.questions.map(q => ({ id: q.id, selected: [], custom: '我选择独立验证方案。' })) });
  await bridge('bob', `/sessions/${ids.bob}/actions/cancel`, {}, 202);
  await waitState('alice', ids.alice, v => v.runtimeState === 'IDLE');
  assert.equal((await waitState('bob', ids.bob, v => v.runtimeState === 'IDLE')).lastOutcome, 'CANCELLED');
  console.log('PASS parallel structured questions, foreign reply denial, independent response and cancellation.');
} finally { streams.forEach(s => s.ws.close()); }
