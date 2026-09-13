import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { WebSocket, WebSocketServer } from 'ws';
import { owners, Logins, cookieId, bridgePath } from './auth.mjs';
import { identityHandler } from './mcp.mjs';

const port = Number(process.env.PORT ?? 13081);
const origin = process.env.PLAYGROUND_ORIGIN ?? `http://127.0.0.1:${port}`;
const bridge = new URL(process.env.DSH_BRIDGE_URL ?? 'http://dsh:13080');
const bridgeSecret = process.env.DATASCALPEL_DSH_BRIDGE_TOKEN;
const passwords = Object.fromEntries(owners.map(o => [o, process.env[`DSH_PLAYGROUND_${o.toUpperCase()}_PASSWORD`]]));
const mcpTokens = Object.fromEntries(owners.map(o => [o, process.env[`DSH_PLAYGROUND_MCP_${o.toUpperCase()}_TOKEN`]]));
if (!bridgeSecret || owners.some(o => !passwords[o] || passwords[o].length < 12 || !mcpTokens[o] || mcpTokens[o].length < 32)
  || passwords.alice === passwords.bob || mcpTokens.alice === mcpTokens.bob) throw new Error('Playground requires independent credentials');
const logins = new Logins(passwords);
const sweep = setInterval(() => logins.sweep(), 15000); sweep.unref();
export function json(res, status, body, headers = {}) {
  if (res.writableEnded || res.destroyed) return;
  res.writeHead(status, { 'content-type': status >= 400 ? 'application/problem+json' : 'application/json', 'cache-control': 'no-store', ...headers });
  res.end(JSON.stringify(status >= 400 ? { type: 'about:blank', status, title: body.code, ...body } : body));
}
export function readJson(req) {
  return new Promise((resolve, reject) => {
    let bytes = 0; const parts = [];
    req.on('data', b => { bytes += b.length; if (bytes <= 1048576) parts.push(b); });
    req.on('end', () => {
      if (bytes > 1048576) return reject(Object.assign(new Error('请求不能超过 1 MiB。'), { status: 413 }));
      if (bytes && !req.headers['content-type']?.startsWith('application/json')) return reject(Object.assign(new Error('需要 JSON 请求。'), { status: 415 }));
      try { resolve(bytes ? JSON.parse(Buffer.concat(parts).toString()) : {}); } catch { reject(Object.assign(new Error('无效 JSON。'), { status: 400 })); }
    });
    req.on('error', reject); req.on('aborted', () => reject(new Error('请求中断。')));
  });
}
function sameOrigin(req) {
  return (!req.headers.origin || req.headers.origin === origin) && req.headers['sec-fetch-site'] !== 'cross-site';
}
const loginAttempts = new Map();
function rateLimit(req) {
  const key = req.socket.remoteAddress, now = Date.now();
  for (const [ip, v] of loginAttempts) if (v.until < now) loginAttempts.delete(ip);
  let entry = loginAttempts.get(key);
  if (!entry) { entry = { count: 0, until: now + 60000 }; loginAttempts.set(key, entry); }
  return ++entry.count > 20;
}
const assets = new Map([['/', ['index.html', 'text/html; charset=utf-8']], ['/app.js', ['app.js', 'text/javascript']], ['/style.css', ['style.css', 'text/css']]]);
const server = createServer(async (req, res) => {
  try {
    const url = new URL(req.url, origin), path = url.pathname;
    if (!sameOrigin(req)) return json(res, 403, { code: 'ORIGIN_REJECTED', detail: '请从验证台地址访问。' });
    if (req.method === 'GET' && path === '/health') return json(res, 200, { ready: true });
    if (req.method === 'GET' && assets.has(path)) {
      const [file, type] = assets.get(path);
      res.writeHead(200, { 'content-type': type, 'cache-control': 'no-store', 'x-content-type-options': 'nosniff',
        'content-security-policy': "default-src 'self'; connect-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'" });
      return res.end(await readFile(new URL(`../public/${file}`, import.meta.url)));
    }
    if (req.method === 'POST' && path === '/api/login') {
      if (rateLimit(req)) return json(res, 429, { code: 'LOGIN_RATE_LIMIT', detail: '尝试过于频繁，请稍后重试。' });
      const input = await readJson(req);
      if (typeof input.username !== 'string' || typeof input.password !== 'string' || input.password.length > 512) return json(res, 400, { code: 'LOGIN_INVALID' });
      const id = logins.login(input.username, input.password);
      if (!id) return json(res, 401, { code: 'LOGIN_INVALID', detail: '账号或密码错误。' });
      return json(res, 200, { user: input.username }, { 'set-cookie': `dsh_playground=${id}; HttpOnly; SameSite=Strict; Path=/; Max-Age=43200${origin.startsWith('https:') ? '; Secure' : ''}` });
    }
    const id = cookieId(req), login = logins.get(id);
    if (!login) return json(res, 401, { code: 'LOGIN_REQUIRED', detail: '请登录验证台。' });
    if (req.method === 'GET' && path === '/api/me') return json(res, 200, { user: login.owner, boundary: 'controlled-tools' });
    if (req.method === 'POST' && path === '/api/logout') {
      logins.logout(id); return json(res, 200, {}, { 'set-cookie': 'dsh_playground=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0' });
    }
    const targetPath = path.startsWith('/api/bridge/') && bridgePath(path.slice('/api/bridge'.length), login.owner);
    if (!targetPath || !['GET', 'POST'].includes(req.method)) return json(res, 404, { code: 'NOT_FOUND' });
    const body = req.method === 'POST' ? await readJson(req) : undefined;
    const upstream = await fetch(new URL(targetPath + url.search, bridge), { method: req.method, redirect: 'error',
      headers: { Authorization: `Bearer ${bridgeSecret}`, 'Content-Type': 'application/json' },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }), signal: AbortSignal.timeout(45000) });
    json(res, upstream.status, await upstream.json());
  } catch (error) {
    json(res, error.status ?? 502, { code: error.status ? 'REQUEST_INVALID' : 'BRIDGE_UNAVAILABLE',
      detail: error.status ? error.message : 'DSH 连接或结果读取失败；已提交的操作可能已执行，请刷新状态，勿自动重发。' });
  }
});
server.requestTimeout = 60000;
const wss = new WebSocketServer({ noServer: true, maxPayload: 1024, perMessageDeflate: false });
server.on('upgrade', async (req, socket, head) => {
  const login = logins.get(cookieId(req)), url = new URL(req.url, origin), sessionId = url.searchParams.get('sessionId');
  if (!login || req.headers.origin !== origin || url.pathname !== '/events' || !/^[0-9a-f-]{36}$/.test(sessionId ?? '')
    || [...url.searchParams.keys()].some(k => k !== 'sessionId') || url.searchParams.getAll('sessionId').length !== 1) {
    socket.end('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n'); return;
  }
  const target = new URL(`/bridge/v2/users/${login.owner}/events?sessionId=${sessionId}`, bridge); target.protocol = target.protocol === 'https:' ? 'wss:' : 'ws:';
  const upstream = new WebSocket(target, { headers: { Authorization: `Bearer ${bridgeSecret}` }, maxPayload: 1048576, handshakeTimeout: 10000 });
  const pending = []; let client;
  const fail = () => { upstream.terminate(); if (client) client.close(1011, 'resync-required'); else socket.end('HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n'); };
  upstream.on('error', fail);
  upstream.on('unexpected-response', (_request, response) => { response.resume(); socket.end(`HTTP/1.1 ${response.statusCode} Rejected\r\nConnection: close\r\n\r\n`); upstream.terminate(); });
  socket.on('error', () => upstream.terminate()); socket.on('close', () => upstream.terminate());
  upstream.on('message', data => {
    if (!client) { if (pending.length > 20) fail(); else pending.push(data); return; }
    if (client.bufferedAmount + data.length > 1048576) { client.close(1013, 'resync-required'); upstream.close(); }
    else if (client.readyState === WebSocket.OPEN) client.send(data.toString());
  });
  upstream.once('open', () => {
    if (!logins.get(cookieId(req)) || socket.destroyed) return upstream.terminate();
    wss.handleUpgrade(req, socket, head, ws => {
      client = ws; login.sockets.add(ws);
      for (const data of pending) ws.send(data.toString()); pending.length = 0;
      ws.on('close', () => { login.sockets.delete(ws); upstream.close(); });
      ws.on('error', () => upstream.terminate());
      ws.on('message', () => ws.close(1008, 'downlink-only'));
    });
  });
  upstream.on('close', () => client?.close(1012, 'resync-required'));
});
const mcp = createServer(identityHandler(mcpTokens, readJson, json));
mcp.requestTimeout = 15000;
mcp.listen(Number(process.env.MCP_PORT ?? 13082), '0.0.0.0');
server.listen(port, '0.0.0.0', () => console.log(`DSH playground listening on ${port}; identity MCP on internal listener`));
for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => {
  for (const id of logins.sessions.keys()) logins.logout(id);
  server.close(); mcp.close(); wss.close();
  setTimeout(() => process.exit(0), 1500).unref();
});
