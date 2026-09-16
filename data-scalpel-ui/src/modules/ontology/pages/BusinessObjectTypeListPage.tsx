import {
  ApartmentOutlined,
  EditOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { Button, Dropdown, Form, Segmented, Select, Space, Table, Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { DirectoryTreePanel, findDirectoryDescendantIds, useDirectoryTree, type DirectorySelection } from '../../directory';
import { useCurrentUser } from '../../system';
import { andSearch, orSearch, searchContains, searchEquals } from '../../../shared/search';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { ManagementCode, ManagementDateTime, ManagementListCell } from '../../../shared/components/ManagementListCells';
import { ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { BusinessOntologyGraphWorkspace } from '../components/BusinessOntologyGraphWorkspace';
import { BusinessObjectTypeBasicsDrawer } from '../components/BusinessObjectTypeBasicsDrawer';
import { useBusinessObjectTypeGraph, useBusinessObjectTypes } from '../hooks/useBusinessObjectTypes';
import type { BusinessObjectType, BusinessObjectTypeGraphRelation } from '../model/businessObjectType';
import '../ontology.css';

interface Filters { keyword?: string; enabled?: 'true' | 'false' }

const directoryFromParams = (value: string | null): DirectorySelection => value === null
  ? undefined
  : value === 'uncategorized' ? null : value;

export const BusinessObjectTypeListPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const user = useCurrentUser();
  const permissions = new Set(user.data?.permissions ?? []);
  const canManage = permissions.has('ontology.manage');
  const canViewDirectories = permissions.has('directory.view');
  const directories = useDirectoryTree('BUSINESS_OBJECT', canViewDirectories);
  const [navigationTab, setNavigationTab] = useState<'directories' | 'resources'>('directories');
  const [form] = Form.useForm<Filters>();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [editing, setEditing] = useState<BusinessObjectType | 'new' | null>(null);
  const view = params.get('view') === 'graph' ? 'graph' : 'list';
  const focusId = params.get('focusId') ?? undefined;
  const directory = directoryFromParams(params.get('directory'));
  const filterKeyword = params.get('keyword') || undefined;
  const filterEnabled = params.get('enabled') === 'true' || params.get('enabled') === 'false'
    ? params.get('enabled') as 'true' | 'false'
    : undefined;
  const filters: Filters = {
    keyword: filterKeyword,
    enabled: filterEnabled,
  };

  useEffect(() => {
    form.setFieldsValue({ keyword: filterKeyword, enabled: filterEnabled });
  }, [filterEnabled, filterKeyword, form]);

  const updateParams = (updates: Record<string, string | undefined>, replace = false) => {
    const next = new URLSearchParams(params);
    Object.entries(updates).forEach(([key, value]) => value === undefined || value === '' ? next.delete(key) : next.set(key, value));
    setParams(next, { replace });
  };
  const selectDirectory = (next: DirectorySelection) => {
    updateParams({ directory: next === undefined ? undefined : next === null ? 'uncategorized' : next, focusId: undefined });
    setPage(0);
  };
  const directoryIds = useMemo(() => directory === undefined || directory === null
    ? []
    : findDirectoryDescendantIds(directories.data ?? [], directory), [directories.data, directory]);
  const directoryFilter = directory === undefined ? undefined : directory === null ? 'directoryId:null'
    : orSearch(...directoryIds.map((id) => searchEquals('directoryId', id)));
  const request = {
    page, size, sort: '-updatedAt',
    search: andSearch(
      orSearch(searchContains('name', filters.keyword), searchContains('code', filters.keyword)),
      filters.enabled === undefined ? undefined : searchEquals('enabled', filters.enabled),
      directoryFilter,
    ),
  };
  const query = useBusinessObjectTypes(request, view === 'list');
  const graphQuery = useBusinessObjectTypeGraph(view === 'graph');
  const graphScopeNodes = useMemo(() => {
    const keyword = filterKeyword?.trim().toLowerCase();
    const acceptedDirectories = new Set(directoryIds);
    return (graphQuery.data?.nodes ?? []).filter((node) => {
      if (keyword && !node.name.toLowerCase().includes(keyword) && !node.code.toLowerCase().includes(keyword)) return false;
      if (filterEnabled !== undefined && node.enabled !== (filterEnabled === 'true')) return false;
      if (directory === null && node.directoryId !== null) return false;
      if (typeof directory === 'string' && !acceptedDirectories.has(node.directoryId ?? '')) return false;
      return true;
    });
  }, [directory, directoryIds, filterEnabled, filterKeyword, graphQuery.data?.nodes]);

  const openFromGraph = (id: string, tab?: string, relationId?: string) => {
    const detailParams = new URLSearchParams();
    if (tab) detailParams.set('tab', tab);
    if (relationId) detailParams.set('relationId', relationId);
    const returnParams = new URLSearchParams(location.search);
    returnParams.set('view', 'graph');
    returnParams.set('focusId', id);
    detailParams.set('returnTo', `${location.pathname}?${returnParams.toString()}`);
    navigate(`/business-object-types/${id}?${detailParams.toString()}`);
  };
  const columns: ColumnsType<BusinessObjectType> = [
    { title: '对象类型 / 编码', key: 'identity', width: 270, render: (_, item) => <ManagementListCell primary={<Button type="link" size="small" onClick={() => navigate(`/business-object-types/${item.id}`)}>{item.name}</Button>} secondary={<ManagementCode value={item.code} />} /> },
    { title: '主来源', key: 'source', width: 200, render: (_, item) => item.mainSourceModelName ?? '尚未配置' },
    { title: '属性 / 关系', key: 'counts', width: 125, render: (_, item) => <ManagementListCell primary={`${item.propertyCount} 个属性`} secondary={`${item.relationCount} 条可访问关系`} /> },
    { title: '状态', key: 'enabled', width: 110, render: (_, item) => <Tag color={item.enabled ? 'success' : 'default'}>{item.enabled ? '启用' : '停用'}</Tag> },
    { title: '更新时间', dataIndex: 'updatedAt', width: 170, render: value => <ManagementDateTime value={value} /> },
    { title: '操作', key: 'actions', width: 70, render: (_, item) => canManage ? <div className="management-row-actions"><div className="management-row-actions-shortcuts"><Tooltip title="修改资料"><Button type="text" size="small" icon={<EditOutlined />} aria-label={`修改${item.name}资料`} onClick={() => setEditing(item)} /></Tooltip></div><Dropdown trigger={['click']} menu={{ items: [{ key: 'open', label: '打开建模' }, { key: 'edit', label: '修改资料' }], onClick: ({ key }) => key === 'open' ? navigate(`/business-object-types/${item.id}`) : setEditing(item) }}><Button type="text" size="small" icon={<MoreOutlined />} aria-label={`${item.name}更多操作`} /></Dropdown></div> : null },
  ];
  const objectNavigation = (
    <div className="ontology-object-navigation">
      <div className="ontology-object-navigation-summary">当前筛选范围 <strong>{graphScopeNodes.length}</strong></div>
      <div className="ontology-object-navigation-list">
        {graphScopeNodes.map((node) => (
          <button
            type="button"
            key={node.id}
            className={focusId === node.id ? 'is-active' : undefined}
            onClick={() => updateParams({ view: 'graph', focusId: node.id })}
          >
            <span><strong>{node.name}</strong><ManagementCode value={node.code} /></span>
            <Tag color={node.enabled ? 'success' : 'default'}>{node.enabled ? '启用' : '停用'}</Tag>
          </button>
        ))}
        {!graphQuery.isLoading && graphScopeNodes.length === 0 && <div className="ontology-object-navigation-empty">当前范围没有对象类型</div>}
      </div>
    </div>
  );
  const allTypesEmpty = !graphQuery.isLoading && (graphQuery.data?.nodes.length ?? 0) === 0;
  const graphEmptyDescription = allTypesEmpty
    ? '全平台尚未创建业务对象类型'
    : directory !== undefined && !filters.keyword && filters.enabled === undefined
      ? '当前目录没有业务对象类型'
      : '没有符合当前筛选条件的对象类型';

  return <>
    <div className={canViewDirectories ? 'directory-management-layout ontology-management-layout' : 'page-stack ontology-management-layout'}>
      {canViewDirectories && <DirectoryTreePanel
        scope="BUSINESS_OBJECT"
        label="业务建模目录"
        tree={directories.data ?? []}
        loading={directories.isFetching}
        selection={directory}
        canManage={permissions.has('directory.manage')}
        onSelectionChange={selectDirectory}
        navigationTabs={view === 'graph' ? { activeKey: navigationTab, resourceLabel: '对象', resourceContent: objectNavigation, onChange: setNavigationTab } : undefined}
      />}
      <section className="management-workbench">
        <div className="management-filter-strip"><Form form={form} autoComplete="off" layout="inline" className="management-filter-form" onFinish={(value) => { updateParams({ keyword: value.keyword?.trim() || undefined, enabled: value.enabled, focusId: undefined }); setPage(0); }}>
          <Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索对象类型名称或编码" /></Form.Item>
          <Form.Item name="enabled"><Select allowClear placeholder="全部状态" options={[{ value: 'true', label: '启用' }, { value: 'false', label: '停用' }]} /></Form.Item>
          <Space><Button type="primary" htmlType="submit">查询</Button><Button type="text" onClick={() => { form.resetFields(); const next = new URLSearchParams(); if (view === 'graph') next.set('view', 'graph'); setParams(next); setPage(0); }}>重置</Button></Space>
        </Form></div>
        <div className={`management-results-surface${view === 'graph' ? ' ontology-graph-results' : ''}`}>
          <div className="management-result-toolbar">
            <div className="ontology-result-heading">
              <Segmented
                value={view}
                options={[
                  { label: <span><UnorderedListOutlined /> 列表</span>, value: 'list' },
                  { label: <span><ApartmentOutlined /> 本体总览</span>, value: 'graph' },
                ]}
                onChange={(next) => updateParams({ view: next === 'graph' ? 'graph' : undefined })}
              />
              <span className="management-result-count">{view === 'list' ? `共 ${query.data?.totalElements ?? 0} 项` : `筛选范围 ${graphScopeNodes.length} 个对象类型`}</span>
            </div>
            <Space>
              {view === 'list' && <Tooltip title="刷新对象类型列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新业务对象类型列表" onClick={() => void query.refetch()} /></Tooltip>}
              {canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>新建对象类型</Button>}
            </Space>
          </div>
          {view === 'list' ? <>
            {query.isError && <InlineFeedback tone="error" label={query.error.message} action={<Button onClick={() => void query.refetch()}>重试</Button>} />}
            {directories.isError && <InlineFeedback tone="error" label="业务建模目录加载失败" action={<Button onClick={() => void directories.refetch()}>重试</Button>} />}
            <Table className="management-table" size="small" rowKey="id" columns={columns} dataSource={query.data?.content ?? []} loading={query.isFetching} scroll={{ x: 850, y: '100%' }} pagination={{ current: page + 1, pageSize: size, total: query.data?.totalElements ?? 0, showSizeChanger: true, placement: ['bottomEnd'], onChange: (nextPage, nextSize) => { setPage(nextSize === size ? nextPage - 1 : 0); setSize(nextSize); } }} />
          </> : (
            <BusinessOntologyGraphWorkspace
              key={`${directory === undefined ? 'all' : directory === null ? 'uncategorized' : directory}:${filters.keyword ?? ''}:${filters.enabled ?? ''}:${focusId ?? ''}`}
              graph={graphQuery.data}
              scopeNodes={graphScopeNodes}
              focusId={focusId}
              loading={graphQuery.isLoading}
              errorMessage={graphQuery.isError ? graphQuery.error.message : undefined}
              emptyDescription={graphEmptyDescription}
              canManage={canManage}
              canReadModels={permissions.has('model.view')}
              onRetry={() => void graphQuery.refetch()}
              onFocusChange={(id) => updateParams({ view: 'graph', focusId: id })}
              onOpenObject={(id, tab) => openFromGraph(id, tab)}
              onOpenRelation={(relation: BusinessObjectTypeGraphRelation) => openFromGraph(relation.sourceObjectTypeId, 'relations', relation.id ?? undefined)}
            />
          )}
        </div>
      </section>
    </div>
    {editing && <BusinessObjectTypeBasicsDrawer objectType={editing === 'new' ? undefined : editing} directoryId={directory} onClose={() => setEditing(null)} onSaved={(result) => { setEditing(null); navigate(`/business-object-types/${result.id}`); }} />}
  </>;
};
