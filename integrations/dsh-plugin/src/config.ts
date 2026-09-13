import { z } from 'zod';
import { isAbsolute } from 'node:path';

const schema = z.object({
  adminEnabled: z.boolean().default(false),
  adminAccessTokenEnv: z.string().default('DATASCALPEL_DSH_ADMIN_BRIDGE_TOKEN'),
  adminMcpUrl: z.string().url().default('http://host.docker.internal:18080/system-mcp'),
  adminMaxActiveSessions: z.number().int().positive().default(8),
  adminIdleTimeoutMs: z.number().int().min(1000).default(900000),
  enabled: z.boolean().default(false),
  playgroundEnabled: z.boolean().default(false),
  playgroundRoot: z.string().default('/workspace/bridge-phase-two'),
  playgroundMcpUrl: z.string().url().default('http://playground:13082/mcp'),
  workspaceRoot: z.string().default('/workspace/bridge-phase-one'),
  presetId: z.string().default('datascalpel-bridge-phase-one'),
  provider: z.string().default(''), model: z.string().default(''),
  accessTokenEnv: z.string().default('DATASCALPEL_DSH_BRIDGE_TOKEN'),
  maxRequestBytes: z.number().int().positive().default(1048576),
  maxActiveSessions: z.number().int().positive().default(4),
  eventBufferBytes: z.number().int().min(1024).default(1048576),
}).strict();
export type Config = z.infer<typeof schema>;
export function configuration(input: unknown): Config {
  const result = schema.parse(input ?? {});
  if ((result.enabled || result.adminEnabled) && (!result.provider.trim() || !result.model.trim() || !isAbsolute(result.workspaceRoot))) {
    throw new Error('Bridge requires provider, model and an absolute workspaceRoot');
  }
  if (result.playgroundEnabled && !isAbsolute(result.playgroundRoot)) throw new Error('Playground root must be absolute');
  return result;
}
export function accessSecret(config: Config): string {
  const secret = process.env[config.accessTokenEnv];
  if (!secret || secret.length < 32 || /\s/.test(secret)) throw new Error('Bridge access secret must contain at least 32 non-whitespace characters');
  return secret;
}
