import type { TableProps } from 'antd';
import { Alert, Button, Col, Drawer, Form, Input, Radio, Row, Select, Space, Table, Tag, Tooltip, TreeSelect, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  useDataSourceNamespaces,
  useDataSourceTables,
  useDataSources,
} from '../../datasource';
import { directoryTreeSelectData, useDirectoryTree } from '../../directory';
import { useCreateDataModel, useExternalTableImportPreview, useUpdateDataModel } from '../hooks/useDataModels';
import {
  dataModelFieldTypeLabels,
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
  canViewDirectories: boolean;
  onClose: () => void;
  onSaved: (model: DataModel, created: boolean) => void;
}

interface DataModelFormValues {
  code?: string;
  name: string;
  directoryId?: string;
  storageDataSourceId: string;
  physicalTableName: string;
  physicalTableMode: PhysicalTableMode;
  clickHouseOrderByColumns?: string[];
  description?: string;
}

const jdbcDataSourceRequest = {
  page: 0,
  size: 500,
  sort: 'code',
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

export const DataModelDrawer = ({ open, model, canViewDirectories, onClose, onSaved }: DataModelDrawerProps) => {
  const [form] = Form.useForm<DataModelFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const [externalTableKeyword, setExternalTableKeyword] = useState('');
  const createMutation = useCreateDataModel();
  const updateMutation = useUpdateDataModel();
  const directoriesQuery = useDirectoryTree('MODEL', open && canViewDirectories);
  const dataSourcesQuery = useDataSources(jdbcDataSourceRequest, open);
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
    form.resetFields();
    if (model) {
      form.setFieldsValue({
        code: model.code,
        name: model.name,
        directoryId: model.directoryId ?? undefined,
        storageDataSourceId: model.storageDataSourceId,
        physicalTableName: model.physicalTableName,
        physicalTableMode: model.physicalTableMode,
        clickHouseOrderByColumns: model.clickHouseOrderByColumns,
        description: model.description ?? undefined,
      });
    }
  }, [form, model, open]);

  const closeDrawer = () => {
    setExternalTableKeyword('');
    onClose();
  };

  const submit = async (values: DataModelFormValues) => {
    try {
      const request: UpdateDataModelRequest = {
        name: values.name,
        directoryId: values.directoryId,
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
      messageApi.error(error instanceof ApiError ? error.message : '保存模型失败');
    }
  };

  const fillPhysicalTableName = () => {
    if (!editing && selectedPhysicalTableMode !== 'EXTERNAL' && !form.getFieldValue('physicalTableName')) {
      form.setFieldValue('physicalTableName', form.getFieldValue('code'));
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

  return (
    <>
      {messageContext}
      <Drawer
        title={editing ? '修改模型' : '新建模型'}
        open={open}
        size="large"
        className="data-model-drawer"
        onClose={closeDrawer}
        destroyOnHidden
        footer={(
          <Space>
            <Button onClick={closeDrawer}>取消</Button>
            <Button
              type="primary"
              loading={createMutation.isPending || updateMutation.isPending}
              disabled={externalTableImportBlocked}
              onClick={() => form.submit()}
            >
              保存
            </Button>
          </Space>
        )}
      >
        <Alert
          type="info"
          showIcon
          className="data-model-mode-alert"
          title={externalTableMode
            ? '选择已有表后会读取并导入字段；发布前仍会实时校验，系统不会修改该表。'
            : '物理表会在发布前实时校验；已存在的受管物理表通过变更计划修改，不直接保存字段定义。'}
        />
        <Form<DataModelFormValues>
          form={form}
          layout="vertical"
          initialValues={{ physicalTableMode: 'MANAGED' }}
          onFinish={(values) => void submit(values)}
        >
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                label="模型名称"
                name="name"
                rules={[{ required: true, whitespace: true, message: '请输入模型名称' }, { max: 100, message: '模型名称不能超过 100 个字符' }]}
              >
                <Input placeholder="如：订单事实模型" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="模型编码"
                name="code"
                rules={editing ? [] : [
                  { required: true, whitespace: true, message: '请输入模型编码' },
                  { pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/, message: '编码以字母开头，只能包含字母、数字和下划线' },
                ]}
              >
                <Input disabled={editing} placeholder="如：order_fact" onBlur={fillPhysicalTableName} />
              </Form.Item>
            </Col>
            {canViewDirectories && (
              <Col span={12}>
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
            <Col span={canViewDirectories ? 12 : 24}>
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
          </Row>

          <div className="data-source-form-section-title">物理位置定义</div>
          <Row gutter={12}>
            <Col span={12}>
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
            <Col span={12}>
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
                  <Input disabled={physicalDefinitionLocked} placeholder="默认与模型编码一致" />
                </Form.Item>
              )}
            </Col>
            {externalTableMode && (
              <Col span={24}>
                {namespacesQuery.isError && <Alert showIcon type="error" title="读取 JDBC 数据源命名空间失败，无法选择已有表" />}
                {tablesQuery.isError && <Alert showIcon type="error" title="读取已有表列表失败，请检查 JDBC 数据源连接" />}
                {tablesQuery.data?.truncated && (
                  <Alert showIcon type="warning" title="可选表已截断为前 500 项，请在数据源管理中缩小连接范围后重试" />
                )}
                {externalPreviewQuery.isFetching && <Alert showIcon type="info" title="正在读取并映射外部表字段…" />}
                {externalPreviewQuery.isError && <Alert showIcon type="error" title="读取外部表导入预览失败，当前不能创建模型" />}
                {externalTableIssues.length > 0 && (
                  <Alert
                    showIcon
                    type="warning"
                    title="该表包含当前模型无法准确表达的字段，不能导入"
                    description={externalTableIssues.join('；')}
                  />
                )}
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
                  extra="按字段编码顺序输入；它决定单机 MergeTree 的 ORDER BY，不是关系型唯一主键。"
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
            <Col span={12}>
              <Form.Item label="说明" name="description" rules={[{ max: 1000, message: '说明不能超过 1000 个字符' }]}>
                <Input placeholder="可选" />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Drawer>
    </>
  );
};
