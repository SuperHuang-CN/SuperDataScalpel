import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  EditOutlined,
  LoadingOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Drawer, Input, Modal, Select, Space, Steps, Table, Tag, TreeSelect, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useDataSources } from '../../datasource';
import { directoryTreeSelectData, useDirectoryTree } from '../../directory';
import {
  fileDatasetParseStatusLabels,
  fileDatasetTypeLabels,
  useFileDatasetCanvasMetadata,
  useFileDatasets,
  useFileDatasetTables,
  type FileDatasetTable,
} from '../../filedataset';
import {
  type ManagedDataModelDraftResult,
  useCreateManagedDataModelDrafts,
  useFileDatasetImportPreviews,
  useModelWarehouseLayers,
} from '../hooks/useDataModels';
import {
  dataModelFieldTypeLabels,
  geometryKindLabels,
} from '../model/dataModel';
import {
  applyFileDatasetDraftWarehouseLayer,
  applyFileDatasetImportPreview,
  clearDuplicateFileDatasetDraftCodeCandidates,
  fileDatasetDraftIssues,
  mergeFileDatasetModelDrafts,
  type FileDatasetModelDraft,
} from '../model/fileDatasetModelImport';
import {
  hasManagedTableDraftIssues,
  isManagedImportTargetSelectable,
  toManagedDataModelDraftRequest,
  type ManagedImportFieldDraft,
} from '../model/managedTableImport';
import { ManagedImportFieldEditor } from './ManagedTableModelImportDrawer';

interface FileDatasetModelImportDrawerProps {
  open: boolean;
  canViewDirectories: boolean;
  initialDirectoryId?: string;
  initialTargetStorageDataSourceId?: string;
  onClose: () => void;
  onViewModel: (modelId: string) => void;
  onAdjustFields: (modelId: string) => void;
}

const listRequest = {
  page: 0,
  size: 500,
  sort: '-updatedAt,name',
} as const;

const jdbcDataSourceRequest = {
  search: 'enabled:"true"',
  page: 0,
  size: 500,
  sort: 'code',
} as const;

const enabledWarehouseLayerRequest = {
  search: 'enabled:"true"',
  page: 0,
  size: 500,
  sort: 'sortOrder,code',
} as const;

const errorMessage = (error: unknown, fallback: string): string => {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return fallback;
};

const isReady = (status: FileDatasetTable['parseStatus']): boolean => (
  status === 'READY' || status === 'SCHEMA_READY'
);

const mappingLabel = (quality: ManagedImportFieldDraft['mappingQuality']): string => {
  if (quality === 'EXACT') return '精确';
  if (quality === 'NORMALIZED') return '归一化';
  if (quality === 'LOSSY') return '有损';
  return '不支持';
};

const resultMessage = (result: ManagedDataModelDraftResult): string => {
  if (result.detail) return `已创建受管草稿，带入 ${result.detail.fields.length} 个字段`;
  return errorMessage(result.error, '创建草稿失败，请稍后重试');
};

export const FileDatasetModelImportDrawer = ({
  open,
  canViewDirectories,
  initialDirectoryId,
  initialTargetStorageDataSourceId,
  onClose,
  onViewModel,
  onAdjustFields,
}: FileDatasetModelImportDrawerProps) => {
  const [step, setStep] = useState(0);
  const [fileDatasetId, setFileDatasetId] = useState<string>();
  const [targetStorageDataSourceId, setTargetStorageDataSourceId] = useState<string | undefined>(
    initialTargetStorageDataSourceId,
  );
  const [directoryId, setDirectoryId] = useState<string | undefined>(initialDirectoryId);
  const [warehouseLayerId, setWarehouseLayerId] = useState<string>();
  const [selectedTables, setSelectedTables] = useState<Map<string, FileDatasetTable>>(new Map());
  const [drafts, setDrafts] = useState<FileDatasetModelDraft[]>([]);
  const [results, setResults] = useState<ManagedDataModelDraftResult[]>([]);
  const [editingField, setEditingField] = useState<{ draftKey: string; fieldKey: string }>();
  const [dirty, setDirty] = useState(false);
  const [modalApi, modalContext] = Modal.useModal();

  const datasetsQuery = useFileDatasets(listRequest);
  const tablesQuery = useFileDatasetTables(fileDatasetId, open && step === 0);
  const tableIds = (tablesQuery.data?.content ?? []).map((table) => table.id);
  const tableMetadataQuery = useFileDatasetCanvasMetadata(tableIds, open && step === 0 && tableIds.length > 0);
  const dataSourcesQuery = useDataSources(jdbcDataSourceRequest, open);
  const warehouseLayersQuery = useModelWarehouseLayers(enabledWarehouseLayerRequest, open);
  const directoriesQuery = useDirectoryTree('MODEL', open && canViewDirectories);
  const previewMutation = useFileDatasetImportPreviews();
  const createMutation = useCreateManagedDataModelDrafts();

  const selectedDataset = datasetsQuery.data?.content.find((dataset) => dataset.id === fileDatasetId);
  const metadataByTableId = useMemo(() => new Map(
    (tableMetadataQuery.data?.tables ?? []).map((table) => [table.fileDatasetTableId, table]),
  ), [tableMetadataQuery.data]);
  const draftIssues = useMemo(() => fileDatasetDraftIssues(drafts), [drafts]);
  const hasDraftIssues = hasManagedTableDraftIssues(draftIssues);
  const failedKeys = useMemo(() => new Set(
    results.filter((result) => !result.detail).map((result) => result.key),
  ), [results]);
  const successCount = results.length - failedKeys.size;
  const busy = previewMutation.isPending || createMutation.isPending;
  const selectedTarget = dataSourcesQuery.data?.content.find((source) => (
    source.id === targetStorageDataSourceId && isManagedImportTargetSelectable(source)
  ));
  const effectiveTargetStorageDataSourceId = selectedTarget?.id;

  const datasetOptions = datasetsQuery.data?.content.map((dataset) => ({
    value: dataset.id,
    label: `${dataset.name}（${fileDatasetTypeLabels[dataset.type]} · 可用表 ${dataset.readyTableCount}/${dataset.tableCount}）`,
    disabled: dataset.readyTableCount === 0,
  })) ?? [];
  const targetOptions = dataSourcesQuery.data?.content
    .filter(isManagedImportTargetSelectable)
    .map((source) => ({
      value: source.id,
      label: `${source.name}（${source.connection.kind === 'JDBC' ? source.connection.databaseName : source.type}）`,
    })) ?? [];
  const warehouseLayerOptions = warehouseLayersQuery.data?.content.map((layer) => ({
    value: layer.id,
    label: `${layer.code} · ${layer.name}${layer.modelCodePrefix ? `（${layer.modelCodePrefix}*）` : ''}`,
  })) ?? [];
  const warehouseLayerPrefix = (layerId?: string) => warehouseLayersQuery.data?.content
    .find((layer) => layer.id === layerId)?.modelCodePrefix ?? undefined;

  const confirmDiscard = (action: () => void) => {
    if (!dirty) {
      action();
      return;
    }
    modalApi.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: '放弃未保存的调整？',
      content: '当前模型或字段已有修改，离开后这些调整不会保留。',
      okText: '放弃修改',
      okButtonProps: { danger: true },
      cancelText: '继续编辑',
      onOk: action,
    });
  };

  const requestClose = () => confirmDiscard(onClose);

  const loadPreviews = async (targetId: string, candidates: FileDatasetModelDraft[]) => {
    if (!candidates.length) return;
    setDrafts((current) => current.map((draft) => candidates.some((item) => item.key === draft.key)
      ? {
        ...draft,
        fields: [],
        previewState: 'loading',
        previewError: undefined,
        tableIssues: [],
        previewIssues: [],
        warnings: [],
      }
      : draft));
    const previewResults = await previewMutation.mutateAsync(candidates.map((draft) => ({
      key: draft.key,
      request: {
        fileDatasetId: draft.datasetId,
        fileDatasetTableId: draft.table.id,
        targetStorageDataSourceId: targetId,
      },
    })));
    setDrafts((current) => current.map((draft) => {
      const result = previewResults.find((item) => item.key === draft.key);
      if (!result) return draft;
      if (result.preview) return applyFileDatasetImportPreview(draft, result.preview);
      return {
        ...draft,
        fields: [],
        previewState: 'error',
        previewError: errorMessage(result.error, '文件逻辑表结构读取失败'),
      };
    }));
  };

  const enterReview = () => {
    if (!selectedDataset) return;
    const nextDrafts = mergeFileDatasetModelDrafts(
      selectedDataset,
      [...selectedTables.values()].sort((left, right) => left.code.localeCompare(right.code)),
      drafts,
      warehouseLayerId,
      warehouseLayerPrefix(warehouseLayerId),
    );
    const newDrafts = nextDrafts.filter((draft) => !drafts.some((current) => current.key === draft.key));
    setDrafts(nextDrafts);
    setStep(1);
    setDirty(false);
    if (effectiveTargetStorageDataSourceId && newDrafts.length > 0) {
      void loadPreviews(effectiveTargetStorageDataSourceId, newDrafts);
    }
  };

  const backToSource = () => confirmDiscard(() => {
    setDrafts([]);
    setResults([]);
    setDirty(false);
    setStep(0);
  });

  const selectTarget = (targetId: string) => {
    const apply = async () => {
      setTargetStorageDataSourceId(targetId);
      setDirty(true);
      await loadPreviews(targetId, drafts);
    };
    if (drafts.some((draft) => draft.fields.length > 0)) {
      modalApi.confirm({
        rootClassName: 'business-overlay business-modal-overlay',
        title: '切换目标数据存储',
        content: '切换后需要按新目标重新验证字段，当前字段调整会被重置。',
        okText: '确认切换',
        cancelText: '取消',
        onOk: apply,
      });
      return;
    }
    void apply();
  };

  const reloadPreviews = () => {
    if (!effectiveTargetStorageDataSourceId) return;
    const apply = () => loadPreviews(effectiveTargetStorageDataSourceId, drafts);
    modalApi.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: '重新读取逻辑表结构',
      content: '重新读取会重置当前字段调整，但会保留模型编码、名称、说明和目标表名。',
      okText: '重新读取',
      cancelText: '取消',
      onOk: apply,
    });
  };

  const updateDraft = (
    key: string,
    field: 'code' | 'name' | 'description' | 'physicalTableName',
    value: string,
  ) => {
    setDirty(true);
    setDrafts((current) => current.map((draft) => draft.key === key
      ? {
        ...draft,
        [field]: value,
        codeOverridden: draft.codeOverridden || field === 'code',
        modelValuesLocked: true,
      }
      : draft));
  };

  const selectBatchWarehouseLayer = (value?: string) => {
    setDirty(true);
    setWarehouseLayerId(value);
    const prefix = warehouseLayerPrefix(value);
    setDrafts((current) => clearDuplicateFileDatasetDraftCodeCandidates(
      current.map((draft) => draft.warehouseLayerOverridden
        ? draft
        : applyFileDatasetDraftWarehouseLayer(draft, value, prefix, false)),
    ));
  };

  const selectDraftWarehouseLayer = (key: string, value?: string) => {
    setDirty(true);
    const prefix = warehouseLayerPrefix(value);
    setDrafts((current) => clearDuplicateFileDatasetDraftCodeCandidates(
      current.map((draft) => draft.key === key
        ? applyFileDatasetDraftWarehouseLayer(draft, value, prefix, true)
        : draft),
    ));
  };

  const currentEditingField = editingField
    ? drafts.find((draft) => draft.key === editingField.draftKey)?.fields
      .find((field) => field.key === editingField.fieldKey) ?? null
    : null;

  const saveField = (field: ManagedImportFieldDraft) => {
    if (!editingField) return;
    setDirty(true);
    setDrafts((current) => current.map((draft) => draft.key === editingField.draftKey ? {
      ...draft,
      fields: draft.fields.map((candidate) => candidate.key === editingField.fieldKey ? field : candidate),
    } : draft));
    setEditingField(undefined);
  };

  const submit = async () => {
    if (!effectiveTargetStorageDataSourceId || hasDraftIssues) return;
    const items = drafts.flatMap((draft) => {
      const request = toManagedDataModelDraftRequest(draft, effectiveTargetStorageDataSourceId, directoryId);
      return request ? [{ key: draft.key, request }] : [];
    });
    if (items.length !== drafts.length) return;
    const nextResults = await createMutation.mutateAsync(items);
    setResults(nextResults);
    setDirty(false);
    setStep(2);
  };

  const editFailures = () => {
    setDrafts((current) => current.filter((draft) => failedKeys.has(draft.key)));
    setResults([]);
    setDirty(false);
    setStep(1);
  };

  const selectColumns: TableProps<FileDatasetTable>['columns'] = [
    {
      title: '逻辑表',
      key: 'table',
      width: 260,
      render: (_value, table) => (
        <Space direction="vertical" size={0}>
          <Typography.Text>{table.name}</Typography.Text>
          <Typography.Text type="secondary"><code>{table.code}</code></Typography.Text>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'parseStatus',
      width: 120,
      render: (status: FileDatasetTable['parseStatus']) => (
        <Space direction="vertical" size={0}>
          <Tag color={isReady(status) ? 'success' : 'default'}>
            {fileDatasetParseStatusLabels[status]}
          </Tag>
          {!isReady(status) && <Typography.Text type="secondary">尚未完成结构解析</Typography.Text>}
        </Space>
      ),
    },
    {
      title: '字段数',
      key: 'fieldCount',
      width: 100,
      render: (_value, table) => {
        if (tableMetadataQuery.isFetching) return <LoadingOutlined />;
        return metadataByTableId.get(table.id)?.fields.length ?? 0;
      },
    },
    {
      title: '结构来源',
      key: 'schemaSource',
      width: 130,
      render: () => selectedDataset && ['CSV', 'TSV', 'TXT', 'JSON', 'JSONL', 'EXCEL'].includes(selectedDataset.type)
        ? <Tag>样本推断</Tag> : <Tag color="blue">声明 Schema</Tag>,
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 180,
      render: (value: string) => new Date(value).toLocaleString(),
    },
  ];

  const fieldColumns = (draft: FileDatasetModelDraft): TableProps<ManagedImportFieldDraft>['columns'] => [
    { title: '来源字段', dataIndex: 'sourceName', width: 150, ellipsis: true, render: (value: string) => <code>{value}</code> },
    { title: '来源类型', dataIndex: 'nativeType', width: 160, ellipsis: true, render: (value: string) => <code>{value}</code> },
    { title: '字段编码', dataIndex: 'code', width: 150, ellipsis: true, render: (value: string) => value ? <code>{value}</code> : <Typography.Text type="danger">待补齐</Typography.Text> },
    { title: '字段名称', dataIndex: 'name', width: 150, ellipsis: true },
    {
      title: '目标类型',
      key: 'fieldType',
      width: 210,
      render: (_value, field) => {
        if (!field.fieldType) return <Typography.Text type="danger">待选择</Typography.Text>;
        if (field.fieldType === 'GEOMETRY' && field.geometry) {
          return `${geometryKindLabels[field.geometry.kind]} · ${field.geometry.crs.authority}:${field.geometry.crs.code}`;
        }
        return dataModelFieldTypeLabels[field.fieldType];
      },
    },
    {
      title: '映射',
      dataIndex: 'mappingQuality',
      width: 100,
      render: (quality: ManagedImportFieldDraft['mappingQuality'], field) => (
        <Tag
          color={quality === 'EXACT' ? 'success' : quality === 'UNSUPPORTED' ? 'error' : 'warning'}
          title={field.mappingMessage}
        >
          {mappingLabel(quality)}
        </Tag>
      ),
    },
    { title: '可空', dataIndex: 'nullable', width: 64, render: (value: boolean) => value ? '是' : '否' },
    {
      title: '校验',
      key: 'issues',
      width: 220,
      render: (_value, field) => {
        const issues = draftIssues.get(draft.key)?.fields.get(field.key) ?? [];
        return issues.length
          ? <Typography.Text type="danger">{issues.join('；')}</Typography.Text>
          : field.sourceIssues.length
            ? <Typography.Text type="warning">{field.sourceIssues.join('；')}</Typography.Text>
            : <Tag color="success">已就绪</Tag>;
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 80,
      fixed: 'right',
      render: (_value, field) => (
        <Button
          type="link"
          size="small"
          icon={<EditOutlined />}
          disabled={busy}
          onClick={() => setEditingField({ draftKey: draft.key, fieldKey: field.key })}
        >
          调整
        </Button>
      ),
    },
  ];

  const reviewColumns: TableProps<FileDatasetModelDraft>['columns'] = [
    {
      title: '来源逻辑表',
      key: 'sourceTable',
      width: 180,
      render: (_value, draft) => <code>{draft.table.code}</code>,
    },
    {
      title: '模型编码',
      key: 'code',
      width: 190,
      render: (_value, draft) => (
        <div>
          <Input
            value={draft.code}
            disabled={busy}
            status={draftIssues.get(draft.key)?.code ? 'error' : undefined}
            placeholder="必填，如 fact_order"
            onChange={(event) => updateDraft(draft.key, 'code', event.target.value.toLowerCase())}
          />
          {draftIssues.get(draft.key)?.code && <Typography.Text type="danger" className="managed-import-field-error">{draftIssues.get(draft.key)?.code}</Typography.Text>}
        </div>
      ),
    },
    {
      title: '模型名称',
      key: 'name',
      width: 190,
      render: (_value, draft) => (
        <div>
          <Input
            value={draft.name}
            disabled={busy}
            status={draftIssues.get(draft.key)?.name ? 'error' : undefined}
            onChange={(event) => updateDraft(draft.key, 'name', event.target.value)}
          />
          {draftIssues.get(draft.key)?.name && <Typography.Text type="danger" className="managed-import-field-error">{draftIssues.get(draft.key)?.name}</Typography.Text>}
        </div>
      ),
    },
    {
      title: '数仓分层',
      key: 'warehouseLayerId',
      width: 180,
      render: (_value, draft) => (
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          value={draft.warehouseLayerId}
          disabled={busy}
          options={warehouseLayerOptions}
          placeholder="未分层"
          className="managed-import-target-select"
          onChange={(value) => selectDraftWarehouseLayer(draft.key, value)}
        />
      ),
    },
    {
      title: '目标物理表名',
      key: 'physicalTableName',
      width: 210,
      render: (_value, draft) => (
        <div>
          <Input
            value={draft.physicalTableName}
            disabled={busy}
            status={draftIssues.get(draft.key)?.physicalTableName ? 'error' : undefined}
            placeholder="必填，如 fact_order"
            onChange={(event) => updateDraft(draft.key, 'physicalTableName', event.target.value.toLowerCase())}
          />
          {draftIssues.get(draft.key)?.physicalTableName && <Typography.Text type="danger" className="managed-import-field-error">{draftIssues.get(draft.key)?.physicalTableName}</Typography.Text>}
        </div>
      ),
    },
    {
      title: '说明',
      key: 'description',
      width: 190,
      render: (_value, draft) => (
        <Input
          value={draft.description}
          disabled={busy}
          status={draftIssues.get(draft.key)?.description ? 'error' : undefined}
          placeholder="可选"
          onChange={(event) => updateDraft(draft.key, 'description', event.target.value)}
        />
      ),
    },
    {
      title: '字段',
      key: 'fields',
      width: 170,
      render: (_value, draft) => {
        const issue = draftIssues.get(draft.key);
        if (draft.previewState === 'loading') return <Space><LoadingOutlined />读取中</Space>;
        if (issue?.preview) return <Typography.Text type="danger">{issue.preview}</Typography.Text>;
        if (issue?.fields.size) return <Typography.Text type="danger">{draft.fields.length} 个，{issue.fields.size} 个待补齐</Typography.Text>;
        return <Tag color="success">{draft.fields.length} 个已就绪</Tag>;
      },
    },
  ];

  const resultColumns: TableProps<ManagedDataModelDraftResult>['columns'] = [
    { title: '模型编码', dataIndex: ['request', 'code'], width: 170, render: (value: string) => <code>{value}</code> },
    { title: '模型名称', dataIndex: ['request', 'name'], width: 180, ellipsis: true },
    { title: '目标物理表', dataIndex: ['request', 'physicalTableName'], width: 190, render: (value: string) => <code>{value}</code> },
    {
      title: '结果',
      key: 'result',
      render: (_value, result) => result.detail ? (
        <Space><CheckCircleOutlined className="managed-import-success" />{resultMessage(result)}</Space>
      ) : (
        <Space><CloseCircleOutlined className="managed-import-failure" /><Typography.Text type="danger">{resultMessage(result)}</Typography.Text></Space>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 170,
      fixed: 'right',
      render: (_value, result) => result.detail ? (
        <Space size={0}>
          <Button type="link" size="small" disabled={failedKeys.size > 0} onClick={() => onViewModel(result.detail?.model.id ?? '')}>查看模型</Button>
          <Button type="link" size="small" disabled={failedKeys.size > 0} onClick={() => onAdjustFields(result.detail?.model.id ?? '')}>调整字段</Button>
        </Space>
      ) : '—',
    },
  ];

  const footer = (() => {
    if (step === 0) return (
      <Space>
        <Button onClick={requestClose}>取消</Button>
        <Button type="primary" disabled={!selectedDataset || selectedTables.size === 0} onClick={enterReview}>
          下一步：校对 {selectedTables.size} 个模型
        </Button>
      </Space>
    );
    if (step === 1) return (
      <Space>
        <Button disabled={busy} onClick={backToSource}>上一步</Button>
        <Button
          type="primary"
          loading={createMutation.isPending}
          disabled={!effectiveTargetStorageDataSourceId || busy || hasDraftIssues || drafts.length === 0}
          onClick={() => void submit()}
        >
          创建 {drafts.length} 个受管草稿
        </Button>
      </Space>
    );
    return (
      <Space>
        {failedKeys.size > 0 && <Button onClick={editFailures}>修改并重试失败项</Button>}
        <Button type="primary" onClick={onClose}>完成</Button>
      </Space>
    );
  })();

  return (
    <>
      {modalContext}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        title="从文件数据集创建模型"
        open={open}
        size="large"
        className="managed-table-model-import-drawer"
        closable={!busy}
        maskClosable={!busy}
        destroyOnHidden
        onClose={busy ? undefined : requestClose}
        footer={footer}
      >
        <Steps
          size="small"
          current={step}
          items={[{ title: '选择逻辑表' }, { title: '校对模型与字段' }, { title: '创建结果' }]}
        />

        {step === 0 && (
          <div className="managed-import-step-content">
            <Alert
              showIcon
              type="info"
              title="这里只复制已解析的逻辑表 Schema，不复制文件数据、不绑定来源，也不会创建物理表。"
            />
            <div className="managed-import-toolbar">
              <Select
                showSearch
                optionFilterProp="label"
                value={fileDatasetId}
                loading={datasetsQuery.isFetching}
                options={datasetOptions}
                placeholder="选择包含已解析逻辑表的文件数据集"
                className="managed-import-source-select"
                onChange={(value) => {
                  setFileDatasetId(value);
                  setSelectedTables(new Map());
                  setDrafts([]);
                  setResults([]);
                  setDirty(false);
                }}
              />
            </div>
            {datasetsQuery.isError && <Alert showIcon type="error" title="读取文件数据集失败" action={<Button size="small" onClick={() => void datasetsQuery.refetch()}>重试</Button>} />}
            {tablesQuery.isError && <Alert showIcon type="error" title="读取逻辑表失败" action={<Button size="small" onClick={() => void tablesQuery.refetch()}>重试</Button>} />}
            {tableMetadataQuery.isError && <Alert showIcon type="error" title="读取逻辑表 Schema 摘要失败" action={<Button size="small" onClick={() => void tableMetadataQuery.refetch()}>重试</Button>} />}
            <Table<FileDatasetTable>
              size="small"
              rowKey="id"
              columns={selectColumns}
              dataSource={tablesQuery.data?.content ?? []}
              loading={tablesQuery.isFetching || tableMetadataQuery.isFetching}
              pagination={false}
              scroll={{ x: 850, y: 430 }}
              rowSelection={{
                preserveSelectedRowKeys: true,
                selectedRowKeys: [...selectedTables.keys()],
                getCheckboxProps: (table) => {
                  const fieldCount = metadataByTableId.get(table.id)?.fields.length ?? 0;
                  const disabled = !isReady(table.parseStatus) || fieldCount === 0;
                  return {
                    disabled,
                    title: !isReady(table.parseStatus) ? '文件尚未完成结构解析' : fieldCount === 0 ? '逻辑表没有可用 Schema' : undefined,
                  };
                },
                onSelect: (table, selected) => setSelectedTables((current) => {
                  const next = new Map(current);
                  if (selected) next.set(table.id, table);
                  else next.delete(table.id);
                  return next;
                }),
                onSelectAll: (selected, _selectedRows, changedRows) => setSelectedTables((current) => {
                  const next = new Map(current);
                  changedRows.forEach((table) => {
                    if (selected) next.set(table.id, table);
                    else next.delete(table.id);
                  });
                  return next;
                }),
              }}
            />
          </div>
        )}

        {step === 1 && (
          <div className="managed-import-step-content">
            <Alert
              showIcon
              type={hasDraftIssues ? 'warning' : 'success'}
              title={hasDraftIssues
                ? '请补齐标红的模型或字段信息；自动填写的内容都可以修改。'
                : `已准备好 ${drafts.length} 个受管模型草稿。创建草稿不会执行建表 DDL。`}
            />
            <div className="managed-import-toolbar">
              <Select
                showSearch
                optionFilterProp="label"
                value={effectiveTargetStorageDataSourceId}
                loading={dataSourcesQuery.isFetching}
                disabled={busy}
                options={targetOptions}
                placeholder="选择具有 STORAGE 用途的目标 JDBC 数据存储"
                className="managed-import-target-select"
                onChange={selectTarget}
              />
              {canViewDirectories && (
                <TreeSelect
                  allowClear
                  treeDefaultExpandAll
                  value={directoryId}
                  disabled={busy}
                  treeData={directoryTreeSelectData(directoriesQuery.data ?? [])}
                  placeholder="模型目录：未分类"
                  className="managed-import-directory-select"
                  onChange={(value) => { setDirectoryId(value); setDirty(true); }}
                />
              )}
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                value={warehouseLayerId}
                disabled={busy}
                loading={warehouseLayersQuery.isFetching}
                options={warehouseLayerOptions}
                placeholder="批量数仓分层：未分层"
                className="managed-import-target-select"
                onChange={selectBatchWarehouseLayer}
              />
              <Button icon={<ReloadOutlined />} loading={previewMutation.isPending} disabled={!effectiveTargetStorageDataSourceId || busy} onClick={reloadPreviews}>
                重新读取结构
              </Button>
            </div>
            <Table<FileDatasetModelDraft>
              size="small"
              rowKey="key"
              columns={reviewColumns}
              dataSource={drafts}
              loading={previewMutation.isPending}
              pagination={false}
              scroll={{ x: 1340, y: 390 }}
              expandable={{
                rowExpandable: (draft) => draft.fields.length > 0,
                expandedRowRender: (draft) => (
                  <div className="managed-import-field-table-wrap">
                    {draft.previewIssues.length > 0 && (
                      <Alert showIcon type="warning" title={draft.previewIssues.join('；')} />
                    )}
                    {draft.warnings.length > 0 && (
                      <Typography.Text type="secondary">Schema 提示：{draft.warnings.join('；')}</Typography.Text>
                    )}
                    <Table<ManagedImportFieldDraft>
                      size="small"
                      rowKey="key"
                      columns={fieldColumns(draft)}
                      dataSource={draft.fields}
                      pagination={false}
                      scroll={{ x: 1200 }}
                    />
                  </div>
                ),
              }}
            />
            {createMutation.isPending && (
              <Alert showIcon icon={<LoadingOutlined />} type="info" title={`正在并发创建 ${drafts.length} 个模型草稿，最多同时处理 3 个…`} />
            )}
          </div>
        )}

        {step === 2 && (
          <div className="managed-import-step-content">
            <Alert
              showIcon
              type={failedKeys.size === 0 ? 'success' : successCount > 0 ? 'warning' : 'error'}
              title={`成功 ${successCount} 个，失败 ${failedKeys.size} 个`}
              description={failedKeys.size > 0
                ? '已成功的模型不会回滚；可以保留失败项，修改后单独重试。'
                : '模型和字段已保存为受管草稿，文件数据未复制，物理表也尚未创建。'}
            />
            <Table<ManagedDataModelDraftResult>
              size="small"
              rowKey="key"
              columns={resultColumns}
              dataSource={results}
              pagination={false}
              scroll={{ x: 900, y: 500 }}
            />
          </div>
        )}
      </Drawer>
      <ManagedImportFieldEditor
        open={Boolean(editingField)}
        field={currentEditingField}
        storageDataSourceId={effectiveTargetStorageDataSourceId}
        onCancel={() => setEditingField(undefined)}
        onSave={saveField}
      />
    </>
  );
};
