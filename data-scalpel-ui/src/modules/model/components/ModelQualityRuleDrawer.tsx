import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Col,
  Drawer,
  Form,
  Input,
  InputNumber,
  Radio,
  Row,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useDataModel, useDataModels } from '../hooks/useDataModels';
import type { DataModelField, PlatformDataType } from '../model/dataModel';
import {
  formatPatternPresetLabels,
  modelQualityRuleSeverityLabels,
  modelQualityRuleTypeLabels,
  qualityConditionOperatorLabels,
  qualityFieldComparisonOperatorLabels,
  type CreateModelQualityRuleRequest,
  type FormatPatternKind,
  type FormatPatternPreset,
  type ModelQualityRule,
  type ModelQualityRuleDefinition,
  type ModelQualityRuleSeverity,
  type ModelQualityRuleType,
  type QualityConditionOperator,
  type QualityFieldComparisonOperator,
  type ViolationMetric,
} from '../model/modelQualityRule';
import { buildDataModelSearch } from '../model/dataModelSearch';

interface ModelQualityRuleDrawerProps {
  open: boolean;
  rule: ModelQualityRule | null;
  fields: DataModelField[];
  submitting: boolean;
  onClose: () => void;
  onSubmit: (request: CreateModelQualityRuleRequest) => void;
}

interface ReferenceMappingValue {
  sourceFieldId?: string;
  targetFieldId?: string;
}

interface RuleFormValues {
  name: string;
  description?: string;
  type: ModelQualityRuleType;
  severity: ModelQualityRuleSeverity;
  enabled: boolean;
  fieldId?: string;
  fieldIds?: string[];
  metric: ViolationMetric;
  toleranceValue: number;
  minimum?: string;
  maximum?: string;
  minimumInclusive: boolean;
  maximumInclusive: boolean;
  minimumLength?: number;
  maximumLength?: number;
  minimumRowCount?: number;
  maximumDelayMinutes?: number;
  patternKind?: FormatPatternKind;
  preset?: FormatPatternPreset;
  regex?: string;
  targetFieldId?: string;
  conditionFieldId?: string;
  conditionOperator?: QualityConditionOperator;
  conditionValue?: string;
  conditionValues?: string[];
  leftFieldId?: string;
  comparisonOperator?: QualityFieldComparisonOperator;
  rightFieldId?: string;
  targetModelId?: string;
  mappings?: ReferenceMappingValue[];
}

const ruleTypeOptions = Object.entries(modelQualityRuleTypeLabels).map(([value, label]) => ({ value, label }));
const severityOptions = Object.entries(modelQualityRuleSeverityLabels).map(([value, label]) => ({ value, label }));
const patternPresetOptions = Object.entries(formatPatternPresetLabels).map(([value, label]) => ({ value, label }));
const conditionOperatorOptions = Object.entries(qualityConditionOperatorLabels).map(([value, label]) => ({ value, label }));
const comparisonOperatorOptions = Object.entries(qualityFieldComparisonOperatorLabels).map(([value, label]) => ({ value, label }));
const numericTypes = new Set<PlatformDataType>(['BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL']);
const rangeTypes = new Set<PlatformDataType>([...numericTypes, 'DATE', 'TIMESTAMP', 'TIMESTAMP_NTZ']);
const timeTypes = new Set<PlatformDataType>(['DATE', 'TIMESTAMP', 'TIMESTAMP_NTZ']);
const scalarTypes = new Set<PlatformDataType>([
  'BOOLEAN', 'BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL',
  'STRING', 'DATE', 'TIMESTAMP', 'TIMESTAMP_NTZ',
]);
const emptyConditionOperators = new Set<QualityConditionOperator>([
  'IS_NULL', 'IS_NOT_NULL', 'IS_EMPTY', 'IS_NOT_EMPTY',
]);

const fieldLabel = (field: Pick<DataModelField, 'name' | 'code'>) => `${field.name}（${field.code}）`;
const fieldOptions = (fields: DataModelField[]) => fields.map((field) => ({
  value: field.id,
  label: fieldLabel(field),
}));
const compatibleReferenceTypes = (source: PlatformDataType, target: PlatformDataType) => (
  source === target || (numericTypes.has(source) && numericTypes.has(target))
);

const definitionValues = (definition: ModelQualityRuleDefinition): Partial<RuleFormValues> => {
  const tolerance = 'tolerance' in definition ? definition.tolerance : undefined;
  const common = {
    type: definition.type,
    fieldId: 'fieldId' in definition ? definition.fieldId : undefined,
    fieldIds: 'fieldIds' in definition ? definition.fieldIds : undefined,
    metric: tolerance?.metric,
    toleranceValue: tolerance?.value,
    minimum: definition.type === 'VALUE_RANGE' ? definition.minimum : undefined,
    maximum: definition.type === 'VALUE_RANGE' ? definition.maximum : undefined,
    minimumInclusive: definition.type === 'VALUE_RANGE' ? definition.minimumInclusive : true,
    maximumInclusive: definition.type === 'VALUE_RANGE' ? definition.maximumInclusive : true,
    minimumLength: definition.type === 'STRING_LENGTH' ? definition.minimumLength : undefined,
    maximumLength: definition.type === 'STRING_LENGTH' ? definition.maximumLength : undefined,
    minimumRowCount: definition.type === 'ROW_COUNT' ? definition.minimumRowCount : undefined,
    maximumDelayMinutes: definition.type === 'FRESHNESS' ? definition.maximumDelayMinutes : undefined,
  };
  if (definition.type === 'FORMAT_PATTERN') return {
    ...common,
    patternKind: definition.patternKind,
    preset: definition.preset,
    regex: definition.regex,
  };
  if (definition.type === 'CONDITIONAL_NOT_NULL') return {
    ...common,
    targetFieldId: definition.targetFieldId,
    conditionFieldId: definition.condition.fieldId,
    conditionOperator: definition.condition.operator,
    conditionValue: definition.condition.values[0],
    conditionValues: definition.condition.values,
  };
  if (definition.type === 'FIELD_COMPARISON') return {
    ...common,
    leftFieldId: definition.leftFieldId,
    comparisonOperator: definition.operator,
    rightFieldId: definition.rightFieldId,
  };
  if (definition.type === 'REFERENCE_EXISTS') return {
    ...common,
    targetModelId: definition.targetModelId,
    mappings: definition.mappings,
  };
  return common;
};

const toDefinition = (values: RuleFormValues): ModelQualityRuleDefinition => {
  const tolerance = { metric: values.metric, value: values.toleranceValue };
  switch (values.type) {
    case 'NOT_NULL': return { type: values.type, fieldId: values.fieldId as string, tolerance };
    case 'UNIQUE': return { type: values.type, fieldIds: values.fieldIds ?? [], tolerance };
    case 'VALUE_RANGE': return {
      type: values.type,
      fieldId: values.fieldId as string,
      minimum: values.minimum?.trim() || undefined,
      maximum: values.maximum?.trim() || undefined,
      minimumInclusive: values.minimumInclusive,
      maximumInclusive: values.maximumInclusive,
      tolerance,
    };
    case 'STRING_LENGTH': return {
      type: values.type,
      fieldId: values.fieldId as string,
      minimumLength: values.minimumLength,
      maximumLength: values.maximumLength,
      tolerance,
    };
    case 'DICTIONARY_MEMBERSHIP': return { type: values.type, fieldId: values.fieldId as string, tolerance };
    case 'ROW_COUNT': return { type: values.type, minimumRowCount: values.minimumRowCount as number };
    case 'FRESHNESS': return {
      type: values.type,
      fieldId: values.fieldId as string,
      maximumDelayMinutes: values.maximumDelayMinutes as number,
    };
    case 'GEOMETRY_VALID': return { type: values.type, fieldId: values.fieldId as string, tolerance };
    case 'GEOMETRY_NON_EMPTY': return { type: values.type, fieldId: values.fieldId as string, tolerance };
    case 'FORMAT_PATTERN': return {
      type: values.type,
      fieldId: values.fieldId as string,
      patternKind: values.patternKind as FormatPatternKind,
      preset: values.patternKind === 'PRESET' ? values.preset : undefined,
      regex: values.patternKind === 'REGEX' ? values.regex : undefined,
      tolerance,
    };
    case 'CONDITIONAL_NOT_NULL': return {
      type: values.type,
      targetFieldId: values.targetFieldId as string,
      condition: {
        fieldId: values.conditionFieldId as string,
        operator: values.conditionOperator as QualityConditionOperator,
        values: values.conditionOperator === 'EQ' || values.conditionOperator === 'NE'
          ? [values.conditionValue ?? '']
          : values.conditionOperator === 'IN' || values.conditionOperator === 'NOT_IN'
            ? values.conditionValues ?? []
            : [],
      },
      tolerance,
    };
    case 'FIELD_COMPARISON': return {
      type: values.type,
      leftFieldId: values.leftFieldId as string,
      operator: values.comparisonOperator as QualityFieldComparisonOperator,
      rightFieldId: values.rightFieldId as string,
      tolerance,
    };
    case 'REFERENCE_EXISTS': return {
      type: values.type,
      targetModelId: values.targetModelId as string,
      mappings: (values.mappings ?? []).map((mapping) => ({
        sourceFieldId: mapping.sourceFieldId as string,
        targetFieldId: mapping.targetFieldId as string,
      })),
      tolerance,
    };
  }
};

interface ReferenceMappingRowProps {
  index: number;
  fields: DataModelField[];
  targetFields: DataModelField[];
  remove: (index: number) => void;
}

const ReferenceMappingRow = ({ index, fields, targetFields, remove }: ReferenceMappingRowProps) => {
  const form = Form.useFormInstance<RuleFormValues>();
  const sourceFieldId = Form.useWatch(['mappings', index, 'sourceFieldId']) as string | undefined;
  const sourceField = fields.find((field) => field.id === sourceFieldId);
  const compatibleTargets = targetFields
    .filter((field) => scalarTypes.has(field.fieldType))
    .filter((field) => !sourceField || compatibleReferenceTypes(sourceField.fieldType, field.fieldType))
    .sort((left, right) => Number(right.primaryKey) - Number(left.primaryKey) || left.sortOrder - right.sortOrder);
  return (
    <Row gutter={8} align="middle">
      <Col span={10}>
        <Form.Item
          name={[index, 'sourceFieldId']}
          rules={[{ required: true, message: '请选择源字段' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            options={fieldOptions(fields.filter((field) => scalarTypes.has(field.fieldType)))}
            placeholder="源字段"
            onChange={() => form.setFieldValue(['mappings', index, 'targetFieldId'], undefined)}
          />
        </Form.Item>
      </Col>
      <Col span={2}><Typography.Text type="secondary">→</Typography.Text></Col>
      <Col span={10}>
        <Form.Item
          name={[index, 'targetFieldId']}
          rules={[{ required: true, message: '请选择目标字段' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            options={compatibleTargets.map((field) => ({
              value: field.id,
              label: fieldLabel(field),
            }))}
            placeholder="目标字段"
          />
        </Form.Item>
      </Col>
      <Col span={2}>
        <Button type="text" danger icon={<DeleteOutlined />} aria-label={`删除第 ${index + 1} 组字段映射`} onClick={() => remove(index)} />
      </Col>
    </Row>
  );
};

export const ModelQualityRuleDrawer = ({
  open,
  rule,
  fields,
  submitting,
  onClose,
  onSubmit,
}: ModelQualityRuleDrawerProps) => {
  const [form] = Form.useForm<RuleFormValues>();
  const [modelKeyword, setModelKeyword] = useState('');
  const type = Form.useWatch('type', form) ?? rule?.ruleType ?? 'NOT_NULL';
  const metric = Form.useWatch('metric', form) ?? 'COUNT';
  const patternKind = Form.useWatch('patternKind', form) ?? 'PRESET';
  const conditionOperator = Form.useWatch('conditionOperator', form) ?? 'EQ';
  const conditionFieldId = Form.useWatch('conditionFieldId', form);
  const leftFieldId = Form.useWatch('leftFieldId', form);
  const targetModelId = Form.useWatch('targetModelId', form);
  const modelsQuery = useDataModels({
    search: buildDataModelSearch({ keyword: modelKeyword }),
    page: 0,
    size: 50,
    sort: 'name,code',
  }, open && type === 'REFERENCE_EXISTS');
  const targetModelQuery = useDataModel(targetModelId, open && type === 'REFERENCE_EXISTS' && Boolean(targetModelId));

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue(rule ? {
      name: rule.name,
      description: rule.description ?? undefined,
      severity: rule.severity,
      enabled: rule.enabled,
      ...definitionValues(rule.definition),
    } : {
      type: 'NOT_NULL',
      severity: 'MAJOR',
      enabled: true,
      metric: 'COUNT',
      toleranceValue: 0,
      minimumInclusive: true,
      maximumInclusive: true,
      patternKind: 'PRESET',
      conditionOperator: 'EQ',
      comparisonOperator: 'LE',
      mappings: [{}],
    });
  }, [form, open, rule]);

  const compatibleFields = useMemo(() => fields.filter((field) => {
    if (type === 'UNIQUE') return scalarTypes.has(field.fieldType);
    if (type === 'VALUE_RANGE') return rangeTypes.has(field.fieldType);
    if (type === 'STRING_LENGTH' || type === 'FORMAT_PATTERN') return field.fieldType === 'STRING';
    if (type === 'DICTIONARY_MEMBERSHIP') return Boolean(field.standardDictionary);
    if (type === 'FRESHNESS') return timeTypes.has(field.fieldType);
    if (type === 'GEOMETRY_VALID' || type === 'GEOMETRY_NON_EMPTY') return field.fieldType === 'GEOMETRY';
    return true;
  }), [fields, type]);
  const conditionField = fields.find((field) => field.id === conditionFieldId);
  const availableConditionOperators = conditionOperatorOptions.filter(({ value }) => (
    conditionField?.fieldType === 'STRING' || (value !== 'IS_EMPTY' && value !== 'IS_NOT_EMPTY')
  ));
  const leftField = fields.find((field) => field.id === leftFieldId);
  const rightFields = fields.filter((field) => (
    scalarTypes.has(field.fieldType)
    && field.id !== leftFieldId
    && (!leftField || compatibleReferenceTypes(leftField.fieldType, field.fieldType))
  ));
  const availableComparisonOperators = comparisonOperatorOptions.filter(({ value }) => (
    !leftField
    || (leftField.fieldType !== 'STRING' && leftField.fieldType !== 'BOOLEAN')
    || value === 'EQ'
    || value === 'NE'
  ));
  const targetFields = targetModelQuery.data?.fields ?? [];
  const queriedModels = modelsQuery.data?.content ?? [];
  const selectedModel = targetModelQuery.data?.model;
  const modelOptions = [
    ...(selectedModel && !queriedModels.some((model) => model.id === selectedModel.id) ? [selectedModel] : []),
    ...queriedModels,
  ].map((model) => ({
    value: model.id,
    label: `${model.name}（${model.code}）`,
  }));
  if (targetModelId && !modelOptions.some((option) => option.value === targetModelId)) {
    modelOptions.push({ value: targetModelId, label: `不可用模型（${targetModelId}）` });
  }
  const hasTolerance = !['ROW_COUNT', 'FRESHNESS'].includes(type);
  const usesDefaultField = ![
    'ROW_COUNT', 'CONDITIONAL_NOT_NULL', 'FIELD_COMPARISON', 'REFERENCE_EXISTS',
  ].includes(type);

  const changeType = () => {
    form.setFieldsValue({
      fieldId: undefined,
      fieldIds: undefined,
      targetFieldId: undefined,
      conditionFieldId: undefined,
      conditionOperator: 'EQ',
      conditionValue: undefined,
      conditionValues: undefined,
      leftFieldId: undefined,
      comparisonOperator: 'LE',
      rightFieldId: undefined,
      targetModelId: undefined,
      mappings: [{}],
      patternKind: 'PRESET',
      preset: undefined,
      regex: undefined,
    });
  };
  const submit = async () => {
    const values = await form.validateFields();
    onSubmit({
      name: values.name.trim(),
      description: values.description?.trim() || undefined,
      severity: values.severity,
      enabled: rule ? rule.enabled : values.enabled,
      definition: toDefinition(values),
    });
  };

  return (
    <Drawer
      rootClassName="business-overlay business-drawer-overlay"
      title={rule ? '修改质量规则' : '新增质量规则'}
      width={640}
      open={open}
      destroyOnHidden
      afterOpenChange={(visible) => { if (!visible) setModelKeyword(''); }}
      onClose={onClose}
      footer={<Space style={{ width: '100%', justifyContent: 'flex-end' }}><Button onClick={onClose}>取消</Button><Button type="primary" loading={submitting} onClick={() => void submit()}>保存</Button></Space>}
    >
      <Form<RuleFormValues> form={form} layout="vertical" autoComplete="off">
        <Row gutter={12}>
          <Col span={12}>
            <Form.Item name="type" label="规则类型" rules={[{ required: true }]}>
              <Select disabled={Boolean(rule)} options={ruleTypeOptions} onChange={changeType} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="severity" label="严重程度" rules={[{ required: true }]}><Select options={severityOptions} /></Form.Item>
          </Col>
        </Row>
        <Form.Item name="name" label="规则名称" rules={[{ required: true, whitespace: true, max: 100 }]}><Input /></Form.Item>

        {type === 'UNIQUE' ? (
          <Form.Item name="fieldIds" label="检查字段" rules={[{ required: true, type: 'array', min: 1, max: 16 }]}>
            <Select mode="multiple" options={fieldOptions(compatibleFields)} placeholder="选择 1～16 个字段" />
          </Form.Item>
        ) : usesDefaultField && (
          <Form.Item name="fieldId" label="检查字段" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" options={fieldOptions(compatibleFields)} placeholder="请选择字段" />
          </Form.Item>
        )}

        {type === 'FORMAT_PATTERN' && (
          <>
            <Form.Item name="patternKind" label="格式来源" rules={[{ required: true }]}>
              <Radio.Group options={[{ value: 'PRESET', label: '预置格式' }, { value: 'REGEX', label: '自定义正则' }]} />
            </Form.Item>
            {patternKind === 'PRESET' ? (
              <Form.Item name="preset" label="预置格式" rules={[{ required: true }]}><Select options={patternPresetOptions} /></Form.Item>
            ) : (
              <Form.Item name="regex" label="Java 正则表达式" rules={[{ required: true, max: 500 }]}>
                <Input.TextArea rows={3} placeholder="按完整字段值匹配，可使用内联修饰符" />
              </Form.Item>
            )}
            <Alert showIcon type="info" title="空值会跳过格式检查；如需限制空值，请同时配置非空规则。" />
          </>
        )}

        {type === 'CONDITIONAL_NOT_NULL' && (
          <>
            <Form.Item name="targetFieldId" label="必填字段" rules={[{ required: true }]}>
              <Select showSearch optionFilterProp="label" options={fieldOptions(fields)} />
            </Form.Item>
            <Row gutter={12}>
              <Col span={12}>
                <Form.Item name="conditionFieldId" label="条件字段" rules={[{ required: true }]}>
                  <Select
                    showSearch
                    optionFilterProp="label"
                    options={fieldOptions(fields.filter((field) => scalarTypes.has(field.fieldType)))}
                    onChange={() => form.setFieldsValue({ conditionOperator: 'EQ', conditionValue: undefined, conditionValues: undefined })}
                  />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="conditionOperator" label="条件操作符" rules={[{ required: true }]}>
                  <Select options={availableConditionOperators} onChange={() => form.setFieldsValue({ conditionValue: undefined, conditionValues: undefined })} />
                </Form.Item>
              </Col>
            </Row>
            {(conditionOperator === 'EQ' || conditionOperator === 'NE') && (
              <Form.Item name="conditionValue" label="条件值" rules={[{ required: true, message: '请输入条件值' }]}><Input /></Form.Item>
            )}
            {(conditionOperator === 'IN' || conditionOperator === 'NOT_IN') && (
              <Form.Item name="conditionValues" label="条件值" rules={[{ required: true, type: 'array', min: 1, max: 100 }]}>
                <Select mode="tags" tokenSeparators={[',', '，']} placeholder="输入后按回车，可填写 1～100 个值" />
              </Form.Item>
            )}
            {emptyConditionOperators.has(conditionOperator) && (
              <Alert showIcon type="info" title="当前操作符不需要填写条件值。条件成立时，必填字段为数据库 NULL 的记录将被判定为异常。" />
            )}
          </>
        )}

        {type === 'FIELD_COMPARISON' && (
          <Row gutter={12}>
            <Col span={9}>
              <Form.Item name="leftFieldId" label="左字段" rules={[{ required: true }]}>
                <Select
                  showSearch
                  optionFilterProp="label"
                  options={fieldOptions(fields.filter((field) => scalarTypes.has(field.fieldType)))}
                  onChange={(value) => {
                    const selected = fields.find((field) => field.id === value);
                    form.setFieldsValue({
                      comparisonOperator: selected?.fieldType === 'STRING' || selected?.fieldType === 'BOOLEAN' ? 'EQ' : 'LE',
                      rightFieldId: undefined,
                    });
                  }}
                />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="comparisonOperator" label="比较符" rules={[{ required: true }]}><Select options={availableComparisonOperators} /></Form.Item>
            </Col>
            <Col span={9}>
              <Form.Item name="rightFieldId" label="右字段" rules={[{ required: true }]}>
                <Select showSearch optionFilterProp="label" options={fieldOptions(rightFields)} />
              </Form.Item>
            </Col>
          </Row>
        )}

        {type === 'REFERENCE_EXISTS' && (
          <>
            <Form.Item name="targetModelId" label="引用目标模型" rules={[{ required: true }]}>
              <Select
                showSearch
                filterOption={false}
                loading={modelsQuery.isFetching || targetModelQuery.isFetching}
                options={modelOptions}
                onSearch={(value) => setModelKeyword(value.trim())}
                onChange={() => form.setFieldsValue({ mappings: [{}] })}
                placeholder="按模型名称或编码搜索"
              />
            </Form.Item>
            {targetModelQuery.isError && <Alert showIcon type="warning" title="目标模型不可用，请重新选择目标模型。" />}
            <Form.List name="mappings" rules={[{
              validator: async (_, mappings: ReferenceMappingValue[] | undefined) => {
                if (!mappings?.length || mappings.length > 16) throw new Error('请配置 1～16 组字段映射');
              },
            }]}>
              {(mappingFields, { add, remove }, { errors }) => (
                <div className="quality-reference-mappings">
                  <div className="quality-reference-mappings-header">
                    <Typography.Text strong>字段映射</Typography.Text>
                    <Button size="small" icon={<PlusOutlined />} disabled={mappingFields.length >= 16} onClick={() => add()}>添加映射</Button>
                  </div>
                  {mappingFields.map(({ key, name }) => (
                    <ReferenceMappingRow key={key} index={name} fields={fields} targetFields={targetFields} remove={remove} />
                  ))}
                  <Form.ErrorList errors={errors} />
                  {selectedModel && <Tag>{selectedModel.status === 'PUBLISHED' ? '已发布' : selectedModel.status === 'DRAFT' ? '草稿' : '已停用'}</Tag>}
                </div>
              )}
            </Form.List>
            <Alert showIcon type="info" title="任一源字段为空时整行跳过引用检查；目标字段优先展示主键字段。" />
          </>
        )}

        {type === 'VALUE_RANGE' && (
          <Row gutter={12}>
            <Col span={12}><Form.Item name="minimum" label="最小值"><Input placeholder="可不填" /></Form.Item></Col>
            <Col span={12}><Form.Item name="maximum" label="最大值"><Input placeholder="可不填" /></Form.Item></Col>
            <Col span={12}><Form.Item name="minimumInclusive" label="包含最小值" valuePropName="checked"><Switch /></Form.Item></Col>
            <Col span={12}><Form.Item name="maximumInclusive" label="包含最大值" valuePropName="checked"><Switch /></Form.Item></Col>
          </Row>
        )}
        {type === 'STRING_LENGTH' && (
          <Row gutter={12}>
            <Col span={12}><Form.Item name="minimumLength" label="最小长度"><InputNumber min={0} precision={0} style={{ width: '100%' }} /></Form.Item></Col>
            <Col span={12}><Form.Item name="maximumLength" label="最大长度"><InputNumber min={0} precision={0} style={{ width: '100%' }} /></Form.Item></Col>
          </Row>
        )}
        {type === 'ROW_COUNT' && (
          <Form.Item name="minimumRowCount" label="最小行数" rules={[{ required: true }]}><InputNumber min={1} precision={0} style={{ width: '100%' }} /></Form.Item>
        )}
        {type === 'FRESHNESS' && (
          <Form.Item name="maximumDelayMinutes" label="最大延迟（分钟）" rules={[{ required: true }]}><InputNumber min={1} precision={0} style={{ width: '100%' }} /></Form.Item>
        )}
        {hasTolerance && (
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="metric" label="异常容忍口径" rules={[{ required: true }]}>
                <Radio.Group options={[{ value: 'COUNT', label: '条数' }, { value: 'PERCENT', label: '比例' }]} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="toleranceValue" label={metric === 'COUNT' ? '允许异常条数' : '允许异常比例（%）'} rules={[{ required: true }]}>
                <InputNumber min={0} max={metric === 'PERCENT' ? 100 : undefined} precision={metric === 'COUNT' ? 0 : 4} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
        )}
        {!rule && <Form.Item name="enabled" label="保存后启用" valuePropName="checked"><Switch /></Form.Item>}
        <Form.Item name="description" label="规则说明" rules={[{ max: 500 }]}><Input.TextArea rows={3} /></Form.Item>
      </Form>
    </Drawer>
  );
};
