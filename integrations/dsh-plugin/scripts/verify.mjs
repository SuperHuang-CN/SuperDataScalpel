import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import WebSocket from 'ws';

const phase = process.argv[2] ?? 'conversation';
const base = process.env.DSH_BRIDGE_URL ?? 'http://127.0.0.1:13080/bridge/v1';
const token = process.env.DATASCALPEL_DSH_BRIDGE_TOKEN;
if (!token) throw new Error('Missing Bridge token environment variable');
const headers = { Authorization: `Bearer ${token}` };
const statePath = new URL('../.verification-state.json', import.meta.url);
const reportPath = new URL(`../verification-${phase}.json`, import.meta.url);
const results = [], sockets = new Set();
const record = (name, details = {}) => { results.push({ name, status: 'PASS', ...details }); console.log(`PASS ${name}`); };
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
async function request(method, path, body, status = 200, customHeaders = headers) {
  const res = await fetch(`${base}${path}`, { method, headers: { ...customHeaders,
    ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body), redirect: 'error', signal: AbortSignal.timeout(15000) });
  const value = await res.json();
  if (res.status !== status) throw new Error(`Expected ${status}, got ${res.status}: ${value.code ?? 'unknown'} at ${method} ${path}`);
  return value;
}
async function stream(sessionId) {
  const url = new URL(`${base}/events`); url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  url.searchParams.set('sessionId', sessionId);
  const ws = new WebSocket(url, { headers }), events = [];
  sockets.add(ws);
  let fault;
  ws.on('message', data => { events.push(JSON.parse(data.toString())); });
  ws.on('error', () => { fault = new Error('WebSocket connection failed'); });
  ws.on('close', code => { if (![1000,1001].includes(code)) fault = new Error(`WebSocket closed ${code}`); });
  const wait = async (type, from = 0) => {
    const deadline = Date.now() + 120000;
    while (Date.now() < deadline) {
      const found = events.slice(from).find(event => event.type === type);
      if (found) return found;
      const failure = events.slice(from).find(event => ['run.failed', 'session.persistence-failed'].includes(event.type));
      if (failure) throw new Error(`${failure.type}: ${failure.data?.code ?? failure.data?.reason}`);
      if (fault) throw fault;
      await pause(100);
    }
    throw new Error(`Timed out waiting for ${type}; execution outcome must be inspected before retrying`);
  };
  await wait('session.snapshot');
  return { ws, events, wait };
}
async function idle(id) {
  for (let i = 0; i < 100; i++) {
    const view = await request('GET', `/sessions/${id}`);
    if (view.runtimeState === 'IDLE') return view;
    await pause(100);
  }
  throw new Error('Session did not become idle');
}
async function history(id) {
  const items = []; let offset = 0;
  for (;;) {
    const page = await request('GET', `/sessions/${id}/messages?offset=${offset}&limit=200`);
    items.push(...page.items); if (!page.hasMore) return items; offset += page.items.length;
  }
}
async function unauthorizedWs(id) {
  const url = new URL(`${base}/events`); url.protocol = 'ws:'; url.searchParams.set('sessionId', id);
  await new Promise((resolve, reject) => {
    const ws = new WebSocket(url); sockets.add(ws);
    const timer = setTimeout(() => { ws.terminate(); reject(new Error('Missing WS rejection')); }, 5000);
    ws.on('unexpected-response', (_, response) => {
      clearTimeout(timer); response.resume(); ws.terminate();
      if (response.statusCode === 401) resolve(); else reject(new Error(`WS unauthorized status ${response.statusCode}`));
    });
    ws.on('error', () => {});
    ws.on('open', () => { clearTimeout(timer); ws.close(); reject(new Error('Unauthorized WS accepted')); });
  });
}

let state;
try {
  const capabilities = await request('GET', '/capabilities');
  assert.equal(capabilities.dshVersion, '0.1.5-rc.1');
  record('capabilities', { pluginVersion: capabilities.pluginVersion, dshVersion: capabilities.dshVersion });
  if (phase === 'conversation') {
    await request('GET', '/capabilities', undefined, 401, {});
    await request('GET', '/capabilities', undefined, 401, { Authorization: 'Bearer incorrect' });
    await request('GET', '/capabilities?token=forbidden', undefined, 400);
    await request('POST', '/capabilities', {}, 405);
    const workspace = await request('POST', '/workspaces/actions/ensure', {});
    const again = await Promise.all([request('POST', '/workspaces/actions/ensure', {}), request('POST', '/workspaces/actions/ensure', {})]);
    assert(again.every(item => item.workspaceId === workspace.workspaceId));
    record('workspace-ensure');
    if (!process.argv.includes('--new-run')) {
      try { state = JSON.parse(await readFile(statePath, 'utf8')); }
      catch (error) { if (error.code !== 'ENOENT') throw error; }
      if (state?.sessionId) throw new Error('Checkpoint already contains a session. Inspect its outcome first; use --new-run only for an intentional separate verification run.');
    }
    state ??= { clientSessionId: randomUUID(), firstMessageId: randomUUID(), marker: `青石-${randomUUID()}`, workspaceId: workspace.workspaceId };
    await writeFile(statePath, JSON.stringify(state, null, 2));
    const session = await request('POST', '/sessions', { clientSessionId: state.clientSessionId }, 201);
    state.sessionId = session.sessionId;
    await writeFile(statePath, JSON.stringify(state, null, 2));
    const duplicate = await request('POST', '/sessions', { clientSessionId: state.clientSessionId });
    assert.equal(duplicate.sessionId, state.sessionId);
    await unauthorizedWs(state.sessionId);
    record('auth-and-session-create-dedup');
    const id = state.sessionId, path = `/sessions/${id}`, feed = await stream(id);
    const firstText = `请记住唯一验证标记 ${state.marker}。只回复你已经记住，并重复标记。`;
    const start = feed.events.length;
    await request('POST', `${path}/messages`, { clientMessageId: state.firstMessageId, text: firstText }, 202);
    const repeated = await request('POST', `${path}/messages`, { clientMessageId: state.firstMessageId, text: firstText }, 202);
    assert.equal(repeated.duplicate, true);
    await request('POST', `${path}/messages`, { clientMessageId: state.firstMessageId, text: 'conflicting' }, 409);
    await feed.wait('run.completed', start); await idle(id);
    const firstHistory = await history(id);
    assert.equal(firstHistory.filter(item => item.id === state.firstMessageId).length, 1);
    assert(feed.events.slice(start).some(event => event.type === 'assistant.delta'));
    assert(firstHistory.some(item => item.role === 'assistant' && JSON.stringify(item.content).includes(state.marker)));
    record('real-stream-final-history-and-message-dedup', { sessionId: id });

    const questionStart = feed.events.length;
    await request('POST', `${path}/messages`, { clientMessageId: randomUUID(), text: '请务必调用 ask_user_question 工具，问我喜欢蓝色还是绿色。必须等我通过工具回复，再告诉我选择的颜色。' }, 202);
    const question = await feed.wait('interaction.requested', questionStart);
    const answers = question.data.questions.map(q => ({ id: q.id, selected: [q.options[0].label] }));
    await request('POST', `${path}/messages`, { clientMessageId: randomUUID(), text: 'busy message' }, 409);
    await request('POST', `${path}/interactions/${question.data.interactionId}/actions/respond`, { answers });
    await request('POST', `${path}/interactions/${question.data.interactionId}/actions/respond`, { answers }, 410);
    await feed.wait('run.completed', questionStart); await idle(id);
    assert(feed.events.slice(questionStart).some(event => event.type === 'tool.started'));
    assert(feed.events.slice(questionStart).some(event => event.type === 'tool.completed'));
    record('native-question-choice-and-continue');

    const freeStart = feed.events.length;
    await request('POST', `${path}/messages`, { clientMessageId: randomUUID(), text: '再次使用 ask_user_question 问我如何称呼我，等待我自由输入姓名，收到后简短问好。' }, 202);
    const free = await feed.wait('interaction.requested', freeStart);
    const freeAnswers = free.data.questions.map(q => ({ id: q.id, selected: [], custom: '验证员小石' }));
    await request('POST', `${path}/interactions/${free.data.interactionId}/actions/respond`, { answers: freeAnswers });
    await feed.wait('run.completed', freeStart); await idle(id);
    record('native-question-free-text');

    const cancelStart = feed.events.length;
    await request('POST', `${path}/messages`, { clientMessageId: randomUUID(), text: '请使用 ask_user_question 问我是否继续，等待我的回答。' }, 202);
    const cancellable = await feed.wait('interaction.requested', cancelStart);
    await request('POST', `${path}/actions/cancel`, {}, 202);
    await feed.wait('run.cancelled', cancelStart);
    const cancelled = await idle(id); assert.equal(cancelled.lastOutcome, 'CANCELLED');
    await request('POST', `${path}/interactions/${cancellable.data.interactionId}/actions/respond`, { answers: [{ id: 'expired', selected: [], custom: 'no' }] }, 410);
    record('cancel-running-question');

    await request('POST', `${path}/messages`, { clientMessageId: randomUUID(), text: '请写一段约 100 字的山水描写。' }, 202);
    feed.ws.close();
    const reconnected = await stream(id);
    const snap = reconnected.events[0].data;
    if (snap.runtimeState !== 'IDLE') await reconnected.wait('run.completed');
    const finalState = await idle(id); assert.equal(finalState.lastOutcome, 'COMPLETED');
    const transcript = await history(id); state.historyIds = transcript.map(item => item.id);
    record('disconnect-and-history-reconnect');
    const foreign = process.env.DSH_BRIDGE_FOREIGN_SESSION_ID ?? randomUUID();
    for (const suffix of ['', '/messages']) await request('GET', `/sessions/${foreign}${suffix}`, undefined, 404);
    for (const suffix of ['/actions/resume', '/actions/cancel']) await request('POST', `/sessions/${foreign}${suffix}`, {}, 404);
    record('foreign-session-rejection', { knownNativeIdUsed: !!process.env.DSH_BRIDGE_FOREIGN_SESSION_ID });

    const interrupted = await request('POST', '/sessions', { clientSessionId: randomUUID() }, 201);
    state.interruptedSessionId = interrupted.sessionId;
    const interruptedFeed = await stream(interrupted.sessionId);
    await request('POST', `/sessions/${interrupted.sessionId}/messages`, { clientMessageId: randomUUID(), text: '请调用 ask_user_question 问我是否准备好，必须等待回复。' }, 202);
    const oldQuestion = await interruptedFeed.wait('interaction.requested');
    state.expiredInteractionId = oldQuestion.data.interactionId;
    // Two live conversation Agents already exist. Two empty Agents fill the configured four slots.
    await request('POST', '/sessions', { clientSessionId: randomUUID() }, 201);
    await request('POST', '/sessions', { clientSessionId: randomUUID() }, 201);
    const full = await request('POST', '/sessions', { clientSessionId: randomUUID() }, 503);
    assert.equal(full.code, 'BRIDGE_CAPACITY_EXCEEDED');
    record('active-agent-capacity');
    await writeFile(statePath, JSON.stringify(state, null, 2));
    record('restart-checkpoint-ready');
    console.log('WAITING_FOR_OPERATOR_RESTART: restart the existing DSH service, then run verify.mjs resume.');
  } else if (phase === 'resume') {
    state = JSON.parse(await readFile(statePath, 'utf8'));
    const id = state.sessionId, path = `/sessions/${id}`;
    const cold = await request('GET', path); assert.equal(cold.runtimeState, 'UNLOADED');
    const before = await history(id); assert.deepEqual(before.map(item => item.id), state.historyIds);
    const resumed = await request('POST', `${path}/actions/resume`, {});
    assert.equal(resumed.runtimeState, 'IDLE');
    const reused = await request('POST', `${path}/actions/resume`, {}); assert.equal(reused.sessionId, id);
    const feed = await stream(id), start = feed.events.length;
    await request('POST', `${path}/messages`, { clientMessageId: randomUUID(), text: '我在对话最初让你记住的唯一验证标记是什么？只回复完整标记。' }, 202);
    await feed.wait('run.completed', start); await idle(id);
    const latest = (await history(id)).filter(item => item.role === 'assistant').at(-1);
    assert(JSON.stringify(latest.content).includes(state.marker));
    record('restart-explicit-resume-and-real-context-recall', { sessionId: id });
    const interruptedPath = `/sessions/${state.interruptedSessionId}`;
    const interrupted = await request('GET', interruptedPath);
    assert.equal(interrupted.runtimeState, 'UNLOADED'); assert.equal(interrupted.pendingInteractions.length, 0);
    await request('POST', `${interruptedPath}/interactions/${state.expiredInteractionId}/actions/respond`, { answers: [{ id: 'expired', selected: [], custom: 'no' }] }, 410);
    const unchanged = await history(state.interruptedSessionId);
    const interruptedFeed = await stream(state.interruptedSessionId);
    const resumeStart = interruptedFeed.events.length;
    await request('POST', `${interruptedPath}/actions/resume`, {});
    await pause(1500);
    const noReplay = await request('GET', interruptedPath);
    assert.equal(noReplay.runtimeState, 'IDLE'); assert.equal(noReplay.pendingInteractions.length, 0);
    const repaired = await history(state.interruptedSessionId);
    assert.deepEqual(repaired.filter(item => item.role !== 'tool').map(item => item.id), unchanged.filter(item => item.role !== 'tool').map(item => item.id));
    const previousIds = new Set(unchanged.map(item => item.id));
    const repairs = repaired.filter(item => !previousIds.has(item.id));
    assert(repairs.every(item => item.role === 'tool' && item.id.startsWith('interrupted-tool-result-')));
    assert(!interruptedFeed.events.slice(resumeStart).some(event => event.type === 'assistant.started'));
    record('interrupted-work-not-replayed-and-old-question-expired');
  } else throw new Error('Unknown phase');
} catch (error) {
  results.push({ name: phase, status: 'FAIL', detail: error.message });
  console.error(`FAIL ${error.message}`); process.exitCode = 1;
} finally {
  for (const ws of sockets) ws.terminate();
  await writeFile(reportPath, JSON.stringify({ phase, timestamp: new Date().toISOString(), results }, null, 2));
}
