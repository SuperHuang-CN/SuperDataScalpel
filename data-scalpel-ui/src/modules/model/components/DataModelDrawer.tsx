import { DatabaseOutlined, IdcardOutlined, TableOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Badge, Button, Col, Drawer, Form, Input, Radio, Row, Select, Space, Table, Tag, Tooltip, TreeSelect, Typography, message } from 'antd';
import { type ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import {
  useDataSourceNamespaces,
  useDataSourceTables,
  useDataSources,
} from '../../datasource';
import { directoryTreeSelectData, useDirectoryTree } from '../../directory';
import {
  useCreateDataModel,
  useExternalTableImportPreview,
  useModelWarehouseLayers,
  useUpdateDataModel,
} from '../hooks/useDataModels';
import {
  dataModelFieldTypeLabels,
  dataModelStatusLabels,
  physicalTableModeLabels,
  type CreateDataModelRequest,
  type DataModel,
  type ExternalTableImportColumn,
  type PhysicalTableMode,
  type UpdateDataModelRequest,
} from '../model/dataModel';
import { isModelDataSourceSelectable } from '../model/managedTableImport';

interface DataModelDrawerProps {
  open: boolean;
  model: DataModel | null;
  initialDirectoryId?: string;
  canViewDirectories: boolean;
  onClose: () => void;
  onSaved: (model: DataModel, created: boolean) => void;
}

interface DataModelFormValues {
  code?: string;
  name: string;
  directoryId?: string;
  warehouseLayerId?: string;
  storageDataSourceId: string;
  physicalTableName: string;
  physicalTableMode: PhysicalTableMode;
  clickHouseOrderByColumns?: string[];
  description?: string;
}

const DataModelFormSection = ({
  title,
  description,
  icon,
  help,
  children,
}: {
  title: string;
  description: string;
  icon: ReactNode;
  help?: ReactNode;
  children: ReactNode;
}) => (
  <section className="data-model-form-section">
    <header className="data-model-form-section-header">
      <span className="data-model-form-section-icon" aria-hidden="true">{icon}</span>
      <span className="data-model-form-section-copy">
        <span className="data-model-form-section-title-row">
          <span className="data-model-form-section-title">{title}</span>
          {help && (
            <ContextHelp
              ariaLabel={`${title}说明`}
              content={help}
              presentation="popover"
              placement="bottomLeft"
            />
          )}
        </span>
        <Typography.Text type="secondary">{description}</Typography.Text>
      </span>
    </header>
    <div className="data-model-form-section-body">{children}</div>
  </section>
);

const jdbcDataSourceRequest = {
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

const lowerIdentifier = (value: string, maxLength: number) => (
  new RegExp(`^[a-z][a-z0-9_]{0,${maxLength - 1}}$`).test(value)
);

const externalTypeLabel = (column: ExternalTableImportColumn) => {
  if (!column.platformType) return '不支持';
  const label = dataModelFieldTypeLabels[column.platformType];
  if (column.platformType === 'STRING') return column.length ? `${label}(${column.length})` : `${label}(无上限)`;
  if (column.platformType === 'DECIMAL') return `${label}(${column.precision},${column.scale})`;
  return label;
};

const externalColumnColumns: TableProps<ExternalTableImportColumn>['columns'] = [
  { title: '字段', dataIndex: 'name', width: 180, ellipsis: true, render: (value: string) => <code>{value}</code> },
  { title: '原生类型', dataIndex: 'nativeType', width: 160, ellipsis: true, render: (value: string) => <code>{value}</code> },
  {
    title: '物理角色',
    dataIndex: 'physicalColumnRole',
    width: 100,
    render: (value: ExternalTableImportColumn['physicalColumnRole']) => value === 'TIME_KEY'
      ? <Tag color="blue">时间主列</Tag>
      : value === 'TAG' ? <Tag color="purple">TAG</Tag> : '普通列',
  },
  {
    title: '导入类型',
    key: 'targetType',
    width: 120,
    render: (_value, column) => {
      return column.importable
        ? externalTypeLabel(column)
        : <Typography.Text type="danger">{externalTypeLabel(column)}</Typography.Text>;
    },
  },
  {
    title: '主键',
    key: 'primaryKey',
    width: 72,
    render: (_value, column) => column.primaryKey ? '是' : '—',
  },
  {
    title: '映射',
    key: 'mappingQuality',
    width: 100,
    render: (_value, column) => (
      <Tooltip title={column.message ?? undefined}>
        <Tag color={column.mappingQuality === 'EXACT' ? 'success' : column.importable ? 'warning' : 'error'}>
          {column.mappingQuality === 'EXACT' ? '精确' : column.mappingQuality === 'NORMALIZED' ? '已归一化' : '不可导入'}
        </Tag>
      </Tooltip>
    ),
  },
  { title: '可空', dataIndex: 'nullable', width: 72, render: (value: boolean) => value ? '是' : '否' },
  { title: '说明', dataIndex: 'comment', width: 220, ellipsis: true, render: (value: string | null) => value || '—' },
];

export const DataModelDrawer = ({
  open,
  model,
  initialDirectoryId,
  canViewDirectories,
  onClose,
  onSaved,
}: DataModelDrawerProps) => {
  const [form] = Form.useForm<DataModelFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const [operationError, setOperationError] = useState<string | null>(null);
  const [externalTableKeyword, setExternalTableKeyword] = useState('');
  const autoFilledModelCodeRef = useRef<string | null>(null);
  const createMutation = useCreateDataModel();
  const updateMutation = useUpdateDataModel();
  const directoriesQuery = useDirectoryTree('MODEL', open && canViewDirectories);
  const dataSourcesQuery = useDataSources(jdbcDataSourceRequest, open);
  const warehouseLayersQuery = useModelWarehouseLayers(enabledWarehouseLayerRequest, open);
  const selectedStorageId = Form.useWatch('storageDataSourceId', form);
  const selectedPhysicalTableMode = Form.useWatch('physicalTableMode', form);
  const selectedPhysicalTableName = Form.useWatch('physicalTableName', form);
  const selectedStorage = dataSourcesQuery.data?.content.find((source) => source.id === selectedStorageId);
  const editing = Boolean(model);
  const physicalDefinitionLocked = model?.status !== undefined && model.status !== 'DRAFT';
  const externalTableMode = selectedPhysicalTableMode === 'EXTERNAL';
  const clickHouseManaged = selectedStorage?.type === 'CLICKHOUSE' && selectedPhysicalTableMode === 'MANAGED';
  const namespacesQuery = useDataSourceNamespaces(
    selectedStorageId,
    open && externalTableMode && !physicalDefinitionLocked,
  );
  const selectedNamespace = namespacesQuery.data?.find((namespace) => namespace.defaultNamespace)
    ?? namespacesQuery.data?.[0];
  const externalTableQuery = useMemo(() => ({
    ...(selectedNamespace?.catalog ? { catalog: selectedNamespace.catalog } : {}),
    ...(selectedNamespace?.schema ? { schema: selectedNamespace.schema } : {}),
    ...(externalTableKeyword.trim() ? { keyword: externalTableKeyword.trim() } : {}),
    includeViews: false,
  }), [externalTableKeyword, selectedNamespace]);
  const tablesQuery = useDataSourceTables(
    selectedStorageId,
    externalTableQuery,
    open && externalTableMode && !physicalDefinitionLocked && Boolean(selectedNamespace),
  );
  const selectedExternalTable = tablesQuery.data?.tables.find((table) => (
    table.identifier.table.toLowerCase() === selectedPhysicalTableName?.toLowerCase()
  ));
  const externalPreviewQuery = useExternalTableImportPreview(
    selectedStorageId,
    selectedExternalTable?.identifier.table,
    open && externalTableMode && Boolean(selectedExternalTable)
      && lowerIdentifier(selectedExternalTable?.identifier.table ?? '', 128),
  );
  const externalTableIssues = useMemo(() => (
    [
      selectedExternalTable && !lowerIdentifier(selectedExternalTable.identifier.table, 128)
        ? `${selectedExternalTable.identifier.table} 的表名不符合当前模型规则（仅支持小写字母、数字和下划线）`
        : null,
      ...(externalPreviewQuery.data?.issues ?? []),
    ].filter((issue): issue is string => Boolean(issue))
  ), [externalPreviewQuery.data, selectedExternalTable]);
  const storageOptions = useMemo(() => dataSourcesQuery.data?.content
    .filter((source) => isModelDataSourceSelectable(
      source,
      externalTableMode ? 'EXTERNAL' : 'MANAGED',
      model?.storageDataSourceId,
    ))
    .map((source) => ({
      value: source.id,
      label: `${source.name}（${source.connection.kind === 'JDBC' ? source.connection.databaseName : source.type}）`,
    })) ?? [], [dataSourcesQuery.data, externalTableMode, model?.storageDataSourceId]);
  const warehouseLayerOptions = useMemo(() => {
    const layers = [...(warehouseLayersQuery.data?.content ?? [])];
    if (model?.warehouseLayer && !layers.some((layer) => layer.id === model.warehouseLayer?.id)) {
      layers.push({
        ...model.warehouseLayer,
        description: null,
        sortOrder: Number.MAX_SAFE_INTEGER,
        inputLayerPolicy: 'UNRESTRICTED',
        allowedInputLayers: [],
        referencedModelCount: 0,
        referencedAsInputByLayerCount: 0,
        deletable: false,
        createdAt: '',
        updatedAt: '',
      });
    }
    return layers.map((layer) => ({
      value: layer.id,
      label: `${layer.code} · ${layer.name}${layer.modelCodePrefix ? `（${layer.modelCodePrefix}*）` : ''}${layer.enabled ? '' : '（已停用）'}`,
      disabled: !layer.enabled && layer.id !== model?.warehouseLayer?.id,
    }));
  }, [model, warehouseLayersQuery.data]);
  const externalTableOptions = useMemo(() => tablesQuery.data?.tables.map((table) => ({
    value: table.identifier.table,
    label: table.comment ? `${table.identifier.table}（${table.comment}）` : table.identifier.table,
  })) ?? [], [tablesQuery.data]);
  const externalTableImportBlocked = externalTableMode && (
    !selectedExternalTable
    || externalPreviewQuery.isFetching
    || externalPreviewQuery.isError
    || !externalPreviewQuery.data?.importable
  );

  useEffect(() => {
    if (!open) return;
    autoFilledModelCodeRef.current = null;
    form.resetFields();
    if (model) {
      form.setFieldsValue({
        code: model.code,
        name: model.name,
        directoryId: model.directoryId ?? undefined,
        warehouseLayerId: model.warehouseLayer?.id,
        storageDataSourceId: model.storageDataSourceId,
        physicalTableName: model.physicalTableName,
        physicalTableMode: model.physicalTableMode,
        clickHouseOrderByColumns: model.clickHouseOrderByColumns,
        description: model.description ?? undefined,
      });
    } else {
      form.setFieldValue('directoryId', initialDirectoryId);
    }
  }, [form, initialDirectoryId, model, open]);

  const closeDrawer = () => {
    setExternalTableKeyword('');
    setOperationError(null);
    onClose();
  };

  const submit = async (values: DataModelFormValues) => {
    setOperationError(null);
    try {
      const request: UpdateDataModelRequest = {
        name: values.name,
        directoryId: values.directoryId,
        warehouseLayerId: values.warehouseLayerId,
        storageDataSourceId: values.storageDataSourceId,
        physicalTableName: values.physicalTableName,
        physicalTableMode: values.physicalTableMode,
        clickHouseOrderByColumns: clickHouseManaged ? values.clickHouseOrderByColumns ?? [] : [],
        description: values.description,
      };
      if (model) {
        const detail = await updateMutation.mutateAsync({ id: model.id, request });
        messageApi.success('模型已保存');
        onSaved(detail.model, false);
      } else {
        if (!values.code) return;
        const createRequest: CreateDataModelRequest = { ...request, code: values.code };
        const detail = await createMutation.mutateAsync(createRequest);
        messageApi.success(values.physicalTableMode === 'EXTERNAL'
          ? `模型已创建，已导入 ${detail.fields.length} 个字段`
          : '模型已创建，请继续配置字段');
        onSaved(detail.model, true);
      }
      closeDrawer();
    } catch (error) {
      const errorMessage = error instanceof ApiError ? error.message : '保存模型失败';
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
    }
  };

  const fillPhysicalTableName = () => {
    if (!editing && selectedPhysicalTableMode !== 'EXTERNAL' && !form.getFieldValue('physicalTableName')) {
      form.setFieldValue('physicalTableName', form.getFieldValue('code'));
    }
  };

  const selectWarehouseLayer = (layerId?: string) => {
    if (editing) return;
    const nextPrefix = warehouseLayersQuery.data?.content
      .find((layer) => layer.id === layerId)?.modelCodePrefix ?? null;
    const currentCode = form.getFieldValue('code')?.trim() ?? '';
    const previousAutoCode = autoFilledModelCodeRef.current;
    if (!currentCode || (previousAutoCode !== null && currentCode === previousAutoCode)) {
      form.setFieldValue('code', nextPrefix ?? undefined);
      autoFilledModelCodeRef.current = nextPrefix;
    }
  };

  const changeModelCode = (value: string) => {
    if (autoFilledModelCodeRef.current !== null && value !== autoFilledModelCodeRef.current) {
      autoFilledModelCodeRef.current = null;
    }
  };

  const selectStorage = () => {
    setExternalTableKeyword('');
    if (externalTableMode) {
      form.setFieldValue('physicalTableName', undefined);
    }
  };

  const selectPhysicalTableMode = (mode: PhysicalTableMode) => {
    setExternalTableKeyword('');
    form.setFieldValue('clickHouseOrderByColumns', []);
    if (mode === 'EXTERNAL') {
      form.setFieldValue('physicalTableName', undefined);
    } else {
      if (selectedStorage && !selectedStorage.purposes.includes('STORAGE')) {
        form.setFieldValue('storageDataSourceId', undefined);
      }
      if (!editing && !form.getFieldValue('physicalTableName')) {
        form.setFieldValue('physicalTableName', form.getFieldValue('code'));
      }
    }
  };

  const effectivePhysicalMode = selectedPhysicalTableMode ?? model?.physicalTableMode ?? 'MANAGED';
  const pending = createMutation.isPending || updateMutation.isPending;
  const footerStatus = operationError ? (
    <InlineFeedback
      tone="error"
      label={editing ? '保存失败' : '创建失败'}
      detail={operationError}
      ariaLabel={editing ? '查看模型保存失败详情' : '查看模型创建失败详情'}
    />
  ) : externalTableMode && externalTableImportBlocked ? (
    <InlineFeedback
      tone="warning"
      label="请选择可导入的外部表"
      detail={externalTableIssues.length > 0
        ? externalTableIssues.join('；')
        : '外部表字段预览完成并确认可导入后，才能创建模型。'}
      ariaLabel="查看外部表导入条件"
    />
  ) : (
    <Badge
      status="default"
      text={editing
        ? `${dataModelStatusLabels[model?.status ?? 'DRAFT']} · Schema v${model?.schemaVersion ?? 1}`
        : effectivePhysicalMode === 'EXTERNAL' ? '创建时同步导入外部表字段' : '创建后继续配置模型字段'}
    />
  );

  return (
    <>
      {messageContext}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        className="data-model-drawer"
        title={(
          <div className="data-model-drawer-title">
            <span className="data-model-drawer-title-icon" aria-hidden="true"><TableOutlined /></span>
            <span className="data-model-drawer-title-copy">
              <span>{editing ? '修改模型' : '新建模型'}</span>
              <Typography.Text type="secondary">定义模型标识、业务归属与物理存储位置</Typography.Text>
            </span>
          </div>
        )}
        extra={(
          <Tag className="data-model-drawer-header-tag">
            {editing ? dataModelStatusLabels[model?.status ?? 'DRAFT'] : physicalTableModeLabels[effectivePhysicalMode]}
          </Tag>
        )}
        open={open}
        size="min(960px, 100vw)"
        closable={pending ? false : { placement: 'end' }}
        maskClosable={!pending}
        onClose={closeDrawer}
        destroyOnHidden
        footer={(
          <div className="data-model-drawer-footer">
            {footerStatus}
            <Space>
              <Button disabled={pending} onClick={closeDrawer}>取消</Button>
              <Button
                type="primary"
                loading={pending}
                disabled={externalTableImportBlocked}
                onClick={() => form.submit()}
              >
                {editing ? '保存修改' : '创建模型'}
              </Button>
            </Space>
          </div>
        )}
      >
        <Form<DataModelFormValues>
          name="data-model-editor-form"
          autoComplete="off"
          form={form}
          layout="vertical"
          className="data-model-form"
          initialValues={{ physicalTableMode: 'MANAGED' }}
          onFinish={(values) => void submit(values)}
        >
          <DataModelFormSection
            title="模型信息"
            description="用于识别模型并建立目录与数仓分层归属"
            icon={<IdcardOutlined />}
          >
            <Row gutter={14}>
              <Col span={12} xs={24} sm={12}>
                <Form.Item
                  label="模型名称"
                  name="name"
                  rules={[{ required: true, whitespace: true, message: '请输入模型名称' }, { max: 100, message: '模型名称不能超过 100 个字符' }]}
                >
                  <Input name="data-model-display-name" autoComplete="off" placeholder="如：订单事实模型" autoFocus />
                </Form.Item>
              </Col>
              <Col span={12} xs={24} sm={12}>
                <Form.Item
                  label="模型编码"
                  name="code"
                  rules={editing ? [] : [
                    { required: true, whitespace: true, message: '请输入模型编码' },
                    { pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/, message: '编码以字母开头，只能包含字母、数字和下划线' },
                  ]}
                >
                  <Input
                    name="data-model-code"
                    autoComplete="off"
                    disabled={editing}
                    placeholder="如：order_fact"
                    onChange={(event) => changeModelCode(event.target.value)}
                    onBlur={fillPhysicalTableName}
                  />
                </Form.Item>
              </Col>
              {canViewDirectories && (
                <Col span={12} xs={24} sm={12}>
                  <Form.Item label="目录" name="directoryId">
                    <TreeSelect
                      allowClear
                      treeDefaultExpandAll
                      treeData={directoryTreeSelectData(directoriesQuery.data ?? [])}
                      placeholder="未分类"
                    />
                  </Form.Item>
                </Col>
              )}
              <Col span={12} xs={24} sm={12}>
                <Form.Item
                  label="数仓分层"
                  name="warehouseLayerId"
                  extra={model?.warehouseLayer && !model.warehouseLayer.enabled
                    ? '当前分层已停用，可以保留或改选其他启用分层。'
                    : '可选；只表达业务组织，不影响物理表结构。'}
                >
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    loading={warehouseLayersQuery.isFetching}
                    options={warehouseLayerOptions}
                    placeholder="未分层"
                    onChange={selectWarehouseLayer}
                  />
                </Form.Item>
              </Col>
              <Col span={24}>
                <Form.Item label="说明" name="description" rules={[{ max: 1000, message: '说明不能超过 1000 个字符' }]}>
                  <Input.TextArea name="data-model-description" autoComplete="off" rows={3} maxLength={1000} showCount />
                </Form.Item>
              </Col>
            </Row>
          </DataModelFormSection>

          <DataModelFormSection
            title="存储与物理表"
            description="选择物理表的管理方式、存储数据源和实际表位置"
            icon={<DatabaseOutlined />}
            help={externalTableMode
              ? '选择已有表后会读取并导入字段；发布前仍会实时校验，系统不会修改该表。'
              : '物理表会在发布前实时校验；已存在的受管表通过变更计划修改，不直接保存字段定义。'}
          >
            {physicalDefinitionLocked && (
              <InlineFeedback
                className="data-model-physical-lock-feedback"
                tone="warning"
                label="物理位置定义已锁定"
                detail="非草稿模型不能直接修改存储数据源、物理表来源和物理表名。"
                ariaLabel="查看模型物理位置锁定原因"
              />
            )}
            <Row gutter={14}>
              <Col span={12} xs={24} sm={12}>
              <Form.Item
                label="物理表来源"
                name="physicalTableMode"
                rules={[{ required: true, message: '请选择物理表来源' }]}
              >
                <Radio.Group
                  disabled={physicalDefinitionLocked}
                  onChange={(event) => selectPhysicalTableMode(event.target.value as PhysicalTableMode)}
                >
                  <Radio value="MANAGED">新建物理表</Radio>
                  <Radio value="EXTERNAL">绑定已有表</Radio>
                </Radio.Group>
              </Form.Item>
              </Col>
              <Col span={12} xs={24} sm={12}>
                <Form.Item
                  label={externalTableMode ? 'JDBC 数据源' : '数据存储'}
                  name="storageDataSourceId"
                  rules={[{ required: true, message: externalTableMode ? '请选择 JDBC 数据源' : '请选择数据存储' }]}
                >
                  <Select
                    showSearch
                    optionFilterProp="label"
                    loading={dataSourcesQuery.isFetching}
                    disabled={physicalDefinitionLocked}
                    options={storageOptions}
                    placeholder={externalTableMode ? '选择已启用的 JDBC 数据源' : '选择具有数据存储用途的 JDBC 数据源'}
                    onChange={selectStorage}
                  />
                </Form.Item>
              </Col>
              <Col span={12} xs={24} sm={12}>
              {externalTableMode ? (
                <Form.Item
                  label="已有物理表"
                  name="physicalTableName"
                  rules={[{ required: true, message: '请选择已有物理表' }]}
                  extra={selectedNamespace ? `仅显示 JDBC 数据源默认命名空间：${selectedNamespace.displayName}` : undefined}
                >
                  <Select
                    allowClear
                    showSearch
                    filterOption={false}
                    loading={namespacesQuery.isFetching || tablesQuery.isFetching}
                    disabled={physicalDefinitionLocked || !selectedStorageId || namespacesQuery.isError || tablesQuery.isError}
                    options={externalTableOptions}
                    placeholder="选择要绑定的物理表"
                    notFoundContent={tablesQuery.data?.truncated ? '结果已截断，请调整数据源范围' : '未找到可绑定的物理表'}
                    onSearch={setExternalTableKeyword}
                    onClear={() => setExternalTableKeyword('')}
                  />
                </Form.Item>
              ) : (
                <Form.Item
                  label="物理表名"
                  name="physicalTableName"
                  rules={[
                    { required: true, whitespace: true, message: '请输入物理表名' },
                    { pattern: /^[A-Za-z][A-Za-z0-9_]{0,127}$/, message: '表名以字母开头，只能包含字母、数字和下划线' },
                  ]}
                >
                  <Input name="data-model-physical-table-name" autoComplete="off" disabled={physicalDefinitionLocked} placeholder="默认与模型编码一致" />
                </Form.Item>
              )}
              </Col>
            {externalTableMode && (
              <Col span={24}>
                <div className="data-model-external-feedbacks">
                {namespacesQuery.isError && <InlineFeedback tone="error" label="读取 JDBC 数据源命名空间失败" detail="当前无法选择已有表，请检查数据源连接和元数据权限。" />}
                {tablesQuery.isError && <InlineFeedback tone="error" label="读取已有表列表失败" detail="请检查 JDBC 数据源连接后重试。" />}
                {tablesQuery.data?.truncated && (
                  <InlineFeedback tone="warning" label="可选表仅显示前 500 项" detail="请在数据源管理中缩小连接范围后重试。" />
                )}
                {externalPreviewQuery.isFetching && <InlineFeedback tone="info" label="正在读取并映射外部表字段…" />}
                {externalPreviewQuery.isError && <InlineFeedback tone="error" label="读取外部表导入预览失败" detail="当前外部表不能导入为模型。" />}
                {externalTableIssues.length > 0 && (
                  <InlineFeedback
                    tone="warning"
                    label="该表包含无法准确表达的字段"
                    detail={externalTableIssues.join('；')}
                    ariaLabel="查看外部表不可导入详情"
                  />
                )}
                </div>
                {externalPreviewQuery.data && (
                  <Table<ExternalTableImportColumn>
                    size="small"
                    className="external-table-field-preview"
                    rowKey={(column) => column.name}
                    columns={externalColumnColumns}
                    dataSource={externalPreviewQuery.data.columns}
                    pagination={false}
                    scroll={{ x: 860, y: 180 }}
                  />
                )}
              </Col>
            )}
            {clickHouseManaged && (
              <Col span={24}>
                <Form.Item
                  label="ClickHouse 排序键"
                  name="clickHouseOrderByColumns"
                  extra="按字段编码顺序输入；它决定单机 MergeTree 的 ORDER BY，不是关系型唯一主键，Geometry 字段不可使用。"
                  rules={[
                    { max: 16, type: 'array', message: '最多配置 16 个排序键字段' },
                    {
                      validator: async (_rule, value: string[] | undefined) => {
                        if (!value?.every((item) => /^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(item))) {
                          throw new Error('排序键必须是合法字段编码');
                        }
                      },
                    },
                  ]}
                >
                  <Select
                    mode="tags"
                    tokenSeparators={[',']}
                    disabled={physicalDefinitionLocked}
                    placeholder="如：event_time, event_id（可留空，使用 tuple()）"
                  />
                </Form.Item>
              </Col>
            )}
            </Row>
          </DataModelFormSection>
        </Form>
      </Drawer>
    </>
  );
};
