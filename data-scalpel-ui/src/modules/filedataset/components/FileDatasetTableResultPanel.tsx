import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ReloadOutlined,
  SwapOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import {
  Alert,
  Button,
  Descriptions,
  Empty,
  Input,
  Modal,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Upload,
  message,
} from 'antd';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import {
  useAppendFileDatasetTable,
  useDeleteFileDatasetTableSource,
  useDownloadFileDatasetFile,
  useFileDatasetPreview,
  useFileDatasetSchema,
  useFileDatasetTableSources,
  useReplaceFileDatasetTableData,
  useReplaceFileDatasetTableSource,
  useUpdateFileDatasetTable,
} from '../hooks/useFileDatasets';
import {
  fileDatasetAccept,
  fileDatasetParseStatusLabels,
  type FileDataset,
  type FileDatasetField,
  type FileDatasetParseStatus,
  type FileDatasetTable,
  type FileDatasetTableSource,
} from '../model/fileDataset';

interface FileDatasetTableResultPanelProps {
  dataset: FileDataset;
  table: FileDatasetTable | null;
  canUpdate: boolean;
}

interface PreviewRow {
  key: number;
  values: unknown[];
}

const parseStatusColors: Record<FileDatasetParseStatus, string> = {
  QUEUED: 'blue',
  PARSING: 'processing',
  SCHEMA_READY: 'warning',
  READY: 'success',
};

const formatPreviewValue = (value: unknown): string => {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'string') return value;
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
};

const formatFieldType = (field: FileDatasetField): string => {
  if (field.fieldType === 'STRING' && field.length !== null) return `STRING(${field.length})`;
  if (field.fieldType === 'DECIMAL' && field.precision !== null && field.scale !== null) {
    return `DECIMAL(${field.precision}, ${field.scale})`;
  }
  return field.fieldType;
};

const previewColumns = (fields: FileDatasetField[]): TableProps<PreviewRow>['columns'] => fields.map((field, index) => ({
  title: <Space size={4}>{field.name}<Tag>{formatFieldType(field)}</Tag></Space>,
  key: `${field.sortOrder}-${field.name}`,
  width: 180,
  ellipsis: true,
  render: (_value: unknown, row: PreviewRow) => formatPreviewValue(row.values[index]),
}));

const metadataString = (metadata: Record<string, unknown>, key: string): string | null => {
  const value = metadata[key];
  return typeof value === 'string' && value.trim() ? value : null;
};

const formatDateTime = (value: string | null): string => value
  ? new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'medium',
    hour12: false,
  }).format(new Date(value))
  : '—';

const errorMessage = (error: unknown, fallback: string): string => (
  error instanceof ApiError || error instanceof Error ? error.message : fallback
);

export const FileDatasetTableResultPanel = ({
  dataset,
  table,
  canUpdate,
}: FileDatasetTableResultPanelProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const updateMutation = useUpdateFileDatasetTable();
  const appendMutation = useAppendFileDatasetTable();
  const replaceDataMutation = useReplaceFileDatasetTableData();
  const replaceSourceMutation = useReplaceFileDatasetTableSource();
  const deleteSourceMutation = useDeleteFileDatasetTableSource();
  const downloadMutation = useDownloadFileDatasetFile();
  const ready = table?.parseStatus === 'READY';
  const schemaReady = table?.parseStatus === 'SCHEMA_READY';
  const schemaAvailable = ready || schemaReady;
  const tableLoadSupported = dataset.type !== 'EXCEL' && dataset.type !== 'GDB';
  const schemaQuery = useFileDatasetSchema(dataset.id, table?.id, Boolean(schemaAvailable));
  const previewQuery = useFileDatasetPreview(dataset.id, table?.id, Boolean(ready && table?.previewSupported));
  const sourcesQuery = useFileDatasetTableSources(dataset.id, table?.id, Boolean(table));
  const sources = sourcesQuery.data ?? [];

  if (!table) {
    return (
      <div className="file-dataset-table-result file-dataset-table-result-empty">
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择一张逻辑表" />
      </div>
    );
  }

  const previewUnavailableReason = metadataString(table.sourceMetadata, 'previewUnavailableReason')
    ?? (dataset.type === 'GDB'
      ? '该 FileGDB 图层已保存字段定义，但当前读取器暂不支持记录预览。'
      : '该表已经保存字段定义，但当前内容不支持安全预览。');

  const rename = () => {
    let name = table.name;
    modalApi.confirm({
      title: '修改表名称',
      content: (
        <Input
          defaultValue={table.name}
          maxLength={255}
          onChange={(event) => { name = event.target.value; }}
        />
      ),
      okText: '保存',
      cancelText: '取消',
      onOk: async () => {
        if (!name.trim()) throw new Error('表名称不能为空');
        try {
          await updateMutation.mutateAsync({
            datasetId: dataset.id,
            tableId: table.id,
            name: name.trim(),
          });
          messageApi.success('表名称已更新');
        } catch (error) {
          messageApi.error(errorMessage(error, '修改表名称失败'));
          throw error;
        }
      },
    });
  };

  const append = async (file: File) => {
    try {
      const result = await appendMutation.mutateAsync({
        datasetId: dataset.id,
        tableId: table.id,
        file,
      });
      messageApi.success(`追加任务已提交（${result.jobId}）`);
    } catch (error) {
      messageApi.error(errorMessage(error, '提交追加任务失败'));
    }
  };

  const replaceAll = (file: File) => modalApi.confirm({
    title: '全量覆盖表数据',
    content: `文件“${file.name}”校验成功后将替代当前全部来源，并立即删除旧文件和对象，操作不可恢复。已排队或运行的 Canvas 任务可能因旧对象消失而失败；校验期间当前数据仍可使用。`,
    okText: '确认覆盖',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        const result = await replaceDataMutation.mutateAsync({
          datasetId: dataset.id,
          tableId: table.id,
          file,
        });
        messageApi.success(`全量覆盖任务已提交（${result.jobId}）`);
      } catch (error) {
        messageApi.error(errorMessage(error, '提交全量覆盖任务失败'));
        throw error;
      }
    },
  });

  const replaceSource = (source: FileDatasetTableSource, file: File) => modalApi.confirm({
    title: '替换当前数据来源',
    content: `文件“${file.name}”校验成功后将替换“${source.sourceName}”，并立即删除旧文件和对象。操作不可恢复，已排队或运行的 Canvas 任务可能失败。`,
    okText: '确认替换',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        const result = await replaceSourceMutation.mutateAsync({
          datasetId: dataset.id,
          tableId: table.id,
          sourceId: source.id,
          file,
        });
        messageApi.success(`来源替换任务已提交（${result.jobId}）`);
      } catch (error) {
        messageApi.error(errorMessage(error, '提交来源替换任务失败'));
        throw error;
      }
    },
  });

  const deleteSource = (source: FileDatasetTableSource) => modalApi.confirm({
    title: '删除当前数据来源',
    content: `确认删除“${source.sourceName}”吗？对象会立即删除且不可恢复；如果这是最后一个来源，逻辑表也会被删除。已排队或运行的 Canvas 任务可能失败。`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await deleteSourceMutation.mutateAsync({
          datasetId: dataset.id,
          tableId: table.id,
          sourceId: source.id,
        });
        messageApi.success('数据来源已删除');
      } catch (error) {
        messageApi.error(errorMessage(error, '删除数据来源失败'));
        throw error;
      }
    },
  });

  const downloadSource = async (source: FileDatasetTableSource) => {
    try {
      const blob = await downloadMutation.mutateAsync({
        datasetId: dataset.id,
        fileId: source.sourceFileId,
      });
      downloadBlob(blob, source.sourceName);
    } catch (error) {
      messageApi.error(errorMessage(error, '下载来源文件失败'));
    }
  };

  const fields = schemaQuery.data?.fields ?? previewQuery.data?.fields ?? [];
  const rows: PreviewRow[] = (previewQuery.data?.rows ?? []).map((values, index) => ({
    key: index,
    values,
  }));
  const detailItems = [
    { key: 'code', label: '表代码', children: <code>{table.code}</code> },
    { key: 'sourceCount', label: '当前来源', children: `${table.sourceCount} 个` },
    { key: 'rowCount', label: '总记录数', children: `${table.totalRowCount} 条` },
    { key: 'samples', label: '预览样本', children: schemaAvailable ? `${table.sampledRecordCount} 条` : '—' },
    {
      key: 'load',
      label: '装载状态',
      children: table.currentLoadJobId ? `处理中（${table.currentLoadJobId}）` : '空闲',
    },
  ];
  const schemaColumns: TableProps<FileDatasetField>['columns'] = [
    { title: '序号', dataIndex: 'sortOrder', width: 70, align: 'right', render: (value: number) => value + 1 },
    { title: '字段名称', dataIndex: 'name', minWidth: 200, ellipsis: true },
    {
      title: '平台类型',
      key: 'fieldType',
      width: 180,
      render: (_value: unknown, field: FileDatasetField) => <Tag>{formatFieldType(field)}</Tag>,
    },
    { title: '允许空值', dataIndex: 'nullable', width: 100, render: (value: boolean) => value ? '是' : '否' },
  ];
  const sourceColumns: TableProps<FileDatasetTableSource>['columns'] = [
    {
      title: '来源文件',
      dataIndex: 'sourceName',
      width: 220,
      ellipsis: true,
      render: (value: string) => value,
    },
    {
      title: '顺序',
      dataIndex: 'sourceOrder',
      width: 70,
      align: 'right',
      render: (value: number) => value + 1,
    },
    {
      title: '记录数',
      dataIndex: 'rowCount',
      width: 100,
      align: 'right',
      render: (value: number) => value,
    },
    {
      title: '生效时间',
      dataIndex: 'activatedAt',
      width: 180,
      render: formatDateTime,
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      fixed: 'right',
      render: (_value: unknown, source: FileDatasetTableSource) => (
        <Space size={2}>
          <Tooltip title="下载来源文件">
            <Button
              type="text"
              icon={<DownloadOutlined />}
              aria-label={`下载来源${source.sourceName}`}
              onClick={() => void downloadSource(source)}
            />
          </Tooltip>
          {canUpdate && tableLoadSupported && (
            <Upload
              accept={fileDatasetAccept(dataset.type)}
              maxCount={1}
              showUploadList={false}
              disabled={Boolean(table.currentLoadJobId)}
              beforeUpload={(file) => {
                void replaceSource(source, file);
                return Upload.LIST_IGNORE;
              }}
            >
              <Tooltip title="替换该来源">
                <Button
                  type="text"
                  icon={<SwapOutlined />}
                  disabled={Boolean(table.currentLoadJobId)}
                  aria-label={`替换来源${source.sourceName}`}
                />
              </Tooltip>
            </Upload>
          )}
          {canUpdate && (
            <Tooltip title="删除来源">
              <Button
                type="text"
                danger
                icon={<DeleteOutlined />}
                disabled={Boolean(table.currentLoadJobId)}
                aria-label={`删除来源${source.sourceName}`}
                onClick={() => deleteSource(source)}
              />
            </Tooltip>
          )}
        </Space>
      ),
    },
  ];

  const resultTabs = [
    ...(schemaAvailable ? [{
      key: 'schema',
      label: `Schema ${fields.length}`,
      children: (
        <Table<FileDatasetField>
          size="small"
          rowKey={(field) => `${field.sortOrder}-${field.name}`}
          columns={schemaColumns}
          dataSource={fields}
          loading={schemaQuery.isFetching}
          pagination={false}
          scroll={{ x: 560, y: '100%' }}
          locale={{ emptyText: schemaQuery.isError ? 'Schema 加载失败' : '没有字段定义' }}
        />
      ),
    }] : []),
    ...(ready && table.previewSupported ? [{
      key: 'preview',
      label: `数据预览 ${rows.length}`,
      children: (
        <Table<PreviewRow>
          size="small"
          rowKey="key"
          columns={previewColumns(previewQuery.data?.fields ?? fields)}
          dataSource={rows}
          loading={previewQuery.isFetching}
          pagination={false}
          scroll={{ x: 'max-content', y: '100%' }}
          locale={{ emptyText: previewQuery.isError ? '数据预览加载失败' : '该表没有可预览的记录' }}
        />
      ),
    }] : []),
    {
      key: 'sources',
      label: `数据来源 ${sources.length}`,
      children: (
        <Table<FileDatasetTableSource>
          size="small"
          rowKey="id"
          columns={sourceColumns}
          dataSource={sources}
          loading={sourcesQuery.isFetching}
          pagination={false}
          scroll={{ x: 820, y: '100%' }}
          locale={{ emptyText: sourcesQuery.isError ? '数据来源加载失败' : '还没有数据来源' }}
        />
      ),
    },
  ];

  return (
    <div className="file-dataset-table-result">
      {messageContext}
      {modalContext}
      <div className="file-dataset-table-result-header">
        <div className="file-dataset-table-result-identity">
          <div>
            <strong>{table.name}</strong>
            <Tag color={parseStatusColors[table.parseStatus]}>
              {fileDatasetParseStatusLabels[table.parseStatus]}
              {table.currentLoadJobId ? ' · 正在追加/覆盖' : ''}
            </Tag>
          </div>
          <span>稳定逻辑表 · 仅保留当前有效数据</span>
        </div>
        <Space size={4}>
          <Tooltip title="刷新 Schema、预览和来源">
            <Button
              icon={<ReloadOutlined />}
              aria-label="刷新表解析结果"
              onClick={() => void Promise.all([
                sourcesQuery.refetch(),
                ...(schemaAvailable ? [schemaQuery.refetch()] : []),
                ...(ready && table.previewSupported ? [previewQuery.refetch()] : []),
              ])}
            />
          </Tooltip>
          {canUpdate && <Button icon={<EditOutlined />} onClick={rename}>修改名称</Button>}
          {canUpdate && tableLoadSupported && schemaAvailable && (
            <>
              <Upload
                accept={fileDatasetAccept(dataset.type)}
                maxCount={1}
                showUploadList={false}
                disabled={Boolean(table.currentLoadJobId)}
                beforeUpload={(file) => {
                  void append(file);
                  return Upload.LIST_IGNORE;
                }}
              >
                <Button
                  icon={<UploadOutlined />}
                  loading={appendMutation.isPending}
                  disabled={Boolean(table.currentLoadJobId)}
                >
                  追加数据
                </Button>
              </Upload>
              <Upload
                accept={fileDatasetAccept(dataset.type)}
                maxCount={1}
                showUploadList={false}
                disabled={Boolean(table.currentLoadJobId)}
                beforeUpload={(file) => {
                  replaceAll(file);
                  return Upload.LIST_IGNORE;
                }}
              >
                <Button
                  danger
                  icon={<SwapOutlined />}
                  loading={replaceDataMutation.isPending}
                  disabled={Boolean(table.currentLoadJobId)}
                >
                  全量覆盖
                </Button>
              </Upload>
            </>
          )}
        </Space>
      </div>
      <Descriptions size="small" column={3} items={detailItems} />
      {table.currentLoadJobId && schemaAvailable && (
        <Alert
          type="info"
          showIcon
          message="已就绪 · 正在追加或覆盖"
          description="新文件正在后台执行完整 Schema 校验；完成前当前数据、Schema 和预览保持可用。"
        />
      )}
      {(table.parseStatus === 'QUEUED' || table.parseStatus === 'PARSING') && (
        <Alert type="info" showIcon message="初始来源正在后台解析" description="完成后自动展示 Schema、预览和来源信息。" />
      )}
      {schemaReady && (
        <Alert type="warning" showIcon message="当前表仅支持 Schema" description={previewUnavailableReason} />
      )}
      {!tableLoadSupported && schemaAvailable && (
        <Alert
          type="info"
          showIcon
          message={`${dataset.type === 'EXCEL' ? 'Excel' : 'FileGDB'} 暂不开放表级追加与覆盖`}
          description="当前阶段仍通过文件页执行整文件替换，后续再设计 Sheet/图层映射。"
        />
      )}
      {ready && table.truncated && (
        <Alert type="info" showIcon message="预览样本已截断" description="Schema 已完成全量检查，管理端只保留有界预览样本。" />
      )}
      {(sourcesQuery.isError || (schemaAvailable && schemaQuery.isError)
        || (ready && table.previewSupported && previewQuery.isError)) && (
        <Alert type="error" showIcon message="表详情的部分数据加载失败" />
      )}
      <Tabs
        className="file-dataset-table-result-tabs"
        defaultActiveKey={schemaAvailable ? 'schema' : 'sources'}
        items={resultTabs}
      />
    </div>
  );
};
