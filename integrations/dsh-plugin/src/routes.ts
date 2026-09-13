import { createHash, timingSafeEqual } from 'node:crypto';
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import type { IncomingMessage, ServerResponse } from 'node:http';
import type { Context } from '@deepseek-ai/cordis';
import '@deepseek-ai/dsh-host-webserver';
import { WebSocketServer } from 'ws';
import type { Config } from './config.js';
import { answerInput, attachmentMessageInput, BridgeError, createInput, emptyInput, messageInput, paging, problem, uuid, fail } from './contracts.js';
import { MAX_ATTACHMENT_REQUEST_BYTES } from './attachments.js';
import type { Sessions } from './sessions.js';
import type { WorkspaceFiles } from './files.js';
import { z } from 'zod';
import type { Events } from './events.js';

export function authenticator(secret: string) {
  const expected = createHash('sha256').update(`Bearer ${secret}`).digest();
  return (req: IncomingMessage) => {
    const count = req.rawHeaders.filter((_, i) => i % 2 === 0).filter(key => key.toLowerCase() === 'authorization').length;
    const actual = createHash('sha256').update(req.headers.authorization ?? '').digest();
    if (count !== 1 || !timingSafeEqual(expected, actual)) fail(401, 'BRIDGE_UNAUTHORIZED', 'Bridge 访问凭据无效。');
    if (req.headers.origin || req.headers['sec-fetch-site'] === 'cross-site') fail(403, 'BRIDGE_ORIGIN_REJECTED', '第一阶段仅支持内部脚本访问。');
  };
}

export async function readBody(req: IncomingMessage, maxBytes: number): Promise<unknown> {
  const size = Number(req.headers['content-length'] ?? 0);
  if (size > maxBytes) fail(413, 'BRIDGE_REQUEST_TOO_LARGE', '请求过大。');
  const chunks: Buffer[] = []; let length = 0;
  // Read with events rather than async iterator: an oversized body must still receive ProblemDetail.
  return new Promise((resolve, reject) => {
    const cleanup = () => { req.off('data', data); req.off('end', end); req.off('error', error); req.off('aborted', aborted); };
    const error = () => { cleanup(); reject(new BridgeError(400, 'BRIDGE_ARGUMENT_INVALID', '请求体未完整读取。')); };
    const aborted = () => error();
    const data = (chunk: Buffer) => {
      length += chunk.length;
      if (length > maxBytes) { cleanup(); req.pause(); reject(new BridgeError(413, 'BRIDGE_REQUEST_TOO_LARGE', '请求过大。')); return; }
      chunks.push(chunk);
    };
    const end = () => {
      cleanup();
      if (!length) { resolve({}); return; }
      if (!req.headers['content-type']?.toLowerCase().startsWith('application/json')) {
        reject(new BridgeError(415, 'BRIDGE_CONTENT_TYPE_UNSUPPORTED', '请求体必须为 JSON。')); return;
      }
      try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8'))); }
      catch { reject(new BridgeError(400, 'BRIDGE_ARGUMENT_INVALID', '请求 JSON 格式无效。')); }
    };
    req.on('data', data); req.on('end', end); req.on('error', error); req.on('aborted', aborted);
  });
}
export function json(res: ServerResponse, status: number, body: unknown, error = false) {
  if (res.destroyed || res.writableEnded) return;
  res.writeHead(status, { 'Content-Type': error ? 'application/problem+json' : 'application/json', 'Cache-Control': 'no-store',
    ...(error ? { Connection: 'close' } : {}), ...(status === 401 ? { 'WWW-Authenticate': 'Bearer' } : {}) });
  res.end(JSON.stringify(body));
}

export function registerRoutes(ctx: Context, config: Config, secret: string, sessions: Sessions, events: Events, prefix = '/bridge/v1', files?: WorkspaceFiles, guard: () => void = () => {}, provision?: (input: unknown) => Promise<void>) {
  const require = createRequire(import.meta.url);
  const pluginVersion = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8')).version as string;
  const dshVersion = require('@deepseek-ai/dsh/package.json').version as string;
  if (dshVersion !== '0.1.5-rc.1') fail(503, 'BRIDGE_VERSION_UNSUPPORTED', '当前 DSH 版本尚未适配。');
  const authenticate = authenticator(secret);
  const managed = prefix.startsWith('/bridge/v3/');
  const wss = new WebSocketServer({ noServer: true, maxPayload: 1024, perMessageDeflate: false });
  const unHttp = ctx.webServer.register({ kind: 'prefix', path: prefix, handler: async (req, res) => {
    try {
      authenticate(req);
      const url = new URL(req.url ?? '', 'http://bridge.invalid');
      if (provision && req.method === 'POST' && url.pathname === `${prefix}/identity/actions/provision` && !url.search) {
        await provision(await readBody(req, config.maxRequestBytes)); return json(res, 200, { provisioned: true });
      }
      guard();
      const parts = url.pathname.slice(prefix.length).split('/').filter(Boolean);
      const method = req.method;
      if (method !== 'GET' && method !== 'POST') fail(405, 'BRIDGE_METHOD_NOT_ALLOWED', '仅支持 GET 和 POST。');
      const path = parts.join('/');
      const list = path === 'sessions' || /^sessions\/[^/]+\/messages$/.test(path);
      const allowedQuery = method === 'GET' && list ? ['offset', 'limit', ...(managed ? (path === 'sessions' ? ['query', 'archived'] : ['mode', 'beforeSeq']) : [])] : method === 'GET' && files && path === 'files' ? ['path'] : [];
      for (const key of url.searchParams.keys()) {
        if (!allowedQuery.includes(key) || url.searchParams.getAll(key).length !== 1) fail(400, 'BRIDGE_ARGUMENT_INVALID', '不支持或重复的查询参数。');
      }
      const getOnly = path === 'capabilities' || /^sessions\/[^/]+$/.test(path);
      const postOnly = path === 'workspaces/actions/ensure' || /^sessions\/[^/]+\/actions\/(resume|cancel)$/.test(path)
        || /^sessions\/[^/]+\/interactions\/[^/]+\/actions\/respond$/.test(path);
      if (getOnly && method !== 'GET' || postOnly && method !== 'POST') fail(405, 'BRIDGE_METHOD_NOT_ALLOWED', '此接口不支持该 HTTP 方法。');
      const body = method === 'POST' ? await readBody(req, managed && /^sessions\/[^/]+\/attachments$/.test(path)
        ? MAX_ATTACHMENT_REQUEST_BYTES : config.maxRequestBytes) : {};
      const at = (m: string, path: string) => method === m && parts.join('/') === path;
      if (files && at('GET', 'files')) return json(res, 200, await files.read(url.searchParams.get('path') ?? ''));
      if (files && at('POST', 'files')) {
        const input = z.object({ path: z.string(), text: z.string() }).strict().parse(body);
        return json(res, 200, await files.write(input.path, input.text));
      }
      if (at('GET', 'capabilities')) {
        await sessions.ready();
        return json(res, 200, { protocolVersion: '1', pluginVersion, dshVersion, ready: true,
          capabilities: ['workspace', 'sessions', 'text-chat', 'events', 'user-questions', 'resume', 'cancel'],
          components: { storage: 'READY', preset: 'READY', modelConfiguration: 'READY' } });
      }
      if (at('POST', 'workspaces/actions/ensure')) {
        emptyInput.parse(body); const value = await sessions.workspaces.ensure();
        return json(res, 200, { workspaceId: value.id, path: value.path, title: value.title });
      }
      if (at('POST', 'sessions')) {
        const result = await sessions.create(createInput.parse(body).clientSessionId);
        return json(res, result.status, result.body);
      }
      if (at('GET', 'sessions')) { if (url.searchParams.has('archived') && !['true','false'].includes(url.searchParams.get('archived')!)) fail(400,'BRIDGE_ARGUMENT_INVALID','归档筛选值无效。');
        if ((url.searchParams.get('query') ?? '').length > 100) fail(400,'BRIDGE_ARGUMENT_INVALID','查询词过长。');
        const p = paging(new URLSearchParams([...url.searchParams].filter(([k]) => ['offset','limit'].includes(k)))); return json(res, 200, await sessions.list(p.offset, p.limit, url.searchParams.get('query') ?? '', url.searchParams.get('archived') === 'true')); }
      if (parts[0] === 'sessions' && parts[1]) {
        const id = parts[1];
        sessions.storage.own(id);
        uuid.parse(id);
        if (managed && parts[2] === 'attachments') {
          if (parts.length === 3 && method === 'POST') return json(res, 201, await sessions.uploadAttachment(id, body));
          if (parts.length === 4 && method === 'GET') return json(res, 200, await sessions.attachments.download(id, uuid.parse(parts[3])));
        }
        if (parts.length === 2 && method === 'GET') return json(res, 200, await sessions.view(id));
        if (parts.length === 3 && parts[2] === 'messages') {
          if (method === 'GET') {
            if (managed && (url.searchParams.has('mode') || url.searchParams.has('beforeSeq'))) {
              if (url.searchParams.get('mode') !== 'cursor' || url.searchParams.has('offset')) fail(400,'BRIDGE_ARGUMENT_INVALID','游标模式不能与 offset 混用。');
              const p = paging(new URLSearchParams([...url.searchParams].filter(([k]) => k === 'limit')), true);
              const raw = url.searchParams.get('beforeSeq');
              if (raw !== null && (!/^(0|[1-9][0-9]*)$/.test(raw) || !Number.isSafeInteger(Number(raw)))) fail(400,'BRIDGE_ARGUMENT_INVALID','消息游标无效。');
              return json(res,200,await sessions.recentMessages(id, raw === null ? undefined : Number(raw), p.limit));
            }
            const p = paging(url.searchParams, true); return json(res, 200, await sessions.messages(id, p.offset, p.limit)); }
          if (managed) {
            const input = attachmentMessageInput.parse(body);
            return json(res, 202, await sessions.send(id, input.clientMessageId, input.text, input.attachmentIds));
          }
          const input = messageInput.parse(body);
          return json(res, 202, await sessions.send(id, input.clientMessageId, input.text));
        }
        if (parts.length === 4 && parts[2] === 'actions' && method === 'POST') {
          if (managed && parts[3] === 'update') {
            const input = z.object({ title: z.string().trim().min(1).max(100) }).strict().parse(body);
            return json(res,200,await sessions.update(id,input.title));
          }
          emptyInput.parse(body);
          if (managed && ['archive','restore'].includes(parts[3]!)) return json(res,200,await sessions.archive(id,parts[3] === 'archive'));
          if (parts[3] === 'resume') return json(res, 200, await sessions.resume(id));
          if (parts[3] === 'cancel') { const result = await sessions.cancel(id); return json(res, result.status, result.body); }
        }
        if (parts.length === 6 && parts[2] === 'interactions' && parts[4] === 'actions' && parts[5] === 'respond' && method === 'POST') {
          return json(res, 200, await sessions.respond(id, uuid.parse(parts[3]), answerInput.parse(body)));
        }
      }
      fail(404, 'BRIDGE_ROUTE_NOT_FOUND', 'Bridge 路由不存在。');
    } catch (error) { const value = problem(error); json(res, value.status, value, true); }
  } });
  const unWs = ctx.webServer.registerUpgrade({ path: `${prefix}/events`, handler: async (req, socket, head) => {
    try {
      authenticate(req); guard();
      const url = new URL(req.url ?? '', 'http://bridge.invalid');
      if ([...url.searchParams.keys()].some(k => k !== 'sessionId') || url.searchParams.getAll('sessionId').length !== 1) fail(400, 'BRIDGE_ARGUMENT_INVALID', '不支持此订阅参数。');
      const id = url.searchParams.get('sessionId');
      if (!id) fail(400, 'BRIDGE_ARGUMENT_INVALID', '缺少会话标识。');
      sessions.storage.own(id);
      uuid.parse(id);
      wss.handleUpgrade(req, socket, head, ws => { void events.attach(ws, id, () => sessions.view(id)); });
    } catch (error) {
      const value = problem(error), body = JSON.stringify(value);
      socket.end(`HTTP/1.1 ${value.status} ${value.code}\r\nContent-Type: application/problem+json\r\nContent-Length: ${Buffer.byteLength(body)}\r\nConnection: close\r\n\r\n${body}`);
    }
  } });
  return () => { unHttp(); unWs(); events.dispose(); wss.close(); };
}
