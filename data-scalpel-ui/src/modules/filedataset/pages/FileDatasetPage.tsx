import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  EllipsisOutlined,
  PlusOutlined,
  ReloadOutlined,
  SlidersOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Alert, Button, Card, Dropdown, Form, Input, Modal, Select, Space, Table, Tag, Tooltip, message } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { DirectoryTreePanel, findDirectoryDescendantIds, useDirectoryTree, type DirectorySelection } from '../../directory';
import { useCurrentUser } from '../../system';
import { FileDatasetDrawer } from '../components/FileDatasetDrawer';
import { FileDatasetParsingDrawer } from '../components/FileDatasetParsingDrawer';
import { ReplaceFileDatasetContentDrawer } from '../components/ReplaceFileDatasetContentDrawer';
import {
  useDeleteFileDataset,
  useDownloadFileDatasetContent,
  useFileDatasets,
} from '../hooks/useFileDatasets';
import {
  fileDatasetFormatLabels,
  fileDatasetFormatOptions,
  fileDatasetCompressionLabels,
  fileDatasetCompressionOptions,
  fileDatasetParseStatusLabels,
  formatFileSize,
  type FileDataset,
  type FileDatasetFilters,
  type FileDatasetParseStatus,
} from '../model/fileDataset';
import { buildFileDatasetSearch } from '../model/fileDatasetSearch';

const DEFAULT_PAGE_SIZE = 20;

const parseStatusColors: Record<FileDatasetParseStatus, string> = {
  UNPARSED: 'default',
  PARSING: 'processing',
  READY: 'success',
  FAILED: 'error',
};

const parseStatusOptions = Object.entries(fileDatasetParseStatusLabels).map(([value, label]) => ({
  value: value as FileDatasetParseStatus,
  label,
}));

const formatDateTime = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium',
  timeStyle: 'medium',
  hour12: false,
}).format(new Date(value));

export const FileDatasetPage = () => {
  const [filterForm] = Form.useForm<FileDatasetFilters>();
  const [filters, setFilters] = useState<FileDatasetFilters>({});
  const [directorySelection, setDirectorySelection] = useState<DirectorySelection>(undefined);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [editingFileDataset, setEditingFileDataset] = useState<FileDataset | null>(null);
  const [replacingFileDataset, setReplacingFileDataset] = useState<FileDataset | null>(null);
  const [parsingFileDataset, setParsingFileDataset] = useState<FileDataset | null>(null);
  const [createDrawerOpen, setCreateDrawerOpen] = useState(false);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canViewDirectories = permissions.has('directory.view');
  const canManageDirectories = permissions.has('directory.manage');
  const canCreate = permissions.has('filedataset.create');
  const canUpdate = permissions.has('filedataset.update');
  const canDelete = permissions.has('filedataset.delete');
  const directoriesQuery = useDirectoryTree('FILE_DATASET', canViewDirectories);
  const request = useMemo(() => ({
    search: buildFileDatasetSearch(filters),
    page,
    size,
    sort: '-updatedAt,name',
  }), [filters, page, size]);
  const fileDatasetsQuery = useFileDatasets(request);
  const downloadMutation = useDownloadFileDatasetContent();
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

  const download = async (fileDataset: FileDataset) => {
    try {
      const blob = await downloadMutation.mutateAsync(fileDataset.id);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = fileDataset.originalFileName;
      document.body.append(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
      messageApi.success('文件下载已开始');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '下载文件失败');
    }
  };

  const remove = async (fileDataset: FileDataset) => {
    try {
      await deleteMutation.mutateAsync(fileDataset.id);
      messageApi.success('文件数据集已删除');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '删除文件数据集失败');
      throw error;
    }
  };

  const confirmDelete = (fileDataset: FileDataset) => {
    modalApi.confirm({
      title: '删除文件数据集',
      content: `确认删除“${fileDataset.name}”及其原始文件吗？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => remove(fileDataset),
    });
  };

  const columns: TableProps<FileDataset>['columns'] = [
    { title: '数据集名称', dataIndex: 'name', width: 200, ellipsis: true },
    { title: '原始文件', dataIndex: 'originalFileName', width: 220, ellipsis: true },
    { title: '格式', dataIndex: 'format', width: 120, render: (value: FileDataset['format']) => <Tag>{fileDatasetFormatLabels[value]}</Tag> },
    {
      title: '压缩',
      dataIndex: 'compression',
      width: 90,
      render: (value: FileDataset['compression']) => value === 'GZIP' ? <Tag color="blue">{fileDatasetCompressionLabels[value]}</Tag> : '—',
    },
    { title: '大小', dataIndex: 'sizeBytes', width: 100, align: 'right', render: (value: number) => formatFileSize(value) },
    {
      title: '解析状态',
      dataIndex: 'parseStatus',
      width: 100,
      render: (value: FileDatasetParseStatus, fileDataset: FileDataset) => (
        <Tag color={parseStatusColors[value]}>
          {fileDatasetParseStatusLabels[value]}{fileDataset.parsingConfigured ? ' · 已配置' : ''}
        </Tag>
      ),
    },
    { title: '说明', dataIndex: 'description', width: 220, ellipsis: true, render: (value: string | null) => value || '—' },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDateTime(value) },
    {
      title: '操作',
      key: 'action',
      width: 140,
      fixed: 'right',
      render: (_: unknown, fileDataset: FileDataset) => (
        <Space size={2}>
          <Tooltip title="下载原文件">
            <Button
              type="text"
              size="small"
              icon={<DownloadOutlined />}
              aria-label={`下载${fileDataset.name}`}
              loading={downloadMutation.isPending && downloadMutation.variables === fileDataset.id}
              onClick={() => void download(fileDataset)}
            />
          </Tooltip>
          {canUpdate && <Tooltip title="修改信息">
            <Button type="text" size="small" icon={<EditOutlined />} aria-label={`修改${fileDataset.name}`} onClick={() => setEditingFileDataset(fileDataset)} />
          </Tooltip>}
          {canUpdate && <Tooltip title="解析设置">
            <Button type="text" size="small" icon={<SlidersOutlined />} aria-label={`配置${fileDataset.name}解析参数`} onClick={() => setParsingFileDataset(fileDataset)} />
          </Tooltip>}
          {(canUpdate || canDelete) && (
            <Dropdown
              trigger={['click']}
              menu={{
                items: [
                  ...(canUpdate ? [{ key: 'replace', icon: <SwapOutlined />, label: '替换内容' }] : []),
                  ...(canDelete ? [{ key: 'delete', danger: true, icon: <DeleteOutlined />, label: '删除' }] : []),
                ],
                onClick: ({ key }) => {
                  if (key === 'replace') setReplacingFileDataset(fileDataset);
                  if (key === 'delete') confirmDelete(fileDataset);
                },
              }}
            >
              <Tooltip title="更多操作">
                <Button type="text" size="small" icon={<EllipsisOutlined />} aria-label={`${fileDataset.name}更多操作`} />
              </Tooltip>
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
        {canViewDirectories && (
          <DirectoryTreePanel
            scope="FILE_DATASET"
            tree={directoriesQuery.data ?? []}
            loading={directoriesQuery.isFetching}
            selection={directorySelection}
            canManage={canManageDirectories}
            onSelectionChange={selectDirectory}
          />
        )}
        <Card className="management-card">
          <div className="management-toolbar">
            <Form<FileDatasetFilters> form={filterForm} layout="inline" className="management-filter-form" onFinish={search}>
              <Form.Item name="keyword" label="名称/文件">
                <Input allowClear placeholder="按数据集名称或文件名筛选" className="file-dataset-keyword-input" />
              </Form.Item>
              <Form.Item name="format" label="格式">
                <Select allowClear placeholder="全部" options={fileDatasetFormatOptions} className="file-dataset-format-select" />
              </Form.Item>
              <Form.Item name="compression" label="压缩">
                <Select allowClear placeholder="全部" options={fileDatasetCompressionOptions} className="file-dataset-format-select" />
              </Form.Item>
              <Form.Item name="parseStatus" label="解析状态">
                <Select allowClear placeholder="全部" options={parseStatusOptions} className="file-dataset-status-select" />
              </Form.Item>
            </Form>
            <Space size={4} className="management-toolbar-actions">
              <Button type="primary" onClick={() => filterForm.submit()}>查询</Button>
              <Button onClick={reset}>重置</Button>
              <Button icon={<ReloadOutlined />} onClick={() => void fileDatasetsQuery.refetch()}>刷新</Button>
              {canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerOpen(true)}>新建</Button>}
            </Space>
          </div>
          {fileDatasetsQuery.isError && (
            <Alert
              type="error"
              showIcon
              className="management-query-error"
              message="文件数据集加载失败"
              action={<Button size="small" onClick={() => void fileDatasetsQuery.refetch()}>重试</Button>}
            />
          )}
          <Table<FileDataset>
            size="small"
            className="management-table"
            rowKey="id"
            columns={columns}
            dataSource={fileDatasetsQuery.data?.content ?? []}
            loading={fileDatasetsQuery.isFetching}
            scroll={{ x: 1360, y: '100%' }}
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
        onClose={() => {
          setCreateDrawerOpen(false);
          setEditingFileDataset(null);
        }}
      />
      <ReplaceFileDatasetContentDrawer
        open={Boolean(replacingFileDataset)}
        fileDataset={replacingFileDataset}
        onClose={() => setReplacingFileDataset(null)}
      />
      <FileDatasetParsingDrawer
        open={Boolean(parsingFileDataset)}
        fileDataset={parsingFileDataset}
        onClose={() => setParsingFileDataset(null)}
      />
    </>
  );
};
