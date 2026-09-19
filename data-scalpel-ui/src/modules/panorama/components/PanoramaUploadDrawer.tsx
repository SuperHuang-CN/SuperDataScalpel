import { createUuid } from '../../../shared/browser/createUuid';
import { CameraOutlined, UploadOutlined } from '@ant-design/icons';
import { useQueries, useQueryClient } from '@tanstack/react-query';
import { Button, Drawer, Form, List, Space, Tag, TreeSelect, Upload, message } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { directoryTreeSelectData, invalidateDirectoryTree, useDirectoryTree } from '../../directory';
import { useCurrentUser } from '../../system';
import { fetchPanorama, panoramaCommand, uploadPanorama } from '../api/panoramaApi';
import { usePageVisible } from '../hooks/usePanoramas';
import { isProcessing, processingLabels, type Panorama } from '../model/panorama';
interface UploadItem { file: File; requestId: string; status: 'waiting' | 'uploading' | 'accepted' | 'failed'; id?: string; error?: string }
export const PanoramaUploadDrawer = ({ onClose, defaultDirectoryId, target }: { onClose: () => void; defaultDirectoryId?: string; target?: Panorama }) => {
  const [items, setItems] = useState<UploadItem[]>([]); const [busy, setBusy] = useState(false);
  const [directoryId, setDirectoryId] = useState(defaultDirectoryId); const [messageApi, context] = message.useMessage();
  const [retrying, setRetrying] = useState<string>(); const controllers = useRef(new Set<AbortController>());
  const client = useQueryClient(); const visible = usePageVisible(); const user = useCurrentUser();
  const directories = useDirectoryTree('PANORAMA', user.data?.permissions.includes('directory.view') ?? false);
  useEffect(() => { const active = controllers.current; return () => { active.forEach(c => c.abort()); }; }, []);
  const accepted = items.filter(item => item.id);
  const processing = useQueries({ queries: accepted.map(item => ({ queryKey: ['panoramas', 'detail', item.id],
    queryFn: ({ signal }: { signal: AbortSignal }) => fetchPanorama(item.id!, signal),
    refetchInterval: (query: { state: { data?: Panorama } }) => visible && isProcessing(query.state.data) ? 2000 : false as const,
    refetchIntervalInBackground: false })) });
  const patch = (requestId: string, values: Partial<UploadItem>) => setItems(current => current.map(item => item.requestId === requestId ? { ...item, ...values } : item));
  const run = async (queue: UploadItem[]) => {
    setBusy(true); let cursor = 0;
    const worker = async () => {
      while (cursor < queue.length) {
        const item = queue[cursor++]; const controller = new AbortController(); controllers.current.add(controller);
        patch(item.requestId, { status: 'uploading', error: undefined });
        try {
          const panorama = await uploadPanorama(item.file, item.requestId, directoryId, target, controller.signal);
          client.setQueryData(['panoramas', 'detail', panorama.id], panorama);
          patch(item.requestId, { status: 'accepted', id: panorama.id });
        } catch (error) {
          if (!controller.signal.aborted) patch(item.requestId, { status: 'failed', error: error instanceof ApiError ? error.message : '上传失败，请重试' });
        } finally { controllers.current.delete(controller); }
      }
    };
    try { await Promise.all([worker(), worker()]); }
    finally { setBusy(false); void client.invalidateQueries({ queryKey: ['panoramas'] }); void invalidateDirectoryTree(client, 'PANORAMA'); }
  };
  const retryProcessing = async (p: Panorama) => {
    if (!p.candidateContent) return;
    setRetrying(p.id);
    try { await panoramaCommand(p.id, 'retry-processing', { candidateId: p.candidateContent.id }); void client.invalidateQueries({ queryKey: ['panoramas'] }); }
    catch (e) { messageApi.error(e instanceof ApiError ? e.message : '重试失败'); }
    finally { setRetrying(undefined); }
  };
  return <Drawer rootClassName="business-overlay business-drawer-overlay" open size={680} onClose={onClose} closable={!busy} maskClosable={!busy} keyboard={!busy}
    title={<Space><CameraOutlined />{target ? `替换“${target.name}”` : '上传全景成品'}</Space>}
    footer={<div className="panorama-drawer-footer"><span>{busy ? '正在上传，请保持页面打开' : `${items.length} 个成品`}</span><Space><Button disabled={busy} onClick={onClose}>关闭</Button><Button type="primary" loading={busy} disabled={!items.some(i => i.status === 'waiting')} onClick={() => void run(items.filter(i => i.status === 'waiting'))}>开始上传</Button></Space></div>}>
    {context}<Form layout="vertical" autoComplete="off">
      {!target && user.data?.permissions.includes('directory.view') && <Form.Item label="保存目录"><TreeSelect allowClear value={directoryId} onChange={setDirectoryId} treeData={directoryTreeSelectData(directories.data ?? [])} placeholder="未分类" /></Form.Item>}
      <Form.Item label="标准球形全景 JPEG" extra="完整 360°×180°、2:1；每张不超过 100 MiB，宽度不超过 32768 像素。">
        <Upload multiple={!target} accept=".jpg,.jpeg" showUploadList={false} disabled={busy || (!!target && items.some(i => i.status === 'accepted'))}
          beforeUpload={file => {
            if (file.size > 100 * 1024 * 1024) { messageApi.error(`${file.name} 超过 100 MiB`); return false; }
            const item: UploadItem = { file, requestId: createUuid(), status: 'waiting' };
            setItems(current => target ? [item] : [...current, item]); return false;
          }}><Button icon={<UploadOutlined />}>选择成品</Button></Upload>
      </Form.Item>
    </Form>
    <List className="management-list" dataSource={items} renderItem={item => {
      const query = processing[accepted.findIndex(value => value.requestId === item.requestId)]; const p = query?.data;
      return <List.Item actions={[
        item.status === 'failed' ? <Button key="retry" disabled={busy} onClick={() => void run([item])}>重新上传</Button> : null,
        user.data?.permissions.includes('panorama.update') && p?.processingStatus === 'FAILED' ? <Button key="process" loading={retrying === p.id} onClick={() => void retryProcessing(p)}>重试处理</Button> : null,
        item.status === 'waiting' ? <Button key="remove" disabled={busy} onClick={() => setItems(rows => rows.filter(row => row !== item))}>移除</Button> : null,
        query?.isError ? <Button key="refresh" onClick={() => void query.refetch()}>重试状态查询</Button> : null,
      ]}><List.Item.Meta title={item.file.name} description={item.error ?? p?.processingError ?? (query?.isError ? '处理状态读取失败' : undefined)} /><Tag>{p ? processingLabels[p.processingStatus] : ({ waiting: '待上传', uploading: '上传中', accepted: '已接收', failed: '上传失败' }[item.status])}</Tag></List.Item>;
    }} />
  </Drawer>;
};
