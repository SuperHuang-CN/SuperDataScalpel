import { CopyOutlined, DeleteOutlined, EditOutlined, FileSearchOutlined, PlusOutlined, ReloadOutlined, SaveOutlined } from '@ant-design/icons';
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
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useCurrentUser } from '../../system';
import {
  isStandardDictionaryTypeFamilyCompatible,
  standardDictionaryValueTypeLabels,
  useStandardDictionaries,
  type StandardDictionarySummary,
} from '../../standard';
import { DataModelPhysicalChangeDrawer } from './DataModelPhysicalChangeDrawer';
import {
  ModelFieldTemplatePickerModal,
  type ModelFieldTemplateCopyField,
} from './ModelFieldTemplatePickerModal';
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
  geometryKindLabels,
  isMetadataOnlyFieldUpdate,
  shouldCreatePhysicalTableChangePlan,
  type DataModel,
  type DataModelField,
  type DataModelFieldInput,
  type PlatformDataType,
  type PlatformTypeCapability,
  type DataModelPhysicalChange,
  type GeometryKind,
} from '../model/dataModel';

interface DataModelFieldsPanelProps {
  model: DataModel;
  canUpdate: boolean;
  onDirtyChange?: (dirty: boolean) => void;
}

export interface DataModelFieldsPanelHandle {
  discardChanges: () => void;
}

interface EditableField extends DataModelFieldInput {
  rowKey: string;
  standardDictionary?: StandardDictionarySummary | null;
  physicalColumnRole?: DataModelField['physicalColumnRole'];
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
  geometryTypeDisabled: boolean;
  storageDataSourceId: string;
  onCancel: () => void;
  onDirtyChange: (dirty: boolean) => void;
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
    ...(field.geometry != null ? { geometry: field.geometry } : {}),
    nullable: field.nullable,
    primaryKey: field.primaryKey,
    sortOrder: field.sortOrder,
    ...(field.description ? { description: field.description } : {}),
    ...(field.standardDictionary ? {
      standardDictionaryId: field.standardDictionary.id,
      standardDictionary: field.standardDictionary,
    } : {}),
    physicalColumnRole: field.physicalColumnRole,
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
  ...(field.geometry !== undefined ? { geometry: field.geometry } : {}),
  nullable: field.nullable,
  primaryKey: field.primaryKey,
  sortOrder: field.sortOrder,
  ...(field.description ? { description: field.description } : {}),
  ...(field.standardDictionaryId ? { standardDictionaryId: field.standardDictionaryId } : {}),
});

const fieldTypeDescription = (field: EditableField) => {
  if (field.fieldType === 'STRING') {
    return field.length
      ? `${dataModelFieldTypeLabels[field.fieldType]}(${field.length})`
      : `${dataModelFieldTypeLabels[field.fieldType]}(无上限)`;
  }
  if (field.fieldType === 'DECIMAL') return `${dataModelFieldTypeLabels[field.fieldType]}(${field.precision ?? '—'},${field.scale ?? 0})`;
  if (field.fieldType === 'GEOMETRY' && field.geometry) {
    return `${geometryKindLabels[field.geometry.kind]} · ${field.geometry.crs.authority}:${field.geometry.crs.code} · ${field.geometry.dimension}`;
  }
  return dataModelFieldTypeLabels[field.fieldType];
};

const FieldEditorModal = ({
  open,
  field,
  nextSortOrder,
  structuralLocked,
  geometryTypeDisabled,
  storageDataSourceId,
  onCancel,
  onDirtyChange,
  onSave,
}: FieldEditorModalProps) => {
  const [form] = Form.useForm<DataModelFieldInput>();
  const selectedType = Form.useWatch('fieldType', form);
  const selectedPrimaryKey = Form.useWatch('primaryKey', form);
  const selectedDictionaryId = Form.useWatch('standardDictionaryId', form);
  const currentUser = useCurrentUser();
  const canViewDictionaries = currentUser.data?.permissions.includes('standard.dictionary.view') ?? false;
  const dictionariesQuery = useStandardDictionaries(
    { page: 0, size: 500, sort: 'name,code' },
    open && canViewDictionaries,
  );
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
      disabled: (capability ? !capability.supported : false)
        || (option.value === 'GEOMETRY' && geometryTypeDisabled),
      title: option.value === 'GEOMETRY' && geometryTypeDisabled
        ? 'ClickHouse 排序键字段不能改为空间类型'
        : capability?.message ?? undefined,
    };
  });
  const geometryKindOptions = (
    selectedCapability?.geometryKinds?.length
      ? selectedCapability.geometryKinds
      : (Object.keys(geometryKindLabels) as GeometryKind[])
  ).map((value) => ({ value, label: geometryKindLabels[value] }));
  const dictionaryOptions = useMemo(() => {
    const dictionaries = [...(dictionariesQuery.data?.content ?? [])];
    if (field?.standardDictionary
      && !dictionaries.some((dictionary) => dictionary.id === field.standardDictionary?.id)) {
      dictionaries.push({
        ...field.standardDictionary,
        description: null,
        createdAt: '',
        updatedAt: '',
      });
    }
    return dictionaries.map((dictionary) => ({
      value: dictionary.id,
      label: `${dictionary.code} · ${dictionary.name}${dictionary.enabled ? '' : '（已停用）'}`,
      disabled: !dictionary.enabled
        || !isStandardDictionaryTypeFamilyCompatible(selectedType, dictionary.valueType),
      title: `${standardDictionaryValueTypeLabels[dictionary.valueType]} · v${dictionary.version}`,
    }));
  }, [dictionariesQuery.data, field, selectedType]);

  useEffect(() => {
    onDirtyChange(false);
    if (!open) return;
    form.resetFields();
    form.setFieldsValue(field ?? {
      fieldType: 'STRING',
      nullable: true,
      primaryKey: false,
      sortOrder: nextSortOrder,
    });
  }, [field, form, nextSortOrder, onDirtyChange, open]);

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
    const values = await form.validateFields();
    const standardDictionary = values.standardDictionaryId
      ? dictionariesQuery.data?.content.find(
        (dictionary) => dictionary.id === values.standardDictionaryId,
      ) ?? (
        field?.standardDictionary?.id === values.standardDictionaryId
          ? field.standardDictionary
          : null
      )
      : null;
    if (structuralLocked && field) {
      onDirtyChange(false);
      onSave({
        ...field,
        name: values.name.trim(),
        sortOrder: values.sortOrder,
        description: values.description?.trim() || undefined,
        standardDictionaryId: values.standardDictionaryId,
        standardDictionary,
      });
      return;
    }
    onDirtyChange(false);
    onSave({
      ...(field?.id ? { id: field.id } : {}),
      code: values.code.trim(),
      name: values.name.trim(),
      fieldType: values.fieldType,
      ...(values.fieldType === 'STRING' ? { length: values.length } : {}),
      ...(values.fieldType === 'DECIMAL' ? { precision: values.precision, scale: values.scale } : {}),
      ...(values.fieldType === 'GEOMETRY' ? { geometry: values.geometry } : {}),
      nullable: values.primaryKey ? false : values.nullable,
      primaryKey: values.fieldType === 'GEOMETRY' ? false : values.primaryKey,
      sortOrder: values.sortOrder,
      ...(values.description?.trim() ? { description: values.description.trim() } : {}),
      ...(values.standardDictionaryId ? { standardDictionaryId: values.standardDictionaryId } : {}),
      standardDictionary,
      rowKey: field?.rowKey ?? newRowKey(),
    });
  };

  return (
    <Modal
      rootClassName="business-overlay business-modal-overlay"
      title={structuralLocked ? '修改外部字段业务信息' : field ? '修改字段' : '新增字段'}
      open={open}
      width={620}
      destroyOnHidden
      onCancel={onCancel}
      onOk={() => void submit()}
      okText="确定"
      cancelText="取消"
    >
      <Form<DataModelFieldInput>
        autoComplete="off"
        form={form}
        layout="vertical"
        onValuesChange={() => onDirtyChange(true)}
      >
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
          {selectedType === 'GEOMETRY' && (
            <>
              <Col span={12}>
                <Form.Item label="几何类型" name={['geometry', 'kind']} rules={[{ required: true, message: '请选择几何类型' }]}>
                  <Select disabled={structuralLocked} options={geometryKindOptions} />
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item label="CRS Authority" name={['geometry', 'crs', 'authority']} rules={[{ required: true }]}>
                  <Input disabled />
                </Form.Item>
              </Col>
              <Col span={6}>
                <Form.Item label="EPSG Code" name={['geometry', 'crs', 'code']} rules={[{ required: true, message: '请输入 EPSG Code' }]}>
                  <InputNumber disabled={structuralLocked} min={1} precision={0} className="data-model-number-input" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="坐标维度" name={['geometry', 'dimension']} rules={[{ required: true }]}>
                  <Select disabled options={[{ value: 'XY', label: 'XY（二维坐标）' }]} />
                </Form.Item>
              </Col>
            </>
          )}
          <Col span={8}>
            <Form.Item label="允许为空" name="nullable" valuePropName="checked">
              <Switch disabled={structuralLocked || selectedPrimaryKey} />
            </Form.Item>
          </Col>
          <Col span={24}>
            <Form.Item
              label="关联码表"
              name="standardDictionaryId"
              extra={!canViewDictionaries
                ? '当前账号没有查看码表权限，已有绑定会保持不变。'
                : selectedDictionaryId && dictionaryOptions.find((option) => option.value === selectedDictionaryId)?.disabled
                  ? '当前码表已停用或与字段类型不兼容，请清空或更换后保存。'
                  : '物理表仍保存码表编码；该绑定仅作为字段业务元数据。'}
            >
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                disabled={!canViewDictionaries}
                loading={dictionariesQuery.isFetching}
                options={dictionaryOptions}
                placeholder="可选"
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item label="主键" name="primaryKey" valuePropName="checked">
              <Switch
                disabled={structuralLocked || selectedType === 'GEOMETRY'}
                onChange={(checked) => checked && form.setFieldValue('nullable', false)}
              />
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

export const DataModelFieldsPanel = forwardRef<DataModelFieldsPanelHandle, DataModelFieldsPanelProps>(({
  model,
  canUpdate,
  onDirtyChange,
}, ref) => {
  const [filterForm] = Form.useForm<FieldFilters>();
  const [filters, setFilters] = useState<FieldFilters>({});
  const [localFields, setLocalFields] = useState<EditableField[] | null>(null);
  const [editingField, setEditingField] = useState<EditableField | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editorDirty, setEditorDirty] = useState(false);
  const [templatePickerOpen, setTemplatePickerOpen] = useState(false);
  const [selectedChange, setSelectedChange] = useState<DataModelPhysicalChange | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const detailQuery = useDataModel(model.id, true);
  const updateMutation = useUpdateDataModelFields();
  const createPlanMutation = useCreatePhysicalTableChangePlan();
  const detailModel = detailQuery.data?.model ?? model;
  const readOnly = detailModel.status === 'PUBLISHED' || !canUpdate;
  const externalModel = detailModel.physicalTableMode === 'EXTERNAL';
  const serverFields = useMemo(() => toEditableFields(detailQuery.data?.fields ?? []), [detailQuery.data?.fields]);
  const inspectionQuery = usePhysicalTableInspection(
    model.id,
    detailModel.physicalTableMode === 'MANAGED' && serverFields.length > 0,
  );
  const fields = localFields ?? serverFields;
  const serverFieldsFingerprint = useMemo(
    () => JSON.stringify(serverFields.map(toFieldInput)),
    [serverFields],
  );
  const currentFieldsFingerprint = useMemo(
    () => JSON.stringify(fields.map(toFieldInput)),
    [fields],
  );
  const dirty = localFields !== null && currentFieldsFingerprint !== serverFieldsFingerprint;
  const hasUnsavedChanges = dirty || editorDirty;
  const metadataOnlyChange = dirty && isMetadataOnlyFieldUpdate(serverFields, fields);
  const physicalTableState = inspectionQuery.data?.state;
  const geometryModel = serverFields.some((field) => field.fieldType === 'GEOMETRY');
  const geometryPhysicalLocked = geometryModel
    && detailModel.physicalTableMode === 'MANAGED'
    && physicalTableState === 'MATCHED';
  const requiresPhysicalChangePlan = !geometryModel
    && shouldCreatePhysicalTableChangePlan(detailModel.physicalTableMode, physicalTableState);
  const directSaveAllowed = serverFields.length === 0
    || geometryPhysicalLocked
    || metadataOnlyChange
    || canSaveFieldsDirectly(detailModel.physicalTableMode, physicalTableState);
  const physicalChangeBlocked = serverFields.length > 0
    && detailModel.physicalTableMode === 'MANAGED'
    && !inspectionQuery.isPending
    && Boolean(inspectionQuery.data)
    && !directSaveAllowed
    && !requiresPhysicalChangePlan;

  const discardChanges = useCallback(() => {
    setLocalFields(null);
    setEditorDirty(false);
    setEditorOpen(false);
    setEditingField(null);
    setTemplatePickerOpen(false);
  }, []);

  const cancelFieldEditor = () => {
    if (!editorDirty) {
      setEditorOpen(false);
      setEditingField(null);
      return;
    }
    modalApi.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: '放弃当前字段修改？',
      content: '字段编辑弹窗中的修改尚未应用，关闭后会丢失。',
      okText: '放弃修改',
      okButtonProps: { danger: true },
      cancelText: '继续编辑',
      onOk: () => {
        setEditorDirty(false);
        setEditorOpen(false);
        setEditingField(null);
      },
    });
  };

  useImperativeHandle(ref, () => ({ discardChanges }), [discardChanges]);

  useEffect(() => {
    onDirtyChange?.(hasUnsavedChanges);
    return () => onDirtyChange?.(false);
  }, [hasUnsavedChanges, onDirtyChange]);

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

  const addTemplateFields = (
    copiedFields: ModelFieldTemplateCopyField[],
    skippedIssues: string[],
  ) => {
    setLocalFields((current) => {
      const source = current ?? serverFields;
      let sortOrder = source.length ? Math.max(...source.map((field) => field.sortOrder)) : 0;
      const appended = copiedFields.map((field): EditableField => {
        sortOrder += 10;
        return {
          code: field.code,
          name: field.name,
          fieldType: field.fieldType,
          ...(field.length !== undefined ? { length: field.length } : {}),
          ...(field.precision !== undefined ? { precision: field.precision } : {}),
          ...(field.scale !== undefined ? { scale: field.scale } : {}),
          ...(field.geometry ? { geometry: field.geometry } : {}),
          nullable: field.nullable,
          primaryKey: field.primaryKey,
          sortOrder,
          ...(field.description ? { description: field.description } : {}),
          ...(field.standardDictionary ? {
            standardDictionaryId: field.standardDictionary.id,
            standardDictionary: field.standardDictionary,
          } : {}),
          rowKey: newRowKey(),
        };
      });
      return [...source, ...appended];
    });
    setTemplatePickerOpen(false);
    if (skippedIssues.length > 0) {
      messageApi.warning(`已添加 ${copiedFields.length} 个字段，另有 ${skippedIssues.length} 项未原样带入`);
    } else {
      messageApi.success(`已从模板添加 ${copiedFields.length} 个字段，请检查后保存`);
    }
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

  const refreshFields = () => {
    if (!hasUnsavedChanges) {
      void detailQuery.refetch();
      return;
    }
    modalApi.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: '放弃未保存的字段修改？',
      content: '刷新后将重新加载最后保存的字段定义，当前修改会丢失。',
      okText: '放弃修改并刷新',
      okButtonProps: { danger: true },
      cancelText: '继续编辑',
      onOk: () => {
        discardChanges();
        void detailQuery.refetch();
      },
    });
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
    {
      title: '物理角色',
      dataIndex: 'physicalColumnRole',
      width: 100,
      render: (value?: DataModelField['physicalColumnRole']) => value === 'TIME_KEY'
        ? <Tag color="blue">时间主列</Tag>
        : value === 'TAG' ? <Tag color="purple">TAG</Tag> : '普通列',
    },
    { title: '主键', dataIndex: 'primaryKey', width: 72, render: (value: boolean) => value ? <Tag color="blue">是</Tag> : '—' },
    { title: '允许为空', dataIndex: 'nullable', width: 90, render: (value: boolean) => value ? '是' : '否' },
    { title: '排序', dataIndex: 'sortOrder', width: 72 },
    {
      title: '关联码表',
      key: 'standardDictionary',
      width: 200,
      ellipsis: true,
      render: (_value, field) => field.standardDictionary
        ? (
          <Tooltip title={`${standardDictionaryValueTypeLabels[field.standardDictionary.valueType]} · v${field.standardDictionary.version}`}>
            <Tag color={field.standardDictionary.enabled ? 'blue' : 'default'}>
              {field.standardDictionary.code} · {field.standardDictionary.name}
            </Tag>
          </Tooltip>
        )
        : '—',
    },
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
          {!externalModel && !geometryPhysicalLocked && (
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
      {modalContext}
      {readOnly && (
        <Alert
          banner
          type="info"
          showIcon
          title={!canUpdate ? '当前账号没有修改模型的权限。' : '模型已发布，字段结构只读；请先停用模型后再修改。'}
        />
      )}
      {!readOnly && detailModel.status === 'DISABLED' && (
        <Alert
          banner
          type="info"
          showIcon
          title="模型已停用，可以修改字段；涉及物理表结构的调整需生成并执行变更计划。"
        />
      )}
      {!readOnly && externalModel && (
        <Alert
          banner
          type="info"
          showIcon
          title="外部表字段结构由数据库维护；此处仅可修改字段名称、说明、展示排序和关联码表。"
        />
      )}
      {!readOnly && geometryPhysicalLocked && (
        <Alert
          banner
          type="info"
          showIcon
          title="包含空间字段的受管物理表已创建；第一版仅可修改字段名称、说明、展示排序和关联码表，不支持物理结构变更。"
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
          title="无法确认受管物理表状态，当前只能保存字段名称、说明、展示排序和关联码表。"
          description={inspectionQuery.error instanceof Error ? inspectionQuery.error.message : '请检查数据存储连接后重试；物理结构修改暂不可用。'}
          action={<Button size="small" onClick={() => void inspectionQuery.refetch()}>重试</Button>}
        />
      )}
      {!readOnly && requiresPhysicalChangePlan && (
        <Alert
          showIcon
          type="info"
          title="物理表结构已匹配：字段名称、说明、展示排序和关联码表可直接保存；物理结构修改需生成并执行变更计划。"
        />
      )}
      {!readOnly && physicalChangeBlocked && (
        <Alert
          showIcon
          type="warning"
          title="受管物理表未处于可规划状态，物理结构修改暂不可用。"
          description="字段名称、说明、展示排序和关联码表仍可直接保存；请先修复物理表状态，再调整字段结构。"
        />
      )}
      <div className="model-tab-toolbar">
        <Form<FieldFilters> autoComplete="off"
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
          {hasUnsavedChanges && <Tag color="processing">有未保存修改</Tag>}
          <Button type="primary" onClick={() => filterForm.submit()}>查询</Button>
          <Button onClick={() => { filterForm.resetFields(); setFilters({}); setPage(1); }}>重置</Button>
          <Button icon={<ReloadOutlined />} onClick={refreshFields}>刷新</Button>
          {!readOnly && directSaveAllowed && (
            <Button icon={<SaveOutlined />} disabled={!dirty} loading={updateMutation.isPending} onClick={() => void saveAll()}>
              保存字段
            </Button>
          )}
          {!readOnly && requiresPhysicalChangePlan && !metadataOnlyChange && (
            <Button type="primary" icon={<FileSearchOutlined />} disabled={!dirty} loading={createPlanMutation.isPending} onClick={() => void createPlan()}>
              生成变更计划
            </Button>
          )}
          {!readOnly && !externalModel && !geometryPhysicalLocked && (
            <Button icon={<CopyOutlined />} onClick={() => setTemplatePickerOpen(true)}>
              从模板添加
            </Button>
          )}
          {!readOnly && !externalModel && !geometryPhysicalLocked && (
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
        scroll={{ x: 1240, y: '100%' }}
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
        structuralLocked={externalModel || geometryPhysicalLocked}
        geometryTypeDisabled={Boolean(
          editingField && detailModel.clickHouseOrderByColumns.includes(editingField.code),
        )}
        storageDataSourceId={detailModel.storageDataSourceId}
        onCancel={cancelFieldEditor}
        onDirtyChange={setEditorDirty}
        onSave={saveField}
      />
      <DataModelPhysicalChangeDrawer
        open={Boolean(selectedChange)}
        modelId={model.id}
        change={selectedChange}
        canUpdate={canUpdate}
        onClose={() => setSelectedChange(null)}
      />
      {templatePickerOpen && (
        <ModelFieldTemplatePickerModal
          open
          storageDataSourceId={detailModel.storageDataSourceId}
          existingFieldCodes={fields.map((field) => field.code)}
          onCancel={() => setTemplatePickerOpen(false)}
          onApply={addTemplateFields}
        />
      )}
    </div>
  );
});

DataModelFieldsPanel.displayName = 'DataModelFieldsPanel';
