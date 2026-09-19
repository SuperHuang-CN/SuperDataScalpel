import { createHash, randomUUID } from 'node:crypto';
import { realpath } from 'node:fs/promises';
import type { Context } from '@deepseek-ai/cordis';
import type { Agent, AgentHandle, AssistantStreamFrame } from '@deepseek-ai/dsh-agent';
import '@deepseek-ai/dsh-agent-presets';
import { SessionId, type SessionEvent } from '@deepseek-ai/dsh-session';
import { MessageId, freezeMessage } from '@deepseek-ai/dsh-llm';
import '@deepseek-ai/dsh-tools';
import { scopeOf } from '@deepseek-ai/dsh-scope';
import { SessionAlreadyOwnedError } from '@deepseek-ai/dsh-session-persistence';
import type { Config } from './config.js';
import { BridgeError, fail, page, Serial, type BridgeEvent } from './contracts.js';
import { BridgeStorage, type SessionRecord } from './storage.js';
import { Workspaces } from './workspaces.js';
import { Interactions } from './interactions.js';
import { hasReceived, outcome, transcript } from './projection.js';
import { ChatAttachments } from './attachments.js';

interface Live {
  handle: AgentHandle; touchedAt: number; release?: () => void; cancelling: boolean; accepting: boolean;
  messageId?: string; runId?: string;
}

export class Sessions {
  private live = new Map<string, Live>();
  private controls = new Map<string, Serial>();
  private factQueues = new Map<string, Serial>();
  private allocations = new Serial();
  private closing = false;
  readonly interactions: Interactions;
  readonly attachments: ChatAttachments;
  constructor(readonly ctx: Context, readonly config: Config, readonly storage: BridgeStorage,
    readonly workspaces: Workspaces, private publish: (event: BridgeEvent) => void,
    private setupTools?: (ctx: Context, record: SessionRecord) => Promise<string[]>,
    private reserve?: () => () => void) {
    this.interactions = new Interactions(event => this.emit(event));
    this.attachments = new ChatAttachments(ctx, storage);
  }
  private serial(id: string) {
    let value = this.controls.get(id);
    if (!value) { value = new Serial(); this.controls.set(id, value); }
    return value;
  }
  private get managed() { return this.config.presetId === 'datascalpel-admin'; }
  private writable(id: string) { if (this.own(id).archived) fail(409, 'BRIDGE_SESSION_ARCHIVED', '请先恢复已归档的会话。'); }
  private available() { if (this.closing) fail(503, 'BRIDGE_NOT_READY', 'Bridge 正在停止。'); }
  private own(id: string) { this.available(); return this.storage.own(id); }
  private emit(event: BridgeEvent) {
    const live = this.live.get(event.sessionId);
    try { this.publish({ messageId: live?.messageId, runId: live?.runId, ...event }); }
    catch { console.error('[dsh-bridge] BRIDGE_EVENT_DELIVERY_FAILED'); }
  }
  async ready() {
    this.available();
    try {
      await this.ctx.agentPresets.resolve(this.config.presetId);
      await this.ctx.llm.resolveCallConfig({ provider: this.config.provider, model: this.config.model });
    } catch { fail(503, 'BRIDGE_NOT_READY', '验证 Preset 或模型配置未就绪。'); }
  }
  async create(clientSessionId: string) {
    return this.allocations.run(async () => {
      this.available();
      let record = [...this.storage.sessions.entries()].map(([, v]) => v).find(v => v.clientSessionId === clientSessionId);
      if (record?.state === 'READY') return { status: 200, body: await this.view(record.sessionId) };
      this.checkCapacity();
      await this.ready();
      const workspace = await this.workspaces.ensure();
      if (!record) {
        record = { sessionId: randomUUID(), clientSessionId, workspaceId: workspace.id, path: workspace.path,
          createdAt: new Date().toISOString(), state: 'CREATING', presetId: this.config.presetId,
          provider: this.config.provider, model: this.config.model,
          ...(this.managed ? { title: '新对话', archived: false, lastActivityAt: new Date().toISOString(), lastOutcome: null } : {}) };
        await this.storage.saveSession(record);
      }
      await this.serial(record.sessionId).run(() => this.load(record!));
      return { status: 201, body: await this.view(record.sessionId) };
    });
  }
  private checkCapacity() {
    if (this.live.size >= this.config.maxActiveSessions) fail(503, 'BRIDGE_CAPACITY_EXCEEDED', '活跃会话数量已达到上限。');
  }
  async resume(id: string) {
    this.writable(id);
    return this.allocations.run(() => this.serial(id).run(async () => {
      this.writable(id);
      if (!this.live.has(id)) { this.checkCapacity(); await this.ready(); await this.load(this.own(id)); }
      return this.view(id);
    }));
  }
  private async load(record: SessionRecord) {
    const id = record.sessionId;
    if (this.live.has(id)) return;
    const release = this.reserve?.();
    let handle: AgentHandle | undefined;
    try {
      if (record.path !== await realpath(this.config.workspaceRoot)
        || record.presetId !== this.config.presetId || record.provider !== this.config.provider || record.model !== this.config.model) {
        fail(409, 'BRIDGE_SESSION_CONFIGURATION_CHANGED', '会话创建配置与当前配置不匹配。');
      }
      const stored = await this.ctx.sessionPersistence.stat(SessionId(id));
      if (stored && (await realpath(stored.header.cwd ?? '')) !== record.path) fail(409, 'BRIDGE_SESSION_CONFIGURATION_CHANGED', '会话工作目录不匹配。');
      if (!stored && record.state === 'READY') fail(500, 'BRIDGE_PERSISTENCE_FAILED', '原生会话记录缺失。');
      const setup = async (agentCtx: Context, agent: Agent) => {
        await this.ctx.agentPresets.mount(agentCtx, record.presetId);
        // Expose native schemas plus DSH's generated run_code SDK. The preset
        // owns native tools; setupTools adds user-scoped file/MCP tools.
        agentCtx.tools.presentAs('both');
        const required = ['ask_user_question', ...await this.setupTools?.(agentCtx, record) ?? []];
        // Hide unrelated host-global tools. Preset and setup registrations are
        // scoped, so this does not remove the selected capability set.
        agentCtx.tools.restrict({ allow: ['ask_user_question'] });
        const names = agentCtx.tools.schemas(scopeOf(agentCtx)).map(tool => tool.name);
        if (required.some(name => !names.includes(name))) fail(503, 'BRIDGE_NOT_READY', '验证会话工具与允许清单不匹配。');
        const allowed = new Set(names);
        agentCtx.tools.guard(execution => allowed.has(execution.name) ? undefined : 'Bridge tool is outside this session capability set');
        // Runs before publication/start: restored queued work must never wake on resume.
        agent.inbox.clear();
        agentCtx.on('user-questions/request', (request, next) => request.agent === agent
          ? this.interactions.ask(id, request) : next(), { prepend: true });
        agentCtx.on('session/event', (session, event) => { if (session === agent.session) this.onEvent(id, event); });
        agentCtx.on('agent/assistant-stream', payload => { if (payload.agent === agent) this.onStream(id, payload.frame); });
      };
      handle = stored ? await this.ctx.agents.resume({ resumeSessionId: SessionId(id),
        agentOptions: { provider: record.provider, model: record.model }, setup })
        : await this.ctx.agents.create({ sessionId: SessionId(id),
          meta: { cwd: record.path, agentPreset: record.presetId },
          agentOptions: { provider: record.provider, model: record.model }, setup });
      await this.ctx.sessions.flush(handle.agent.session);
      const workspace = this.ctx.workspaceRegistry.get(record.workspaceId as ReturnType<typeof import('@deepseek-ai/dsh-workspace').WorkspaceId>);
      if (!workspace || workspace.path !== record.path) fail(500, 'BRIDGE_WORKSPACE_UNAVAILABLE', '验证工作区记录不匹配。');
      await workspace.attachSession(SessionId(id));
      await this.storage.saveSession({ ...record, state: 'READY' });
      this.available();
      this.live.set(id, { handle, release, touchedAt: Date.now(), cancelling: false, accepting: false });
    } catch (error) {
      await handle?.dispose().catch(() => undefined);
      release?.();
      if (error instanceof BridgeError) throw error;
      if (error instanceof SessionAlreadyOwnedError) fail(409, 'BRIDGE_SESSION_OWNED', '会话被其他原生入口占用。');
      // Stack locations identify the failing public API without logging provider config or credentials.
      console.error('[dsh-bridge] BRIDGE_SESSION_CREATE_FAILED', error instanceof Error ? error.name : 'unknown',
        error instanceof Error ? error.stack?.split('\n').filter(line => /^\s+at /.test(line)).join('\n') : '');
      fail(500, 'BRIDGE_SESSION_CREATE_FAILED', '无法创建或恢复验证会话，请检查服务诊断。');
    }
  }
  async events(id: string): Promise<readonly SessionEvent[]> {
    this.own(id);
    const live = this.live.get(id);
    if (live) {
      const snapshot = live.handle.agent.session.snapshotEvents();
      try { await this.ctx.sessions.flush(live.handle.agent.session); }
      catch { fail(500, 'BRIDGE_PERSISTENCE_FAILED', '无法确认原生会话历史已持久化。'); }
      return snapshot;
    }
    let reader;
    try {
      reader = await this.ctx.sessionPersistence.open(SessionId(id), 'read');
      return (await reader.read()).events;
    } catch { throw new BridgeError(500, 'BRIDGE_PERSISTENCE_FAILED', '无法读取原生会话历史。'); }
    finally { await reader?.close(); }
  }
  async view(id: string) {
    const record = this.own(id), live = this.live.get(id);
    const events = await this.events(id);
    const pendingInteractions = this.interactions.list(id);
    return { sessionId: id, workspaceId: record.workspaceId,
      runtimeState: !live ? 'UNLOADED' : live.cancelling ? 'CANCELLING' : pendingInteractions.length ? 'WAITING_FOR_INPUT'
        : live.accepting || live.handle.agent.status === 'running' ? 'RUNNING' : 'IDLE',
      ...(this.managed ? this.metadata(record) : {}),
      lastOutcome: outcome(events, !!live && (live.accepting || live.handle.agent.status === 'running')), pendingInteractions, createdAt: record.createdAt,
      historyThroughSeq: events.at(-1)?.seq ?? -1 };
  }
  async list(offset: number, limit: number, query = '', archived = false) {
    if (this.managed) {
      const records = [...this.storage.sessions.entries()].map(([, r]) => r)
        .filter(r => r.state === 'READY' && !!r.archived === archived && (r.title ?? '').toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()))
        .sort((a,b) => (b.lastActivityAt ?? b.createdAt).localeCompare(a.lastActivityAt ?? a.createdAt) || a.sessionId.localeCompare(b.sessionId));
      const result = page(records, offset, limit);
      return { ...result, items: result.items.map(r => ({ sessionId: r.sessionId, workspaceId: r.workspaceId, createdAt: r.createdAt,
        ...this.metadata(r), runtimeState: this.runtimeState(r.sessionId), pendingInteractions: this.interactions.list(r.sessionId) })) };
    }
    this.available();
    const records = [...this.storage.sessions.entries()].map(([, r]) => r).filter(r => r.state === 'READY')
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || a.sessionId.localeCompare(b.sessionId));
    const result = page(records, offset, limit);
    return { ...result, items: await Promise.all(result.items.map(r => this.view(r.sessionId))) };
  }
  private project(id: string, events: readonly SessionEvent[]) {
    return transcript(events).map(message => {
      const receipt = this.storage.messages.get(`${id}:${message.id}`);
      if (!receipt?.attachmentIds?.length || message.role !== 'user') return message;
      return { ...message, content: receipt.userText ? [{ type: 'text', text: receipt.userText }] : [],
        attachments: this.attachments.summaries(id, receipt.attachmentIds) };
    });
  }
  async messages(id: string, offset: number, limit: number) { return page(this.project(id, await this.events(id)), offset, limit); }
  async uploadAttachment(id: string, input: unknown) {
    this.writable(id);
    return this.serial(id).run(() => this.attachments.upload(id, input));
  }
  async send(id: string, clientMessageId: string, text: string, attachmentIds: string[] = []) {
    this.writable(id);
    return this.serial(id).run(async () => {
      this.writable(id);
      const digest = createHash('sha256').update(attachmentIds.length ? JSON.stringify([text, attachmentIds]) : text).digest('hex'), key = `${id}:${clientMessageId}`;
      const previous = this.storage.messages.get(key);
      if (previous) {
        if (previous.digest !== digest) fail(409, 'BRIDGE_MESSAGE_CONFLICT', '同一消息 ID 对应不同内容。');
        if (previous.state !== 'ACCEPTED') {
          if (!hasReceived(await this.events(id), clientMessageId)) {
            fail(503, 'BRIDGE_MESSAGE_OUTCOME_UNKNOWN', '消息接收结果无法确认，请读取历史，不要重复入队。');
          }
          try { await this.storage.messages.put(key, { ...previous, state: 'ACCEPTED' }); }
          catch { fail(503, 'BRIDGE_MESSAGE_OUTCOME_UNKNOWN', '消息控制状态无法持久化，请读取历史。'); }
        }
        return { sessionId: id, messageId: clientMessageId, accepted: true, duplicate: true };
      }
      const live = this.live.get(id);
      if (!live) fail(409, 'BRIDGE_SESSION_NOT_LOADED', '请先恢复会话。');
      if (live.accepting || live.cancelling || live.handle.agent.status !== 'idle') fail(409, 'BRIDGE_SESSION_BUSY', '会话正在执行。');
      const content = await this.attachments.content(id, attachmentIds);
      const record = { sessionId: id, messageId: clientMessageId, digest, state: 'RECEIVING' as const,
        ...(attachmentIds.length ? { attachmentIds, userText: text } : {}) };
      try { await this.storage.messages.put(key, record); }
      catch { fail(500, 'BRIDGE_PERSISTENCE_FAILED', '无法保存消息接收意图。'); }
      live.touchedAt = Date.now();
      live.accepting = true; live.messageId = clientMessageId;
      const agent = live.handle.agent;
      try {
        // The public maintenance boundary parks the wake until the receipt is durable.
        await agent.runMaintenance(async () => {
          try {
            agent.send(freezeMessage({ id: MessageId(clientMessageId), role: 'user', source: { kind: 'user' },
              content: [...(text.trim() ? [{ type: 'text' as const, text }] : []), ...content] }), 'next-turn', true);
            await this.ctx.sessions.flush(agent.session);
            await this.storage.messages.put(key, { ...record, state: 'ACCEPTED' });
            await this.updateMetadata(id, { lastActivityAt: new Date().toISOString(),
              ...(!this.own(id).titleManual && this.own(id).title === '新对话' ? { title: [...(text.trim() || (attachmentIds.length ? this.attachments.own(id, attachmentIds[0]!).name : '新对话')).replace(/\s+/g, ' ')].slice(0,40).join('') } : {}) });
            this.emit({ type: 'message.accepted', sessionId: id, messageId: clientMessageId, data: { accepted: true } });
          } catch (error) {
            agent.cancel({ kind: 'hook', reason: 'Bridge receipt persistence failed' });
            await this.ctx.sessions.flush(agent.session).catch(() => undefined);
            throw error;
          }
        });
      } catch { fail(503, 'BRIDGE_MESSAGE_OUTCOME_UNKNOWN', '消息接收结果无法确认，请读取历史。'); }
      finally { live.accepting = false; }
      return { sessionId: id, messageId: clientMessageId, accepted: true, duplicate: false };
    });
  }
  async cancel(id: string) {
    this.own(id);
    return this.serial(id).run(async () => {
      const live = this.live.get(id);
      const running = live && live.handle.agent.status !== 'idle';
      if (running) {
        live.cancelling = true;
        live.handle.agent.cancel({ kind: 'user' });
        this.interactions.cancel(id);
      }
      return { status: running ? 202 : 200, body: await this.view(id) };
    });
  }
  async respond(id: string, interactionId: string, answer: Parameters<Interactions['respond']>[2]) {
    return this.serial(id).run(async () => {
      this.writable(id);
      this.interactions.validate(id, interactionId, answer);
      // Persist before resolving: an acknowledged answer must never be reported as unsubmitted.
      await this.updateMetadata(id, { lastActivityAt: new Date().toISOString() });
      return this.interactions.respond(id, interactionId, answer);
    });
  }
  private onStream(id: string, frame: AssistantStreamFrame) {
    if (frame.type === 'start') this.emit({ type: 'assistant.started', sessionId: id, data: { attemptId: frame.attemptId } });
    if (frame.type === 'chunk' && frame.chunk.type === 'text-delta') this.emit({ type: 'assistant.delta', sessionId: id,
      data: { attemptId: frame.attemptId, text: frame.chunk.text } });
    if (frame.type === 'end') this.emit({ type: 'assistant.settled', sessionId: id, data: { attemptId: frame.attemptId, outcome: frame.outcome } });
  }
  private onEvent(id: string, event: SessionEvent) {
    const live = this.live.get(id);
    if (event.type === 'turn/start' && live) live.runId = `${id}:${event.data.turn}`;
    if (!['assistant/message', 'tool/call', 'tool/result', 'turn/end'].includes(event.type)) return;
    if (event.type === 'turn/end' && live) { live.cancelling = false; live.touchedAt = Date.now(); }
    let queue = this.factQueues.get(id);
    if (!queue) { queue = new Serial(); this.factQueues.set(id, queue); }
    // Capture correlation before another turn starts. Flush asynchronously without holding the Agent loop.
    const correlation = { messageId: live?.messageId, runId: live?.runId };
    void queue.run(async () => {
      try {
        if (live) await this.ctx.sessions.flush(live.handle.agent.session);
        if (event.type === 'turn/end') await this.serial(id).run(() => this.updateMetadata(id, { lastActivityAt: new Date(event.time).toISOString(), lastOutcome: outcome([event]) }));
        this.publishFact(id, event, correlation);
      } catch {
        this.emit({ ...correlation, type: 'session.persistence-failed', sessionId: id,
          data: { code: 'BRIDGE_PERSISTENCE_FAILED' } });
      }
    });
  }
  private publishFact(id: string, event: SessionEvent, correlation: Pick<BridgeEvent, 'messageId' | 'runId'>) {
    const publish = (event: BridgeEvent) => this.emit({ ...event, ...correlation });
    if (event.type === 'assistant/message' && transcript([event]).length) publish({ type: 'assistant.completed', sessionId: id, sourceSeq: event.seq,
      data: { message: transcript([event])[0], interrupted: event.data.interrupted ?? false } });
    if (event.type === 'tool/call') publish({ type: 'tool.started', sessionId: id, sourceSeq: event.seq,
      toolCallId: event.data.callId, data: { name: event.data.name, arguments: event.data.arguments } });
    if (event.type === 'tool/result') publish({ type: 'tool.completed', sessionId: id, sourceSeq: event.seq,
      toolCallId: event.data.message.content[0].toolCallId, data: { message: transcript([event])[0] } });
    if (event.type === 'turn/end') {
      const reason = event.data.reason.kind;
      publish({ type: reason === 'completed' ? 'run.completed' : reason === 'aborted' ? 'run.cancelled' : 'run.failed',
        sessionId: id, sourceSeq: event.seq, data: { reason,
          ...(reason === 'error' ? { code: 'BRIDGE_MODEL_EXECUTION_FAILED', detail: '模型执行失败，请检查模型配置与服务诊断。' } : {}) } });
    }
  }
  private metadata(record: SessionRecord) {
    return { title: record.title ?? '新对话', archived: record.archived ?? false,
      lastActivityAt: record.lastActivityAt ?? record.createdAt, lastOutcome: record.lastOutcome ?? null };
  }
  private runtimeState(id: string) {
    const live = this.live.get(id);
    return !live ? 'UNLOADED' : live.cancelling ? 'CANCELLING' : this.interactions.list(id).length ? 'WAITING_FOR_INPUT'
      : live.accepting || live.handle.agent.status !== 'idle' ? 'RUNNING' : 'IDLE';
  }
  private async updateMetadata(id: string, patch: Partial<SessionRecord>) {
    if (!this.managed) return;
    const previous = this.own(id);
    const record = { ...previous, ...patch };
    if (previous.lastActivityAt && patch.lastActivityAt && patch.lastActivityAt < previous.lastActivityAt) record.lastActivityAt = previous.lastActivityAt;
    await this.storage.saveSession(record);
    this.emit({ type: 'session.updated', sessionId: id, data: this.metadata(record) });
  }
  /** One-time, cold migration of old Bridge records. No Agent or model request. */
  async initializeMetadata() {
    if (!this.managed) return;
    for (const [, record] of this.storage.sessions.entries()) {
      if (record.state !== 'READY' || record.lastActivityAt) continue;
      const events = await this.events(record.sessionId);
      const first = transcript(events).find(m => m.role === 'user');
      const text = first?.content.filter((b): b is {type: 'text'; text: string} => typeof b === 'object' && b !== null && 'type' in b && b.type === 'text' && 'text' in b && typeof b.text === 'string').map(b => b.text).join(' ') ?? '';
      const activity = events.filter(e => ['user/message', 'tool/result', 'turn/end'].includes(e.type)).at(-1);
      await this.storage.saveSession({ ...record, title: [...text.trim().replace(/\s+/g, ' ')].slice(0,40).join('') || '新对话',
        archived: false, lastActivityAt: activity ? new Date(activity.time).toISOString() : record.createdAt, lastOutcome: outcome(events) });
    }
  }
  async update(id: string, title: string) {
    this.own(id);
    return this.serial(id).run(async () => {
      await this.updateMetadata(id, { title, titleManual: true }); return this.view(id);
    });
  }
  async archive(id: string, archived: boolean) {
    this.own(id);
    return this.serial(id).run(async () => {
      const state = this.runtimeState(id);
      if (archived && !['IDLE','UNLOADED'].includes(state)) fail(409, 'BRIDGE_SESSION_BUSY', '请先停止执行，再归档会话。');
      await this.updateMetadata(id, { archived });
      if (archived) {
        const live = this.live.get(id);
        if (live) { await live.handle.dispose(); this.live.delete(id); live.release?.(); }
      }
      return this.view(id);
    });
  }
  async recentMessages(id: string, beforeSeq: number | undefined, limit: number) {
    const events = await this.events(id);
    const candidates = this.project(id, events).filter(m => beforeSeq === undefined || m.sourceSeq < beforeSeq);
    const items = candidates.slice(-limit);
    return { items, hasMore: candidates.length > items.length, nextBeforeSeq: items[0]?.sourceSeq ?? null,
      historyThroughSeq: events.at(-1)?.seq ?? -1 };
  }
  get activeCount() { return this.live.size; }
  async releaseIdle(maxAge: number) {
    await this.allocations.run(async () => {
      for (const id of [...this.live.keys()]) await this.serial(id).run(async () => {
        const live = this.live.get(id);
        if (!live || live.accepting || live.cancelling || live.handle.agent.status !== 'idle'
          || Date.now() - live.touchedAt < maxAge) return;
        this.live.delete(id);
        try { await live.handle.dispose(); } finally { live.release?.(); }
      });
    });
  }
  revoke() {
    this.closing = true;
    for (const [id, live] of this.live) {
      live.handle.agent.cancel({ kind: 'hook', reason: 'Admin identity lease expired' });
      this.interactions.cancel(id);
    }
  }
  async dispose() {
    this.closing = true;
    await this.allocations.run(async () => undefined);
    await Promise.all([...this.controls.values()].map(s => s.run(async () => undefined)));
    this.interactions.dispose();
    const handles = [...this.live.values()]; this.live.clear();
    const settled = await Promise.allSettled(handles.map(async value => { try { await value.handle.dispose(); } finally { value.release?.(); } }));
    await Promise.all([...this.factQueues.values()].map(queue => queue.run(async () => undefined)));
    if (settled.some(value => value.status === 'rejected')) console.error('[dsh-bridge] BRIDGE_DISPOSAL_FAILED');
  }
}
