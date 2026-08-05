import {
  DeleteOutlined,
  DashboardOutlined,
  EditOutlined,
  EllipsisOutlined,
  FileTextOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Alert, Button, Dropdown, Form, Modal, Select, Table, Tooltip, message } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { ManagementDateTime, ManagementListCell, ManagementStatusIndicator } from '../../../shared/components/ManagementListCells';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { DirectoryTreePanel, findDirectoryDescendantIds, useDirectoryTree, type DirectorySelection } from '../../directory';
import { useCurrentUser } from '../../system';
import { FileDatasetDrawer } from '../components/FileDatasetDrawer';
import { FileDatasetParseQueueDrawer } from '../components/FileDatasetParseQueueDrawer';
import { useDeleteFileDataset, useFileDatasets } from '../hooks/useFileDatasets';
import {
  fileDatasetTypeLabels,
  fileDatasetTypeOptions,
  type FileDataset,
  type FileDatasetFilters,
} from '../model/fileDataset';
import { buildFileDatasetSearch } from '../model/fileDatasetSearch';

const DEFAULT_PAGE_SIZE = 20;

export const FileDatasetPage = () => {
  const [filterForm] = Form.useForm<FileDatasetFilters>();
  const [filters, setFilters] = useState<FileDatasetFilters>({});
  const [directorySelection, setDirectorySelection] = useState<DirectorySelection>(undefined);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [editingFileDataset, setEditingFileDataset] = useState<FileDataset | null>(null);
  const [createDrawerOpen, setCreateDrawerOpen] = useState(false);
  const [parseQueueDrawerOpen, setParseQueueDrawerOpen] = useState(false);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const currentUserQuery = useCurrentUser();
  const navigate = useNavigate();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canViewDirectories = permissions.has('directory.view');
  const canManageDirectories = permissions.has('directory.manage');
  const canCreate = permissions.has('filedataset.create');
  const canUpdate = permissions.has('filedataset.update');
  const canDelete = permissions.has('filedataset.delete');
  const directoriesQuery = useDirectoryTree('FILE_DATASET', canViewDirectories);
  const request = useMemo(() => ({
    search: buildFileDatasetSearch(filters), page, size, sort: '-updatedAt,name',
  }), [filters, page, size]);
  const fileDatasetsQuery = useFileDatasets(request);
  const deleteMutation = useDeleteFileDataset();

  const search = (nextFilters: FileDatasetFilters) => {
    setFilters(nextFilters);
    setPage(0);
  };

  const reset = () => {
    filterForm.resetFields();
    setDirectorySelection(undefined);
    search({});
  };

  const applyDirectFilters = (values: FileDatasetFilters) => {
    search({ ...filters, keyword: values.keyword, type: values.type });
  };

  const selectDirectory = (selection: DirectorySelection) => {
    setDirectorySelection(selection);
    if (selection === undefined) {
      search({ ...filters, directoryIds: undefined, uncategorized: undefined });
    } else if (selection === null) {
      search({ ...filters, directoryIds: undefined, uncategorized: true });
    } else {
      search({
        ...filters,
        directoryIds: findDirectoryDescendantIds(directoriesQuery.data ?? [], selection),
        uncategorized: undefined,
      });
    }
  };

  const confirmDelete = (fileDataset: FileDataset) => {
    modalApi.confirm({
      title: '删除文件数据集',
      content: `确认删除“${fileDataset.name}”及其 ${fileDataset.fileCount} 个文件、${fileDataset.tableCount} 张表吗？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteMutation.mutateAsync(fileDataset.id);
          messageApi.success('文件数据集已删除');
        } catch (error) {
          messageApi.error(error instanceof ApiError ? error.message : '删除文件数据集失败');
          throw error;
        }
      },
    });
  };

  const columns: TableProps<FileDataset>['columns'] = [
    {
      title: '数据集', dataIndex: 'name', width: 280,
      render: (value: string, dataset: FileDataset) => (
        <ManagementListCell icon={<FileTextOutlined />} iconTone="cyan" primary={<Button type="link" className="file-dataset-name-button" onClick={() => navigate(`/file-dataset/${dataset.id}`, { state: { fromFileDatasetList: true } })}>{value}</Button>} secondary={dataset.description || '—'} />
      ),
    },
    { title: '类型', dataIndex: 'type', width: 120, render: (value: FileDataset['type']) => fileDatasetTypeLabels[value] },
    { title: '数据规模', width: 130, align: 'right', render: (_value: unknown, dataset) => <ManagementListCell primary={`${dataset.fileCount} 个文件`} secondary={`${dataset.tableCount} 张表`} /> },
    {
      title: '表就绪情况',
      width: 140,
      render: (_value: unknown, dataset: FileDataset) => dataset.tableCount === 0
        ? <ManagementStatusIndicator label="尚未上传" />
        : <ManagementStatusIndicator label={`${dataset.readyTableCount} / ${dataset.tableCount} 已就绪`} tone={dataset.readyTableCount === dataset.tableCount ? 'success' : 'processing'} />,
    },
    { title: '更新时间', dataIndex: 'updatedAt', width: 160, render: (value: string) => <ManagementDateTime value={value} /> },
    {
      title: '操作', key: 'action', width: 112, render: (_value: unknown, dataset: FileDataset) => (canUpdate || canDelete) && (
        <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">{canUpdate && <Tooltip title="修改数据集"><Button type="text" icon={<EditOutlined />} aria-label={`修改${dataset.name}`} onClick={() => setEditingFileDataset(dataset)} /></Tooltip>}</div>
          <Dropdown trigger={['click']} menu={{ items: [
            ...(canUpdate ? [{ key: 'edit', icon: <EditOutlined />, label: '修改', onClick: () => setEditingFileDataset(dataset) }] : []),
            ...(canUpdate && canDelete ? [{ type: 'divider' as const }] : []),
            ...(canDelete ? [{ key: 'delete', danger: true, icon: <DeleteOutlined />, label: '删除', onClick: () => confirmDelete(dataset) }] : []),
          ] }}><Tooltip title="更多操作"><Button className="management-row-actions-more" type="text" icon={<EllipsisOutlined />} aria-label={`${dataset.name}的更多操作`} /></Tooltip></Dropdown>
        </div>
      ),
    },
  ];

  return (
    <>
      {messageContext}
      {modalContext}
      <div className={canViewDirectories ? 'directory-management-layout' : 'page-stack'}>
        {canViewDirectories && <DirectoryTreePanel scope="FILE_DATASET" tree={directoriesQuery.data ?? []} loading={directoriesQuery.isFetching} selection={directorySelection} canManage={canManageDirectories} onSelectionChange={selectDirectory} />}
        <section className="management-workbench">
          <div className="management-filter-strip">
            <Form<FileDatasetFilters> autoComplete="off" form={filterForm} layout="inline" className="management-filter-form" onFinish={applyDirectFilters}>
              <Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索数据集名称" className="file-dataset-keyword-input" /></Form.Item>
              <Form.Item name="type"><Select allowClear placeholder="全部类型" options={fileDatasetTypeOptions} className="file-dataset-format-select" /></Form.Item>
            </Form>
            <ManagementFilterActions form={filterForm} appliedFilters={filters} additionalActive={directorySelection !== undefined} loading={fileDatasetsQuery.isFetching} onReset={reset} />
          </div>
          <div className="management-results-surface">
            <div className="management-result-toolbar">
            <span className="management-result-title">文件数据集 <span className="management-result-count">共 {fileDatasetsQuery.data?.totalElements ?? 0} 项</span></span>
            <div className="management-result-actions"><Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新文件数据集列表" onClick={() => void fileDatasetsQuery.refetch()} /></Tooltip><Button icon={<DashboardOutlined />} onClick={() => setParseQueueDrawerOpen(true)}>解析队列</Button>{canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerOpen(true)}>新建</Button>}</div>
            </div>
            {fileDatasetsQuery.isError && <Alert type="error" showIcon className="management-query-error" message="文件数据集加载失败" action={<Button size="small" onClick={() => void fileDatasetsQuery.refetch()}>重试</Button>} />}
            <Table<FileDataset>
            size="small"
            className="management-table"
            rowKey="id"
            columns={columns}
            dataSource={fileDatasetsQuery.data?.content ?? []}
            loading={fileDatasetsQuery.isFetching}
            scroll={{ y: '100%' }}
            pagination={{
              current: page + 1,
              pageSize: size,
              total: fileDatasetsQuery.data?.totalElements ?? 0,
              size: 'small',
              position: ['bottomRight'],
              hideOnSinglePage: false,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 项`,
            }}
            onChange={(pagination) => {
              setPage((pagination.current ?? 1) - 1);
              setSize(pagination.pageSize ?? DEFAULT_PAGE_SIZE);
            }}
            />
          </div>
        </section>
      </div>
      <FileDatasetDrawer
        open={createDrawerOpen || Boolean(editingFileDataset)}
        fileDataset={editingFileDataset}
        initialDirectoryId={typeof directorySelection === 'string' ? directorySelection : undefined}
        canViewDirectories={canViewDirectories}
        onClose={() => { setCreateDrawerOpen(false); setEditingFileDataset(null); }}
      />
      <FileDatasetParseQueueDrawer
        open={parseQueueDrawerOpen}
        onClose={() => setParseQueueDrawerOpen(false)}
      />
    </>
  );
};
