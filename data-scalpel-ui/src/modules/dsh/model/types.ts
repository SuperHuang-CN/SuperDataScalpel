export interface Question {
  id: string; question: string; detail?: string; header?: string; multiSelect?: boolean;
  options?: { label: string; description?: string }[];
}
export interface Interaction { interactionId: string; questions: Question[] }
export interface Session {
  sessionId: string; workspaceId: string; title: string; archived: boolean;
  createdAt: string; lastActivityAt: string;
  runtimeState: 'UNLOADED' | 'IDLE' | 'RUNNING' | 'WAITING_FOR_INPUT' | 'CANCELLING';
  lastOutcome: 'COMPLETED' | 'CANCELLED' | 'FAILED' | 'INTERRUPTED' | null;
  pendingInteractions: Interaction[]; historyThroughSeq?: number;
}
export interface Attachment { id: string; name: string; mediaType: string; bytes: number; kind: 'image' | 'file'; width?: number; height?: number }
export interface Message { id: string; role: string; content: unknown[]; sourceSeq: number; attachments?: Attachment[] }
export interface History { items: Message[]; hasMore: boolean; nextBeforeSeq: number | null; historyThroughSeq: number }
export interface SessionPage { items: Session[]; offset: number; limit: number; hasMore: boolean }
export interface Capabilities { enabled: boolean; ready: boolean; code?: string; capabilities?: string[];
  attachments?: { maxFileBytes: number; maxPerMessage: number; imageSupported: boolean | null; extensions: string[] } }
export interface BridgeEvent {
  type: string; sessionId?: string; messageId?: string; runId?: string;
  sourceSeq?: number; eventSeq?: number; streamId?: string; toolCallId?: string; data: unknown;
}
export const object = (value: unknown): Record<string, unknown> => typeof value === 'object' && value !== null ? value as Record<string, unknown> : {};
export const busy = (session?: Session) => !!session && ['RUNNING', 'WAITING_FOR_INPUT', 'CANCELLING'].includes(session.runtimeState);
export const stateLabel = (session: Session) => session.archived ? '已归档' : ({
  UNLOADED: '可继续', IDLE: '就绪', RUNNING: '正在执行', WAITING_FOR_INPUT: '等待回答', CANCELLING: '正在停止',
}[session.runtimeState]);
export function mergeMessages(...groups: Message[][]) {
  return [...new Map(groups.flat().map(m => [m.id, m])).values()].sort((a,b) => a.sourceSeq - b.sourceSeq);
}
