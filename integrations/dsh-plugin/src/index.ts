import { mountAdmin } from './admin.js';
import type { Context } from '@deepseek-ai/cordis';
import { configuration, accessSecret } from './config.js';
import { BridgeStorage } from './storage.js';
import { Workspaces } from './workspaces.js';
import { Sessions } from './sessions.js';
import { Events } from './events.js';
import { registerRoutes } from './routes.js';
import { WorkspaceFiles } from './files.js';
import { apply as mountMcp } from '@deepseek-ai/dsh-mcp-client';

export const name = 'datascalpel-bridge';
export const inject = ['webServer', 'agents', 'agentPresets', 'sessions', 'sessionPersistence', 'storageDomain', 'workspaceRegistry', 'tools', 'llm', 'userQuestions', 'attachments'];

export const apply = async (ctx: Context, input: unknown) => {
  const config = configuration(input);
  if (config.adminEnabled) await mountAdmin(ctx, config);
  if (!config.enabled) return;
  const secret = accessSecret(config);
  const storage = await BridgeStorage.open(ctx);
  const events = new Events(config.eventBufferBytes);
  const sessions = new Sessions(ctx, config, storage, new Workspaces(ctx, config.workspaceRoot), event => events.publish(event));
  let remove: (() => void) | undefined;
  const dispose = async () => { remove?.(); events.dispose(); await sessions.dispose(); await storage.close(); };
  ctx.effect(() => dispose);
  try { remove = registerRoutes(ctx, config, secret, sessions, events); }
  catch (error) { await dispose(); throw error; }
  if (config.playgroundEnabled) {
    for (const owner of ['alice', 'bob'] as const) {
      const token = process.env[`DSH_PLAYGROUND_MCP_${owner.toUpperCase()}_TOKEN`];
      if (!token || token.length < 32) throw new Error('Playground requires independent MCP credentials');
      const userConfig = { ...config, workspaceRoot: `${config.playgroundRoot}/${owner}`, presetId: 'datascalpel-bridge-phase-two' };
      const userStorage = await BridgeStorage.open(ctx, owner);
      const userEvents = new Events(config.eventBufferBytes);
      const files = new WorkspaceFiles(userConfig.workspaceRoot);
      const userSessions = new Sessions(ctx, userConfig, userStorage, new Workspaces(ctx, userConfig.workspaceRoot),
        event => userEvents.publish(event), async (agentCtx, record) => {
          const serverName = `test_${record.sessionId.replaceAll('-', '').slice(0, 24)}`;
          await mountMcp(agentCtx, { transport: 'streamable-http', serverName, url: config.playgroundMcpUrl,
            headers: { Authorization: `Bearer ${token}` }, toolCallTimeoutMs: 15000, failOnStartupError: true });
          return [...files.mount(agentCtx), `mcp__${serverName}__whoami`];
        });
      let removeUser: (() => void) | undefined;
      ctx.effect(() => async () => { removeUser?.(); userEvents.dispose(); await userSessions.dispose(); await userStorage.close(); });
      removeUser = registerRoutes(ctx, userConfig, secret, userSessions, userEvents, `/bridge/v2/users/${owner}`, files);
    }
  }
};
