import { CompactAlert as Alert, ContextHelp } from '../../../shared/components/ContextualFeedback';
import {
  AppstoreOutlined,
  BarsOutlined,
  DeleteOutlined,
  EditOutlined,
  FileTextOutlined,
  MoreOutlined,
  MinusCircleOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import { Badge, Button, Card, Col, Drawer, Dropdown, Form, Input, InputNumber, Modal, Row, Select, Space, Switch, Table, Tag, Tooltip, Typography, message } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ManagementCode, ManagementDateTime, ManagementListCell, ManagementStatusIndicator } from '../../../shared/components/ManagementListCells';
import { ManagementAdaptiveMoreFilters, ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import {
  isStandardDictionaryTypeFamilyCompatible,
  standardDictionaryValueTypeLabels,
  useStandardDictionaries,
  type StandardDictionarySummary,
} from '../../standard';
import { useCurrentUser } from '../../system';
import {
  useCreateModelFieldTemplate,
  useDeleteModelFieldTemplate,
  useModelFieldTemplateCommand,
  useModelFieldTemplates,
  useUpdateModelFieldTemplate,
} from '../hooks/useDataModels';
import {
  dataModelFieldTypeLabels,
  geometryKindLabels,
  type CreateModelFieldTemplateRequest,
  type ModelFieldTemplate,
  type ModelFieldTemplateField,
  type ModelFieldTemplateFieldInput,
  type PlatformDataType,
} from '../model/dataModel';

interface TemplateFilters {
  keyword?: string;
  category?: string;
  enabled?: boolean;
}

interface TemplateDrawerProps {
  open: boolean;
  template: ModelFieldTemplate | null;
  onClose: () => void;
}

const fieldTypeOptions = (Object.entries(dataModelFieldTypeLabels) as [PlatformDataType, string][])
  .map(([value, label]) => ({ value, label }));

const escapeDsl = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

const buildSearch = (filters: TemplateFilters) => {
  const keyword = filters.keyword?.trim();
  const conditions = [
    keyword
      ? `(code:*"${escapeDsl(keyword)}"* OR name:*"${escapeDsl(keyword)}"*)`
      : undefined,
    filters.category?.trim() ? `category:*"${escapeDsl(filters.category.trim())}"*` : undefined,
    filters.enabled === undefined ? undefined : `enabled:"${filters.enabled}"`,
  ].filter((condition): condition is string => Boolean(condition));
  return conditions.length ? conditions.join(' AND ') : undefined;
};

const typeDescription = (field: ModelFieldTemplateField) => {
  if (field.fieldType === 'STRING') {
    return field.length ? `字符串(${field.length})` : '字符串(无上限)';
  }
  if (field.fieldType === 'DECIMAL') return `小数(${field.precision ?? '—'},${field.scale ?? 0})`;
  if (field.fieldType === 'GEOMETRY' && field.geometry) {
    return `${geometryKindLabels[field.geometry.kind]} · EPSG:${field.geometry.crs.code}`;
  }
  return dataModelFieldTypeLabels[field.fieldType];
};

const toFieldInput = (field: ModelFieldTemplateField): ModelFieldTemplateFieldInput => ({
  id: field.id,
  code: field.code,
  name: field.name,
  fieldType: field.fieldType,
  ...(field.length !== null ? { length: field.length } : {}),
  ...(field.precision !== null ? { precision: field.precision } : {}),
  ...(field.scale !== null ? { scale: field.scale } : {}),
  ...(field.geometry ? { geometry: field.geometry } : {}),
  nullable: field.nullable,
  primaryKey: field.primaryKey,
  sortOrder: field.sortOrder,
  ...(field.description ? { description: field.description } : {}),
  ...(field.standardDictionary ? { standardDictionaryId: field.standardDictionary.id } : {}),
});

const TemplateDrawer = ({ open, template, onClose }: TemplateDrawerProps) => {
  const [form] = Form.useForm<CreateModelFieldTemplateRequest>();
  const dirtyRef = useRef(false);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const createMutation = useCreateModelFieldTemplate();
  const updateMutation = useUpdateModelFieldTemplate();
  const currentUser = useCurrentUser();
  const canViewDictionaries = currentUser.data?.permissions.includes('standard.dictionary.view') ?? false;
  const dictionariesQuery = useStandardDictionaries(
    { page: 0, size: 500, sort: 'name,code' },
    open && canViewDictionaries,
  );
  const fieldValues = Form.useWatch('fields', form) ?? [];
  const dictionaryOptions = useMemo(() => {
    const dictionaries = [...(dictionariesQuery.data?.content ?? [])] as StandardDictionarySummary[];
    for (const field of template?.fields ?? []) {
      const dictionary = field.standardDictionary;
      if (dictionary && !dictionaries.some((candidate) => candidate.id === dictionary.id)) {
        dictionaries.push(dictionary);
      }
    }
    return dictionaries;
  }, [dictionariesQuery.data, template]);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue(template ? {
      code: template.code,
      name: template.name,
      category: template.category ?? undefined,
      description: template.description ?? undefined,
      sortOrder: template.sortOrder,
      fields: template.fields.map(toFieldInput),
    } : {
      sortOrder: 100,
      fields: [{
        code: '',
        name: '',
        fieldType: 'STRING',
        nullable: true,
        primaryKey: false,
        sortOrder: 10,
      }],
    });
    dirtyRef.current = false;
  }, [form, open, template]);

  const close = (confirmDirty = true) => {
    if (confirmDirty && dirtyRef.current) {
      modalApi.confirm({
        rootClassName: 'business-overlay business-modal-overlay',
        title: '放弃未保存修改？',
        content: '常用字段模板内容已修改，关闭后这些修改不会保留。',
        okText: '放弃修改',
        okButtonProps: { danger: true },
        cancelText: '继续编辑',
        onOk: onClose,
      });
      return;
    }
    onClose();
  };

  const changeType = (index: number, fieldType: PlatformDataType) => {
    const fields = [...(form.getFieldValue('fields') ?? [])];
    const field = fields[index];
    if (!field) return;
    fields[index] = {
      ...field,
      fieldType,
      length: fieldType === 'STRING' ? field.length : undefined,
      precision: fieldType === 'DECIMAL' ? field.precision ?? 18 : undefined,
      scale: fieldType === 'DECIMAL' ? field.scale ?? 2 : undefined,
      geometry: fieldType === 'GEOMETRY'
        ? field.geometry ?? {
          kind: 'POINT',
          crs: { authority: 'EPSG', code: 4326 },
          dimension: 'XY',
        }
        : undefined,
      primaryKey: fieldType === 'GEOMETRY' ? false : field.primaryKey,
    };
    form.setFieldValue('fields', fields);
  };

  const submit = async (values: CreateModelFieldTemplateRequest) => {
    const request: CreateModelFieldTemplateRequest = {
      code: values.code.trim(),
      name: values.name.trim(),
      category: values.category?.trim() || undefined,
      description: values.description?.trim() || undefined,
      sortOrder: values.sortOrder,
      fields: values.fields.map((field) => ({
        ...(field.id ? { id: field.id } : {}),
        code: field.code.trim(),
        name: field.name.trim(),
        fieldType: field.fieldType,
        ...(field.fieldType === 'STRING' && field.length ? { length: field.length } : {}),
        ...(field.fieldType === 'DECIMAL' ? {
          precision: field.precision,
          scale: field.scale ?? 0,
        } : {}),
        ...(field.fieldType === 'GEOMETRY' && field.geometry ? { geometry: field.geometry } : {}),
        nullable: field.primaryKey ? false : field.nullable,
        primaryKey: field.fieldType === 'GEOMETRY' ? false : field.primaryKey,
        sortOrder: field.sortOrder,
        description: field.description?.trim() || undefined,
        standardDictionaryId: field.standardDictionaryId || undefined,
      })),
    };
    try {
      if (template) {
        await updateMutation.mutateAsync({
          id: template.id,
          request: { ...request, expectedVersion: template.version },
        });
        messageApi.success('常用字段模板已保存');
      } else {
        await createMutation.mutateAsync(request);
        messageApi.success('常用字段模板已创建');
      }
      dirtyRef.current = false;
      close(false);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存常用字段模板失败');
    }
  };

  return (
    <>
      {messageContext}
      {modalContext}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        className="data-model-drawer field-template-drawer"
        title={(
          <div className="data-model-drawer-title">
            <span className="data-model-drawer-title-icon" aria-hidden="true"><AppstoreOutlined /></span>
            <span className="data-model-drawer-title-copy">
              <span>{template ? '修改常用字段模板' : '新建常用字段模板'}</span>
              <Typography.Text type="secondary">定义可复用的字段组合、类型约束与业务标准</Typography.Text>
            </span>
          </div>
        )}
        extra={<Tag className="data-model-drawer-header-tag">{template?.code ?? '待创建'}</Tag>}
        open={open}
        width={1040}
        destroyOnHidden
        onClose={() => close()}
        footer={(
          <div className="data-model-drawer-footer">
            <Badge
              status={template?.enabled === false ? 'default' : 'processing'}
              text={template ? `${template.enabled ? '启用' : '停用'} · v${template.version} · ${fieldValues.length} 个字段` : `${fieldValues.length} 个待创建字段`}
            />
            <Space>
              <Button onClick={() => close()}>取消</Button>
              <Button
                type="primary"
                loading={createMutation.isPending || updateMutation.isPending}
                onClick={() => form.submit()}
              >
                {template ? '保存修改' : '创建模板'}
              </Button>
            </Space>
          </div>
        )}
      >
        <Form<CreateModelFieldTemplateRequest>
          name="model-field-template-editor-form"
          autoComplete="off"
          form={form}
          layout="vertical"
          className="data-model-form field-template-form"
          onValuesChange={() => { dirtyRef.current = true; }}
          onFinish={(values) => void submit(values)}
        >
          <section className="data-model-form-section">
            <header className="data-model-form-section-header">
              <span className="data-model-form-section-icon" aria-hidden="true"><FileTextOutlined /></span>
              <span className="data-model-form-section-copy">
                <span className="data-model-form-section-title">模板信息</span>
                <Typography.Text type="secondary">设置模板身份、分类和适用场景</Typography.Text>
              </span>
            </header>
            <div className="data-model-form-section-body">
              <Row gutter={14}>
                <Col span={8} xs={24} md={8}>
              <Form.Item
                label="模板编码"
                name="code"
                rules={[
                  { required: true, whitespace: true, message: '请输入模板编码' },
                  { pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/, message: '编码以字母开头，只能包含字母、数字和下划线' },
                ]}
              >
                <Input name="model-field-template-code" autoComplete="off" placeholder="如：AUDIT_FIELDS" />
              </Form.Item>
            </Col>
                <Col span={8} xs={24} md={8}>
              <Form.Item label="模板名称" name="name" rules={[{ required: true, whitespace: true }]}>
                <Input name="model-field-template-name" autoComplete="off" placeholder="如：审计字段组" />
              </Form.Item>
            </Col>
                <Col span={8} xs={24} md={8}>
              <Form.Item label="分类" name="category" rules={[{ max: 100 }]}>
                <Input name="model-field-template-category" autoComplete="off" placeholder="可自由填写，如：系统字段" />
              </Form.Item>
            </Col>
                <Col span={6} xs={24} md={6}>
              <Form.Item label="排序" name="sortOrder" rules={[{ required: true }]}>
                <InputNumber min={0} max={9999} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
                <Col span={18} xs={24} md={18}>
              <Form.Item label="说明" name="description" rules={[{ max: 500 }]}>
                <Input name="model-field-template-description" autoComplete="off" placeholder="说明适用场景和使用约定" />
              </Form.Item>
            </Col>
              </Row>
            </div>
          </section>
          <Form.List
            name="fields"
            rules={[{
              validator: async (_rule, fields) => {
                if (!fields?.length) throw new Error('模板至少需要一个字段');
              },
            }]}
          >
            {(items, { add, remove }, { errors }) => (
              <section className="data-model-form-section field-template-fields-section">
                <header className="data-model-form-section-header">
                  <span className="data-model-form-section-icon" aria-hidden="true"><BarsOutlined /></span>
                  <span className="data-model-form-section-copy">
                    <span className="data-model-form-section-title-row">
                      <span className="data-model-form-section-title">模板字段</span>
                      <ContextHelp
                        ariaLabel="查看模板字段使用说明"
                        content="模板只在选用时复制字段快照；之后修改或删除模板都不会改变已有模型，也不会触发物理表操作。"
                        presentation="popover"
                      />
                    </span>
                    <Typography.Text type="secondary">按使用顺序维护字段类型、约束和数据标准</Typography.Text>
                  </span>
                  <Button
                    className="field-template-add-field"
                    icon={<PlusOutlined />}
                    onClick={() => {
                      const nextSortOrder = fieldValues.length
                        ? Math.max(...fieldValues.map((field) => field.sortOrder ?? 0)) + 10
                        : 10;
                      add({
                        code: '', name: '', fieldType: 'STRING', nullable: true,
                        primaryKey: false, sortOrder: nextSortOrder,
                      });
                    }}
                  >
                    添加字段
                  </Button>
                </header>
                <div className="data-model-form-section-body field-template-fields-body">
                  <Space direction="vertical" size={10} style={{ width: '100%' }}>
                    {items.map(({ key, name, ...restField }, index) => {
                  const selectedType = fieldValues[index]?.fieldType;
                  const selectedPrimaryKey = fieldValues[index]?.primaryKey;
                  const selectedDictionaryId = fieldValues[index]?.standardDictionaryId;
                  const options = dictionaryOptions.map((dictionary) => ({
                    value: dictionary.id,
                    label: `${dictionary.code} · ${dictionary.name}${dictionary.enabled ? '' : '（已停用）'}`,
                    disabled: (!dictionary.enabled && dictionary.id !== selectedDictionaryId)
                      || !isStandardDictionaryTypeFamilyCompatible(selectedType, dictionary.valueType),
                    title: `${standardDictionaryValueTypeLabels[dictionary.valueType]} · v${dictionary.version}`,
                  }));
                  return (
                    <Card
                      key={key}
                      className="field-template-field-card"
                      size="small"
                      title={(
                        <span className="field-template-field-card-title">
                          <span className="field-template-field-index">{index + 1}</span>
                          <span>字段定义</span>
                        </span>
                      )}
                      extra={(
                        <Tooltip title={items.length === 1 ? '模板至少保留一个字段' : '移除字段'}>
                          <span>
                            <Button
                              type="text"
                              danger
                              disabled={items.length === 1}
                              icon={<MinusCircleOutlined />}
                              aria-label={`移除模板字段${index + 1}`}
                              onClick={() => remove(name)}
                            />
                          </span>
                        </Tooltip>
                      )}
                    >
                      <Form.Item {...restField} name={[name, 'id']} hidden><Input /></Form.Item>
                      <Row gutter={12}>
                        <Col span={6}>
                          <Form.Item
                            {...restField}
                            label="字段编码"
                            name={[name, 'code']}
                            rules={[
                              { required: true, whitespace: true },
                              { pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/, message: '编码格式不合法' },
                            ]}
                          >
                            <Input name={`model-field-template-field-${index}-code`} autoComplete="off" placeholder="如：created_at" />
                          </Form.Item>
                        </Col>
                        <Col span={6}>
                          <Form.Item {...restField} label="字段名称" name={[name, 'name']} rules={[{ required: true, whitespace: true }]}>
                            <Input name={`model-field-template-field-${index}-name`} autoComplete="off" placeholder="如：创建时间" />
                          </Form.Item>
                        </Col>
                        <Col span={6}>
                          <Form.Item {...restField} label="字段类型" name={[name, 'fieldType']} rules={[{ required: true }]}>
                            <Select options={fieldTypeOptions} onChange={(value) => changeType(index, value)} />
                          </Form.Item>
                        </Col>
                        {selectedType === 'STRING' && (
                          <Col span={6}>
                            <Form.Item {...restField} label="长度（可选）" name={[name, 'length']}>
                              <InputNumber min={1} precision={0} style={{ width: '100%' }} />
                            </Form.Item>
                          </Col>
                        )}
                        {selectedType === 'DECIMAL' && (
                          <>
                            <Col span={3}>
                              <Form.Item {...restField} label="精度" name={[name, 'precision']} rules={[{ required: true }]}>
                                <InputNumber min={1} max={38} precision={0} style={{ width: '100%' }} />
                              </Form.Item>
                            </Col>
                            <Col span={3}>
                              <Form.Item {...restField} label="小数位" name={[name, 'scale']} rules={[{ required: true }]}>
                                <InputNumber min={0} max={38} precision={0} style={{ width: '100%' }} />
                              </Form.Item>
                            </Col>
                          </>
                        )}
                        {selectedType === 'GEOMETRY' && (
                          <>
                            <Col span={6}>
                              <Form.Item {...restField} label="几何类型" name={[name, 'geometry', 'kind']} rules={[{ required: true }]}>
                                <Select options={(Object.entries(geometryKindLabels)).map(([value, label]) => ({ value, label }))} />
                              </Form.Item>
                            </Col>
                            <Col span={3}>
                              <Form.Item {...restField} label="CRS" name={[name, 'geometry', 'crs', 'authority']}>
                                <Input disabled />
                              </Form.Item>
                            </Col>
                            <Col span={3}>
                              <Form.Item {...restField} label="EPSG" name={[name, 'geometry', 'crs', 'code']} rules={[{ required: true }]}>
                                <InputNumber min={1} precision={0} style={{ width: '100%' }} />
                              </Form.Item>
                            </Col>
                            <Form.Item {...restField} name={[name, 'geometry', 'dimension']} hidden><Input /></Form.Item>
                          </>
                        )}
                        <Col span={6}>
                          <Form.Item
                            {...restField}
                            label="关联码表"
                            name={[name, 'standardDictionaryId']}
                            extra={!canViewDictionaries ? '无查看码表权限，已有绑定保持不变' : undefined}
                          >
                            <Select
                              allowClear
                              showSearch
                              optionFilterProp="label"
                              disabled={!canViewDictionaries}
                              loading={dictionariesQuery.isFetching}
                              options={options}
                              placeholder="可选"
                            />
                          </Form.Item>
                        </Col>
                        <Col span={3}>
                          <Form.Item {...restField} label="允许为空" name={[name, 'nullable']} valuePropName="checked">
                            <Switch disabled={selectedPrimaryKey} />
                          </Form.Item>
                        </Col>
                        <Col span={3}>
                          <Form.Item {...restField} label="主键" name={[name, 'primaryKey']} valuePropName="checked">
                            <Switch
                              disabled={selectedType === 'GEOMETRY'}
                              onChange={(checked) => checked && form.setFieldValue(['fields', index, 'nullable'], false)}
                            />
                          </Form.Item>
                        </Col>
                        <Col span={3}>
                          <Form.Item {...restField} label="排序" name={[name, 'sortOrder']} rules={[{ required: true }]}>
                            <InputNumber min={0} max={9999} precision={0} style={{ width: '100%' }} />
                          </Form.Item>
                        </Col>
                        <Col span={15}>
                          <Form.Item {...restField} label="说明" name={[name, 'description']} rules={[{ max: 500 }]}>
                            <Input name={`model-field-template-field-${index}-description`} autoComplete="off" placeholder="可选" />
                          </Form.Item>
                        </Col>
                      </Row>
                    </Card>
                  );
                    })}
                    <Form.ErrorList errors={errors} />
                  </Space>
                </div>
              </section>
            )}
          </Form.List>
        </Form>
      </Drawer>
    </>
  );
};

export const ModelFieldTemplatePage = () => {
  const [filterForm] = Form.useForm<TemplateFilters>();
  const [advancedFilterForm] = Form.useForm<TemplateFilters>();
  const [advancedFilterOpen, setAdvancedFilterOpen] = useState(false);
  const [advancedFilters, setAdvancedFilters] = useState<TemplateFilters>({});
  const [filters, setFilters] = useState<TemplateFilters>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<ModelFieldTemplate | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const currentUser = useCurrentUser();
  const canUpdate = currentUser.data?.permissions.includes('model.update') ?? false;
  const request = useMemo(() => ({
    search: buildSearch(filters),
    page,
    size,
    sort: 'category,sortOrder,name,code',
  }), [filters, page, size]);
  const templatesQuery = useModelFieldTemplates(request);
  const advancedFilterCount = Number(advancedFilters.enabled !== undefined);
  const enableMutation = useModelFieldTemplateCommand('enable');
  const disableMutation = useModelFieldTemplateCommand('disable');
  const deleteMutation = useDeleteModelFieldTemplate();

  const toggleTemplate = async (template: ModelFieldTemplate) => {
    try {
      await (template.enabled ? disableMutation : enableMutation).mutateAsync(template.id);
      messageApi.success(template.enabled ? '模板已停用' : '模板已启用');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '模板状态操作失败');
    }
  };

  const removeTemplate = (template: ModelFieldTemplate) => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: '删除常用字段模板',
    content: `确认删除“${template.name}（${template.code}）”吗？已复制到模型的字段不会受影响。`,
    okText: '删除',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: async () => {
      try { await deleteMutation.mutateAsync(template.id); messageApi.success('模板已删除'); }
      catch (error) { messageApi.error(error instanceof ApiError ? error.message : '删除模板失败'); throw error; }
    },
  });

  const columns: TableProps<ModelFieldTemplate>['columns'] = [
    { title: '模板', dataIndex: 'name', width: 260, render: (value: string, template) => <ManagementListCell icon={<AppstoreOutlined />} iconTone="cyan" primary={value} secondary={<><ManagementCode value={template.code} /> {template.description || ''}</>} /> },
    {
      title: '分类 / 状态', width: 160,
      render: (_value: unknown, template) => <ManagementListCell primary={template.category || '未分类'} secondary={<ManagementStatusIndicator label={template.enabled ? '启用' : '停用'} tone={template.enabled ? 'success' : 'default'} />} />,
    },
    {
      title: '字段组成',
      key: 'fields',
      width: 360,
      render: (_value, template) => (
        <ManagementListCell
          primary={template.fieldCount === 1 ? '单字段' : `字段组 · ${template.fieldCount}`}
          secondary={<Tooltip title={template.fields.map((field) => `${field.code} · ${field.name} · ${typeDescription(field)}`).join('\n')}><span className="management-field-preview"><ManagementCode value={template.fields.slice(0, 4).map((field) => field.code).join(', ') || '—'} />{template.fields.length > 4 && <span> +{template.fields.length - 4}</span>}</span></Tooltip>}
        />
      ),
    },
    { title: '版本 / 排序', width: 110, render: (_value: unknown, template) => <ManagementListCell primary={`v${template.version}`} secondary={`排序 ${template.sortOrder}`} /> },
    { title: '更新时间', dataIndex: 'updatedAt', width: 160, render: (value: string) => <ManagementDateTime value={value} /> },
    {
      title: '操作',
      key: 'actions',
      width: 112,
      render: (_value, template) => canUpdate ? (
        <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">
            <Tooltip title="修改"><Button type="text" icon={<EditOutlined />} aria-label={`修改${template.name}`} onClick={() => { setEditingTemplate(template); setDrawerOpen(true); }} /></Tooltip>
            <Tooltip title={template.enabled ? '停用' : '启用'}><Button type="text" icon={template.enabled ? <PauseCircleOutlined /> : <PlayCircleOutlined />} aria-label={`${template.enabled ? '停用' : '启用'}${template.name}`} loading={(template.enabled ? disableMutation : enableMutation).isPending && (template.enabled ? disableMutation : enableMutation).variables === template.id} onClick={() => void toggleTemplate(template)} /></Tooltip>
          </div>
          <Dropdown menu={{ items: [
            { key: 'edit', icon: <EditOutlined />, label: '修改', onClick: () => { setEditingTemplate(template); setDrawerOpen(true); } },
            { key: 'lifecycle', icon: template.enabled ? <PauseCircleOutlined /> : <PlayCircleOutlined />, label: template.enabled ? '停用' : '启用', onClick: () => void toggleTemplate(template) },
            { type: 'divider' },
            { key: 'delete', icon: <DeleteOutlined />, label: '删除', danger: true, onClick: () => removeTemplate(template) },
          ] satisfies MenuProps['items'] }}><Tooltip title="更多操作"><Button className="management-row-actions-more" type="text" icon={<MoreOutlined />} aria-label={`${template.name}的更多操作`} /></Tooltip></Dropdown>
        </div>
      ) : '—',
    },
  ];
  const applyDirect = (values: TemplateFilters) => {
    const enabled = advancedFilterForm.getFieldValue('enabled');
    setAdvancedFilters({ enabled });
    setFilters({ keyword: values.keyword, category: values.category, enabled });
    setPage(0);
  };
  const confirmAdvanced = () => { setAdvancedFilters({ enabled: advancedFilterForm.getFieldValue('enabled') }); setAdvancedFilterOpen(false); };
  const clearAdvanced = () => advancedFilterForm.setFieldValue('enabled', undefined);
  const reset = () => { filterForm.resetFields(); advancedFilterForm.resetFields(); advancedFilterForm.setFieldValue('enabled', undefined); setAdvancedFilters({}); setAdvancedFilterOpen(false); setFilters({}); setPage(0); };

  return (
    <>
      {messageContext}
      {modalContext}
      <section className="management-workbench">
        <div className="management-filter-strip">
          <Form<TemplateFilters>
            autoComplete="off"
            form={filterForm}
            layout="inline"
            onFinish={applyDirect}
          >
            <Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索模板名称或编码" /></Form.Item>
            <Form.Item name="category"><Input allowClear placeholder="搜索分类" /></Form.Item>
          </Form>
            <ManagementAdaptiveMoreFilters
              count={advancedFilterCount}
              open={advancedFilterOpen}
              onOpenChange={(open) => {
                setAdvancedFilterOpen(open);
                if (open) {
                  advancedFilterForm.resetFields();
                  advancedFilterForm.setFieldValue('enabled', advancedFilters.enabled);
                }
              }}
              onClear={clearAdvanced}
              onCancel={() => {
                advancedFilterForm.setFieldValue('enabled', advancedFilters.enabled);
                setAdvancedFilterOpen(false);
              }}
              onConfirm={confirmAdvanced}
            >
              <Form<TemplateFilters> form={advancedFilterForm} layout="vertical" autoComplete="off" initialValues={advancedFilters}><Form.Item name="enabled" label="状态"><Select allowClear placeholder="全部状态" options={[{ value: true, label: '启用' }, { value: false, label: '停用' }]} className="advanced-filter-select" /></Form.Item></Form>
            </ManagementAdaptiveMoreFilters>
          <ManagementFilterActions form={filterForm} appliedFilters={filters} additionalActive={advancedFilterCount > 0} loading={templatesQuery.isFetching} onReset={reset} />
        </div>
        <div className="management-results-surface">
          <div className="management-result-toolbar">
          <div className="management-result-title">常用字段模板 <span className="management-result-count">共 {templatesQuery.data?.totalElements ?? 0} 项</span></div>
          <Space size={4} className="management-result-actions">
            <Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新字段模板" onClick={() => void templatesQuery.refetch()} /></Tooltip>
            {canUpdate && (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingTemplate(null); setDrawerOpen(true); }}>
                新建
              </Button>
            )}
          </Space>
          </div>
          {templatesQuery.isError && (
            <Alert
              showIcon
              type="error"
              title="常用字段模板加载失败"
              action={<Button onClick={() => void templatesQuery.refetch()}>重试</Button>}
              className="management-inline-alert"
            />
          )}
          <Table<ModelFieldTemplate>
          size="small"
          className="management-table"
          rowKey="id"
          columns={columns}
          dataSource={templatesQuery.data?.content ?? []}
          loading={templatesQuery.isFetching}
          scroll={{ y: '100%' }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: templatesQuery.data?.totalElements ?? 0,
            showSizeChanger: true,
            hideOnSinglePage: false,
            showTotal: (total) => `共 ${total} 项`,
          }}
          onChange={(pagination) => {
            setPage((pagination.current ?? 1) - 1);
            setSize(pagination.pageSize ?? 20);
          }}
          />
        </div>
      </section>
      <TemplateDrawer
        open={drawerOpen}
        template={editingTemplate}
        onClose={() => { setDrawerOpen(false); setEditingTemplate(null); }}
      />
    </>
  );
};
