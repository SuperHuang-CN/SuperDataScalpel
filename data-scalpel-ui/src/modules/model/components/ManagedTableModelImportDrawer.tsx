import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  EditOutlined,
  LoadingOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import {
  Alert,
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Steps,
  Switch,
  Table,
  Tag,
  TreeSelect,
  Typography,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  useDataSourceNamespaces,
  useDataSourceTables,
  useDataSources,
  type DataSourceNamespace,
  type DataSourceTable,
} from '../../datasource';
import { directoryTreeSelectData, useDirectoryTree } from '../../directory';
import {
  type ManagedDataModelDraftResult,
  useCreateManagedDataModelDrafts,
  useManagedImportPreviews,
  useModelWarehouseLayers,
  usePlatformTypeCapabilities,
} from '../hooks/useDataModels';
import {
  dataModelFieldTypeLabels,
  geometryKindLabels,
  type GeometryKind,
  type PlatformDataType,
} from '../model/dataModel';
import {
  applyManagedImportPreview,
  applyManagedDraftWarehouseLayer,
  clearDuplicateManagedDraftCodeCandidates,
  hasManagedTableDraftIssues,
  isManagedImportSourceSelectable,
  isManagedImportTargetSelectable,
  mergeManagedTableModelDrafts,
  managedTableDraftIssues,
  managedTableKey,
  toManagedDataModelDraftRequest,
  type ManagedImportFieldDraft,
  type ManagedTableModelDraft,
} from '../model/managedTableImport';

interface ManagedTableModelImportDrawerProps {
  open: boolean;
  canViewDirectories: boolean;
  initialDirectoryId?: string;
  initialTargetStorageDataSourceId?: string;
  onClose: () => void;
  onAdjustFields: (modelId: string) => void;
}

interface ManagedImportFieldEditorProps {
  open: boolean;
  field: ManagedImportFieldDraft | null;
  storageDataSourceId?: string;
  onCancel: () => void;
  onSave: (field: ManagedImportFieldDraft) => void;
}

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

const namespaceKey = (namespace: Pick<DataSourceNamespace, 'catalog' | 'schema'>): string => JSON.stringify([
  namespace.catalog,
  namespace.schema,
]);

const errorMessage = (error: unknown, fallback: string): string => {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return fallback;
};

const typeOptions = (Object.entries(dataModelFieldTypeLabels) as [PlatformDataType, string][])
  .map(([value, label]) => ({ value, label }));

export const ManagedImportFieldEditor = ({
  open,
  field,
  storageDataSourceId,
  onCancel,
  onSave,
}: ManagedImportFieldEditorProps) => {
  const [form] = Form.useForm<ManagedImportFieldDraft>();
  const selectedType = Form.useWatch('fieldType', form);
  const primaryKey = Form.useWatch('primaryKey', form);
  const capabilitiesQuery = usePlatformTypeCapabilities(storageDataSourceId, open);
  const capabilities = useMemo(() => new Map(
    (capabilitiesQuery.data ?? []).map((capability) => [capability.type, capability]),
  ), [capabilitiesQuery.data]);
  const options = typeOptions.map((option) => {
    const capability = capabilities.get(option.value);
    return {
      ...option,
      disabled: capability ? !capability.supported : false,
      title: capability?.message ?? undefined,
    };
  });
  const selectedCapability = selectedType ? capabilities.get(selectedType) : undefined;
  const geometryKindOptions = (
    selectedCapability?.geometryKinds?.length
      ? selectedCapability.geometryKinds
      : (Object.keys(geometryKindLabels) as GeometryKind[])
  ).map((value) => ({ value, label: geometryKindLabels[value] }));

  useEffect(() => {
    if (!open || !field) return;
    form.resetFields();
    form.setFieldsValue(field);
  }, [field, form, open]);

  useEffect(() => {
    if (open && primaryKey) form.setFieldValue('nullable', false);
  }, [form, open, primaryKey]);

  const changeType = (fieldType: PlatformDataType) => {
    form.setFieldValue('length', fieldType === 'STRING' ? form.getFieldValue('length') : undefined);
    form.setFieldValue('precision', fieldType === 'DECIMAL' ? form.getFieldValue('precision') ?? 18 : undefined);
    form.setFieldValue('scale', fieldType === 'DECIMAL' ? form.getFieldValue('scale') ?? 2 : undefined);
    form.setFieldValue('geometry', fieldType === 'GEOMETRY'
      ? form.getFieldValue('geometry') ?? {
        kind: 'POINT',
        crs: { authority: 'EPSG', code: 4326 },
        dimension: 'XY',
      }
      : undefined);
    if (fieldType === 'GEOMETRY') form.setFieldValue('primaryKey', false);
  };

  const submit = async () => {
    if (!field) return;
    const values = await form.validateFields();
    onSave({
      ...field,
      code: values.code.trim(),
      name: values.name.trim(),
      fieldType: values.fieldType,
      ...(values.fieldType === 'STRING' && values.length !== undefined ? { length: values.length } : { length: undefined }),
      ...(values.fieldType === 'DECIMAL'
        ? { precision: values.precision, scale: values.scale }
        : { precision: undefined, scale: undefined }),
      ...(values.fieldType === 'GEOMETRY'
        ? { geometry: values.geometry }
        : { geometry: undefined }),
      nullable: values.primaryKey ? false : values.nullable,
      primaryKey: values.fieldType === 'GEOMETRY' ? false : values.primaryKey,
      sortOrder: values.sortOrder,
      description: values.description?.trim() ?? '',
    });
  };

  return (
    <Modal
      rootClassName="business-overlay business-modal-overlay"
      title={field ? `调整字段：${field.sourceName}` : '调整字段'}
      open={open}
      width={680}
      destroyOnHidden
      okText="确定"
      cancelText="取消"
      onCancel={onCancel}
      onOk={() => void submit()}
    >
      {field?.sourceIssues.length ? (
        <Alert
          showIcon
          type="warning"
          title="源字段有未自动带入的信息"
          description={field.sourceIssues.join('；')}
          className="managed-import-field-alert"
        />
      ) : null}
      <Form<ManagedImportFieldDraft> autoComplete="off" form={form} layout="vertical">
        <div className="managed-import-field-form-grid">
          <Form.Item
            label="字段编码"
            name="code"
            rules={[
              { required: true, whitespace: true, message: '请输入字段编码' },
              { pattern: /^[a-z][a-z0-9_]{0,63}$/, message: '须以小写字母开头，只能包含小写字母、数字和下划线' },
            ]}
          >
            <Input onChange={(event) => form.setFieldValue('code', event.target.value.toLowerCase())} />
          </Form.Item>
          <Form.Item
            label="字段名称"
            name="name"
            rules={[
              { required: true, whitespace: true, message: '请输入字段名称' },
              { max: 100, message: '不能超过 100 个字符' },
            ]}
          >
            <Input />
          </Form.Item>
          <Form.Item label="目标类型" name="fieldType" rules={[{ required: true, message: '请选择目标字段类型' }]}>
            <Select loading={capabilitiesQuery.isFetching} options={options} onChange={changeType} />
          </Form.Item>
          {selectedType === 'STRING' && (
            <Form.Item label="长度（可选）" name="length">
              <InputNumber min={1} precision={0} className="data-model-number-input" />
            </Form.Item>
          )}
          {selectedType === 'DECIMAL' && (
            <>
              <Form.Item label="精度" name="precision" rules={[{ required: true, message: '请输入精度' }]}>
                <InputNumber min={1} max={38} precision={0} className="data-model-number-input" />
              </Form.Item>
              <Form.Item
                label="小数位"
                name="scale"
                dependencies={['precision']}
                rules={[
                  { required: true, message: '请输入小数位' },
                  ({ getFieldValue }) => ({
                    validator: (_rule, value: number | undefined) => (
                      value !== undefined && value >= 0 && value <= (getFieldValue('precision') ?? 0)
                        ? Promise.resolve()
                        : Promise.reject(new Error('小数位须为 0 至精度值'))
                    ),
                  }),
                ]}
              >
                <InputNumber min={0} max={38} precision={0} className="data-model-number-input" />
              </Form.Item>
            </>
          )}
          {selectedType === 'GEOMETRY' && (
            <>
              <Form.Item label="几何类型" name={['geometry', 'kind']} rules={[{ required: true, message: '请选择几何类型' }]}>
                <Select options={geometryKindOptions} />
              </Form.Item>
              <Form.Item label="CRS Authority" name={['geometry', 'crs', 'authority']} rules={[{ required: true }]}>
                <Input disabled />
              </Form.Item>
              <Form.Item label="EPSG Code" name={['geometry', 'crs', 'code']} rules={[{ required: true, message: '请输入 EPSG Code' }]}>
                <InputNumber min={1} precision={0} className="data-model-number-input" />
              </Form.Item>
              <Form.Item label="坐标维度" name={['geometry', 'dimension']} rules={[{ required: true }]}>
                <Select disabled options={[{ value: 'XY', label: 'XY（二维坐标）' }]} />
              </Form.Item>
            </>
          )}
          <Form.Item label="排序值" name="sortOrder" rules={[{ required: true, message: '请输入排序值' }]}>
            <InputNumber min={0} precision={0} className="data-model-number-input" />
          </Form.Item>
          <Form.Item label="允许为空" name="nullable" valuePropName="checked">
            <Switch disabled={primaryKey} />
          </Form.Item>
          <Form.Item label="主键" name="primaryKey" valuePropName="checked">
            <Switch disabled={selectedType === 'GEOMETRY'} />
          </Form.Item>
          <Form.Item
            label="字段说明"
            name="description"
            className="managed-import-field-description"
            rules={[{ max: 500, message: '不能超过 500 个字符' }]}
          >
            <Input.TextArea rows={2} />
          </Form.Item>
        </div>
      </Form>
    </Modal>
  );
};

const resultMessage = (result: ManagedDataModelDraftResult): string => {
  if (result.detail) return `已创建受管草稿，带入 ${result.detail.fields.length} 个字段`;
  return errorMessage(result.error, '创建草稿失败，请稍后重试');
};

export const ManagedTableModelImportDrawer = ({
  open,
  canViewDirectories,
  initialDirectoryId,
  initialTargetStorageDataSourceId,
  onClose,
  onAdjustFields,
}: ManagedTableModelImportDrawerProps) => {
  const [step, setStep] = useState(0);
  const [sourceDataSourceId, setSourceDataSourceId] = useState<string>();
  const [namespaceId, setNamespaceId] = useState<string>();
  const [targetStorageDataSourceId, setTargetStorageDataSourceId] = useState<string | undefined>(
    initialTargetStorageDataSourceId,
  );
  const [directoryId, setDirectoryId] = useState<string | undefined>(initialDirectoryId);
  const [warehouseLayerId, setWarehouseLayerId] = useState<string>();
  const [keyword, setKeyword] = useState('');
  const [selectedTables, setSelectedTables] = useState<Map<string, DataSourceTable>>(new Map());
  const [drafts, setDrafts] = useState<ManagedTableModelDraft[]>([]);
  const [results, setResults] = useState<ManagedDataModelDraftResult[]>([]);
  const [editingField, setEditingField] = useState<{ draftKey: string; fieldKey: string }>();
  const [modalApi, modalContext] = Modal.useModal();
  const dataSourcesQuery = useDataSources(jdbcDataSourceRequest, open);
  const warehouseLayersQuery = useModelWarehouseLayers(enabledWarehouseLayerRequest, open);
  const directoriesQuery = useDirectoryTree('MODEL', open && canViewDirectories);
  const namespacesQuery = useDataSourceNamespaces(sourceDataSourceId, open && step === 0);
  const selectedNamespace = namespacesQuery.data?.find((namespace) => namespaceKey(namespace) === namespaceId)
    ?? namespacesQuery.data?.find((namespace) => namespace.defaultNamespace)
    ?? namespacesQuery.data?.[0];
  const tableQuery = useMemo(() => ({
    ...(selectedNamespace?.catalog ? { catalog: selectedNamespace.catalog } : {}),
    ...(selectedNamespace?.schema ? { schema: selectedNamespace.schema } : {}),
    ...(keyword.trim() ? { keyword: keyword.trim() } : {}),
    includeViews: false,
  }), [keyword, selectedNamespace]);
  const tablesQuery = useDataSourceTables(
    sourceDataSourceId,
    tableQuery,
    open && step === 0 && Boolean(selectedNamespace),
  );
  const previewMutation = useManagedImportPreviews();
  const createMutation = useCreateManagedDataModelDrafts();
  const draftIssues = useMemo(() => managedTableDraftIssues(drafts), [drafts]);
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

  const sourceOptions = dataSourcesQuery.data?.content
    .filter(isManagedImportSourceSelectable)
    .map((source) => ({
      value: source.id,
      label: `${source.name}（${source.connection.kind === 'JDBC' ? source.connection.databaseName : source.type}）`,
    })) ?? [];
  const targetOptions = dataSourcesQuery.data?.content
    .filter(isManagedImportTargetSelectable)
    .map((source) => ({
      value: source.id,
      label: `${source.name}（${source.connection.kind === 'JDBC' ? source.connection.databaseName : source.type}）`,
    })) ?? [];
  const namespaceOptions = namespacesQuery.data?.map((namespace) => ({
    value: namespaceKey(namespace),
    label: namespace.displayName,
  })) ?? [];
  const warehouseLayerOptions = warehouseLayersQuery.data?.content.map((layer) => ({
    value: layer.id,
    label: `${layer.code} · ${layer.name}${layer.modelCodePrefix ? `（${layer.modelCodePrefix}*）` : ''}`,
  })) ?? [];
  const warehouseLayerPrefix = (layerId?: string) => warehouseLayersQuery.data?.content
    .find((layer) => layer.id === layerId)?.modelCodePrefix ?? undefined;

  const loadPreviews = async (
    targetId: string,
    candidates: ManagedTableModelDraft[],
  ) => {
    if (!sourceDataSourceId || !candidates.length) return;
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
        sourceDataSourceId,
        sourceTable: draft.table.identifier,
        targetStorageDataSourceId: targetId,
      },
    })));
    setDrafts((current) => current.map((draft) => {
      const result = previewResults.find((item) => item.key === draft.key);
      if (!result) return draft;
      if (result.preview) return applyManagedImportPreview(draft, result.preview);
      return {
        ...draft,
        fields: [],
        previewState: 'error',
        previewError: errorMessage(result.error, '源表结构读取失败'),
      };
    }));
  };

  const updateSelections = (tables: DataSourceTable[], selected: boolean) => {
    setSelectedTables((current) => {
      const next = new Map(current);
      tables.forEach((table) => {
        if (selected) next.set(managedTableKey(table), table);
        else next.delete(managedTableKey(table));
      });
      return next;
    });
  };

  const enterReview = () => {
    const nextDrafts = mergeManagedTableModelDrafts(
      [...selectedTables.values()].sort((left, right) => left.identifier.table.localeCompare(right.identifier.table)),
      drafts,
      warehouseLayerId,
      warehouseLayerPrefix(warehouseLayerId),
    );
    const newDrafts = nextDrafts.filter((draft) => !drafts.some((current) => current.key === draft.key));
    setDrafts(nextDrafts);
    setStep(1);
    if (effectiveTargetStorageDataSourceId && newDrafts.length > 0) {
      void loadPreviews(effectiveTargetStorageDataSourceId, newDrafts);
    }
  };

  const selectTarget = (targetId: string) => {
    const apply = async () => {
      setTargetStorageDataSourceId(targetId);
      await loadPreviews(targetId, drafts);
    };
    if (drafts.some((draft) => draft.fields.length > 0)) {
      modalApi.confirm({
        rootClassName: 'business-overlay business-modal-overlay',
        title: '切换目标数据存储',
        content: '切换后需要按新目标重新映射字段，当前字段调整会被重置。确认继续吗？',
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
    if (drafts.some((draft) => draft.fields.length > 0)) {
      modalApi.confirm({
        rootClassName: 'business-overlay business-modal-overlay',
        title: '重新读取源表结构',
        content: '重新读取会重置当前字段调整，但会保留模型编码、名称、说明和目标表名。确认继续吗？',
        okText: '重新读取',
        cancelText: '取消',
        onOk: apply,
      });
      return;
    }
    void apply();
  };

  const updateDraft = (
    key: string,
    field: 'code' | 'name' | 'description' | 'physicalTableName',
    value: string,
  ) => setDrafts((current) => current.map((draft) => draft.key === key
    ? {
      ...draft,
      [field]: value,
      codeOverridden: draft.codeOverridden || field === 'code',
      modelValuesLocked: true,
    }
    : draft));

  const selectBatchWarehouseLayer = (value?: string) => {
    setWarehouseLayerId(value);
    const prefix = warehouseLayerPrefix(value);
    setDrafts((current) => clearDuplicateManagedDraftCodeCandidates(
      current.map((draft) => draft.warehouseLayerOverridden
        ? draft
        : applyManagedDraftWarehouseLayer(draft, value, prefix, false)),
    ));
  };

  const selectDraftWarehouseLayer = (key: string, value?: string) => {
    const prefix = warehouseLayerPrefix(value);
    setDrafts((current) => clearDuplicateManagedDraftCodeCandidates(
      current.map((draft) => draft.key === key
        ? applyManagedDraftWarehouseLayer(draft, value, prefix, true)
        : draft),
    ));
  };

  const currentEditingField = editingField
    ? drafts.find((draft) => draft.key === editingField.draftKey)?.fields
      .find((field) => field.key === editingField.fieldKey) ?? null
    : null;

  const saveField = (field: ManagedImportFieldDraft) => {
    if (!editingField) return;
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
    setStep(2);
  };

  const editFailures = () => {
    setDrafts((current) => current.filter((draft) => failedKeys.has(draft.key)));
    setResults([]);
    setStep(1);
  };

  const selectColumns: TableProps<DataSourceTable>['columns'] = [
    {
      title: '物理表',
      key: 'table',
      width: 320,
      ellipsis: true,
      render: (_value, table) => <code>{table.identifier.table}</code>,
    },
    { title: '类型', dataIndex: 'type', width: 100, render: (value: string) => <Tag>{value}</Tag> },
    { title: '表注释', dataIndex: 'comment', ellipsis: true, render: (value: string | null) => value || '—' },
  ];

  const fieldColumns = (draft: ManagedTableModelDraft): TableProps<ManagedImportFieldDraft>['columns'] => [
    { title: '源字段', dataIndex: 'sourceName', width: 150, ellipsis: true, render: (value: string) => <code>{value}</code> },
    { title: '原生类型', dataIndex: 'nativeType', width: 130, ellipsis: true, render: (value: string) => <code>{value}</code> },
    { title: '字段编码', dataIndex: 'code', width: 150, ellipsis: true, render: (value: string) => value ? <code>{value}</code> : <Typography.Text type="danger">待补齐</Typography.Text> },
    { title: '字段名称', dataIndex: 'name', width: 150, ellipsis: true },
    {
      title: '目标类型',
      key: 'fieldType',
      width: 210,
      render: (_value, field) => {
        if (!field.fieldType) return <Typography.Text type="danger">待选择</Typography.Text>;
        if (field.fieldType === 'GEOMETRY' && field.geometry) {
          return `${geometryKindLabels[field.geometry.kind]} · ${field.geometry.crs.authority}:${field.geometry.crs.code} · ${field.geometry.dimension}`;
        }
        return dataModelFieldTypeLabels[field.fieldType];
      },
    },
    {
      title: '映射',
      dataIndex: 'mappingQuality',
      width: 100,
      render: (value: ManagedImportFieldDraft['mappingQuality'], field) => (
        <Tag color={value === 'EXACT' ? 'success' : value === 'UNSUPPORTED' ? 'error' : 'warning'} title={field.mappingMessage}>
          {value === 'EXACT' ? '精确' : value === 'NORMALIZED' ? '归一化' : value === 'LOSSY' ? '有损' : '不支持'}
        </Tag>
      ),
    },
    { title: '可空', dataIndex: 'nullable', width: 64, render: (value: boolean) => value ? '是' : '否' },
    { title: '主键', dataIndex: 'primaryKey', width: 64, render: (value: boolean) => value ? '是' : '—' },
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

  const reviewColumns: TableProps<ManagedTableModelDraft>['columns'] = [
    {
      title: '源表',
      key: 'sourceTable',
      width: 220,
      ellipsis: true,
      render: (_value, draft) => <code>{draft.table.identifier.table}</code>,
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
      width: 200,
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
      width: 190,
      render: (_value, draft) => (
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          value={draft.warehouseLayerId}
          disabled={busy}
          loading={warehouseLayersQuery.isFetching}
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
      width: 210,
      render: (_value, draft) => (
        <div>
          <Input
            value={draft.description}
            disabled={busy}
            status={draftIssues.get(draft.key)?.description ? 'error' : undefined}
            placeholder="可选"
            onChange={(event) => updateDraft(draft.key, 'description', event.target.value)}
          />
          {draftIssues.get(draft.key)?.description && <Typography.Text type="danger" className="managed-import-field-error">{draftIssues.get(draft.key)?.description}</Typography.Text>}
        </div>
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
    { title: '模型编码', dataIndex: ['request', 'code'], width: 180, render: (value: string) => <code>{value}</code> },
    { title: '模型名称', dataIndex: ['request', 'name'], width: 200, ellipsis: true },
    { title: '目标物理表', dataIndex: ['request', 'physicalTableName'], width: 200, render: (value: string) => <code>{value}</code> },
    {
      title: '结果',
      key: 'result',
      render: (_value, result) => result.detail ? (
        <Space><CheckCircleOutlined className="managed-import-success" /><Typography.Text>{resultMessage(result)}</Typography.Text></Space>
      ) : (
        <Space><CloseCircleOutlined className="managed-import-failure" /><Typography.Text type="danger">{resultMessage(result)}</Typography.Text></Space>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 110,
      fixed: 'right',
      render: (_value, result) => result.detail ? (
        <Button
          type="link"
          size="small"
          disabled={failedKeys.size > 0}
          title={failedKeys.size > 0 ? '请先重试或处理失败项，避免丢失当前批次状态' : undefined}
          onClick={() => onAdjustFields(result.detail?.model.id ?? '')}
        >
          调整字段
        </Button>
      ) : '—',
    },
  ];

  const footer = (() => {
    if (step === 0) return (
      <Space>
        <Button onClick={onClose}>取消</Button>
        <Button type="primary" disabled={!sourceDataSourceId || selectedTables.size === 0} onClick={enterReview}>
          下一步：校对 {selectedTables.size} 个模型
        </Button>
      </Space>
    );
    if (step === 1) return (
      <Space>
        <Button disabled={busy} onClick={() => setStep(0)}>上一步</Button>
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
        title="从 JDBC 表结构创建模型"
        open={open}
        size="large"
        className="managed-table-model-import-drawer"
        closable={!busy}
        maskClosable={!busy}
        destroyOnHidden
        onClose={busy ? undefined : onClose}
        footer={footer}
      >
        <Steps
          size="small"
          current={step}
          items={[{ title: '选择源表' }, { title: '校对模型与字段' }, { title: '创建结果' }]}
        />

        {step === 0 && (
          <div className="managed-import-step-content">
            <Alert
              showIcon
              type="info"
              title="这里只读取 JDBC 表结构，不绑定源表、不导入数据，也不会创建或修改任何物理表。"
            />
            <div className="managed-import-toolbar">
              <Select
                showSearch
                optionFilterProp="label"
                value={sourceDataSourceId}
                loading={dataSourcesQuery.isFetching}
                options={sourceOptions}
                placeholder="选择任意已启用的 JDBC 来源"
                className="managed-import-source-select"
                onChange={(value) => {
                  setSourceDataSourceId(value);
                  setNamespaceId(undefined);
                  setKeyword('');
                  setSelectedTables(new Map());
                  setDrafts([]);
                  setResults([]);
                }}
              />
              <Select
                showSearch
                optionFilterProp="label"
                value={selectedNamespace ? namespaceKey(selectedNamespace) : undefined}
                loading={namespacesQuery.isFetching}
                options={namespaceOptions}
                disabled={!sourceDataSourceId}
                placeholder="选择 catalog / schema"
                className="managed-import-namespace-select"
                onChange={(value) => {
                  setNamespaceId(value);
                  setKeyword('');
                }}
              />
              <Input.Search
                allowClear
                value={keyword}
                placeholder="筛选物理表"
                className="managed-import-search"
                onChange={(event) => setKeyword(event.target.value)}
              />
            </div>
            {selectedNamespace && (
              <Typography.Text type="secondary">
                当前命名空间：{selectedNamespace.displayName}；跨命名空间已选择 {selectedTables.size} 张表
              </Typography.Text>
            )}
            {namespacesQuery.isError && <Alert showIcon type="error" title="读取 JDBC 数据源命名空间失败" />}
            {tablesQuery.isError && <Alert showIcon type="error" title="读取物理表失败，请检查 JDBC 数据源连接和元数据权限" />}
            {tablesQuery.data?.truncated && <Alert showIcon type="warning" title="表列表已截断为前 500 项，请使用关键字缩小范围" />}
            <Table<DataSourceTable>
              size="small"
              rowKey={managedTableKey}
              columns={selectColumns}
              dataSource={tablesQuery.data?.tables ?? []}
              loading={namespacesQuery.isFetching || tablesQuery.isFetching}
              pagination={false}
              scroll={{ x: 760, y: 430 }}
              rowSelection={{
                preserveSelectedRowKeys: true,
                selectedRowKeys: [...selectedTables.keys()],
                onSelect: (table, selected) => updateSelections([table], selected),
                onSelectAll: (selected, _selectedRows, changedRows) => updateSelections(changedRows, selected),
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
                  onChange={setDirectoryId}
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
              <Button
                icon={<ReloadOutlined />}
                loading={previewMutation.isPending}
                disabled={!effectiveTargetStorageDataSourceId || busy}
                onClick={reloadPreviews}
              >
                重新读取结构
              </Button>
            </div>
            {drafts.some((draft) => draft.previewIssues.length || draft.warnings.length) && (
              <Alert
                showIcon
                type="warning"
                title="部分源结构无法完整表达"
                description="展开对应模型查看字段映射；默认值、自增、生成列和索引等未支持信息仅作提示，不会伪造到模型中。"
              />
            )}
            <Table<ManagedTableModelDraft>
              size="small"
              rowKey="key"
              columns={reviewColumns}
              dataSource={drafts}
              loading={previewMutation.isPending}
              pagination={false}
              scroll={{ x: 1390, y: 390 }}
              expandable={{
                rowExpandable: (draft) => draft.fields.length > 0,
                expandedRowRender: (draft) => (
                  <div className="managed-import-field-table-wrap">
                    {(draft.previewIssues.length > 0 || draft.warnings.length > 0) && (
                      <Alert
                        showIcon
                        type="warning"
                        title={[...draft.previewIssues, ...draft.warnings].join('；')}
                      />
                    )}
                    <Table<ManagedImportFieldDraft>
                      size="small"
                      rowKey="key"
                      columns={fieldColumns(draft)}
                      dataSource={draft.fields}
                      pagination={false}
                      scroll={{ x: 1240 }}
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
                ? '已成功的模型不会回滚；可以保留失败项，修改后单独重试。物理表仍需在模型详情中显式创建。'
                : '模型和字段已保存为受管草稿，尚未创建物理表。请先调整字段，再显式创建物理表。'}
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
