import type { Context } from '@deepseek-ai/cordis';
import { apply as mountMcp } from '@deepseek-ai/dsh-mcp-client';
import { z } from 'zod';
import { accessSecret, type Config } from './config.js';
import { authenticator, json, readBody, registerRoutes } from './routes.js';
import { fail, problem, Serial, uuid } from './contracts.js';
import { BridgeStorage } from './storage.js';
import { Workspaces } from './workspaces.js';
import { WorkspaceFiles } from './files.js';
import { Sessions } from './sessions.js';
import { Events } from './events.js';
import { MAX_ATTACHMENT_BYTES, MAX_MESSAGE_ATTACHMENTS } from './attachments.js';

const credentialInput = z.object({ tokenId: uuid, revision: z.number().int().positive(),
  secret: z.string().regex(/^dssmcp_[A-Za-z0-9_-]{43}$/) }).strict();
export class AgentCapacity {
  private count = 0;
  constructor(private limit: number) {}
  reserve = () => {
    if (this.count >= this.limit) fail(503, 'BRIDGE_CAPACITY_EXCEEDED', '全局活跃 Agent 数量已达到上限。');
    this.count++; let released = false;
    return () => { if (!released) { released = true; this.count--; } };
  };
}
export class IdentityLease {
  private until = Date.now() + 10000;
  private revoked = false;
  renew() { if (!this.revoked) this.until = Date.now() + 10000; }
  revoke() { this.revoked = true; }
  get expired() { return this.revoked || Date.now() >= this.until; }
  require() { if (this.expired) fail(401, 'BRIDGE_IDENTITY_EXPIRED', 'Admin 用户授权已失效。'); }
}
interface UserScope {
  credential: z.infer<typeof credentialInput>; lease: IdentityLease;
  sessions: Sessions; events: Events; storage: BridgeStorage; remove: () => void;
}

/** Trusted Admin is the only source of identity. Credentials live in these scopes, never storage. */
export async function mountAdmin(ctx: Context, config: Config) {
  const secret = accessSecret({ ...config, accessTokenEnv: config.adminAccessTokenEnv });
  const authenticate = authenticator(secret), users = new Map<string, UserScope>();
  const serial = new Serial(), capacity = new AgentCapacity(config.adminMaxActiveSessions);
  const removers: (() => void)[] = []; let stopping = false;
  const ready = async () => {
    try {
      await ctx.agentPresets.resolve('datascalpel-admin');
      await ctx.llm.resolveCallConfig({ provider: config.provider, model: config.model });
    } catch { fail(503, 'BRIDGE_NOT_READY', 'Admin Preset 或模型配置未就绪。'); }
  };
  const dispose = async (id: string, scope: UserScope) => {
    // Stop accepting and request cancellation synchronously before awaiting native disposal.
    scope.lease.revoke(); scope.sessions.revoke(); scope.events.dispose(); scope.remove(); users.delete(id);
    try { await scope.sessions.dispose(); } finally { await scope.storage.close(); }
  };
  const provision = async (id: string, input: unknown) => serial.run(async () => {
    if (stopping) fail(503, 'BRIDGE_NOT_READY', '插件正在停止。');
    const credential = credentialInput.parse(input), existing = users.get(id);
    if (existing?.lease.expired) { await dispose(id, existing); }
    else if (existing) {
      if (existing.credential.tokenId !== credential.tokenId || existing.credential.revision !== credential.revision
        || existing.credential.secret !== credential.secret) fail(409, 'BRIDGE_CREDENTIAL_CHANGED', '托管凭据发生变化，请先释放原用户运行状态。');
      existing.lease.renew(); return;
    }
    await ready();
    const userConfig = { ...config, workspaceRoot: `/workspace/datascalpel-users/${id}`, presetId: 'datascalpel-admin' };
    const storage = await BridgeStorage.open(ctx, `admin_${id.replaceAll('-', '')}`);
    const events = new Events(config.eventBufferBytes), lease = new IdentityLease();
    const files = new WorkspaceFiles(userConfig.workspaceRoot);
    const sessions: Sessions = new Sessions(ctx, userConfig, storage, new Workspaces(ctx, userConfig.workspaceRoot),
      event => events.publish(event), async (agentCtx, record) => {
        lease.require();
        const serverName = `system_${record.sessionId.replaceAll('-', '').slice(0, 24)}`;
        await mountMcp(agentCtx, { transport: 'streamable-http', serverName, url: config.adminMcpUrl,
          headers: { Authorization: `Bearer ${credential.secret}` }, toolCallTimeoutMs: 65000, failOnStartupError: true });
        return [...files.mount(agentCtx), ...sessions.attachments.mount(agentCtx, record.sessionId),
          ...['api_search', 'api_describe', 'api_invoke'].map(name => `mcp__${serverName}__${name}`)];
      }, capacity.reserve);
    try {
      await sessions.initializeMetadata();
      const remove = registerRoutes(ctx, userConfig, secret, sessions, events, `/bridge/v3/users/${id}`, undefined, () => lease.require(), input => provision(id, input));
      users.set(id, { credential, lease, sessions, events, storage, remove });
    } catch (error) { await sessions.dispose(); await storage.close(); throw error; }
  });
  const unHttp = ctx.webServer.register({ kind: 'prefix', path: '/bridge/v3', handler: async (req, res) => {
    try {
      authenticate(req);
      const url = new URL(req.url ?? '', 'http://bridge.invalid');
      if (url.search) fail(400, 'BRIDGE_ARGUMENT_INVALID', '不支持查询参数。');
      if (req.method === 'GET' && url.pathname === '/bridge/v3/capabilities') {
        await ready();
        const model = await ctx.llm.resolveModelInfo(config.provider, config.model);
        return json(res, 200, { enabled: true, ready: true, protocolVersion: '3', pluginVersion: '0.5.0', dshVersion: '0.1.5-rc.1',
          capabilities: ['personal-workspace', 'sessions', 'system-mcp', 'events', 'user-questions', 'resume', 'cancel', 'session-management', 'history-cursor', 'chat-attachments'],
          attachments: { maxFileBytes: MAX_ATTACHMENT_BYTES, maxPerMessage: MAX_MESSAGE_ATTACHMENTS,
            imageSupported: model.inputModalities ? model.inputModalities.includes('image') : null,
            extensions: ['.xlsx','.xls','.csv','.tsv','.txt','.md','.json','.yaml','.yml','.png','.jpg','.jpeg','.webp','.gif'] },
          maxActiveSessions: config.adminMaxActiveSessions, maxActiveSessionsPerUser: config.maxActiveSessions });
      }
      if (req.method === 'GET' && url.pathname === '/bridge/v3/active-users') return json(res, 200, { userIds: [...users.keys()] });
      if (req.method !== 'POST') fail(404, 'BRIDGE_ROUTE_NOT_FOUND', 'Bridge 路由不存在。');
      const body = await readBody(req, config.maxRequestBytes);
      if (url.pathname === '/bridge/v3/actions/renew-leases') {
        const input = z.object({ userIds: z.array(uuid).max(10000) }).strict().parse(body);
        await serial.run(async () => {
          const enabled = new Set(input.userIds), stale: Promise<void>[] = [];
          for (const [id, scope] of users) { if (enabled.has(id)) scope.lease.renew(); else stale.push(dispose(id, scope)); }
          await Promise.all(stale);
        });
        return json(res, 200, { renewed: true });
      }
      const match = /^\/bridge\/v3\/users\/([a-f0-9-]+)\/identity\/actions\/provision$/.exec(url.pathname);
      if (!match) fail(404, 'BRIDGE_ROUTE_NOT_FOUND', 'Bridge 路由不存在。');
      const id = uuid.parse(match[1]);
      await provision(id, body); return json(res, 200, { provisioned: true });
    } catch (error) { const value = problem(error); json(res, value.status, value, true); }
  } });
  removers.push(unHttp);
  const sweep = setInterval(() => {
    for (const scope of users.values()) if (scope.lease.expired) { scope.lease.revoke();scope.sessions.revoke();scope.events.dispose(); }
    void serial.run(async () => {
      const pending: Promise<void>[] = [];
      for (const [id, scope] of users) {
        if (scope.lease.expired) pending.push(dispose(id, scope));
        else pending.push(scope.sessions.releaseIdle(config.adminIdleTimeoutMs));
      }
      await Promise.all(pending);
    }).catch(() => console.error('[dsh-bridge] BRIDGE_USER_CLEANUP_FAILED'));
  }, 1000); sweep.unref();
  ctx.effect(() => async () => {
    stopping = true; clearInterval(sweep); removers.forEach(remove => remove());
    await serial.run(() => Promise.all([...users].map(([id, scope]) => dispose(id, scope))));
  });
}
