import { z } from 'zod';
import { defineDomain, domainTable, type Domain } from '@deepseek-ai/dsh-storage-domain';
import type { Context } from '@deepseek-ai/cordis';
import { BridgeError } from './contracts.js';
import { attachmentRecord } from './attachments.js';

const sessionRecord = z.object({
  sessionId: z.string().uuid(), clientSessionId: z.string().uuid(), workspaceId: z.string(),
  path: z.string(), createdAt: z.string(), state: z.enum(['CREATING', 'READY']),
  presetId: z.string(), provider: z.string(), model: z.string(),
  title: z.string().optional(), titleManual: z.boolean().optional(), archived: z.boolean().optional(),
  lastActivityAt: z.string().optional(), lastOutcome: z.enum(['COMPLETED','FAILED','CANCELLED','INTERRUPTED']).nullable().optional(),
});
const messageRecord = z.object({
  sessionId: z.string().uuid(), messageId: z.string().uuid(), digest: z.string(),
  state: z.enum(['RECEIVING', 'ACCEPTED']),
  attachmentIds: z.array(z.string().uuid()).optional(),
  userText: z.string().optional(),
});
export const bridgeDomain = defineDomain({
  // The JSON backend opens newly declared tables as empty. This is an additive schema extension;
  // changing the unit version would reject existing single-document stores before validation.
  name: 'datascalpel_bridge', version: 1,
  tables: { sessions: domainTable(sessionRecord), messages: domainTable(messageRecord), attachments: domainTable(attachmentRecord) },
});
export type SessionRecord = z.infer<typeof sessionRecord>;
export class BridgeStorage {
  readonly sessions;
  readonly messages;
  readonly attachments;
  constructor(readonly domain: Domain<typeof bridgeDomain>) {
    this.sessions = domain.table('sessions'); this.messages = domain.table('messages');
    this.attachments = domain.table('attachments');
  }
  static async open(ctx: Context, owner?: string) {
    if (owner && !/^(alice|bob|admin_[a-f0-9]{32})$/.test(owner)) throw new Error('Invalid storage owner');
    const definition = owner ? { ...bridgeDomain, name: `datascalpel_bridge_${owner}` } : bridgeDomain;
    return new BridgeStorage(await ctx.storageDomain.open(definition));
  }
  own(id: string): SessionRecord {
    const record = this.sessions.get(id);
    if (!record) throw new BridgeError(404, 'BRIDGE_SESSION_NOT_FOUND', '验证会话不存在。');
    return record;
  }
  async saveSession(record: SessionRecord) {
    try { await this.sessions.put(record.sessionId, record); }
    catch { throw new BridgeError(500, 'BRIDGE_PERSISTENCE_FAILED', '无法保存会话控制信息。'); }
  }
  close() { return this.domain.close(); }
}
