import { randomUUID } from 'node:crypto';
import WebSocket from 'ws';
import type { BridgeEvent } from './contracts.js';

/** A failing observer cannot affect the session that produced a fact. */
export class Events {
  private listeners = new Set<(event: BridgeEvent) => void>();
  private sockets = new Set<WebSocket>();
  constructor(private maxBytes: number) {}
  publish(event: BridgeEvent) { for (const listener of this.listeners) { try { listener(event); } catch { /* Observer is isolated. */ } } }
  async attach(ws: WebSocket, sessionId: string, snapshot: () => Promise<unknown>) {
    const streamId = randomUUID(); let seq = 0, buffering = true, alive = true, bytes = 0;
    const pending: BridgeEvent[] = [];
    this.sockets.add(ws);
    const closeSlow = () => {
      if (ws.readyState === WebSocket.OPEN) ws.close(1013, 'resync-required');
      const termination = setTimeout(() => ws.terminate(), 1000); termination.unref();
    };
    const send = (event: BridgeEvent) => {
      if (ws.readyState !== WebSocket.OPEN) return;
      const text = JSON.stringify({ version: '1', streamId, eventSeq: ++seq, ...event });
      if (Buffer.byteLength(text) + ws.bufferedAmount > this.maxBytes) { closeSlow(); return; }
      ws.send(text, error => { if (error) ws.terminate(); });
    };
    const listener = (event: BridgeEvent) => {
      if (event.sessionId !== sessionId) return;
      if (!buffering) { send(event); return; }
      bytes += Buffer.byteLength(JSON.stringify(event));
      if (bytes > this.maxBytes) { closeSlow(); cleanup(); return; }
      pending.push(event);
    };
    const timer = setInterval(() => {
      if (!alive) { ws.terminate(); return; }
      alive = false; if (ws.readyState === WebSocket.OPEN) ws.ping();
    }, 15000); timer.unref();
    const cleanup = () => { clearInterval(timer); this.listeners.delete(listener); this.sockets.delete(ws); pending.length = 0; };
    this.listeners.add(listener);
    ws.on('pong', () => { alive = true; });
    ws.on('close', cleanup); ws.on('error', cleanup);
    ws.on('message', () => ws.close(1008, 'downlink-only'));
    try {
      const state = await snapshot();
      send({ type: 'session.snapshot', sessionId, data: state });
      const boundary = (state as { historyThroughSeq?: number }).historyThroughSeq ?? -1;
      buffering = false;
      for (const event of pending) if (event.sourceSeq === undefined || event.sourceSeq > boundary) send(event);
      pending.length = 0;
    } catch { ws.close(1011, 'snapshot-unavailable'); cleanup(); }
  }
  dispose() {
    for (const ws of this.sockets) { ws.close(1001, 'bridge-stopping'); ws.terminate(); }
    this.sockets.clear(); this.listeners.clear();
  }
}
