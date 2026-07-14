import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import {
  Alert,
  Button,
  Col,
  Drawer,
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
import { useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useDataModel, useUpdateDataModelFields } from '../hooks/useDataModels';
import {
  dataModelFieldTypeLabels,
  type DataModel,
  type DataModelField,
  type DataModelFieldInput,
  type DataModelFieldType,
} from '../model/dataModel';

interface DataModelFieldsDrawerProps {
  open: boolean;
  model: DataModel | null;
  onClose: () => void;
}

interface EditableField extends DataModelFieldInput {
  rowKey: string;
}

interface FieldEditorModalProps {
  open: boolean;
  field: EditableField | null;
  nextSortOrder: number;
  onCancel: () => void;
  onSave: (field: EditableField) => void;
}

const fieldTypeOptions = (Object.entries(dataModelFieldTypeLabels) as [DataModelFieldType, string][])
  .map(([value, label]) => ({ value, label }));

const newRowKey = () => `new-${Date.now()}-${Math.random().toString(36).slice(2)}`;

const FieldEditorModal = ({ open, field, nextSortOrder, onCancel, onSave }: FieldEditorModalProps) => {
  const [form] = Form.useForm<DataModelFieldInput>();
  const selectedType = Form.useWatch('fieldType', form);
  const selectedPrimaryKey = Form.useWatch('primaryKey', form);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    if (field) {
      form.setFieldsValue(field);
    } else {
      form.setFieldsValue({
        fieldType: 'STRING',
        length: 255,
        nullable: true,
        primaryKey: false,
        sortOrder: nextSortOrder,
      });
    }
  }, [field, form, nextSortOrder, open]);

  const changeType = (fieldType: DataModelFieldType) => {
    form.setFieldValue('length', fieldType === 'STRING' ? form.getFieldValue('length') ?? 255 : undefined);
    form.setFieldValue('precision', fieldType === 'DECIMAL' ? form.getFieldValue('precision') ?? 18 : undefined);
    form.setFieldValue('scale', fieldType === 'DECIMAL' ? form.getFieldValue('scale') ?? 2 : undefined);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const normalized: EditableField = {
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
    };
    onSave(normalized);
  };

  return (
    <Modal
      title={field ? '修改字段' : '新增字段'}
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
              <Input placeholder="如：order_id" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="字段名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入字段名称' }]}>
              <Input placeholder="如：订单ID" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="字段类型" name="fieldType" rules={[{ required: true }]}>
              <Select options={fieldTypeOptions} onChange={changeType} />
            </Form.Item>
          </Col>
          {selectedType === 'STRING' && (
            <Col span={12}>
              <Form.Item label="长度" name="length" rules={[{ required: true, message: '请输入字符串长度' }]}>
                <InputNumber min={1} max={4000} precision={0} className="data-model-number-input" />
              </Form.Item>
            </Col>
          )}
          {selectedType === 'DECIMAL' && (
            <>
              <Col span={6}>
                <Form.Item label="精度" name="precision" rules={[{ required: true, message: '请输入精度' }]}>
                  <InputNumber min={1} max={38} precision={0} className="data-model-number-input" />
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item label="小数位" name="scale" rules={[{ required: true, message: '请输入小数位' }]}>
                  <InputNumber min={0} max={38} precision={0} className="data-model-number-input" />
                </Form.Item>
              </Col>
            </>
          )}
          <Col span={8}>
            <Form.Item label="允许为空" name="nullable" valuePropName="checked">
              <Switch disabled={selectedPrimaryKey} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item label="主键" name="primaryKey" valuePropName="checked">
              <Switch onChange={(checked) => checked && form.setFieldValue('nullable', false)} />
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

const fieldTypeDescription = (field: EditableField) => {
  if (field.fieldType === 'STRING') return `${dataModelFieldTypeLabels[field.fieldType]}(${field.length ?? '—'})`;
  if (field.fieldType === 'DECIMAL') return `${dataModelFieldTypeLabels[field.fieldType]}(${field.precision ?? '—'},${field.scale ?? 0})`;
  return dataModelFieldTypeLabels[field.fieldType];
};

export const DataModelFieldsDrawer = ({ open, model, onClose }: DataModelFieldsDrawerProps) => {
  if (!open || !model) return null;
  return <DataModelFieldsDrawerContent key={model.id} model={model} onClose={onClose} />;
};

interface DataModelFieldsDrawerContentProps {
  model: DataModel;
  onClose: () => void;
}

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

const DataModelFieldsDrawerContent = ({ model, onClose }: DataModelFieldsDrawerContentProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const [localFields, setLocalFields] = useState<EditableField[] | null>(null);
  const [editingField, setEditingField] = useState<EditableField | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const detailQuery = useDataModel(model.id, true);
  const updateMutation = useUpdateDataModelFields();
  const readOnly = detailQuery.data?.model.status !== 'DRAFT';
  const serverFields = detailQuery.data ? toEditableFields(detailQuery.data.fields) : [];
  const fields = localFields ?? serverFields;

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
        request: {
          fields: fields.map(toFieldInput),
        },
      });
      setLocalFields(toEditableFields(detail.fields));
      messageApi.success('模型字段已保存');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存模型字段失败');
    }
  };

  const columns: TableProps<EditableField>['columns'] = [
    { title: '字段编码', dataIndex: 'code', width: 150, ellipsis: true, render: (value: string) => <code>{value}</code> },
    { title: '字段名称', dataIndex: 'name', width: 150, ellipsis: true },
    { title: '类型', key: 'type', width: 130, render: (_value, field) => fieldTypeDescription(field) },
    { title: '主键', dataIndex: 'primaryKey', width: 70, render: (value: boolean) => value ? <Tag color="blue">是</Tag> : '—' },
    { title: '允许为空', dataIndex: 'nullable', width: 90, render: (value: boolean) => value ? '是' : '否' },
    { title: '排序', dataIndex: 'sortOrder', width: 70 },
    { title: '说明', dataIndex: 'description', ellipsis: true, render: (value?: string) => value || '—' },
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
              size="small"
              icon={<EditOutlined />}
              aria-label={`修改字段${field.name}`}
              onClick={() => { setEditingField(field); setEditorOpen(true); }}
            />
          </Tooltip>
          <Popconfirm
            title="删除字段"
            description={`确认删除“${field.name}”吗？保存后生效。`}
            okText="删除"
            cancelText="取消"
            onConfirm={() => setLocalFields((current) => (current ?? serverFields).filter((item) => item.rowKey !== field.rowKey))}
          >
            <Tooltip title="删除字段">
              <Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label={`删除字段${field.name}`} />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    }] : []),
  ];

  const nextSortOrder = fields.length ? Math.max(...fields.map((field) => field.sortOrder)) + 10 : 10;

  return (
    <>
      {messageContext}
      <Drawer
        title={`字段管理 · ${model.name}`}
        open
        size="min(1080px, 92vw)"
        className="data-model-fields-drawer"
        destroyOnHidden
        onClose={onClose}
        footer={(
          <Space>
            <Button onClick={onClose}>关闭</Button>
            {!readOnly && (
              <Button type="primary" loading={updateMutation.isPending} onClick={() => void saveAll()}>
                保存字段
              </Button>
            )}
          </Space>
        )}
      >
        {readOnly && (
          <Alert type="info" showIcon title="模型已发布，字段结构为只读。第一版不会同步或修改物理表。" />
        )}
        {detailQuery.error && (
          <Alert
            type="error"
            showIcon
            title="字段加载失败"
            action={<Button size="small" onClick={() => void detailQuery.refetch()}>重试</Button>}
          />
        )}
        <div className="data-model-fields-toolbar">
          <span>共 {fields.length} 个字段</span>
          <Space size={4}>
            <Button icon={<ReloadOutlined />} onClick={() => { setLocalFields(null); void detailQuery.refetch(); }}>刷新</Button>
            {!readOnly && (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingField(null); setEditorOpen(true); }}>
                新增字段
              </Button>
            )}
          </Space>
        </div>
        <Table<EditableField>
          size="small"
          className="data-model-fields-table"
          rowKey="rowKey"
          columns={columns}
          dataSource={fields}
          loading={detailQuery.isFetching}
          pagination={false}
          scroll={{ x: 940, y: '100%' }}
        />
      </Drawer>
      <FieldEditorModal
        open={editorOpen}
        field={editingField}
        nextSortOrder={nextSortOrder}
        onCancel={() => { setEditorOpen(false); setEditingField(null); }}
        onSave={saveField}
      />
    </>
  );
};
