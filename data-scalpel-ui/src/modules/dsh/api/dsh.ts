import { requestBlob, requestJson } from '../../../shared/api/http';
import type { Attachment, Capabilities, History, Session, SessionPage } from '../model/types';
const root = '/v1/dsh';
const post = <T>(path: string, body: unknown = {}) => requestJson<T>(root + path, { method: 'POST', body: JSON.stringify(body) }, 55_000);
export const dshApi = {
  capabilities: () => requestJson<Capabilities>(root + '/capabilities'),
  ensure: () => post<{ workspaceId: string }>('/workspace/actions/ensure'),
  list: (query: string, archived: boolean, offset: number) => requestJson<SessionPage>(root + '/sessions?' + new URLSearchParams({ query, archived: String(archived), offset: String(offset), limit: '20' })),
  create: (clientSessionId: string) => post<Session>('/sessions', { clientSessionId }),
  session: (id: string) => requestJson<Session>(root + `/sessions/${id}`),
  history: (id: string, beforeSeq?: number) => requestJson<History>(root + `/sessions/${id}/messages?mode=cursor&limit=50${beforeSeq === undefined ? '' : `&beforeSeq=${beforeSeq}`}`),
  update: (id: string, title: string) => post<Session>(`/sessions/${id}/actions/update`, { title }),
  archive: (id: string, archived: boolean) => post<Session>(`/sessions/${id}/actions/${archived ? 'archive' : 'restore'}`),
  send: (id: string, clientMessageId: string, text: string, attachmentIds: string[] = []) => post<{ messageId: string; accepted: boolean }>(`/sessions/${id}/messages`, { clientMessageId, text, ...(attachmentIds.length ? { attachmentIds } : {}) }),
  upload: (id: string, clientAttachmentId: string, name: string, data: string, signal: AbortSignal) => requestJson<Attachment>(root + `/sessions/${id}/attachments`, {
    method: 'POST', body: JSON.stringify({ clientAttachmentId, name, data }), signal,
  }, 55_000),
  attachment: (id: string, attachmentId: string, signal?: AbortSignal) => requestBlob(root + `/sessions/${id}/attachments/${attachmentId}`, { signal }, 55_000),
  cancel: (id: string) => post<Session>(`/sessions/${id}/actions/cancel`),
  answer: (id: string, interactionId: string, answers: { id: string; selected: string[]; custom?: string }[]) => post<{ accepted: boolean }>(`/sessions/${id}/interactions/${interactionId}/actions/respond`, { answers }),
};
