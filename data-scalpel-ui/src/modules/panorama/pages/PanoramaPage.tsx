import { DeleteOutlined, EditOutlined, EllipsisOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Dropdown, Form, Input, Modal, Segmented, Select, Table, Tooltip, message, type TableProps } from 'antd';
import { lazy, Suspense, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { ManagementAdaptiveMoreFilters, ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { andSearch, searchComparison, searchContains, searchEquals } from '../../../shared/search';
import { DirectoryTreePanel, findDirectoryDescendantIds, useDirectoryTree, type DirectorySelection } from '../../directory';
import { useCurrentUser } from '../../system';
import { PanoramaEditDrawer } from '../components/PanoramaEditDrawer';
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
  const [more, setMore] = useState(false); const [advanced, setAdvanced] = useState<Pick<Filters, 'from' | 'to'>>({});
  const [advancedDraft, setAdvancedDraft] = useState(advanced);
  const [messageApi, context] = message.useMessage(); const [modal, modalContext] = Modal.useModal();
  const navigate = useNavigate(); const user = useCurrentUser(); const permissions = user.data?.permissions ?? [];
  const directories = useDirectoryTree('PANORAMA', permissions.includes('directory.view'));
  const request: PanoramaQuery = useMemo(() => ({ search: andSearch(searchContains('name', filters.keyword), searchEquals('processingStatus', filters.status),
    filters.location === 'yes' ? 'latitude!null' : filters.location === 'no' ? 'latitude:null' : undefined,
    searchComparison('captureTime', '>=', filters.from), searchComparison('captureTime', '<=', filters.to)),
    directoryIds: selection ? findDirectoryDescendantIds(directories.data ?? [], selection) : undefined, uncategorized: selection === null,
    page, size, sort: '-updatedAt,id' }), [directories.data, filters, page, selection, size]);
  const query = usePanoramas(request); const command = usePanoramaCommand();
  const apply = (values: Filters) => { setFilters({ ...values, ...advanced }); setPage(0); };
  const reset = () => { form.resetFields(); setFilters({}); setAdvanced({}); setAdvancedDraft({}); setSelection(undefined); setPage(0); };
  const remove = (p: Panorama) => modal.confirm({ title: '删除全景影像', content: `确认删除“${p.name}”及其原图和预览文件吗？`, okText: '删除', cancelText: '取消', okButtonProps: { danger: true },
    onOk: async () => { try { await command.mutateAsync({ id: p.id, action: 'delete' }); messageApi.success('全景已删除'); } catch (e) { messageApi.error(e instanceof ApiError ? e.message : '删除失败'); throw e; } } });
  const columns: TableProps<Panorama>['columns'] = [
    { title: '全景影像', width: 280, render: (_: unknown, p) => <div className="panorama-name-cell">{p.currentContent ? <PanoramaImage id={p.id} version={p.contentVersion} /> : <span className="panorama-no-preview">待处理</span>}<Button type="link" onClick={() => navigate(`/panorama/${p.id}`)} title={p.name}>{p.name}</Button></div> },
    { title: '拍摄时间', width: 210, render: (_: unknown, p) => captureLabel(p) },
    { title: 'WGS84 位置', width: 155, render: (_: unknown, p) => p.latitude == null ? '—' : `${p.longitude?.toFixed(5)}, ${p.latitude.toFixed(5)}` },
    { title: '尺寸 / 大小', width: 155, align: 'right', render: (_: unknown, p) => { const c = p.currentContent ?? p.candidateContent; return c ? <span>{c.width} × {c.height}<br />{bytesLabel(c.byteSize)}</span> : '—'; } },
    { title: '处理状态', width: 140, render: (_: unknown, p) => <span>{processingLabels[p.processingStatus]}{p.currentContent && p.candidateContent && <small> · 当前成品可用</small>}</span> },
    { title: '操作', width: 100, render: (_: unknown, p) => (permissions.includes('panorama.update') || permissions.includes('panorama.delete')) && <div className="management-row-actions">
      <div className="management-row-actions-shortcuts">{permissions.includes('panorama.update') && <Tooltip title="修改资料"><Button type="text" icon={<EditOutlined />} aria-label={`修改${p.name}`} onClick={() => setEditing(p)} /></Tooltip>}</div>
      <Dropdown trigger={['click']} menu={{ items: [
        ...(permissions.includes('panorama.update') ? [{ key: 'edit', label: '修改资料', icon: <EditOutlined />, onClick: () => setEditing(p) }] : []),
        ...(permissions.includes('panorama.delete') ? [{ key: 'delete', label: '删除', danger: true, disabled: p.processingStatus === 'PROCESSING', icon: <DeleteOutlined />, onClick: () => remove(p) }] : []),
      ] }}><Button className="management-row-actions-more" type="text" icon={<EllipsisOutlined />} aria-label={`${p.name}的更多操作`} /></Dropdown>
    </div> },
  ];
  return <>{context}{modalContext}<div className={`${permissions.includes('directory.view') ? 'directory-management-layout' : 'page-stack'} panorama-page`}>
    {permissions.includes('directory.view') && <DirectoryTreePanel scope="PANORAMA" tree={directories.data ?? []} loading={directories.isFetching} selection={selection} canManage={permissions.includes('directory.manage')} onSelectionChange={value => { setSelection(value); setPage(0); }} />}
    <section className="management-workbench"><div className="management-filter-strip">
      <Form form={form} autoComplete="off" layout="inline" className="management-filter-form" onFinish={apply}>
        <Form.Item name="keyword"><ManagementSearchInput placeholder="搜索全景名称" allowClear /></Form.Item>
        <Form.Item name="status"><Select placeholder="全部处理状态" allowClear style={{ width: 140 }} options={Object.entries(processingLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
        <Form.Item name="location"><Select placeholder="全部位置" allowClear style={{ width: 120 }} options={[{ value: 'yes', label: '有位置' }, { value: 'no', label: '无位置' }]} /></Form.Item>
        <ManagementAdaptiveMoreFilters count={Object.values(advanced).filter(Boolean).length} open={more} onOpenChange={open => { setMore(open); if (open) setAdvancedDraft(advanced); }} onClear={() => setAdvancedDraft({})} onCancel={() => { setMore(false); setAdvancedDraft(advanced); }} onConfirm={() => { setAdvanced(advancedDraft); setMore(false); }}>
          <div className="panorama-time-filters"><Input aria-label="拍摄开始时间" type="datetime-local" value={more ? advancedDraft.from : advanced.from} onChange={event => more ? setAdvancedDraft({ ...advancedDraft, from: event.target.value }) : setAdvanced({ ...advanced, from: event.target.value })} /><span>至</span><Input aria-label="拍摄结束时间" type="datetime-local" value={more ? advancedDraft.to : advanced.to} onChange={event => more ? setAdvancedDraft({ ...advancedDraft, to: event.target.value }) : setAdvanced({ ...advanced, to: event.target.value })} /></div>
        </ManagementAdaptiveMoreFilters>
      </Form><ManagementFilterActions form={form} appliedFilters={filters} additionalActive={selection !== undefined || !!advanced.from || !!advanced.to} loading={query.isFetching} onReset={reset} />
    </div><div className="management-results-surface"><div className="management-result-toolbar"><span className="management-result-title">全景影像 {view === 'list' && <span className="management-result-count">共 {query.data?.totalElements ?? 0} 项</span>}</span><div className="management-result-actions">
      <Segmented options={[{ label: '列表', value: 'list' }, { label: '地图', value: 'map' }]} value={view} onChange={setView} />
      {view === 'list' && <Button type="text" icon={<ReloadOutlined />} aria-label="刷新全景列表" onClick={() => void query.refetch()} />}
      {permissions.includes('panorama.create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => setUploading(true)}>上传成品</Button>}
    </div></div>
      {view === 'list' ? <>{query.isError && <InlineFeedback tone="error" label={query.error instanceof ApiError ? query.error.message : '全景列表加载失败'} action={<Button onClick={() => void query.refetch()}>重试</Button>} />}
        <Table<Panorama> className="management-table management-table-comfortable" size="small" rowKey="id" columns={columns} dataSource={query.data?.content ?? []} loading={query.isFetching} scroll={{ y: '100%' }} pagination={{ current: page + 1, pageSize: size, total: query.data?.totalElements ?? 0, size: 'small', position: ['bottomRight'], hideOnSinglePage: false, showSizeChanger: true, showTotal: total => `共 ${total} 项`, onChange: (next, nextSize) => { setPage(nextSize !== size ? 0 : next - 1); setSize(nextSize); } }} />
      </> : <Suspense fallback="正在加载地图…"><PanoramaMap query={{ ...request, page: undefined, size: undefined }} revision={query.dataUpdatedAt} /></Suspense>}
    </div></section></div>{uploading && <PanoramaUploadDrawer defaultDirectoryId={selection ?? undefined} onClose={() => setUploading(false)} />}{editing && <PanoramaEditDrawer panorama={editing} onClose={() => setEditing(undefined)} />}</>;
};
