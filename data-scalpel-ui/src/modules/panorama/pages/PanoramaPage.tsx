import { workspaceResourceTheme } from '../../../shared/theme/workspaceResourceTheme';
import '../../../shared/theme/resource-workspace.css';
import { DeleteOutlined, EditOutlined, CameraOutlined, EnvironmentOutlined, UnorderedListOutlined, UploadOutlined, ReloadOutlined } from '@ant-design/icons';
import { ConfigProvider, Badge, Button, Empty, Form, Input, Modal, Segmented, Select, Table, Tooltip, message, type TableProps } from 'antd';
import { lazy, Suspense, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { ManagementName } from '../../../shared/components/ManagementListCells';
import { ManagementMoreFilters, ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { andSearch, searchComparison, searchContains, searchEquals } from '../../../shared/search';
import { DirectoryTreePanel, findDirectoryDescendantIds, useDirectoryTree, type DirectorySelection } from '../../directory';
import { useCurrentUser } from '../../system';
import { PanoramaEditDrawer } from '../components/PanoramaEditDrawer';
import { panoramaDeleteConfirmation } from '../components/panoramaDeleteConfirmation';
import { PanoramaImage } from '../components/PanoramaImage';
import { PanoramaUploadDrawer } from '../components/PanoramaUploadDrawer';
import { usePanoramas, usePanoramaCommand } from '../hooks/usePanoramas';
import { bytesLabel, captureLabel, processingLabels, type Panorama, type PanoramaQuery, type ProcessingStatus } from '../model/panorama';
import '../panorama.css';
const PanoramaMap = lazy(() => import('../components/PanoramaMap').then(module => ({ default: module.PanoramaMap })));
interface Filters { keyword?: string; status?: ProcessingStatus; location?: 'yes' | 'no'; from?: string; to?: string }
export const PanoramaPage = () => {
  const [form] = Form.useForm<Filters>(); const [filters, setFilters] = useState<Filters>({});
  const [selection, setSelection] = useState<DirectorySelection>(); const [page, setPage] = useState(0); const [size, setSize] = useState(20);
  const [view, setView] = useState<string>('list'); const [uploading, setUploading] = useState(false); const [editing, setEditing] = useState<Panorama>();
  const [more, setMore] = useState(false); const [advanced, setAdvanced] = useState<Pick<Filters, 'from' | 'to' | 'location'>>({});
  const [advancedDraft, setAdvancedDraft] = useState(advanced);
  const [messageApi, context] = message.useMessage(); const [modal, modalContext] = Modal.useModal();
  const navigate = useNavigate(); const user = useCurrentUser(); const permissions = user.data?.permissions ?? [];
  const directories = useDirectoryTree('PANORAMA', permissions.includes('directory.view'));
  const request: PanoramaQuery = useMemo(() => ({ search: andSearch(searchContains('name', filters.keyword), searchEquals('processingStatus', filters.status),
    filters.location === 'yes' ? 'latitude!null' : filters.location === 'no' ? 'latitude:null' : undefined,
    searchComparison('captureTime', '>=', filters.from), searchComparison('captureTime', '<=', filters.to)),
    directoryIds: selection ? findDirectoryDescendantIds(directories.data ?? [], selection) : undefined, uncategorized: selection === null,
    page, size, sort: '-updatedAt,id' }), [directories.data, filters, page, selection, size]);
  const totalsQuery = usePanoramas({ page: 0, size: 1 }, permissions.includes('directory.view'));
  const query = usePanoramas(request); const command = usePanoramaCommand();
  const apply = (values: Filters) => { setFilters({ ...values, ...advanced }); setPage(0); };
  const reset = () => { form.resetFields(); setFilters({}); setAdvanced({}); setAdvancedDraft({}); setSelection(undefined); setPage(0); };
  const remove = (p: Panorama) => modal.confirm({ ...panoramaDeleteConfirmation(p),
    onOk: async () => { try { await command.mutateAsync({ id: p.id, action: 'delete' }); messageApi.success('全景已删除'); } catch (e) { messageApi.error(e instanceof ApiError ? e.message : '删除失败'); throw e; } } });
  const columns: TableProps<Panorama>['columns'] = [
    { title: '全景影像', width: 280, render: (_: unknown, p) => <div className="panorama-name-cell">{p.currentContent ? <PanoramaImage id={p.id} version={p.contentVersion} /> : <span className="panorama-no-preview" aria-label="暂无预览"><CameraOutlined /></span>}<ManagementName name={p.name} description={p.description}><Button type="link" onClick={() => navigate(`/panorama/${p.id}`)} title={p.name}>{p.name}</Button></ManagementName></div> },
    { title: '拍摄时间', width: 210, render: (_: unknown, p) => captureLabel(p) },
    { title: 'WGS84 位置', width: 155, render: (_: unknown, p) => p.latitude == null ? '—' : `${p.longitude?.toFixed(5)}, ${p.latitude.toFixed(5)}` },
    { title: '尺寸 / 大小', width: 155, align: 'right', render: (_: unknown, p) => { const c = p.currentContent ?? p.candidateContent; return c ? <span>{c.width} × {c.height}<br />{bytesLabel(c.byteSize)}</span> : '—'; } },
    { title: '处理状态', width: 160, render: (_: unknown, p) => <div className="panorama-status-cell"><Badge status={p.processingStatus === 'FAILED' ? 'error' : p.processingStatus === 'READY' ? 'success' : 'processing'} text={processingLabels[p.processingStatus]} />{p.currentContent && p.candidateContent && <small>当前成品仍可浏览</small>}{p.processingError && <Tooltip title={p.processingError}><small className="panorama-processing-error">{p.processingError}</small></Tooltip>}</div> },
    { title: '操作', width: 100, fixed: 'right', render: (_: unknown, p) => (permissions.includes('panorama.update') || permissions.includes('panorama.delete')) && <div className="management-row-actions">
      {permissions.includes('panorama.update') && <Tooltip title="修改资料"><Button type="text" icon={<EditOutlined />} aria-label={`修改${p.name}`} onClick={() => setEditing(p)} /></Tooltip>}
      {permissions.includes('panorama.delete') && <Tooltip title={p.processingStatus === 'PROCESSING' ? '处理中暂不可删除' : '删除影像'}><span><Button type="text" danger disabled={p.processingStatus === 'PROCESSING'} icon={<DeleteOutlined />} aria-label={`删除${p.name}`} onClick={() => remove(p)} /></span></Tooltip>}
    </div> },
  ];
  return <ConfigProvider theme={workspaceResourceTheme}>{context}{modalContext}<div className={`${permissions.includes('directory.view') ? 'directory-management-layout' : 'page-stack'} panorama-page resource-workspace-list`}>
    {permissions.includes('directory.view') && <DirectoryTreePanel scope="PANORAMA" totalResourceCount={totalsQuery.data?.totalElements} tree={directories.data ?? []} loading={directories.isFetching} selection={selection} canManage={permissions.includes('directory.manage')} onSelectionChange={value => { setSelection(value); setPage(0); }} />}
    <section className="management-workbench"><div className="management-filter-strip">
      <Form form={form} autoComplete="off" layout="inline" className="management-filter-form" onFinish={apply}>
        <Form.Item name="keyword"><ManagementSearchInput placeholder="搜索全景名称" allowClear /></Form.Item>
        <Form.Item name="status"><Select placeholder="全部处理状态" allowClear style={{ width: 140 }} options={Object.entries(processingLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
        <ManagementMoreFilters count={Object.values(advanced).filter(Boolean).length} open={more} onOpenChange={open => { setMore(open); if (open) setAdvancedDraft(advanced); }} onClear={() => setAdvancedDraft({})} onCancel={() => { setMore(false); setAdvancedDraft(advanced); }} onConfirm={() => { setAdvanced(advancedDraft); setMore(false); }}>
          <div className="panorama-advanced-filters">
            <label htmlFor="panorama-location-filter">位置信息</label><Select id="panorama-location-filter" placeholder="全部位置" allowClear value={advancedDraft.location} onChange={location => setAdvancedDraft({ ...advancedDraft, location })} options={[{ value: 'yes', label: '有位置' }, { value: 'no', label: '无位置' }]} />
            <label htmlFor="panorama-capture-from">拍摄开始时间</label><Input id="panorama-capture-from" type="datetime-local" value={advancedDraft.from ?? ''} onChange={event => setAdvancedDraft({ ...advancedDraft, from: event.target.value })} />
            <label htmlFor="panorama-capture-to">拍摄结束时间</label><Input id="panorama-capture-to" type="datetime-local" value={advancedDraft.to ?? ''} onChange={event => setAdvancedDraft({ ...advancedDraft, to: event.target.value })} />
          </div>
        </ManagementMoreFilters>
      </Form><ManagementFilterActions form={form} appliedFilters={filters} additionalActive={selection !== undefined || Object.values(advanced).some(Boolean)} loading={query.isFetching} onReset={reset} /><div className="resource-list-commands"><Tooltip title="刷新全景影像"><Button icon={<ReloadOutlined />} aria-label="刷新全景影像" loading={query.isFetching} onClick={() => void query.refetch()} /></Tooltip>{permissions.includes('panorama.create') && <Button type="primary" icon={<UploadOutlined />} onClick={() => setUploading(true)}>上传成品</Button>}</div>
    </div><div className="management-results-surface"><div className="management-result-toolbar"><span className="management-result-title"><CameraOutlined aria-hidden />全景影像 {view === 'list' && <span className="management-result-count">共 {query.data?.totalElements ?? 0} 项</span>}</span><div className="management-result-actions">
      <Segmented options={[{ label: '列表', value: 'list', icon: <UnorderedListOutlined /> }, { label: '地图', value: 'map', icon: <EnvironmentOutlined /> }]} value={view} onChange={setView} />
    </div></div>
      {view === 'list' ? <>{query.isError && <InlineFeedback tone="error" label={query.error instanceof ApiError ? query.error.message : '全景列表加载失败'} action={<Button onClick={() => void query.refetch()}>重试</Button>} />}
        <Table<Panorama> className="management-table management-table-comfortable" size="small" rowKey="id" columns={columns} dataSource={query.data?.content ?? []} loading={query.isFetching} scroll={{ x: 1040, y: '100%' }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={Object.values(filters).some(Boolean) || selection !== undefined ? '没有符合条件的全景影像' : '还没有全景影像'}>{Object.values(filters).some(Boolean) || selection !== undefined ? <Button onClick={reset}>清空筛选</Button> : permissions.includes('panorama.create') && <Button type="primary" icon={<UploadOutlined />} onClick={() => setUploading(true)}>上传全景成品</Button>}</Empty> }} pagination={{ current: page + 1, pageSize: size, total: query.data?.totalElements ?? 0, size: 'small', position: ['bottomRight'], hideOnSinglePage: false, showSizeChanger: true, showTotal: total => `共 ${total} 项`, onChange: (next, nextSize) => { setPage(nextSize !== size ? 0 : next - 1); setSize(nextSize); } }} />
      </> : <Suspense fallback="正在加载地图…"><PanoramaMap query={{ ...request, page: undefined, size: undefined }} revision={query.dataUpdatedAt} /></Suspense>}
    </div></section></div>{uploading && <PanoramaUploadDrawer defaultDirectoryId={selection ?? undefined} onClose={() => setUploading(false)} />}{editing && <PanoramaEditDrawer panorama={editing} onClose={() => setEditing(undefined)} />}</ConfigProvider>;
};
