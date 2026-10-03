import {
  ArrowLeftOutlined,
  DeleteOutlined,
  EditOutlined,
  ReloadOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { Button, ConfigProvider, Modal, Result, Skeleton, Space, Tabs, Tag, Tooltip, message } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { useDirectoryTree, type DirectoryTreeNode } from '../../directory';
import { useCurrentUser } from '../../system';
import { workspaceResourceTheme } from '../../../shared/theme/workspaceResourceTheme';
import { ManagementStatusIndicator } from '../../../shared/components/ManagementListCells';
import { fileDatasetReadiness } from '../model/fileDatasetReadiness';
import { FileDatasetUploadDrawer } from '../components/FileDatasetUploadDrawer';
import { FileDatasetParseHistoryPanel } from '../components/FileDatasetParseHistoryPanel';
import './file-dataset-detail.css';
import { fileDatasetDeleteConfirmation } from '../components/fileDatasetDeleteConfirmation';
import { FileDatasetDrawer } from '../components/FileDatasetDrawer';
import { FileDatasetFilesPanel } from '../components/FileDatasetFilesPanel';
import { FileDatasetOverviewPanel } from '../components/FileDatasetOverviewPanel';
import { FileDatasetTablesPanel } from '../components/FileDatasetTablesPanel';
import { FileDatasetTypeIcon } from '../components/FileDatasetTypeIcon';
import { useDeleteFileDataset, useFileDataset, useFileDatasetTables } from '../hooks/useFileDatasets';
import {
  fileDatasetTypeLabels,
  isActiveFileDatasetParseStatus,
  type FileDataset,
} from '../model/fileDataset';
import {
  normalizeFileDatasetDetailTab,
  resolveFileDatasetTableId,
  type FileDatasetDetailTabKey,
} from '../model/fileDatasetDetail';

interface FileDatasetDetailLocationState {
  fromFileDatasetList?: boolean;
}

const FileDatasetDetailTabLabel = ({ label, count }: { label: string; count: number }) => (
  <span className="file-dataset-detail-tab-label" aria-label={`${label} ${count}`}>
    <span>{label}</span>{' '}
    <span className="file-dataset-detail-tab-count" aria-hidden="true">{count}</span>
  </span>
);

export const FileDatasetDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [uploadOpen, setUploadOpen] = useState(false);
  const [editing, setEditing] = useState(false);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const detailQuery = useFileDataset(id, Boolean(id));
  const tablesQuery = useFileDatasetTables(id, Boolean(id));
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canViewDirectories = permissions.has('directory.view');
  const canUpdate = permissions.has('filedataset.update');
  const canDelete = permissions.has('filedataset.delete');
  const directoriesQuery = useDirectoryTree('FILE_DATASET', canViewDirectories);
  const deleteMutation = useDeleteFileDataset();
  const activeTab = normalizeFileDatasetDetailTab(searchParams.get('tab'));
  const requestedTableId = searchParams.get('tableId');
  const dataset = detailQuery.data;
  const tables = useMemo(() => tablesQuery.data?.content ?? [], [tablesQuery.data?.content]);
  const selectedTableId = resolveFileDatasetTableId(requestedTableId, tables);
  const observedActiveTables = useRef(false);

  useEffect(() => {
    const active = tables.some((table) => (
      isActiveFileDatasetParseStatus(table.parseStatus) || Boolean(table.currentLoadJobId)
    ));
    if (active) {
      observedActiveTables.current = true;
    } else if (observedActiveTables.current) {
      observedActiveTables.current = false;
      void detailQuery.refetch();
    }
  }, [detailQuery, tables]);

  const directoryNameById = useMemo(() => {
    const names = new Map<string, string>();
    const collect = (nodes: DirectoryTreeNode[]) => nodes.forEach((node) => {
      names.set(node.id, node.name);
      collect(node.children);
    });
    collect(directoriesQuery.data ?? []);
    return names;
  }, [directoriesQuery.data]);

  useEffect(() => {
    if (activeTab !== 'tables' || tablesQuery.isPending || tablesQuery.isError) return;
    if (selectedTableId === requestedTableId) return;
    const next = new URLSearchParams(searchParams);
    if (selectedTableId) next.set('tableId', selectedTableId);
    else next.delete('tableId');
    setSearchParams(next, { replace: true });
  }, [activeTab, requestedTableId, searchParams, selectedTableId, setSearchParams, tablesQuery.isError, tablesQuery.isPending]);

  const backToList = () => {
    const state = location.state as FileDatasetDetailLocationState | null;
    if (state?.fromFileDatasetList) navigate(-1);
    else navigate('/file-dataset');
  };

  const selectTab = (tab: FileDatasetDetailTabKey) => {
    const next = new URLSearchParams(searchParams);
    next.set('tab', tab);
    if (tab !== 'tables') next.delete('tableId');
    setSearchParams(next, { replace: true });
  };

  const openTable = (tableId: string) => {
    const next = new URLSearchParams(searchParams);
    next.set('tab', 'tables');
    next.set('tableId', tableId);
    setSearchParams(next, { replace: true });
  };

  const remove = (target: FileDataset) => modalApi.confirm({
    ...fileDatasetDeleteConfirmation(target),
    onOk: async () => {
      try {
        await deleteMutation.mutateAsync(target.id);
        messageApi.success('文件数据集已删除');
        navigate('/file-dataset', { replace: true });
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.message : '删除文件数据集失败');
        throw error;
      }
    },
  });

  if (!id) {
    return <Result status="404" title="文件数据集地址无效" extra={<Button type="primary" onClick={() => navigate('/file-dataset')}>返回文件数据集列表</Button>} />;
  }

  if (detailQuery.isPending) {
    return <div className="file-dataset-detail-loading"><Skeleton active paragraph={{ rows: 8 }} /></div>;
  }

  if (!dataset || detailQuery.error) {
    return (
      <Result
        status="error"
        title="文件数据集详情加载失败"
        subTitle={detailQuery.error instanceof ApiError ? detailQuery.error.message : '请确认文件数据集是否存在。'}
        extra={(
          <Space>
            <Button onClick={backToList}>返回列表</Button>
            <Button type="primary" onClick={() => void detailQuery.refetch()}>重试</Button>
          </Space>
        )}
      />
    );
  }

  const readiness = fileDatasetReadiness(dataset);
  const directoryName = dataset.directoryId ? directoryNameById.get(dataset.directoryId) : undefined;
  const tabItems = [
    {
      key: 'overview',
      label: '概览',
      children: (
        <FileDatasetOverviewPanel
          key={dataset.id}
          dataset={dataset}
          directoryName={directoryName}
          tables={tables}
          tablesLoading={tablesQuery.isFetching}
          tablesError={tablesQuery.isError || tablesQuery.isRefetchError}
          onRetryTables={() => void tablesQuery.refetch()}
        />
      ),
    },
    {
      key: 'files',
      label: <FileDatasetDetailTabLabel label="文件" count={dataset.fileCount} />,
      children: (
        <FileDatasetFilesPanel
          key={dataset.id}
          dataset={dataset}
          canUpdate={canUpdate}
          onViewHistory={() => selectTab('history')}
          onRefreshTables={() => void Promise.all([tablesQuery.refetch(), detailQuery.refetch()])}
        />
      ),
    },
    {
      key: 'tables',
      label: <FileDatasetDetailTabLabel label="数据表" count={dataset.tableCount} />,
      children: (
        <FileDatasetTablesPanel
          dataset={dataset}
          tables={tables}
          selectedTableId={selectedTableId}
          canUpdate={canUpdate}
          loading={tablesQuery.isFetching}
          error={tablesQuery.isError || tablesQuery.isRefetchError}
          onSelectTable={openTable}
          onRefresh={() => void tablesQuery.refetch()}
        />
      ),
    },
    { key: 'history', label: '解析记录', children: <FileDatasetParseHistoryPanel key={dataset.id} datasetId={dataset.id} /> },
  ];

  return (
    <ConfigProvider theme={workspaceResourceTheme}>
    <div className="file-dataset-detail-page business-detail-page">
      {messageContext}
      {modalContext}
      <div className="file-dataset-detail-header business-detail-header">
        <div className="file-dataset-detail-identity">
          <div className="file-dataset-detail-title-row">
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={backToList}>返回列表</Button>
            <span className="business-detail-resource-icon business-detail-resource-icon-cyan">
              <FileDatasetTypeIcon type={dataset.type} />
            </span>
            <span className="file-dataset-detail-title">{dataset.name}</span>
            <Tag>{fileDatasetTypeLabels[dataset.type]}</Tag>
            <ManagementStatusIndicator label={readiness.label} tone={readiness.tone} title={readiness.help} />
          </div>
          <div className="file-dataset-detail-subtitle">
            <span>{directoryName ?? (dataset.directoryId ? '目录已删除' : '未分类')}</span>
            <span>·</span>
            <span>{dataset.fileCount} 个文件</span>
            <span>·</span>
            <span>{dataset.tableCount} 张逻辑表</span>
          </div>
        </div>
        <Space size={4}>
          <Tooltip title="刷新详情">
            <Button
              icon={<ReloadOutlined />}
              aria-label="刷新文件数据集详情"
              loading={detailQuery.isFetching || tablesQuery.isFetching}
              onClick={() => void Promise.all([detailQuery.refetch(), tablesQuery.refetch()])}
            />
          </Tooltip>
          {canUpdate && <Button type="primary" icon={<UploadOutlined />} onClick={() => setUploadOpen(true)}>上传文件</Button>}
          {canUpdate && <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>修改</Button>}
          {canDelete && (
            <Tooltip title="删除文件数据集">
              <Button danger icon={<DeleteOutlined />} aria-label={`删除${dataset.name}`} onClick={() => remove(dataset)} />
            </Tooltip>
          )}
        </Space>
      </div>
      <Tabs
        activeKey={activeTab}
        className="file-dataset-detail-tabs business-detail-tabs"
        destroyOnHidden
        items={tabItems}
        onChange={(key) => selectTab(key as FileDatasetDetailTabKey)}
      />
      <FileDatasetUploadDrawer dataset={uploadOpen && canUpdate ? dataset : null} onClose={() => setUploadOpen(false)} onOpenDetail={(tab) => { setUploadOpen(false); selectTab(tab); }} />
      <FileDatasetDrawer
        open={editing}
        fileDataset={dataset}
        canViewDirectories={canViewDirectories}
        onClose={() => setEditing(false)}
      />
    </div>
    </ConfigProvider>
  );
};
