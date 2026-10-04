import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  DashboardOutlined,
  EditOutlined,
  FileTextOutlined,
  PlusOutlined,
  ReloadOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, ConfigProvider, Empty, Form, Modal, Select, Table, Tooltip, message } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { ManagementDateTime, ManagementListCell, ManagementName, ManagementStatusIndicator } from '../../../shared/components/ManagementListCells';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { DirectoryTreePanel, findDirectoryDescendantIds, useDirectoryTree, type DirectorySelection } from '../../directory';
import { useCurrentUser } from '../../system';
import { FileDatasetUploadDrawer } from '../components/FileDatasetUploadDrawer';
import { fileDatasetDeleteConfirmation } from '../components/fileDatasetDeleteConfirmation';
import { FileDatasetDrawer } from '../components/FileDatasetDrawer';
import { FileDatasetParseQueueDrawer } from '../components/FileDatasetParseQueueDrawer';
import { FileDatasetTypeIcon } from '../components/FileDatasetTypeIcon';
import { fileDatasetReadiness } from '../model/fileDatasetReadiness';
import { workspaceResourceTheme } from '../../../shared/theme/workspaceResourceTheme';
import './file-dataset-list.css';
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
  const resultsRef = useRef<HTMLDivElement>(null);
  const [resultsWidth, setResultsWidth] = useState(900);
  useEffect(() => {
    const results = resultsRef.current;
    if (!results || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(([entry]) => setResultsWidth(Math.floor(entry.contentRect.width)));
    observer.observe(results);
    return () => observer.disconnect();
  }, []);
  const [filterForm] = Form.useForm<FileDatasetFilters>();
  const [filters, setFilters] = useState<FileDatasetFilters>({});
  const [directorySelection, setDirectorySelection] = useState<DirectorySelection>(undefined);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [editingFileDataset, setEditingFileDataset] = useState<FileDataset | null>(null);
  const [uploadDataset, setUploadDataset] = useState<FileDataset | null>(null);
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
  const totalsQuery = useFileDatasets({ page: 0, size: 1 }, canViewDirectories);
  const deleteMutation = useDeleteFileDataset();
  const refresh = () => {
    void fileDatasetsQuery.refetch();
    if (canViewDirectories) {
      void directoriesQuery.refetch();
      void totalsQuery.refetch();
    }
  };

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
      ...fileDatasetDeleteConfirmation(fileDataset),
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

  const informationWidth = Math.max(900, resultsWidth - 16) - 112;
  const columns: TableProps<FileDataset>['columns'] = [
    {
      title: '数据集 / 说明', dataIndex: 'name', width: informationWidth * 0.32,
      render: (value: string, dataset) => (
        <ManagementListCell
          primary={<ManagementName name={dataset.name} description={dataset.description}><Tooltip title={value}><Button type="link" className="file-dataset-name-button" onClick={() => navigate(`/file-dataset/${dataset.id}`, { state: { fromFileDatasetList: true } })}>{value}</Button></Tooltip></ManagementName>}
          secondary={dataset.description ? <Tooltip title={dataset.description}><span>{dataset.description}</span></Tooltip> : '—'}
        />
      ),
    },
    {
      title: '文件类型', dataIndex: 'type', width: informationWidth * 0.18,
      render: (type: FileDataset['type']) => <span className="file-dataset-format"><FileDatasetTypeIcon type={type} /><span>{fileDatasetTypeLabels[type]}</span></span>,
    },
    { title: '数据规模', width: informationWidth * 0.14, align: 'right', render: (_value: unknown, dataset) => <ManagementListCell primary={`${dataset.fileCount} 个文件`} secondary={`${dataset.tableCount} 张表`} /> },
    {
      title: '表就绪情况', width: informationWidth * 0.20,
      render: (_value: unknown, dataset) => {
        const readiness = fileDatasetReadiness(dataset);
        return <ManagementListCell
          primary={<ManagementStatusIndicator label={readiness.label} tone={readiness.tone} title={readiness.help} />}
          secondary={readiness.detail}
        />;
      },
    },
    { title: '更新时间', dataIndex: 'updatedAt', width: informationWidth * 0.16, render: (value: string) => <ManagementDateTime value={value} /> },
    {
      title: '操作', key: 'action', width: 112, fixed: 'right', className: 'file-dataset-actions-column',
      render: (_value: unknown, dataset) => (canUpdate || canDelete) && (
        <div className="management-row-actions">
          {canUpdate && <Tooltip title="上传文件"><Button type="text" icon={<UploadOutlined />} aria-label={`上传文件到${dataset.name}`} onClick={() => setUploadDataset(dataset)} /></Tooltip>}
          {canUpdate && <Tooltip title="修改数据集"><Button type="text" icon={<EditOutlined />} aria-label={`修改${dataset.name}`} onClick={() => setEditingFileDataset(dataset)} /></Tooltip>}
          {canDelete && <Tooltip title="删除数据集"><Button type="text" danger icon={<DeleteOutlined />} aria-label={`删除${dataset.name}`} onClick={() => confirmDelete(dataset)} /></Tooltip>}
        </div>
      ),
    },
  ];
  const hasFilters = Boolean(filters.keyword || filters.type || directorySelection !== undefined);

  return (
    <ConfigProvider theme={workspaceResourceTheme}>
      {messageContext}
      {modalContext}
      <div className={`file-dataset-list-page ${canViewDirectories ? 'directory-management-layout' : 'page-stack'}`}>
        {canViewDirectories && <DirectoryTreePanel scope="FILE_DATASET" totalResourceCount={totalsQuery.data?.totalElements ?? 0} tree={directoriesQuery.data ?? []} loading={directoriesQuery.isFetching} selection={directorySelection} canManage={canManageDirectories} onSelectionChange={selectDirectory} />}
        <section className="management-workbench">
          <div className="management-filter-strip">
            <Form<FileDatasetFilters> autoComplete="off" form={filterForm} layout="inline" className="management-filter-form" onFinish={applyDirectFilters}>
              <Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索数据集名称" aria-label="搜索数据集名称" className="file-dataset-keyword-input" /></Form.Item>
              <Form.Item name="type"><Select allowClear showSearch={{ optionFilterProp: 'label' }} aria-label="文件类型" placeholder="全部文件类型" options={fileDatasetTypeOptions}
                popupMatchSelectWidth={240} classNames={{ popup: { root: 'file-dataset-format-popup' } }}
                optionRender={(option) => <span className="file-dataset-format"><FileDatasetTypeIcon type={option.data.value} /><span>{option.data.label}</span></span>}
                labelRender={({ value, label }) => <span className="file-dataset-format"><FileDatasetTypeIcon type={value as FileDataset['type']} /><span>{label}</span></span>}
                className="file-dataset-format-select" /></Form.Item>
            </Form>
            <ManagementFilterActions form={filterForm} appliedFilters={filters} additionalActive={directorySelection !== undefined} loading={fileDatasetsQuery.isFetching} onReset={reset} />
            <div className="file-dataset-list-commands">
              <Tooltip title="刷新列表与目录"><Button icon={<ReloadOutlined />} aria-label="刷新文件数据集列表与目录" onClick={refresh} /></Tooltip>
              <Button icon={<DashboardOutlined />} onClick={() => setParseQueueDrawerOpen(true)}>解析队列</Button>
              {canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerOpen(true)}>新建数据集</Button>}
            </div>
          </div>
          <div className="management-results-surface" ref={resultsRef}>
            <div className="management-result-toolbar">
            <span className="management-result-title"><FileTextOutlined aria-hidden="true" />文件数据集 <span className="management-result-count">共 {fileDatasetsQuery.data?.totalElements ?? 0} 项</span></span>

            </div>
            {fileDatasetsQuery.isError && <Alert type="error" showIcon className="management-query-error" message="文件数据集加载失败" action={<Button size="small" onClick={() => void fileDatasetsQuery.refetch()}>重试</Button>} />}
            <Table<FileDataset>
            size="small"
            className="management-table management-table-comfortable"
            rowKey="id"
            columns={columns}
            dataSource={fileDatasetsQuery.data?.content ?? []}
            loading={fileDatasetsQuery.isFetching}
            scroll={{ x: 900, y: '100%' }}
            tableLayout="fixed"
            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={hasFilters ? '没有符合当前条件的数据集' : '暂无文件数据集'}>{hasFilters ? <Button onClick={reset}>清空筛选</Button> : canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerOpen(true)}>新建数据集</Button>}</Empty> }}
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
              setPage(pagination.pageSize !== size ? 0 : (pagination.current ?? 1) - 1);
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
      <FileDatasetUploadDrawer dataset={canUpdate ? uploadDataset : null} onClose={() => { setUploadDataset(null); refresh(); }} onOpenDetail={(tab) => { if (uploadDataset) navigate(`/file-dataset/${uploadDataset.id}?tab=${tab}`, { state: { fromFileDatasetList: true } }); setUploadDataset(null); }} />
      <FileDatasetParseQueueDrawer
        open={parseQueueDrawerOpen}
        onClose={() => setParseQueueDrawerOpen(false)}
      />
    </ConfigProvider>
  );
};
