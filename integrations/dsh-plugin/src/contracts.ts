import { z } from 'zod';

export const uuid = z.string().uuid();
export const emptyInput = z.object({}).strict();
export const createInput = z.object({ clientSessionId: uuid }).strict();
export const messageInput = z.object({ clientMessageId: uuid, text: z.string().refine(value => value.trim().length > 0) }).strict();
export const attachmentMessageInput = z.object({ clientMessageId: uuid, text: z.string().default(''),
  attachmentIds: z.array(uuid).max(5).refine(ids => new Set(ids).size === ids.length).default([]) }).strict()
  .refine(value => !!value.text.trim() || value.attachmentIds.length > 0);
export const answerInput = z.object({ answers: z.array(z.object({
  id: z.string().min(1), selected: z.array(z.string()), custom: z.string().optional(),
}).strict()).min(1) }).strict();

export class BridgeError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) { super(message); }
}
export function fail(status: number, code: string, message: string): never { throw new BridgeError(status, code, message); }
export function problem(error: unknown) {
  const e = error instanceof BridgeError ? error : error instanceof z.ZodError
    ? new BridgeError(400, 'BRIDGE_ARGUMENT_INVALID', '参数不符合接口契约。')
    : new BridgeError(500, 'BRIDGE_INTERNAL_ERROR', 'Bridge 内部处理失败。');
  return { type: 'about:blank', title: e.code, status: e.status, detail: e.message, code: e.code, timestamp: new Date().toISOString() };
}

/** Serialize controls without retaining fulfilled/rejected chains indefinitely. */
export class Serial {
  private tail: Promise<unknown> = Promise.resolve();
  run<T>(fn: () => Promise<T>): Promise<T> {
    const result = this.tail.then(fn);
    this.tail = result.catch(() => undefined);
    return result;
  }
}

export type Outcome = 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'INTERRUPTED' | null;
export interface BridgeEvent {
  type: string;
  sessionId: string;
  messageId?: string;
  runId?: string;
  sourceSeq?: number;
  toolCallId?: string;
  data: unknown;
}

export function paging(search: URLSearchParams, history = false) {
  for (const key of search.keys()) if (!['offset', 'limit'].includes(key)) fail(400, 'BRIDGE_ARGUMENT_INVALID', '不支持此查询参数。');
  const parse = (key: string, fallback: number) => {
    const raw = search.get(key);
    if (raw === null) return fallback;
    if (!/^(0|[1-9][0-9]*)$/.test(raw)) fail(400, 'BRIDGE_ARGUMENT_INVALID', '分页参数必须为整数。');
    const value = Number(raw);
    if (!Number.isSafeInteger(value)) fail(400, 'BRIDGE_ARGUMENT_INVALID', '分页参数超出范围。');
    return value;
  };
  const offset = parse('offset', 0), limit = parse('limit', history ? 50 : 20);
  if (limit < 1 || limit > (history ? 200 : 100)) fail(400, 'BRIDGE_ARGUMENT_INVALID', '分页数量超出范围。');
  return { offset, limit };
}
export function page<T>(items: T[], offset: number, limit: number) {
  return { items: items.slice(offset, offset + limit), offset, limit, hasMore: offset + limit < items.length };
}
