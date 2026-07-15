import { DeleteOutlined, EditOutlined, FileSearchOutlined, PlusOutlined, ReloadOutlined, SaveOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import {
  Alert,
  Button,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { DataModelPhysicalChangeDrawer } from './DataModelPhysicalChangeDrawer';
import {
  useCreatePhysicalTableChangePlan,
  useDataModel,
  usePhysicalTableInspection,
  usePlatformTypeCapabilities,
  useUpdateDataModelFields,
} from '../hooks/useDataModels';
import {
  canSaveFieldsDirectly,
  dataModelFieldTypeLabels,
  shouldCreatePhysicalTableChangePlan,
  type DataModel,
  type DataModelField,
  type DataModelFieldInput,
  type PlatformDataType,
  type PlatformTypeCapability,
  type DataModelPhysicalChange,
} from '../model/dataModel';

interface DataModelFieldsPanelProps {
  model: DataModel;
  canUpdate: boolean;
}

interface EditableField extends DataModelFieldInput {
  rowKey: string;
}

interface FieldFilters {
  keyword?: string;
  fieldType?: PlatformDataType;
}

interface FieldEditorModalProps {
  open: boolean;
  field: EditableField | null;
  nextSortOrder: number;
  structuralLocked: boolean;
  storageDataSourceId: string;
  onCancel: () => void;
  onSave: (field: EditableField) => void;
}

const fieldTypeOptions = (Object.entries(dataModelFieldTypeLabels) as [PlatformDataType, string][])
  .map(([value, label]) => ({ value, label }));

const newRowKey = () => `new-${Date.now()}-${Math.random().toString(36).slice(2)}`;

const toEditableFields = (fields: DataModelField[]): EditableField[] => (
  fields.map((field) => ({
    id: field.id,
    code: field.code,
    name: field.name,
    fieldType: field.fieldType,
    ...(field.length !== null ? { length: field.length } : {}),
    ...(field.precision !== null ? { precision: field.precision } : {}),
    ...(field.scale !== null ? { scale: field.scale } : {}),
    nullable: field.nullable,
    primaryKey: field.primaryKey,
    sortOrder: field.sortOrder,
    ...(field.description ? { description: field.description } : {}),
    rowKey: field.id,
  }))
);

const toFieldInput = (field: EditableField): DataModelFieldInput => ({
  ...(field.id ? { id: field.id } : {}),
  code: field.code,
  name: field.name,
  fieldType: field.fieldType,
  ...(field.length !== undefined ? { length: field.length } : {}),
  ...(field.precision !== undefined ? { precision: field.precision } : {}),
  ...(field.scale !== undefined ? { scale: field.scale } : {}),
  nullable: field.nullable,
  primaryKey: field.primaryKey,
  sortOrder: field.sortOrder,
  ...(field.description ? { description: field.description } : {}),
});

const fieldTypeDescription = (field: EditableField) => {
  if (field.fieldType === 'STRING') {
    return field.length
      ? `${dataModelFieldTypeLabels[field.fieldType]}(${field.length})`
      : `${dataModelFieldTypeLabels[field.fieldType]}(无上限)`;
  }
  if (field.fieldType === 'DECIMAL') return `${dataModelFieldTypeLabels[field.fieldType]}(${field.precision ?? '—'},${field.scale ?? 0})`;
  return dataModelFieldTypeLabels[field.fieldType];
};

const FieldEditorModal = ({
  open,
  field,
  nextSortOrder,
  structuralLocked,
  storageDataSourceId,
  onCancel,
  onSave,
}: FieldEditorModalProps) => {
  const [form] = Form.useForm<DataModelFieldInput>();
  const selectedType = Form.useWatch('fieldType', form);
  const selectedPrimaryKey = Form.useWatch('primaryKey', form);
  const capabilitiesQuery = usePlatformTypeCapabilities(storageDataSourceId, open && !structuralLocked);
  const capabilities = useMemo(() => new Map(
    (capabilitiesQuery.data ?? []).map((capability) => [capability.type, capability]),
  ), [capabilitiesQuery.data]);
  const selectedCapability: PlatformTypeCapability | undefined = selectedType
    ? capabilities.get(selectedType)
    : undefined;
  const editorTypeOptions = fieldTypeOptions.map((option) => {
    const capability = capabilities.get(option.value);
    return {
      ...option,
      disabled: capability ? !capability.supported : false,
      title: capability?.message ?? undefined,
    };
  });

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue(field ?? {
      fieldType: 'STRING',
      nullable: true,
      primaryKey: false,
      sortOrder: nextSortOrder,
    });
  }, [field, form, nextSortOrder, open]);

  useEffect(() => {
    if (open && selectedType === 'STRING' && selectedCapability?.lengthParameterSupported === false) {
      form.setFieldValue('length', undefined);
    }
  }, [form, open, selectedCapability, selectedType]);

  const changeType = (fieldType: PlatformDataType) => {
    const capability = capabilities.get(fieldType);
    form.setFieldValue(
      'length',
      fieldType === 'STRING' && capability?.lengthParameterSupported !== false ? form.getFieldValue('length') : undefined,
    );
    form.setFieldValue('precision', fieldType === 'DECIMAL' ? form.getFieldValue('precision') ?? 18 : undefined);
    form.setFieldValue('scale', fieldType === 'DECIMAL' ? form.getFieldValue('scale') ?? 2 : undefined);
  };

  const submit = async () => {
    const values = await form.validateFields();
    if (structuralLocked && field) {
      onSave({
        ...field,
        name: values.name.trim(),
        sortOrder: values.sortOrder,
        description: values.description?.trim() || undefined,
      });
      return;
    }
    onSave({
      ...(field?.id ? { id: field.id } : {}),
      code: values.code.trim(),
      name: values.name.trim(),
      fieldType: values.fieldType,
      ...(values.fieldType === 'STRING' ? { length: values.length } : {}),
      ...(values.fieldType === 'DECIMAL' ? { precision: values.precision, scale: values.scale } : {}),
      nullable: values.primaryKey ? false : values.nullable,
      primaryKey: values.primaryKey,
      sortOrder: values.sortOrder,
      ...(values.description?.trim() ? { description: values.description.trim() } : {}),
      rowKey: field?.rowKey ?? newRowKey(),
    });
  };

  return (
    <Modal
      title={structuralLocked ? '修改外部字段业务信息' : field ? '修改字段' : '新增字段'}
      open={open}
      width={620}
      destroyOnHidden
      onCancel={onCancel}
      onOk={() => void submit()}
      okText="确定"
      cancelText="取消"
    >
      <Form<DataModelFieldInput> form={form} layout="vertical">
        <Row gutter={12}>
          <Col span={12}>
            <Form.Item
              label="字段编码"
              name="code"
              rules={[
                { required: true, whitespace: true, message: '请输入字段编码' },
                { pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/, message: '编码以字母开头，只能包含字母、数字和下划线' },
              ]}
            >
              <Input disabled={structuralLocked} placeholder="如：order_id" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="字段名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入字段名称' }]}>
              <Input placeholder="如：订单ID" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="字段类型" name="fieldType" rules={[{ required: true }]}>
              <Select
                disabled={structuralLocked || capabilitiesQuery.isFetching}
                loading={capabilitiesQuery.isFetching}
                options={editorTypeOptions}
                onChange={changeType}
              />
            </Form.Item>
          </Col>
          {selectedType === 'STRING' && (structuralLocked || selectedCapability?.lengthParameterSupported !== false) && (
            <Col span={12}>
              <Form.Item label="长度（可选）" name="length" extra="留空表示无长度上限，由目标数据库映射为 text、CLOB 或 String">
                <InputNumber disabled={structuralLocked} min={1} precision={0} className="data-model-number-input" />
              </Form.Item>
            </Col>
          )}
          {selectedType === 'STRING' && !structuralLocked && selectedCapability?.lengthParameterSupported === false && (
            <Col span={12}>
              <Alert type="info" showIcon title="该数据存储仅支持无长度上限的字符串" description={selectedCapability.message} />
            </Col>
          )}
          {selectedType === 'DECIMAL' && (
            <>
              <Col span={6}>
                <Form.Item label="精度" name="precision" rules={[{ required: true, message: '请输入精度' }]}>
                  <InputNumber disabled={structuralLocked} min={1} max={38} precision={0} className="data-model-number-input" />
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item label="小数位" name="scale" rules={[{ required: true, message: '请输入小数位' }]}>
                  <InputNumber disabled={structuralLocked} min={0} max={38} precision={0} className="data-model-number-input" />
                </Form.Item>
              </Col>
            </>
          )}
          <Col span={8}>
            <Form.Item label="允许为空" name="nullable" valuePropName="checked">
              <Switch disabled={structuralLocked || selectedPrimaryKey} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item label="主键" name="primaryKey" valuePropName="checked">
              <Switch disabled={structuralLocked} onChange={(checked) => checked && form.setFieldValue('nullable', false)} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item label="排序" name="sortOrder" rules={[{ required: true }]}>
              <InputNumber min={0} precision={0} className="data-model-number-input" />
            </Form.Item>
          </Col>
          <Col span={24}>
            <Form.Item label="说明" name="description" rules={[{ max: 500 }]}>
              <Input placeholder="可选" />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Modal>
  );
};

export const DataModelFieldsPanel = ({ model, canUpdate }: DataModelFieldsPanelProps) => {
  const [filterForm] = Form.useForm<FieldFilters>();
  const [filters, setFilters] = useState<FieldFilters>({});
  const [localFields, setLocalFields] = useState<EditableField[] | null>(null);
  const [editingField, setEditingField] = useState<EditableField | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [selectedChange, setSelectedChange] = useState<DataModelPhysicalChange | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [messageApi, messageContext] = message.useMessage();
  const detailQuery = useDataModel(model.id, true);
  const updateMutation = useUpdateDataModelFields();
  const createPlanMutation = useCreatePhysicalTableChangePlan();
  const detailModel = detailQuery.data?.model ?? model;
  const readOnly = detailModel.status !== 'DRAFT' || !canUpdate;
  const externalModel = detailModel.physicalTableMode === 'EXTERNAL';
  const serverFields = useMemo(() => toEditableFields(detailQuery.data?.fields ?? []), [detailQuery.data?.fields]);
  const inspectionQuery = usePhysicalTableInspection(
    model.id,
    detailModel.physicalTableMode === 'MANAGED' && serverFields.length > 0,
  );
  const fields = localFields ?? serverFields;
  const dirty = localFields !== null;
  const physicalTableState = inspectionQuery.data?.state;
  const requiresPhysicalChangePlan = shouldCreatePhysicalTableChangePlan(detailModel.physicalTableMode, physicalTableState);
  const directSaveAllowed = serverFields.length === 0
    || canSaveFieldsDirectly(detailModel.physicalTableMode, physicalTableState);
  const physicalChangeBlocked = serverFields.length > 0
    && detailModel.physicalTableMode === 'MANAGED'
    && !inspectionQuery.isPending
    && Boolean(inspectionQuery.data)
    && !directSaveAllowed
    && !requiresPhysicalChangePlan;

  const visibleFields = useMemo(() => {
    const keyword = filters.keyword?.trim().toLowerCase();
    return fields.filter((field) => (
      (!keyword || field.code.toLowerCase().includes(keyword) || field.name.toLowerCase().includes(keyword))
      && (!filters.fieldType || field.fieldType === filters.fieldType)
    ));
  }, [fields, filters]);

  const maxPage = Math.max(1, Math.ceil(visibleFields.length / pageSize));
  const effectivePage = Math.min(page, maxPage);

  const saveField = (field: EditableField) => {
    setLocalFields((current) => {
      const source = current ?? serverFields;
      const existingIndex = source.findIndex((item) => item.rowKey === field.rowKey);
      if (existingIndex < 0) return [...source, field].sort((left, right) => left.sortOrder - right.sortOrder);
      const next = [...source];
      next[existingIndex] = field;
      return next.sort((left, right) => left.sortOrder - right.sortOrder);
    });
    setEditorOpen(false);
    setEditingField(null);
  };

  const saveAll = async () => {
    try {
      const detail = await updateMutation.mutateAsync({
        id: model.id,
        request: { fields: fields.map(toFieldInput) },
      });
      setLocalFields(null);
      messageApi.success(`已保存 ${detail.fields.length} 个字段`);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存模型字段失败');
    }
  };

  const createPlan = async () => {
    try {
      const change = await createPlanMutation.mutateAsync({
        id: model.id,
        request: { fields: fields.map(toFieldInput) },
      });
      messageApi.success('变更计划已生成，请审阅受控 SQL 后明确执行方式');
      setSelectedChange(change);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '生成物理表变更计划失败');
    }
  };

  const columns: TableProps<EditableField>['columns'] = [
    { title: '字段编码', dataIndex: 'code', width: 170, ellipsis: true, fixed: 'left', render: (value: string) => <code>{value}</code> },
    { title: '字段名称', dataIndex: 'name', width: 170, ellipsis: true },
    { title: '类型', key: 'type', width: 140, render: (_value, field) => fieldTypeDescription(field) },
    { title: '主键', dataIndex: 'primaryKey', width: 72, render: (value: boolean) => value ? <Tag color="blue">是</Tag> : '—' },
    { title: '允许为空', dataIndex: 'nullable', width: 90, render: (value: boolean) => value ? '是' : '否' },
    { title: '排序', dataIndex: 'sortOrder', width: 72 },
    { title: '说明', dataIndex: 'description', width: 260, ellipsis: true, render: (value?: string) => value || '—' },
    ...(!readOnly ? [{
      title: '操作',
      key: 'actions',
      width: 76,
      fixed: 'right' as const,
      render: (_value: unknown, field: EditableField) => (
        <Space size={2}>
          <Tooltip title="修改字段">
            <Button
              type="text"
              icon={<EditOutlined />}
              aria-label={`修改字段${field.name}`}
              onClick={() => { setEditingField(field); setEditorOpen(true); }}
            />
          </Tooltip>
          {!externalModel && (
            <Popconfirm
              title="删除字段"
              description={`确认删除“${field.name}”吗？保存后生效。`}
              okText="删除"
              cancelText="取消"
              onConfirm={() => setLocalFields((current) => (current ?? serverFields).filter((item) => item.rowKey !== field.rowKey))}
            >
              <Tooltip title="删除字段">
                <Button type="text" danger icon={<DeleteOutlined />} aria-label={`删除字段${field.name}`} />
              </Tooltip>
            </Popconfirm>
          )}
        </Space>
      ),
    }] : []),
  ];

  const nextSortOrder = fields.length ? Math.max(...fields.map((field) => field.sortOrder)) + 10 : 10;

  return (
    <div className="model-detail-tab-panel model-fields-panel">
      {messageContext}
      {readOnly && (
        <Alert banner type="info" showIcon title={detailModel.status !== 'DRAFT' ? '模型已发布，字段结构只读。' : '当前账号没有修改模型的权限。'} />
      )}
      {!readOnly && externalModel && (
        <Alert
          banner
          type="info"
          showIcon
          title="外部表字段结构由数据库维护；此处仅可修改字段名称、说明和展示排序。"
        />
      )}
      {detailQuery.error && (
        <Alert
          type="error"
          showIcon
          title="字段加载失败"
          action={<Button onClick={() => void detailQuery.refetch()}>重试</Button>}
        />
      )}
      {!readOnly && inspectionQuery.isPending && (
        <Alert showIcon type="info" title="正在检查受管物理表状态…" />
      )}
      {!readOnly && inspectionQuery.error && (
        <Alert
          showIcon
          type="error"
          title="无法确认受管物理表状态，当前不能直接保存字段。"
          description={inspectionQuery.error instanceof Error ? inspectionQuery.error.message : '请检查数据存储连接后重试。'}
          action={<Button size="small" onClick={() => void inspectionQuery.refetch()}>重试</Button>}
        />
      )}
      {!readOnly && requiresPhysicalChangePlan && (
        <Alert
          showIcon
          type="info"
          title="物理表结构已匹配：字段修改会先生成变更计划，执行成功后才同步模型字段。"
        />
      )}
      {!readOnly && physicalChangeBlocked && (
        <Alert
          showIcon
          type="warning"
          title="受管物理表未处于可规划状态，不能直接保存字段。"
          description="请先在基本信息页检查并修复物理表状态；物理表严格匹配后可生成变更计划。"
        />
      )}
      <div className="model-tab-toolbar">
        <Form<FieldFilters>
          form={filterForm}
          layout="inline"
          initialValues={filters}
          onFinish={(values) => { setFilters(values); setPage(1); }}
        >
          <Form.Item name="keyword" label="名称/编码">
            <Input allowClear placeholder="筛选字段" className="model-field-keyword-input" />
          </Form.Item>
          <Form.Item name="fieldType" label="类型">
            <Select allowClear placeholder="全部" options={fieldTypeOptions} className="model-field-type-select" />
          </Form.Item>
        </Form>
        <Space size={4}>
          <Button type="primary" onClick={() => filterForm.submit()}>查询</Button>
          <Button onClick={() => { filterForm.resetFields(); setFilters({}); setPage(1); }}>重置</Button>
          <Button icon={<ReloadOutlined />} onClick={() => { setLocalFields(null); void detailQuery.refetch(); }}>刷新</Button>
          {!readOnly && directSaveAllowed && (
            <Button icon={<SaveOutlined />} disabled={!dirty} loading={updateMutation.isPending} onClick={() => void saveAll()}>
              保存字段
            </Button>
          )}
          {!readOnly && requiresPhysicalChangePlan && (
            <Button type="primary" icon={<FileSearchOutlined />} disabled={!dirty} loading={createPlanMutation.isPending} onClick={() => void createPlan()}>
              生成变更计划
            </Button>
          )}
          {!readOnly && !externalModel && (
            <Button type={requiresPhysicalChangePlan ? 'default' : 'primary'} icon={<PlusOutlined />} onClick={() => { setEditingField(null); setEditorOpen(true); }}>
              新增字段
            </Button>
          )}
        </Space>
      </div>
      <Table<EditableField>
        size="small"
        className="management-table model-fields-table"
        rowKey="rowKey"
        columns={columns}
        dataSource={visibleFields}
        loading={detailQuery.isFetching}
        scroll={{ x: 1050, y: '100%' }}
        pagination={{
          current: effectivePage,
          pageSize,
          total: visibleFields.length,
          placement: ['bottomEnd'],
          hideOnSinglePage: false,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 项`,
        }}
        onChange={(pagination) => {
          setPage(pagination.current ?? 1);
          setPageSize(pagination.pageSize ?? 20);
        }}
      />
      <FieldEditorModal
        open={editorOpen}
        field={editingField}
        nextSortOrder={nextSortOrder}
        structuralLocked={externalModel}
        storageDataSourceId={detailModel.storageDataSourceId}
        onCancel={() => { setEditorOpen(false); setEditingField(null); }}
        onSave={saveField}
      />
      <DataModelPhysicalChangeDrawer
        open={Boolean(selectedChange)}
        modelId={model.id}
        change={selectedChange}
        canUpdate={canUpdate}
        onClose={() => setSelectedChange(null)}
      />
    </div>
  );
};
