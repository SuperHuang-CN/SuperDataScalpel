import { isAppendSurfaceEvent, type SessionEvent } from '@deepseek-ai/dsh-session';
import type { Message } from '@deepseek-ai/dsh-llm';
import type { Outcome } from './contracts.js';

/** Human transcript follows append messages, never request headers or compaction replacements. */
export function transcript(events: readonly SessionEvent[]) {
  const items: { id: string; role: string; content: unknown[]; sourceSeq: number }[] = [];
  for (const event of events) {
    let message: Message | undefined;
    if (event.type === 'user/message' && event.data.source.kind === 'user') message = event.data;
    if (event.type === 'assistant/message') message = event.data.message;
    if (event.type === 'tool/result') message = event.data.message;
    if (!message || !isAppendSurfaceEvent(event)) continue;
    const content = message.content.filter(block => ['text', 'tool-call', 'tool-result'].includes(block.type));
    items.push({ id: message.id, role: event.type === 'tool/result' ? 'tool' : message.role, content, sourceSeq: event.seq });
  }
  return items;
}

export function outcome(events: readonly SessionEvent[], active = false): Outcome {
  let value: Outcome = null;
  let unfinished = false;
  for (const event of events) {
    if (event.type === 'turn/start') unfinished = true;
    if (event.type === 'turn/end') { unfinished = false; value = event.data.reason.kind === 'completed' ? 'COMPLETED'
      : event.data.reason.kind === 'aborted' ? 'CANCELLED'
      : event.data.reason.kind === 'interrupted' ? 'INTERRUPTED' : 'FAILED'; }
  }
  return unfinished && !active ? 'INTERRUPTED' : value;
}

export function hasReceived(events: readonly SessionEvent[], id: string) {
  return events.some(event => event.type === 'user/message' && event.data.id === id
    || event.type === 'agent/inbox/spliced' && event.data.inserted.some(message => message.id === id));
}
