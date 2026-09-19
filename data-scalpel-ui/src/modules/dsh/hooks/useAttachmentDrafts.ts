import { createUuid } from '../../../shared/browser/createUuid';
import { useEffect, useRef, useState } from 'react';
import { dshApi } from '../api/dsh';
import type { Attachment, Capabilities } from '../model/types';

export interface AttachmentDraft {
  key: string; file: File; preview?: string; state: 'uploading' | 'ready' | 'error'; error?: string; attachment?: Attachment;
}
const encoded = (file: File): Promise<string> => new Promise((resolve, reject) => {
  const reader = new FileReader();
  reader.onload = () => { const value = String(reader.result); resolve(value.slice(value.indexOf(',') + 1)); };
  reader.onerror = () => reject(new Error('无法读取此文件，请重新选择'));
  reader.readAsDataURL(file);
});

/** In-memory drafts stay with their session when the drawer or route changes. */
export function useAttachmentDrafts(limits?: Capabilities['attachments']) {
  const [drafts, setDrafts] = useState<Record<string, AttachmentDraft[]>>({});
  const current = useRef(drafts); const controllers = useRef(new Map<string, AbortController>());
  const update = (session: string, fn: (old: AttachmentDraft[]) => AttachmentDraft[]) => {
    current.current = { ...current.current, [session]: fn(current.current[session] ?? []) }; setDrafts(current.current);
  };
  const upload = async (session: string, draft: AttachmentDraft) => {
    const controller = new AbortController(); controllers.current.set(draft.key, controller);
    update(session, values => values.map(v => v.key === draft.key ? { ...v, state: 'uploading', error: undefined } : v));
    try {
      const data = await encoded(draft.file); controller.signal.throwIfAborted();
      const attachment = await dshApi.upload(session, draft.key, draft.file.name, data, controller.signal);
      if (!controller.signal.aborted) update(session, values => values.map(v => v.key === draft.key ? { ...v, state: 'ready', attachment } : v));
    } catch (e) {
      if (!controller.signal.aborted) update(session, values => values.map(v => v.key === draft.key ? { ...v, state: 'error', error: e instanceof Error ? e.message : '附件上传失败' } : v));
    } finally { controllers.current.delete(draft.key); }
  };
  const add = (session: string, files: File[]) => {
    if (!limits) return '附件服务尚未就绪';
    if ((current.current[session]?.length ?? 0) + files.length > limits.maxPerMessage) return `每条消息最多添加 ${limits.maxPerMessage} 个附件`;
    for (const file of files) {
      if (!limits.extensions.includes('.' + file.name.split('.').at(-1)?.toLowerCase())) return `暂不支持 ${file.name} 的文件类型`;
      if (file.size > limits.maxFileBytes) return `${file.name} 超过 ${limits.maxFileBytes / 1024 / 1024} MiB`;
      if (file.name.length > 200) return '文件名最多 200 个字符';
    }
    const additions: AttachmentDraft[] = files.map(file => ({ key: createUuid(), file, state: 'uploading',
      preview: /\.(png|jpe?g|webp|gif)$/i.test(file.name) ? URL.createObjectURL(file) : undefined }));
    update(session, old => [...old, ...additions]);
    for (const draft of additions) void upload(session, draft);
  };
  const remove = (session: string, key: string) => {
    controllers.current.get(key)?.abort();
    const draft = current.current[session]?.find(v => v.key === key);
    if (draft?.preview) URL.revokeObjectURL(draft.preview);
    update(session, old => old.filter(v => v.key !== key));
  };
  const clearSent = (session: string, ids: string[]) => {
    for (const draft of current.current[session] ?? []) if (draft.attachment && ids.includes(draft.attachment.id)) remove(session, draft.key);
  };
  useEffect(() => {
    const active = controllers.current;
    return () => {
      active.forEach(c => c.abort()); active.clear();
      Object.values(current.current).flat().forEach(d => { if (d.preview) URL.revokeObjectURL(d.preview); });
    };
  }, []);
  return { drafts, add, remove, clearSent, retry: (session: string, draft: AttachmentDraft) => { void upload(session, draft); } };
}
