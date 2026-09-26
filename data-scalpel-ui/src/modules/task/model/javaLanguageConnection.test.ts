import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { connectJavaLanguage, newEditorSessionId } from './javaLanguageConnection';

const api = vi.hoisted(() => ({ requestJson: vi.fn() }));
vi.mock('../../../shared/api/http', () => api);

class TestSocket extends EventTarget {
  static OPEN = 1;
  static instances: TestSocket[] = [];
  readyState = 0;
  bufferedAmount = 0;
  sent: string[] = [];
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  onclose: (() => void) | null = null;
  constructor(readonly url: URL, readonly protocols: string[]) { super(); TestSocket.instances.push(this); }
  send(value: string) { this.sent.push(value); }
  open() { this.readyState = 1; this.onopen?.(); }
  close(code = 1000, reason = '') { if (this.readyState === 3) return; this.readyState = 3; this.onclose?.(); this.dispatchEvent(new CloseEvent('close', { code, reason })); }
  receive(value: string) { this.dispatchEvent(new MessageEvent('message', { data: value })); }
}

beforeEach(() => {
  vi.stubGlobal('WebSocket', TestSocket);
  TestSocket.instances = [];
  api.requestJson.mockReset().mockResolvedValue({ path: '/api/v1/java-language/connection', ticket: 'ticket.one-use' });
});
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); });

async function connect() {
  const controller = new AbortController();
  const pending = connectJavaLanguage('task', 'editor', controller.signal);
  await vi.waitFor(() => expect(TestSocket.instances).toHaveLength(1));
  const socket = TestSocket.instances[0];
  socket.open();
  return { transport: await pending, socket, controller };
}

describe('Java language transport', () => {
  it.each([[4001, '重新鉴权'], [1013, '繁忙'], [1008, '被拒绝'], [1011, '连接异常'], [1006, '网络']])(
    'preserves a safe close reason for code %s', async (code, message) => {
      const { transport, socket } = await connect();
      const closed = vi.fn();
      transport.connection.onClose(() => closed(transport.failureMessage));
      transport.connection.listen();
      socket.close(Number(code), 'internal path or token must not be shown');
      expect(closed).toHaveBeenCalledWith(expect.stringContaining(String(message)));
      expect(transport.failureMessage).not.toContain('internal path');
      transport.close();
    });

  it('uses a same-admin connection with a one-use subprotocol, never a URL credential', async () => {
    const { transport, socket } = await connect();
    expect(socket.url.pathname).toBe('/api/v1/java-language/connection');
    expect(socket.url.search).toBe('');
    expect(socket.protocols).toEqual(['datascalpel-java', 'ticket.one-use']);
    expect(api.requestJson.mock.calls[0][0]).toBe('/v1/tasks/task/java-language-tickets');
    transport.close();
    expect(socket.readyState).toBe(3);
  });

  it('correlates JSON-RPC requests and disposes outstanding work when closed', async () => {
    const { transport, socket } = await connect();
    transport.connection.listen();
    const result = transport.connection.sendRequest('initialize', {});
    await vi.waitFor(() => expect(socket.sent.length).toBe(1));
    const sent = JSON.parse(socket.sent[0]) as { id: number };
    socket.receive(JSON.stringify({ jsonrpc: '2.0', id: sent.id, result: { capabilities: {} } }));
    await expect(result).resolves.toEqual({ capabilities: {} });
    const incomplete = transport.connection.sendRequest('textDocument/hover', {});
    const rejection = expect(incomplete).rejects.toThrow();
    transport.close();
    await rejection;
  });

  it('does not open a socket after the editor is disposed during ticket acquisition', async () => {
    const controller = new AbortController();
    controller.abort();
    await expect(connectJavaLanguage('task', 'editor', controller.signal)).rejects.toThrow();
    expect(TestSocket.instances).toHaveLength(0);
  });

  it('closes on malformed language messages instead of forwarding them to Monaco', async () => {
    const { transport, socket } = await connect();
    transport.connection.onError(() => undefined);
    transport.connection.listen();
    socket.receive('not JSON');
    expect(socket.readyState).toBe(3);
    transport.close();
  });

  it('generates distinct editor identities on plain HTTP without randomUUID', () => {
    const first = newEditorSessionId();
    expect(first).toMatch(/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/);
    expect(newEditorSessionId()).not.toBe(first);
  });
});
