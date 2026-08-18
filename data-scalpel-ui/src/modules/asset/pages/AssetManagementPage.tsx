import {
  CloudSyncOutlined,
  DeleteOutlined,
  EditOutlined,
  ExportOutlined,
  MoreOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Dropdown,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  TreeSelect,
  Typography,
  message,
} from 'antd';
import type { MenuProps, TableProps } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { directoryTreeSelectData, findDirectoryDescendantIds, useDirectoryTree, type DirectoryTreeNode } from '../../directory';
import { useCurrentUser } from '../../system';
import { AssetEditDrawer } from '../components/AssetEditDrawer';
import { AssetRegistrationDrawer } from '../components/AssetRegistrationDrawer';
import { useAssetBatchCommand, useAssetCommand, useAssets, useDeleteAsset } from '../hooks/useAssets';
import {
  assetSourcePath,
  assetStatusLabels,
  assetSyncStatusLabels,
  assetTypeLabels,
  type Asset,
  type AssetBatchOperationResult,
  type AssetStatus,
  type AssetSyncStatus,
  type AssetType,
} from '../model/asset';
import './assetManagement.css';

interface AssetFilters {
  keyword?: string;
  assetType?: AssetType;
  status?: AssetStatus;
  syncStatus?: AssetSyncStatus;
  directoryId?: string;
}

const escapeSearchValue = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

const buildSearch = (filters: AssetFilters, directories: DirectoryTreeNode[] | undefined): string | undefined => {
  const conditions: string[] = [];
  if (filters.keyword?.trim()) {
    const value = escapeSearchValue(filters.keyword.trim());
    conditions.push(`(portalName:*"${value}"* OR sourceName:*"${value}"* OR sourceCode:*"${value}"*)`);
  }
  if (filters.assetType) conditions.push(`assetType:"${filters.assetType}"`);
  if (filters.status) conditions.push(`status:"${filters.status}"`);
  if (filters.syncStatus) conditions.push(`syncStatus:"${filters.syncStatus}"`);
  if (filters.directoryId) {
    const ids = findDirectoryDescendantIds(directories ?? [], filters.directoryId);
    if (ids.length) conditions.push(`(${ids.map((id) => `directoryId:"${id}"`).join(' OR ')})`);
  }
  return conditions.length ? conditions.join(' AND ') : undefined;
};

const formatDateTime = (value: string | null): string => value
  ? new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date(value))
  : '—';

const statusColor: Record<AssetStatus, string> = { DRAFT: 'default', PUBLISHED: 'success', OFFLINE: 'warning' };
const syncColor: Record<AssetSyncStatus, string> = {
  IN_SYNC: 'success', OUTDATED: 'processing', SOURCE_UNAVAILABLE: 'warning', SOURCE_MISSING: 'error', FAILED: 'error',
};

const sourcePermission: Record<AssetType, string> = {
  DATA_MODEL: 'model.view',
  FILE_DATASET: 'filedataset.view',
  DICTIONARY: 'standard.dictionary.view',
  DATA_SERVICE: 'service.view',
};

const batchSummary = (result: AssetBatchOperationResult) => (
  `共 ${result.totalCount} 项：正常 ${result.successCount}，待同步 ${result.outdatedCount}，`
  + `来源不可用 ${result.unavailableCount}，来源缺失 ${result.missingCount}，失败 ${result.failedCount}`
);

export const AssetManagementPage = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm<AssetFilters>();
  const [messageApi, messageContext] = message.useMessage();
  const [modal, modalContext] = Modal.useModal();
  const [filters, setFilters] = useState<AssetFilters>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [registrationOpen, setRegistrationOpen] = useState(false);
  const [editingAsset, setEditingAsset] = useState<Asset | null>(null);
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canManage = permissions.has('asset.manage');
  const canViewAnySource = Object.values(sourcePermission).some((permission) => permissions.has(permission));
  const canViewDirectories = permissions.has('directory.view');
  const directoriesQuery = useDirectoryTree('ASSET', canViewDirectories);
  const search = useMemo(() => buildSearch(filters, directoriesQuery.data), [directoriesQuery.data, filters]);
  const assetsQuery = useAssets({ search, page: page - 1, size: pageSize, sort: '-updatedAt' });
  const commandMutation = useAssetCommand();
  const deleteMutation = useDeleteAsset();
  const batchMutation = useAssetBatchCommand();

  const directoryPaths = useMemo(() => {
    const paths = new Map<string, string>();
    const visit = (nodes: DirectoryTreeNode[], ancestors: string[]) => nodes.forEach((node) => {
      const path = [...ancestors, node.name];
      paths.set(node.id, path.join(' / '));
      visit(node.children, path);
    });
    visit(directoriesQuery.data ?? [], []);
    return paths;
  }, [directoriesQuery.data]);

  const runCommand = async (asset: Asset, command: 'publish' | 'offline' | 'check' | 'sync') => {
    try {
      const result = await commandMutation.mutateAsync({ id: asset.id, command });
      if ((command === 'check' || command === 'sync') && result.syncStatus !== 'IN_SYNC') {
        messageApi.warning(`${asset.effectiveName}：${assetSyncStatusLabels[result.syncStatus]}`);
      } else {
        messageApi.success(command === 'publish' ? '资产已发布' : command === 'offline' ? '资产已下线' : command === 'check' ? '资产检查完成' : '资产已同步');
      }
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '资产操作失败');
    }
  };

  const remove = (asset: Asset) => modal.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: '删除资产记录',
    content: `确认删除“${asset.effectiveName}”吗？该操作不会删除原资源。`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await deleteMutation.mutateAsync(asset.id);
        messageApi.success('资产记录已删除');
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.message : '删除资产失败');
        throw error;
      }
    },
  });

  const runBatch = (command: 'check-all' | 'sync-all') => modal.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: command === 'check-all' ? '全量检查资产' : '全量同步资产',
    content: command === 'check-all'
      ? '将逐项检查所有已登记资产，不修改已保存的来源快照。'
      : '将逐项更新所有已登记资产的来源快照，不覆盖门户信息。',
    okText: command === 'check-all' ? '开始检查' : '开始同步',
    cancelText: '取消',
    onOk: async () => {
      try {
        const result = await batchMutation.mutateAsync(command);
        const summary = batchSummary(result);
        if (result.failedCount || result.unavailableCount || result.missingCount) messageApi.warning(summary, 8);
        else messageApi.success(summary, 8);
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.message : '全量操作失败');
        throw error;
      }
    },
  });

  const columns: TableProps<Asset>['columns'] = [
    {
      title: '资产',
      width: 280,
      render: (_, asset) => (
        <div className="asset-primary-cell">
          <Button type="link" onClick={() => setEditingAsset(asset)}>{asset.effectiveName}</Button>
          <Typography.Text type="secondary" ellipsis={{ tooltip: asset.sourceCode ?? asset.resourceId }}>{asset.sourceCode ?? '无来源编码'}</Typography.Text>
        </div>
      ),
    },
    { title: '类型', dataIndex: 'assetType', width: 120, render: (value: AssetType) => assetTypeLabels[value] },
    { title: '业务领域', dataIndex: 'directoryId', width: 210, ellipsis: true, render: (value: string | null) => value ? directoryPaths.get(value) ?? '领域不可用' : '未设置' },
    { title: '发布状态', dataIndex: 'status', width: 110, render: (value: AssetStatus) => <Tag color={statusColor[value]}>{assetStatusLabels[value]}</Tag> },
    {
      title: '同步状态',
      dataIndex: 'syncStatus',
      width: 150,
      render: (value: AssetSyncStatus, asset) => <Tooltip title={asset.syncError}><Tag color={syncColor[value]}>{assetSyncStatusLabels[value]}</Tag></Tooltip>,
    },
    {
      title: '来源时间 / 同步时间',
      width: 190,
      render: (_, asset) => <div className="asset-primary-cell"><span>{formatDateTime(asset.sourceUpdatedAt)}</span><Typography.Text type="secondary">{formatDateTime(asset.lastSyncedAt)}</Typography.Text></div>,
    },
    ...(canManage || canViewAnySource ? [{
      title: '操作',
      fixed: 'right' as const,
      width: 118,
      render: (_: unknown, asset: Asset) => {
        const canViewSource = permissions.has(sourcePermission[asset.assetType]);
        if (!canManage) {
          return canViewSource
            ? <Tooltip title="查看源资源"><Button type="text" icon={<ExportOutlined />} aria-label={`查看${asset.effectiveName}的源资源`} onClick={() => navigate(assetSourcePath(asset))} /></Tooltip>
            : '—';
        }
        const menuItems: MenuProps['items'] = [
          { key: 'check', icon: <SafetyCertificateOutlined />, label: '检查来源', onClick: () => void runCommand(asset, 'check') },
          { key: 'sync', icon: <CloudSyncOutlined />, label: '重新同步', onClick: () => void runCommand(asset, 'sync') },
          ...(canViewSource ? [{ key: 'source', icon: <ExportOutlined />, label: '查看源资源', onClick: () => navigate(assetSourcePath(asset)) }] : []),
          { type: 'divider' },
          { key: 'delete', icon: <DeleteOutlined />, label: '删除记录', danger: true, disabled: asset.status === 'PUBLISHED', onClick: () => void remove(asset) },
        ];
        return (
          <div className="management-row-actions">
            <Tooltip title="编辑资产"><Button type="text" icon={<EditOutlined />} aria-label={`编辑资产${asset.effectiveName}`} onClick={() => setEditingAsset(asset)} /></Tooltip>
            {asset.status === 'PUBLISHED'
              ? <Tooltip title="下线资产"><Button type="text" icon={<PauseCircleOutlined />} aria-label={`下线资产${asset.effectiveName}`} onClick={() => void runCommand(asset, 'offline')} /></Tooltip>
              : <Tooltip title="发布资产"><Button type="text" icon={<PlayCircleOutlined />} aria-label={`发布资产${asset.effectiveName}`} onClick={() => void runCommand(asset, 'publish')} /></Tooltip>}
            <Dropdown menu={{ items: menuItems }}><Tooltip title="更多操作"><Button type="text" icon={<MoreOutlined />} aria-label={`${asset.effectiveName}的更多操作`} /></Tooltip></Dropdown>
          </div>
        );
      },
    }] : []),
  ];

  const applyFilters = (values: AssetFilters) => { setFilters(values); setPage(1); };
  const reset = () => { form.resetFields(); setFilters({}); setPage(1); };

  return (
    <div className="management-page asset-management-page">
      {messageContext}
      {modalContext}
      <section className="management-workbench">
        <div className="management-filter-strip">
          <Form<AssetFilters> autoComplete="off" form={form} layout="inline" onFinish={applyFilters}>
            <Form.Item name="keyword"><Input allowClear placeholder="搜索名称或编码" /></Form.Item>
            <Form.Item name="assetType"><Select allowClear placeholder="全部类型" options={(Object.entries(assetTypeLabels) as Array<[AssetType, string]>).map(([value, label]) => ({ value, label }))} /></Form.Item>
            <Form.Item name="status"><Select allowClear placeholder="发布状态" options={(Object.entries(assetStatusLabels) as Array<[AssetStatus, string]>).map(([value, label]) => ({ value, label }))} /></Form.Item>
            <Form.Item name="syncStatus"><Select allowClear placeholder="同步状态" options={(Object.entries(assetSyncStatusLabels) as Array<[AssetSyncStatus, string]>).map(([value, label]) => ({ value, label }))} /></Form.Item>
            {canViewDirectories && <Form.Item name="directoryId"><TreeSelect allowClear treeDefaultExpandAll placeholder="业务领域" treeData={directoryTreeSelectData(directoriesQuery.data ?? [])} /></Form.Item>}
            <Form.Item><Space size={4}><Button type="primary" htmlType="submit" loading={assetsQuery.isFetching}>查询</Button>{Object.values(filters).some(Boolean) && <Button type="text" onClick={reset}>重置</Button>}</Space></Form.Item>
          </Form>
        </div>
        <div className="management-results-surface">
          <div className="management-result-toolbar">
            <div className="management-result-title">资产管理 <span className="management-result-count">共 {assetsQuery.data?.totalElements ?? 0} 项</span></div>
            <Space size={4} className="management-result-actions">
              <Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新资产列表" onClick={() => void assetsQuery.refetch()} /></Tooltip>
              {canManage && <>
                <Button icon={<SafetyCertificateOutlined />} loading={batchMutation.isPending} onClick={() => void runBatch('check-all')}>全量检查</Button>
                <Button icon={<CloudSyncOutlined />} loading={batchMutation.isPending} onClick={() => void runBatch('sync-all')}>全量同步</Button>
                <Button type="primary" icon={<PlusOutlined />} onClick={() => setRegistrationOpen(true)}>登记资产</Button>
              </>}
            </Space>
          </div>
          {assetsQuery.isError && <Alert type="error" showIcon message="资产加载失败" description={assetsQuery.error instanceof Error ? assetsQuery.error.message : undefined} />}
          <Table<Asset>
            className="management-table"
            size="small"
            rowKey="id"
            columns={columns}
            dataSource={assetsQuery.data?.content ?? []}
            loading={assetsQuery.isFetching}
            scroll={{ y: '100%' }}
            pagination={{ current: page, pageSize, total: assetsQuery.data?.totalElements ?? 0, showSizeChanger: true, hideOnSinglePage: false, showTotal: (total) => `共 ${total} 项`, placement: ['bottomEnd'] }}
            onChange={(pagination) => { setPage(pagination.current ?? 1); setPageSize(pagination.pageSize ?? 20); }}
          />
        </div>
      </section>
      {canManage && <AssetRegistrationDrawer open={registrationOpen} onClose={() => setRegistrationOpen(false)} />}
      <AssetEditDrawer open={Boolean(editingAsset)} asset={editingAsset} directories={directoriesQuery.data ?? []} readOnly={!canManage} onClose={() => setEditingAsset(null)} />
    </div>
  );
};
