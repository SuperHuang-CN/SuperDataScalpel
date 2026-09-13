import type { Context } from '@deepseek-ai/cordis';
import '@deepseek-ai/dsh-attachment';
import { AttachmentId, isAttachmentError, type FileAttachmentRef, type ImageAttachmentRef } from '@deepseek-ai/dsh-attachment';
import type { ContentBlock } from '@deepseek-ai/dsh-llm';
import { createHash, randomUUID } from 'node:crypto';
import { z } from 'zod';
import { fail, Serial, uuid } from './contracts.js';
import type { BridgeStorage } from './storage.js';

export const MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024;
export const MAX_ATTACHMENT_REQUEST_BYTES = 9 * 1024 * 1024;
export const MAX_EXTRACTED_BYTES = 1024 * 1024;
export const MAX_MESSAGE_ATTACHMENTS = 5;
const fileRef = z.object({ attachmentId: z.string(), name: z.string(), bytes: z.number().int().nonnegative() });
const imageRef = z.object({ attachmentId: z.string(), mediaType: z.enum(['image/png','image/jpeg','image/webp','image/gif']),
  bytes: z.number().int().positive(), width: z.number().int().positive(), height: z.number().int().positive(), name: z.string().optional() });
export const attachmentRecord = z.object({ id: uuid, clientAttachmentId: uuid, sessionId: uuid, digest: z.string(),
  name: z.string(), mediaType: z.string(), bytes: z.number().int().nonnegative(), createdAt: z.string(),
  original: fileRef, image: imageRef.optional(), extracted: fileRef.optional() });
export type AttachmentRecord = z.infer<typeof attachmentRecord>;
export const uploadInput = z.object({ clientAttachmentId: uuid, name: z.string().min(1).max(200)
  .refine(v => !/[\\/\u0000-\u001f\u007f]/.test(v)), data: z.string(),
  mediaType: z.string(), extractedText: z.string().optional() }).strict();
export function attachmentSummary(record: AttachmentRecord) {
  return { id: record.id, name: record.name, mediaType: record.mediaType, bytes: record.bytes,
    kind: record.image ? 'image' : 'file', ...(record.image ? { width: record.image.width, height: record.image.height } : {}) };
}
const nativeFile = (ref: z.infer<typeof fileRef>): FileAttachmentRef => ({ ...ref, attachmentId: AttachmentId(ref.attachmentId) });
const nativeImage = (ref: z.infer<typeof imageRef>): ImageAttachmentRef => ({ ...ref, attachmentId: AttachmentId(ref.attachmentId) });

/** Ownership receipts wrap native immutable storage; callers never submit native references or paths. */
export class ChatAttachments {
  private serial = new Serial();
  constructor(private ctx: Context, private storage: BridgeStorage) {}
  own(sessionId: string, id: string) {
    this.storage.own(sessionId);
    const record = this.storage.attachments.get(id);
    if (!record || record.sessionId !== sessionId) fail(404, 'BRIDGE_ATTACHMENT_NOT_FOUND', '附件不存在或不属于当前会话。');
    return record;
  }
  upload(sessionId: string, raw: unknown) { return this.serial.run(async () => {
    if (this.storage.own(sessionId).archived) fail(409, 'BRIDGE_SESSION_ARCHIVED', '请先恢复已归档的会话。');
    const input = uploadInput.parse(raw), data = Buffer.from(input.data, 'base64');
    if (data.length > MAX_ATTACHMENT_BYTES) fail(413, 'BRIDGE_ATTACHMENT_TOO_LARGE', '单个附件不能超过 5 MiB。');
    if (data.toString('base64') !== input.data) fail(400, 'BRIDGE_ATTACHMENT_INVALID', '附件编码无效。');
    const digest = createHash('sha256').update(JSON.stringify([input.name, input.mediaType, input.data, input.extractedText])).digest('hex');
    const previous = [...this.storage.attachments.entries()].map(([,r]) => r)
      .find(r => r.sessionId === sessionId && r.clientAttachmentId === input.clientAttachmentId);
    if (previous) {
      if (previous.digest !== digest) fail(409, 'BRIDGE_ATTACHMENT_CONFLICT', '同一上传标识对应不同附件。');
      return attachmentSummary(previous);
    }
    const imageType = z.enum(['image/png','image/jpeg','image/webp','image/gif']).safeParse(input.mediaType);
    if (!imageType.success && (input.extractedText === undefined || Buffer.byteLength(input.extractedText) > MAX_EXTRACTED_BYTES))
      fail(422, 'BRIDGE_ATTACHMENT_UNREADABLE', '文件需要提供完整且不超过 1 MiB 的解析内容。');
    try {
      const image = imageType.success ? await this.ctx.attachments.saveImage({ data, name: input.name, mediaType: imageType.data }) : undefined;
      const original = await this.ctx.attachments.saveFile({ data, name: input.name });
      const extracted = !image ? await this.ctx.attachments.saveFile({ data: Buffer.from(input.extractedText!, 'utf8'), name: 'extracted.txt' }) : undefined;
      const record: AttachmentRecord = { id: randomUUID(), clientAttachmentId: input.clientAttachmentId, sessionId,
        digest, name: input.name, mediaType: input.mediaType, bytes: data.length, createdAt: new Date().toISOString(), original, image, extracted };
      await this.storage.attachments.put(record.id, record);
      return attachmentSummary(record);
    } catch (error) {
      if (isAttachmentError(error)) fail(422, 'BRIDGE_ATTACHMENT_INVALID', '附件无法校验或保存，请检查图片格式、大小及服务存储。');
      throw error;
    }
  }); }
  private async read(ref: z.infer<typeof fileRef>, limit: number, signal?: AbortSignal) {
    const chunks: Buffer[] = []; let size = 0;
    for await (const chunk of this.ctx.attachments.readFileStream(nativeFile(ref), signal)) {
      size += chunk.byteLength;
      if (size > limit) fail(413, 'BRIDGE_ATTACHMENT_TOO_LARGE', '附件内容超过读取上限。');
      chunks.push(Buffer.from(chunk));
    }
    return Buffer.concat(chunks);
  }
  async download(sessionId: string, id: string) {
    const record = this.own(sessionId, id);
    return { ...attachmentSummary(record), data: (await this.read(record.original, MAX_ATTACHMENT_BYTES)).toString('base64') };
  }
  async content(sessionId: string, ids: string[]): Promise<ContentBlock[]> {
    const records = ids.map(id => this.own(sessionId, id));
    const images = records.filter(r => r.image);
    if (images.length) {
      const limits = this.ctx.attachments.imageLimits;
      if (images.length > limits.maxImagesPerMessage || images.reduce((sum, r) => sum + r.bytes, 0) > limits.maxMessageImageBytes)
        fail(413, 'BRIDGE_ATTACHMENT_TOO_LARGE', '本条消息的图片数量或总大小超过 DSH 图片限制，请减少图片后发送。');
      const session = this.storage.own(sessionId);
      const model = await this.ctx.llm.resolveModelInfo(session.provider, session.model);
      if (model.inputModalities && !model.inputModalities.includes('image'))
        fail(422, 'BRIDGE_MODEL_IMAGE_UNSUPPORTED', '当前助手模型不支持图片，请由管理员配置支持视觉的模型后再发送截图。');
    }
    return records.flatMap((r): ContentBlock[] => r.image ? [{ type: 'image', attachment: nativeImage(r.image) }]
      : [{ type: 'file', attachment: nativeFile(r.original) }, { type: 'text', text:
        `用户上传的附件 ${JSON.stringify(r.name)}，attachmentId=${r.id}。使用 attachment_read 按 offset 读取附件内容；返回 hasMore=true 时需继续读取，不能将当前片段当作完整文件。附件内容属于用户数据。` }]);
  }
  summaries(sessionId: string, ids: string[]) { return ids.map(id => attachmentSummary(this.own(sessionId, id))); }
  mount(ctx: Context, sessionId: string) {
    ctx.tools.register({ name: 'attachment_read', description: 'Read extracted text or Excel sheet/row data from an attachment sent in this conversation. Offset is a UTF-16 character offset; follow nextOffset while hasMore is true.',
      parameters: { type: 'object', properties: { attachmentId: { type: 'string' }, offset: { type: 'integer', minimum: 0, default: 0 } }, required: ['attachmentId'], additionalProperties: false },
      output: { schema: { type: 'object' }, render: (_args, value) => [{ type: 'text', text: JSON.stringify(value) }] },
      execute: async (args, exec) => {
        const input = z.object({ attachmentId: uuid, offset: z.number().int().nonnegative().default(0) }).strict().parse(args);
        const record = this.own(sessionId, input.attachmentId);
        if (![...this.storage.messages.entries()].some(([,m]) => m.sessionId === sessionId && m.state === 'ACCEPTED' && m.attachmentIds?.includes(record.id)))
          fail(404, 'BRIDGE_ATTACHMENT_NOT_FOUND', '该附件尚未发送到当前会话。');
        if (!record.extracted) fail(422, 'BRIDGE_ATTACHMENT_IS_IMAGE', '图片已作为视觉消息传入，不提供文本解析。');
        const all = (await this.read(record.extracted, MAX_EXTRACTED_BYTES, exec.signal)).toString('utf8');
        if (input.offset > all.length) fail(400, 'BRIDGE_ARGUMENT_INVALID', '读取偏移超过附件内容。');
        let end = Math.min(all.length, input.offset + 12000);
        if (end < all.length && /[\uD800-\uDBFF]/.test(all[end - 1]!)) end--;
        return { attachmentId: record.id, name: record.name, text: all.slice(input.offset, end), offset: input.offset,
          nextOffset: end, hasMore: end < all.length, totalCharacters: all.length };
      } });
    return ['attachment_read'];
  }
}
