import test from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { Events } from '../dist/events.js';

class Socket extends EventEmitter {
  readyState = 1; bufferedAmount = 0; sent = []; closed;
  send(data, callback) { this.sent.push(JSON.parse(data)); callback?.(); }
  close(code) { this.closed = code; this.readyState = 3; this.emit('close'); }
  terminate() { this.readyState = 3; this.emit('close'); }
  ping() {}
}

test('snapshot boundary keeps concurrent completion without replaying old facts', async () => {
  const events = new Events(10000), socket = new Socket();
  let finish;
  const pending = events.attach(socket, 'A', () => new Promise(resolve => { finish = resolve; }));
  events.publish({ type: 'assistant.completed', sessionId: 'A', sourceSeq: 5, data: {} });
  events.publish({ type: 'run.completed', sessionId: 'A', sourceSeq: 6, data: {} });
  events.publish({ type: 'run.completed', sessionId: 'B', sourceSeq: 7, data: {} });
  finish({ historyThroughSeq: 5 }); await pending;
  assert.deepEqual(socket.sent.map(e => e.type), ['session.snapshot', 'run.completed']);
  assert.deepEqual(socket.sent.map(e => e.eventSeq), [1, 2]);
  events.dispose();
});

test('slow consumer closes with resync and does not block another subscriber', async () => {
  const events = new Events(500), slow = new Socket(), fast = new Socket();
  await events.attach(slow, 'A', async () => ({}));
  await events.attach(fast, 'A', async () => ({}));
  slow.bufferedAmount = 500;
  events.publish({ type: 'assistant.delta', sessionId: 'A', data: { text: 'hello' } });
  assert.equal(slow.closed, 1013);
  assert.equal(fast.sent.at(-1).type, 'assistant.delta');
  fast.close(1000);
  assert.doesNotThrow(() => events.publish({ type: 'run.completed', sessionId: 'A', data: {} }));
  events.dispose();
});

test('downlink connection rejects commands and oversized snapshot is explicit', async () => {
  const events = new Events(300), commands = new Socket(), big = new Socket();
  await events.attach(commands, 'A', async () => ({}));
  commands.emit('message', 'run'); assert.equal(commands.closed, 1008);
  await events.attach(big, 'A', async () => ({ body: 'x'.repeat(1000) }));
  assert.equal(big.closed, 1013); assert.equal(big.sent.length, 0);
  events.dispose();
});
