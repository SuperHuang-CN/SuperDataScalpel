import { CloseOutlined, DownloadOutlined, FileOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Image, Modal, Spin, Tooltip } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { dshApi } from '../api/dsh';
import type { Attachment } from '../model/types';
import type { AttachmentDraft } from '../hooks/useAttachmentDrafts';

const size = (bytes: number) => bytes < 1024 ? `${bytes} B` : bytes < 1048576 ? `${(bytes / 1024).toFixed(1)} KiB` : `${(bytes / 1048576).toFixed(1)} MiB`;
export function AttachmentDrafts({ items, disabled, remove, retry }: { items: AttachmentDraft[]; disabled: boolean;
  remove: (key: string) => void; retry: (value: AttachmentDraft) => void }) {
  return <div className="dsh-attachments" aria-label="待发送附件">{items.map(item => <div className="dsh-attachment" key={item.key}>
    {item.preview ? <Image src={item.preview} width={52} height={44} alt={item.file.name} /> : <FileOutlined className="dsh-file-icon" />}
    <div className="dsh-attachment-info"><span title={item.file.name}>{item.file.name}</span><small>{size(item.file.size)} · {item.state === 'uploading' ? '正在上传' : item.state === 'error' ? '上传失败' : '已就绪'}</small>
      {item.error && <span role="alert" className="dsh-error">{item.error}</span>}</div>
    {item.state === 'uploading' && <Spin size="small" />}
    {item.state === 'error' && <Tooltip title="重试上传"><Button type="text" size="small" icon={<ReloadOutlined />} aria-label={`重试上传 ${item.file.name}`} disabled={disabled} onClick={() => retry(item)} /></Tooltip>}
    <Tooltip title="移除附件"><Button type="text" size="small" icon={<CloseOutlined />} aria-label={`移除附件 ${item.file.name}`} disabled={disabled} onClick={() => remove(item.key)} /></Tooltip>
  </div>)}</div>;
}

export function SentAttachment({ user, sessionId, attachment }: { user: string; sessionId: string; attachment: Attachment }) {
  const [preview, setPreview] = useState(false); const [loading, setLoading] = useState(false); const [error, setError] = useState('');
  const thumbnail = useRef<HTMLImageElement>(null); const fullImage = useRef<HTMLImageElement>(null);
  const image = useQuery({ queryKey: ['dsh', user, 'attachment', sessionId, attachment.id],
    queryFn: ({ signal }) => dshApi.attachment(sessionId, attachment.id, signal), enabled: attachment.kind === 'image', retry: false, staleTime: Infinity, gcTime: 0 });
  useEffect(() => {
    if (!image.data) return;
    const objectUrl = URL.createObjectURL(image.data);
    if (thumbnail.current) thumbnail.current.src = objectUrl;
    if (fullImage.current) fullImage.current.src = objectUrl;
    return () => URL.revokeObjectURL(objectUrl);
  }, [image.data, preview]);
  const download = async () => {
    setLoading(true); setError('');
    try {
      const blob = image.data ?? await dshApi.attachment(sessionId, attachment.id);
      const href = URL.createObjectURL(blob); const link = document.createElement('a');
      link.href = href; link.download = attachment.name; link.click();
      window.setTimeout(() => URL.revokeObjectURL(href), 1000);
    } catch (e) { setError(e instanceof Error ? e.message : '附件下载失败'); }
    finally { setLoading(false); }
  };
  return <div className={`dsh-sent-attachment ${attachment.kind === 'image' ? 'is-image' : ''}`}>
    {image.data && <button className="dsh-image-preview" aria-label={`预览 ${attachment.name}`} onClick={() => setPreview(true)}><img ref={thumbnail} alt={attachment.name} /></button>}
    <Modal title={attachment.name} open={preview} onCancel={() => setPreview(false)} footer={null} destroyOnHidden width="min(90vw, 1000px)" afterOpenChange={() => {
      if (fullImage.current && thumbnail.current) fullImage.current.src = thumbnail.current.src;
    }}><img ref={fullImage} className="dsh-image-full" alt={attachment.name} /></Modal>
    {image.isFetching && <Spin size="small" />}
    <div className="dsh-attachment"><FileOutlined /><div className="dsh-attachment-info"><span title={attachment.name}>{attachment.name}</span><small>{size(attachment.bytes)}</small></div>
      <Tooltip title="下载附件"><Button type="text" icon={<DownloadOutlined />} aria-label={`下载 ${attachment.name}`} loading={loading} onClick={() => void download()} /></Tooltip></div>
    {(error || image.error) && <div className="dsh-error" role="alert">{error || image.error?.message}
      {image.error && <Button type="link" size="small" onClick={() => void image.refetch()}>重试预览</Button>}</div>}
  </div>;
}
