import test from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { PassThrough } from 'node:stream';
import { authenticator, readBody } from '../dist/routes.js';
import { transcript, outcome, hasReceived } from '../dist/projection.js';
import { Interactions } from '../dist/interactions.js';
import { BridgeStorage } from '../dist/storage.js';
import { Sessions } from '../dist/sessions.js';
import { Serial, paging, messageInput } from '../dist/contracts.js';
import { configuration, accessSecret } from '../dist/config.js';

const code = expected => error => error.code === expected;
test('Bearer is independent of Cookie, rejects duplicate auth and browser origins', () => {
  const auth = authenticator('test-secret');
  const req = { rawHeaders: ['Authorization', 'Bearer test-secret'], headers: { authorization: 'Bearer test-secret' } };
  auth(req);
  for (const headers of [{ cookie: 'session=valid' }, { authorization: 'Bearer wrong' }]) {
    assert.throws(() => auth({ ...req, headers }), code('BRIDGE_UNAUTHORIZED'));
  }
  assert.throws(() => auth({ ...req, rawHeaders: [...req.rawHeaders, ...req.rawHeaders] }), code('BRIDGE_UNAUTHORIZED'));
  assert.throws(() => auth({ ...req, headers: { ...req.headers, origin: 'http://localhost' } }), code('BRIDGE_ORIGIN_REJECTED'));
});

test('body bounds apply to declared and chunked bodies; malformed JSON is explicit', async () => {
  const read = async (value, headers = {}, limit = 16) => {
    const req = new PassThrough(); req.headers = { 'content-type': 'application/json', ...headers };
    const result = readBody(req, limit); req.end(value); return result;
  };
  assert.deepEqual(await read('{"a":1}'), { a: 1 });
  await assert.rejects(read('x'.repeat(17)), code('BRIDGE_REQUEST_TOO_LARGE'));
  await assert.rejects(read('{}', { 'content-length': '17' }), code('BRIDGE_REQUEST_TOO_LARGE'));
  await assert.rejects(read('{'), code('BRIDGE_ARGUMENT_INVALID'));
  await assert.rejects(read('{}', { 'content-type': 'text/plain' }), code('BRIDGE_CONTENT_TYPE_UNSUPPORTED'));
});

test('transcript keeps append facts and excludes compaction replacements and internal headers', () => {
  const message = (id, text) => ({ id, role: 'assistant', content: [{ type: 'text', text }] });
  const events = [
    { type: 'assistant/message', seq: 1, surfaceOp: 'append', data: { message: message('original', 'original') } },
    { type: 'assistant/message', seq: 2, surfaceOp: { kind: 'replace', startSeq: 0, endSeq: 1 }, data: { message: message('summary', 'summary') } },
    { type: 'system/message', seq: 3, surfaceOp: 'append', data: { headers: { Authorization: 'secret' } } },
  ];
  assert.deepEqual(transcript(events).map(x => x.id), ['original']);
  assert.equal(JSON.stringify(transcript(events)).includes('secret'), false);
});

test('unfinished cold turn is interrupted; live turn retains preceding outcome', () => {
  const events = [{ type: 'turn/end', data: { reason: { kind: 'completed' } } }, { type: 'turn/start' }];
  assert.equal(outcome(events), 'INTERRUPTED');
  assert.equal(outcome(events, true), 'COMPLETED');
  assert.equal(outcome([{ type: 'turn/start' }], true), null);
  assert.equal(hasReceived([{ type: 'agent/inbox/spliced', data: { inserted: [{ id: 'receipt' }] } }], 'receipt'), true);
});

test('question replies match exact session and interaction; options/free text settle once', async () => {
  const events = [], questions = new Interactions(e => events.push(e));
  const request = { questions: [{ id: 'color', question: '颜色', options: [{ label: '蓝色' }, { label: '绿色' }], multiSelect: false }] };
  const pending = questions.ask('A', request);
  const id = questions.list('A')[0].interactionId;
  assert.throws(() => questions.respond('B', id, { answers: [] }), code('BRIDGE_INTERACTION_EXPIRED'));
  assert.throws(() => questions.respond('A', id, { answers: [{ id: 'color', selected: ['未知'] }] }), code('BRIDGE_ARGUMENT_INVALID'));
  assert.throws(() => questions.respond('A', id, { answers: [{ id: 'color', selected: ['蓝色', '绿色'] }] }), code('BRIDGE_ARGUMENT_INVALID'));
  assert.throws(() => questions.respond('A', id, { answers: [{ id: 'color', selected: ['蓝色'], custom: '绿色' }] }), code('BRIDGE_ARGUMENT_INVALID'));
  const answer = { answers: [{ id: 'color', selected: [], custom: '紫色' }] };
  questions.respond('A', id, answer);
  assert.deepEqual(await pending, answer);
  assert.throws(() => questions.respond('A', id, answer), code('BRIDGE_INTERACTION_EXPIRED'));
  assert.deepEqual(events.map(e => e.type), ['interaction.requested', 'interaction.resolved']);
});

test('cancellation aborts native question wait and expires reply', async () => {
  const questions = new Interactions(() => {}), signal = new AbortController();
  const pending = questions.ask('A', { questions: [], signal: signal.signal });
  const id = questions.list('A')[0].interactionId;
  const rejected = assert.rejects(pending, /cancelled/);
  signal.abort(); await rejected;
  assert.throws(() => questions.respond('A', id, { answers: [] }), code('BRIDGE_INTERACTION_EXPIRED'));
});

test('foreign native session cannot become owned merely by knowing its ID', () => {
  const storage = new BridgeStorage({ table: () => new Map() });
  assert.throws(() => storage.own(randomUUID()), code('BRIDGE_SESSION_NOT_FOUND'));
});

function fixture(failFlush = false) {
  const records = new Map(), events = []; let sends = 0;
  const storage = { own: () => ({}), messages: { get: key => records.get(key), put: async (key, value) => { records.set(key, value); } } };
  const agent = { status: 'idle', session: { snapshotEvents: () => [...events] }, runMaintenance: async fn => fn(),
    send: message => { sends++; events.push({ type: 'agent/inbox/spliced', data: { inserted: [message] } }); agent.status = 'running'; },
    cancel: () => { agent.status = 'idle'; } };
  const sessions = new Sessions({ sessions: { flush: async () => { if (failFlush) throw new Error('disk'); } } }, {}, storage, {}, () => {});
  sessions.live.set('session', { handle: { agent }, accepting: false, cancelling: false });
  return { sessions, sends: () => sends, records, agent };
}

test('concurrent identical message executes once, conflicting and busy messages are rejected', async () => {
  const { sessions, sends } = fixture();
  const id = randomUUID();
  const result = await Promise.all([sessions.send('session', id, 'hello'), sessions.send('session', id, 'hello')]);
  assert.equal(sends(), 1); assert.equal(result[1].duplicate, true);
  await assert.rejects(sessions.send('session', id, 'different'), code('BRIDGE_MESSAGE_CONFLICT'));
  await assert.rejects(sessions.send('session', randomUUID(), 'new'), code('BRIDGE_SESSION_BUSY'));
});

test('failed persistence never reports accepted and retry never enqueues again', async () => {
  const { sessions, sends } = fixture(true), id = randomUUID();
  await assert.rejects(sessions.send('session', id, 'hello'), code('BRIDGE_MESSAGE_OUTCOME_UNKNOWN'));
  await assert.rejects(sessions.send('session', id, 'hello'), code('BRIDGE_PERSISTENCE_FAILED'));
  assert.equal(sends(), 1);
});

test('serial failures release next control and public inputs reject widening scope', async () => {
  const serial = new Serial();
  await assert.rejects(serial.run(async () => { throw new Error('failed'); }));
  assert.equal(await serial.run(async () => 7), 7);
  assert.throws(() => paging(new URLSearchParams('limit=201'), true));
  assert.throws(() => paging(new URLSearchParams('url=http://evil')));
  assert.equal(messageInput.safeParse({ clientMessageId: randomUUID(), text: 'ok', role: 'system' }).success, false);
  assert.equal(messageInput.parse({ clientMessageId: randomUUID(), text: '  hello  ' }).text, '  hello  ');
  assert.equal(messageInput.safeParse({ clientMessageId: randomUUID(), text: '  ' }).success, false);
});

test('missing configuration and storage failures are explicit before execution', async () => {
  assert.equal(configuration({}).enabled, false);
  assert.throws(() => configuration({ enabled: true }), /requires provider/);
  assert.throws(() => accessSecret({ accessTokenEnv: 'DSH_BRIDGE_TEST_ABSENT_SECRET_48F19' }), /access secret/);
  const sessions = new Sessions({ agentPresets: { resolve: async () => { throw new Error('missing preset'); } } }, {}, {}, {}, () => {});
  await assert.rejects(sessions.ready(), code('BRIDGE_NOT_READY'));
  const storage = new BridgeStorage({ table: () => ({ put: async () => { throw new Error('disk'); } }) });
  await assert.rejects(storage.saveSession({ sessionId: randomUUID() }), code('BRIDGE_PERSISTENCE_FAILED'));
});

test('cold history uses the native read handle and never starts an Agent', async () => {
  let closed = false;
  const ctx = { sessionPersistence: { open: async (id, mode) => {
    assert.equal(mode, 'read');
    return { read: async () => ({ events: [] }), close: async () => { closed = true; } };
  } }, get agents() { throw new Error('History must not create Agents'); } };
  const sessions = new Sessions(ctx, {}, { own: () => ({}) }, {}, () => {});
  assert.deepEqual(await sessions.messages(randomUUID(), 0, 50), { items: [], offset: 0, limit: 50, hasMore: false });
  assert.equal(closed, true);
});
