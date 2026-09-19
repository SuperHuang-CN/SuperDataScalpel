import { ArrowLeftOutlined, CameraOutlined, DeleteOutlined, DownloadOutlined, EditOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Descriptions, Empty, Modal, Result, Skeleton, Space, Tabs, Tag, Typography, message } from 'antd';
import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { useCurrentUser } from '../../system';
import { useDirectoryTree, type DirectoryTreeNode } from '../../directory';
import { fetchPanoramaImage } from '../api/panoramaApi';
import { PanoramaEditDrawer } from '../components/PanoramaEditDrawer';
import { PanoramaUploadDrawer } from '../components/PanoramaUploadDrawer';
import { usePanorama, usePanoramaCommand } from '../hooks/usePanoramas';
import { bytesLabel, captureLabel, isProcessing, processingLabels } from '../model/panorama';
import '../panorama.css';
const Viewer = lazy(() => import('../components/PanoramaViewer').then(module => ({ default: module.PanoramaViewer })));
export const PanoramaDetailPage = () => {
  const { id = '' } = useParams(); const navigate = useNavigate(); const query = usePanorama(id); const command = usePanoramaCommand();
  const [editing, setEditing] = useState(false); const [replacing, setReplacing] = useState(false); const [downloading, setDownloading] = useState(false);
  const [messageApi, context] = message.useMessage(); const [modal, modalContext] = Modal.useModal();
  const user = useCurrentUser(); const permissions = user.data?.permissions ?? []; const canUpdate = permissions.includes('panorama.update');
  const directories = useDirectoryTree('PANORAMA', permissions.includes('directory.view'));
  const downloadRequest = useRef<AbortController | null>(null);
  useEffect(() => () => downloadRequest.current?.abort(), []);
  if (query.isPending) return <Skeleton active />;
  const panorama = query.data;
  if (!panorama) return <Result status="error" title="全景详情加载失败" subTitle={query.error instanceof ApiError ? query.error.message : undefined} extra={<Space><Button onClick={() => navigate('/panorama')}>返回列表</Button><Button onClick={() => void query.refetch()}>重试</Button></Space>} />;
  const current = panorama.currentContent; const metadata = current?.metadata;
  const directoryName = (nodes: DirectoryTreeNode[]): string | undefined => {
    for (const node of nodes) { if (node.id === panorama.directoryId) return node.name; const nested = directoryName(node.children); if (nested) return nested; } return undefined;
  };
  const act = async (action: 'retry-processing' | 'discard-replacement') => {
    if (!panorama.candidateContent) return;
    try { await command.mutateAsync({ id, action, body: { candidateId: panorama.candidateContent.id } }); messageApi.success(action === 'retry-processing' ? '已排队重试' : '已放弃替换'); }
    catch (e) { messageApi.error(e instanceof ApiError ? e.message : '操作失败'); }
  };
  const download = async () => {
    const controller = new AbortController(); downloadRequest.current = controller; setDownloading(true);
    try { const blob = await fetchPanoramaImage(id, panorama.contentVersion, 'content', controller.signal); if (!controller.signal.aborted) downloadBlob(blob, current?.originalFilename ?? `${panorama.name}.jpg`); }
    catch (e) { if (!controller.signal.aborted) { messageApi.error(e instanceof ApiError ? e.message : '下载失败'); if (e instanceof ApiError && e.problem?.code === 'PANORAMA_CONTENT_CHANGED') void query.refetch(); } }
    finally { setDownloading(false); }
  };
  const remove = () => modal.confirm({ title: '删除全景影像', content: `确认删除“${panorama.name}”及其原图和预览文件吗？`, okText: '删除', cancelText: '取消', okButtonProps: { danger: true },
    onOk: async () => { try { await command.mutateAsync({ id, action: 'delete' }); messageApi.success('全景已删除'); navigate('/panorama'); } catch (e) { messageApi.error(e instanceof ApiError ? e.message : '删除失败'); throw e; } } });
  return <div className="panorama-detail-page business-detail-page">{context}{modalContext}
    <div className="business-detail-header"><Space wrap><Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/panorama')}>返回列表</Button><CameraOutlined /><Typography.Text strong>{panorama.name}</Typography.Text><Tag>{processingLabels[panorama.processingStatus]}</Tag></Space>
      <Space wrap><Button icon={<ReloadOutlined />} aria-label="刷新全景详情" onClick={() => void query.refetch()} />{current && <Button icon={<DownloadOutlined />} loading={downloading} onClick={() => void download()}>下载原图</Button>}
        {canUpdate && <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>修改资料</Button>}{canUpdate && <Button disabled={isProcessing(panorama)} onClick={() => setReplacing(true)}>替换成品</Button>}
        {permissions.includes('panorama.delete') && <Button danger icon={<DeleteOutlined />} aria-label={`删除${panorama.name}`} disabled={panorama.processingStatus === 'PROCESSING'} onClick={remove} />}
      </Space>
    </div>
    {query.isError && <InlineFeedback tone="error" label="详情刷新失败" action={<Button onClick={() => void query.refetch()}>重试</Button>} />}
    {panorama.candidateContent && <div className="panorama-candidate"><Space wrap><Tag>{current ? '候选替换' : '首次上传'}</Tag><span>{panorama.candidateContent.originalFilename}</span><span>{processingLabels[panorama.processingStatus]}</span>
      {panorama.processingError && <InlineFeedback tone="error" label={panorama.processingError} />}
      {canUpdate && panorama.processingStatus === 'FAILED' && <><Button loading={command.isPending} onClick={() => void act('retry-processing')}>重试处理</Button>{current && <Button loading={command.isPending} onClick={() => void act('discard-replacement')}>放弃本次替换</Button>}</>}
    </Space></div>}
    <Tabs className="business-detail-tabs panorama-detail-tabs" destroyOnHidden items={[
      { key: 'viewer', label: '全景浏览', children: current ? <Suspense fallback={<Skeleton active />}><Viewer id={id} version={panorama.contentVersion} /></Suspense> : <Empty description="全景尚未就绪，处理完成后即可浏览" /> },
      { key: 'info', label: '基本信息', children: <div className="panorama-info"><Descriptions bordered column={{ xs: 1, sm: 2 }} items={[
        { key: 'name', label: '名称', children: panorama.name }, { key: 'directory', label: '目录', children: panorama.directoryId ? directoryName(directories.data ?? []) ?? '—' : '未分类' },
        { key: 'description', label: '描述', children: panorama.description || '—', span: 2 },
        { key: 'time', label: `拍摄时间 · ${panorama.timeMode === 'AUTO' ? '文件' : '人工'}`, children: captureLabel(panorama) },
        { key: 'location', label: `WGS84 位置 · ${panorama.locationMode === 'AUTO' ? '文件' : '人工'}`, children: panorama.latitude == null ? '—' : `${panorama.longitude}, ${panorama.latitude}` },
        { key: 'version', label: '当前内容版本', children: panorama.contentVersion || '—' }, { key: 'filename', label: '原始文件名', children: current?.originalFilename ?? '—' },
        { key: 'size', label: '尺寸 / 大小', children: current ? `${current.width} × ${current.height} · ${bytesLabel(current.byteSize)}` : '—' },
        { key: 'camera', label: '设备', children: [metadata?.manufacturer, metadata?.cameraModel].filter(Boolean).join(' ') || '—' },
        { key: 'exif-alt', label: 'EXIF 高度（米）', children: metadata?.exifAltitude ?? '—' }, { key: 'abs-alt', label: 'DJI 绝对高度（米）', children: metadata?.djiAbsoluteAltitude ?? '—' },
        { key: 'rel-alt', label: 'DJI 相对高度（米）', children: metadata?.djiRelativeAltitude ?? '—' }, { key: 'yaw', label: '飞行器偏航角（非全景正北）', children: metadata?.aircraftYaw ?? '—' },
        { key: 'heading', label: '全景方向声明（度）', children: metadata?.panoramaHeading ?? '—' }, { key: 'projection', label: '投影声明', children: metadata?.projection ?? '用户声明的完整球形全景' },
        { key: 'file-time', label: '文件提取时间', children: metadata ? captureLabel(metadata) : '—' }, { key: 'file-location', label: '文件提取位置', children: metadata?.latitude != null ? `${metadata.longitude}, ${metadata.latitude}（${metadata.locationSource}）` : '—' },
        { key: 'sha', label: 'SHA-256', children: <Typography.Text copyable={!!current} className="panorama-hash">{current?.sha256 ?? '—'}</Typography.Text>, span: 2 },
      ]} />{metadata?.warnings.length ? <InlineFeedback tone="warning" label={metadata.warnings.join('；')} action={canUpdate ? <Button onClick={() => setEditing(true)}>补录资料</Button> : undefined} /> : null}</div> },
    ]} />
    {editing && <PanoramaEditDrawer panorama={panorama} onClose={() => setEditing(false)} />}{replacing && <PanoramaUploadDrawer target={panorama} onClose={() => setReplacing(false)} />}
  </div>;
};
