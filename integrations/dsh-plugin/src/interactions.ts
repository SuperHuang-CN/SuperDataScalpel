import { randomUUID } from 'node:crypto';
import type { AskUserQuestionAnswer, AskUserQuestionRequestEvent } from '@deepseek-ai/dsh-user-questions/types';
import { BridgeError, type BridgeEvent } from './contracts.js';

interface Pending {
  id: string; request: AskUserQuestionRequestEvent;
  resolve: (answer: AskUserQuestionAnswer) => void; reject: (error: Error) => void;
  abort: () => void;
}
export class Interactions {
  private pending = new Map<string, Pending>();
  constructor(private emit: (event: BridgeEvent) => void) {}
  list(sessionId: string) {
    const item = this.pending.get(sessionId);
    return item ? [{ interactionId: item.id, questions: item.request.questions }] : [];
  }
  ask(sessionId: string, request: AskUserQuestionRequestEvent): Promise<AskUserQuestionAnswer> {
    if (request.signal?.aborted) return Promise.reject(new Error('Question cancelled'));
    if (this.pending.has(sessionId)) return Promise.reject(new Error('A question is already pending'));
    return new Promise((resolve, reject) => {
      const item: Pending = { id: randomUUID(), request, resolve, reject, abort: () => this.cancel(sessionId) };
      this.pending.set(sessionId, item);
      request.signal?.addEventListener('abort', item.abort, { once: true });
      this.emit({ type: 'interaction.requested', sessionId, data: { interactionId: item.id, questions: request.questions } });
    });
  }
  validate(sessionId: string, id: string, answer: AskUserQuestionAnswer) {
    const item = this.pending.get(sessionId);
    if (!item || item.id !== id) throw new BridgeError(410, 'BRIDGE_INTERACTION_EXPIRED', '追问已失效。');
    const invalid = () => { throw new BridgeError(400, 'BRIDGE_ARGUMENT_INVALID', '答案必须对应当前问题及其选项。'); };
    if (answer.answers.length !== item.request.questions.length) invalid();
    const ids = new Set<string>();
    for (const value of answer.answers) {
      const question = item.request.questions.find(q => q.id === value.id);
      if (!question || ids.has(value.id)) invalid();
      ids.add(value.id);
      if (!question) return invalid();
      if (new Set(value.selected).size !== value.selected.length || (!question.multiSelect && value.selected.length > 1)) invalid();
      if (!question.multiSelect && value.selected.length && value.custom?.trim()) invalid();
      if (value.selected.some(label => !question.options?.some(option => option.label === label))) invalid();
      if (!value.selected.length && !value.custom?.trim()) invalid();
    }
    return item;
  }
  respond(sessionId: string, id: string, answer: AskUserQuestionAnswer) {
    const item = this.validate(sessionId, id, answer);
    this.remove(sessionId, item);
    this.emit({ type: 'interaction.resolved', sessionId, data: { interactionId: id, outcome: 'ANSWERED' } });
    item.resolve(answer);
    return { interactionId: id, accepted: true };
  }
  private remove(sessionId: string, item: Pending) {
    this.pending.delete(sessionId); item.request.signal?.removeEventListener('abort', item.abort);
  }
  cancel(sessionId: string) {
    const item = this.pending.get(sessionId);
    if (!item) return;
    this.remove(sessionId, item);
    this.emit({ type: 'interaction.resolved', sessionId, data: { interactionId: item.id, outcome: 'CANCELLED' } });
    item.reject(new Error('Question cancelled'));
  }
  dispose() { for (const id of this.pending.keys()) this.cancel(id); }
}
