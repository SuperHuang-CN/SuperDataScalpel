import {
  DeleteOutlined,
  DashboardOutlined,
  EditOutlined,
  EllipsisOutlined,
  FolderOpenOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Alert, Button, Card, Dropdown, Form, Input, Modal, Select, Space, Table, Tag, Tooltip, message } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
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

const formatDateTime = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium', timeStyle: 'medium', hour12: false,
}).format(new Date(value));

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
      title: '数据集名称', dataIndex: 'name', width: 220, ellipsis: true,
      render: (value: string, dataset: FileDataset) => (
        <Button
          type="link"
          className="file-dataset-name-button"
          onClick={() => navigate(`/file-dataset/${dataset.id}`, { state: { fromFileDatasetList: true } })}
        >
          {value}
        </Button>
      ),
    },
    { title: '类型', dataIndex: 'type', width: 120, render: (value: FileDataset['type']) => <Tag>{fileDatasetTypeLabels[value]}</Tag> },
    { title: '文件数', dataIndex: 'fileCount', width: 90, align: 'right' },
    { title: '表数', dataIndex: 'tableCount', width: 90, align: 'right' },
    {
      title: '表就绪情况',
      width: 140,
      render: (_value: unknown, dataset: FileDataset) => dataset.tableCount === 0
        ? <Tag>尚未上传</Tag>
        : <Tag color={dataset.readyTableCount === dataset.tableCount ? 'success' : 'processing'}>{dataset.readyTableCount} / {dataset.tableCount} 已就绪</Tag>,
    },
    { title: '说明', dataIndex: 'description', width: 260, ellipsis: true, render: (value: string | null) => value || '—' },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: formatDateTime },
    {
      title: '操作', key: 'action', width: 110, fixed: 'right', render: (_value: unknown, dataset: FileDataset) => (
        <Space size={2}>
          <Tooltip title="查看详情"><Button type="text" icon={<FolderOpenOutlined />} aria-label={`查看${dataset.name}详情`} onClick={() => navigate(`/file-dataset/${dataset.id}`, { state: { fromFileDatasetList: true } })} /></Tooltip>
          {canUpdate && <Tooltip title="修改数据集"><Button type="text" icon={<EditOutlined />} aria-label={`修改${dataset.name}`} onClick={() => setEditingFileDataset(dataset)} /></Tooltip>}
          {canDelete && (
            <Dropdown
              trigger={['click']}
              menu={{ items: [{ key: 'delete', danger: true, icon: <DeleteOutlined />, label: '删除' }], onClick: () => confirmDelete(dataset) }}
            >
              <Tooltip title="更多操作"><Button type="text" icon={<EllipsisOutlined />} aria-label={`${dataset.name}更多操作`} /></Tooltip>
            </Dropdown>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      {messageContext}
      {modalContext}
      <div className={canViewDirectories ? 'directory-management-layout' : 'page-stack'}>
        {canViewDirectories && <DirectoryTreePanel scope="FILE_DATASET" tree={directoriesQuery.data ?? []} loading={directoriesQuery.isFetching} selection={directorySelection} canManage={canManageDirectories} onSelectionChange={selectDirectory} />}
        <Card className="management-card">
          <div className="management-toolbar">
            <Form<FileDatasetFilters> form={filterForm} layout="inline" className="management-filter-form" onFinish={search}>
              <Form.Item name="keyword" label="名称"><Input allowClear placeholder="按数据集名称筛选" className="file-dataset-keyword-input" /></Form.Item>
              <Form.Item name="type" label="类型"><Select allowClear placeholder="全部" options={fileDatasetTypeOptions} className="file-dataset-format-select" /></Form.Item>
            </Form>
            <Space size={4} className="management-toolbar-actions">
              <Button type="primary" onClick={() => filterForm.submit()}>查询</Button>
              <Button onClick={reset}>重置</Button>
              <Button icon={<ReloadOutlined />} onClick={() => void fileDatasetsQuery.refetch()}>刷新</Button>
              <Button icon={<DashboardOutlined />} onClick={() => setParseQueueDrawerOpen(true)}>解析队列</Button>
              {canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerOpen(true)}>新建</Button>}
            </Space>
          </div>
          {fileDatasetsQuery.isError && <Alert type="error" showIcon className="management-query-error" message="文件数据集加载失败" action={<Button size="small" onClick={() => void fileDatasetsQuery.refetch()}>重试</Button>} />}
          <Table<FileDataset>
            size="small"
            className="management-table"
            rowKey="id"
            columns={columns}
            dataSource={fileDatasetsQuery.data?.content ?? []}
            loading={fileDatasetsQuery.isFetching}
            scroll={{ x: 1300, y: '100%' }}
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
        </Card>
      </div>
      <FileDatasetDrawer
        open={createDrawerOpen || Boolean(editingFileDataset)}
        fileDataset={editingFileDataset}
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
