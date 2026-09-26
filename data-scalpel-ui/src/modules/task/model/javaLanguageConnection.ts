import {
  AbstractMessageReader, AbstractMessageWriter, createMessageConnection,
  type DataCallback, type Disposable, type Message, type MessageConnection,
} from 'vscode-jsonrpc/browser';
import { requestJson } from '../../../shared/api/http';

class SocketReader extends AbstractMessageReader {
  private listening?: Disposable;
  failureMessage?: string;
  constructor(private socket: WebSocket) { super(); }
  listen(callback: DataCallback): Disposable {
    this.listening?.dispose();
    const receive = (event: MessageEvent<unknown>) => {
      try {
        if (typeof event.data !== 'string' || event.data.length > 2 * 1024 * 1024) throw new Error('Invalid language message');
        const value: unknown = JSON.parse(event.data);
        if (!value || typeof value !== 'object' || !('jsonrpc' in value) || value.jsonrpc !== '2.0') throw new Error('Invalid JSON RPC');
        callback(value as Message);
      } catch (error) { this.fireError(error); this.socket.close(); }
    };
    const close = (event: CloseEvent) => {
      // Use stable close codes, never expose arbitrary upstream exception text.
      this.failureMessage = event.code === 4001 ? '连接需要重新鉴权'
        : event.code === 1013 ? '语义服务繁忙或暂不可用，请稍后重试'
          : event.code === 1008 ? '语义连接被拒绝，请重新连接'
            : event.code === 1011 ? '语义服务连接异常，请重新连接'
              : '语义连接已断开，请检查网络或重新连接';
      this.fireClose();
    };
    this.socket.addEventListener('message', receive);
    this.socket.addEventListener('close', close);
    this.listening = { dispose: () => { this.socket.removeEventListener('message', receive); this.socket.removeEventListener('close', close); } };
    return this.listening;
  }
  override dispose() { this.listening?.dispose(); this.listening = undefined; super.dispose(); }
}

class SocketWriter extends AbstractMessageWriter {
  constructor(private socket: WebSocket) { super(); }
  async write(message: Message): Promise<void> {
    const value = JSON.stringify(message);
    if (this.socket.readyState !== WebSocket.OPEN || this.socket.bufferedAmount > 1024 * 1024) throw new Error('Language connection unavailable');
    if (new TextEncoder().encode(value).length > 2 * 1024 * 1024) throw new Error('Language message too large');
    this.socket.send(value);
  }
  end() { this.socket.close(); }
}

export interface JavaLanguageTransport { connection: MessageConnection; readonly failureMessage?: string; close(): void }

/** A per-editor identity; getRandomValues also works on ordinary intranet HTTP pages. */
export const newEditorSessionId = (): string => {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
};

export async function connectJavaLanguage(taskId: string, editorSessionId: string, signal: AbortSignal): Promise<JavaLanguageTransport> {
  const ticket = await requestJson<{ path: string; ticket: string }>(`/v1/tasks/${taskId}/java-language-tickets`, {
    method: 'POST', body: JSON.stringify({ editorSessionId }), signal,
  });
  if (signal.aborted) throw new DOMException('Aborted', 'AbortError');
  const api = import.meta.env.VITE_API_BASE_URL ?? '/api';
  const url = new URL(ticket.path, new URL(api, window.location.href));
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  const socket = new WebSocket(url, ['datascalpel-java', ticket.ticket]);
  const abort = () => socket.close();
  signal.addEventListener('abort', abort, { once: true });
  try {
    await new Promise<void>((resolve, reject) => {
      const timer = window.setTimeout(() => { socket.close(); reject(new Error('代码提示连接超时')); }, 15_000);
      const done = (error?: Error) => {
        clearTimeout(timer); socket.onopen = null; socket.onerror = null; socket.onclose = null;
        if (error) reject(error); else resolve();
      };
      socket.onopen = () => done();
      socket.onerror = () => done(new Error('代码提示连接失败'));
      socket.onclose = () => done(new Error('代码提示服务未就绪或容量已满'));
    });
    const reader = new SocketReader(socket);
    const writer = new SocketWriter(socket);
    const connection = createMessageConnection(reader, writer);
    let closed = false;
    return { connection, get failureMessage() { return reader.failureMessage; }, close: () => {
      if (closed) return;
      closed = true;
      signal.removeEventListener('abort', abort);
      reader.dispose(); socket.close();
      // jsonrpc 8 registers pending requests after awaiting write(). Let the already
      // completed WebSocket write settle before disposing, so those promises reject too.
      queueMicrotask(() => { connection.dispose(); writer.dispose(); });
    } };
  } catch (error) { signal.removeEventListener('abort', abort); socket.close(); throw error; }
}
