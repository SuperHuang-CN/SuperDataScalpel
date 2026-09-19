import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import { ClockCircleOutlined, DeleteOutlined, PlusOutlined, SettingOutlined } from '@ant-design/icons';
import { Button, Card, Form, Input, Modal, Select, Space, Tag, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import {
  CanvasNodeType,
  type JoinCondition,
  type JoinOutputColumn,
  type SpatialJoinCondition,
  type SpatialJoinConfiguration,
  type SpatialJoinOneToOneOptions,
  type SpatialJoinDistanceOutput,
  type SpatialJoinSpatialNearCondition,
  type SpatialJoinTemporalCondition,
  type SpatialPredicate,
} from '../../canvasTypes';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { JoinOutputColumnsEditor } from '../../components/JoinOutputColumnsEditor';
import { suggestJoinOutputColumns } from '../../components/joinOutputColumns';
import type {
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from '../nodeSpec';
import { OneToOneOptionsModal } from './OneToOneOptionsModal';
import {
  createSpatialJoinOneToOneOptions,
  spatialJoinOneToOneSummary,
} from './oneToOneOptions';
import { TemporalConditionModal } from './TemporalConditionModal';
import { spatialJoinTemporalSummary } from './temporalCondition';
import { SpatialNearConditionModal } from './SpatialNearConditionModal';
import { DistanceOutputModal } from './DistanceOutputModal';
import {
  spatialJoinDistanceOutputSummary,
  spatialJoinNearSummary,
} from './spatialNear';

interface SpatialJoinFormValues {
  leftTableName: string;
  rightTableName: string;
  outputTableName: string;
  joinType: 'INNER' | 'LEFT';
  conditions: SpatialJoinCondition[];
  attributeConditions?: JoinCondition[] | null;
  outputColumns?: JoinOutputColumn[] | null;
  joinOperation?: 'JOIN_ONE_TO_MANY' | 'JOIN_ONE_TO_ONE' | null;
  oneToOne?: SpatialJoinOneToOneOptions | null;
  temporalCondition?: SpatialJoinTemporalCondition | null;
  spatialNear?: SpatialJoinSpatialNearCondition | null;
  distanceOutput?: SpatialJoinDistanceOutput | null;
}

const predicates: readonly { value: SpatialPredicate; label: string }[] = [
  { value: 'INTERSECTS', label: 'INTERSECTS · 相交' },
  { value: 'CONTAINS', label: 'CONTAINS · 左侧包含右侧' },
  { value: 'WITHIN', label: 'WITHIN · 左侧位于右侧内' },
  { value: 'COVERS', label: 'COVERS · 左侧覆盖右侧' },
  { value: 'COVERED_BY', label: 'COVERED_BY · 左侧被右侧覆盖' },
  { value: 'TOUCHES', label: 'TOUCHES · 边界接触' },
  { value: 'OVERLAPS', label: 'OVERLAPS · 重叠' },
  { value: 'CROSSES', label: 'CROSSES · 穿越' },
  { value: 'EQUALS', label: 'EQUALS · 空间相等' },
];

const fingerprint = (value: SpatialJoinConfiguration) => JSON.stringify(value);

const toConfiguration = (values: SpatialJoinFormValues): SpatialJoinConfiguration => {
  const base: SpatialJoinConfiguration = {
    leftTableName: values.leftTableName ?? '',
    rightTableName: values.rightTableName ?? '',
    outputTableName: values.outputTableName?.trim() ?? '',
    joinType: values.joinType === 'LEFT' ? 'LEFT' : 'INNER',
    conditions: (values.conditions ?? []).map((condition) => ({
      leftGeometryColumnName: condition.leftGeometryColumnName ?? '',
      predicate: condition.predicate ?? null,
      rightGeometryColumnName: condition.rightGeometryColumnName ?? '',
    })),
  };
  const withAttributes: SpatialJoinConfiguration = values.attributeConditions === undefined
    ? base
    : values.attributeConditions === null
      ? { ...base, attributeConditions: null }
      : {
          ...base,
          attributeConditions: values.attributeConditions.map((condition) => ({
            leftColumnName: condition.leftColumnName ?? '',
            operator: 'EQUALS',
            rightColumnName: condition.rightColumnName ?? '',
          })),
        };
  let result = withAttributes;
  if (values.outputColumns === null) {
    result = { ...result, outputColumns: null };
  } else if (values.outputColumns !== undefined) {
    result = {
      ...result,
      outputColumns: values.outputColumns.map((column) => ({
        sourceSide: column.sourceSide === 'RIGHT' ? 'RIGHT' : 'LEFT',
        sourceColumnName: column.sourceColumnName ?? '',
        outputColumnName: column.outputColumnName?.trim() ?? '',
        included: column.included !== false,
      })),
    };
  }
  if (values.joinOperation !== undefined) {
    result = { ...result, joinOperation: values.joinOperation };
  }
  if (values.oneToOne !== undefined) {
    result = { ...result, oneToOne: values.oneToOne };
  }
  if (values.temporalCondition !== undefined) {
    result = { ...result, temporalCondition: values.temporalCondition };
  }
  if (values.spatialNear !== undefined) {
    result = values.spatialNear === null ? { ...result, spatialNear: null } : {
      ...result,
      spatialNear: {
        ...values.spatialNear,
        leftGeometryColumnName: values.spatialNear.leftGeometryColumnName ?? '',
        rightGeometryColumnName: values.spatialNear.rightGeometryColumnName ?? '',
        distanceMethod: values.spatialNear.distanceMethod ?? null,
        distance: values.spatialNear.distance ?? null,
        distanceUnit: values.spatialNear.distanceUnit ?? null,
      },
    };
  }
  if (values.distanceOutput !== undefined) {
    result = values.distanceOutput === null ? { ...result, distanceOutput: null } : {
      ...result,
      distanceOutput: {
        ...values.distanceOutput,
        enabled: values.distanceOutput.enabled === true,
        spatialDistanceColumnName: values.distanceOutput.spatialDistanceColumnName?.trim() ?? '',
        spatialDistanceUnit: values.distanceOutput.spatialDistanceUnit ?? null,
        temporalDifferenceColumnName: values.distanceOutput.temporalDifferenceColumnName?.trim() ?? '',
        temporalDifferenceUnit: values.distanceOutput.temporalDifferenceUnit ?? null,
      },
    };
  }
  return result;
};

const SpatialJoinInspector = ({
  node,
  validation,
  validationUnavailableMessage,
  onApply,
  onDirtyChange,
  inspectorRef,
}: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.SpatialJoin>) => {
  const [form] = Form.useForm<SpatialJoinFormValues>();
  const [projectionOpen, setProjectionOpen] = useState(false);
  const [oneToOneOpen, setOneToOneOpen] = useState(false);
  const [temporalOpen, setTemporalOpen] = useState(false);
  const [spatialNearOpen, setSpatialNearOpen] = useState(false);
  const [distanceOutputOpen, setDistanceOutputOpen] = useState(false);
  const leftTableName = Form.useWatch('leftTableName', form) ?? '';
  const rightTableName = Form.useWatch('rightTableName', form) ?? '';
  const conditions = Form.useWatch('conditions', form) ?? [];
  const attributeConditions = Form.useWatch('attributeConditions', form) ?? [];
  const configuredOutputColumns = Form.useWatch('outputColumns', { form, preserve: true });
  const joinOperation = Form.useWatch('joinOperation', form) ?? 'JOIN_ONE_TO_MANY';
  const oneToOne = Form.useWatch('oneToOne', { form, preserve: true });
  const temporalCondition = Form.useWatch('temporalCondition', { form, preserve: true });
  const spatialNear = Form.useWatch('spatialNear', { form, preserve: true });
  const distanceOutput = Form.useWatch('distanceOutput', { form, preserve: true });
  const projectionConfigured = Array.isArray(configuredOutputColumns);
  const outputColumns = configuredOutputColumns ?? [];
  const tables = validation?.inputTables ?? [];
  const leftTable = tables.find((table) => table.name === leftTableName);
  const rightTable = tables.find((table) => table.name === rightTableName);
  const leftGeometryColumns = leftTable?.columns.filter(
    (column) => column.fieldType === 'GEOMETRY',
  ) ?? [];
  const rightGeometryColumns = rightTable?.columns.filter(
    (column) => column.fieldType === 'GEOMETRY',
  ) ?? [];
  const leftAttributeColumns = leftTable?.columns.filter(
    (column) => column.fieldType !== 'GEOMETRY',
  ) ?? [];
  const rightAttributeColumns = rightTable?.columns.filter(
    (column) => column.fieldType !== 'GEOMETRY',
  ) ?? [];

  const markDirty = (values = form.getFieldsValue(true)) => {
    onDirtyChange(fingerprint(toConfiguration(values)) !== fingerprint(node.configuration));
  };

  const setOutputColumns = (columns: JoinOutputColumn[]) => {
    form.setFieldValue('outputColumns', columns);
    markDirty({ ...form.getFieldsValue(true), outputColumns: columns });
  };

  const suggestOutputColumnsIfEmpty = (nextLeft: string, nextRight: string) => {
    const current = form.getFieldValue('outputColumns') as JoinOutputColumn[] | null | undefined;
    if (!Array.isArray(current) || current.length > 0) return;
    const nextLeftTable = tables.find((table) => table.name === nextLeft);
    const nextRightTable = tables.find((table) => table.name === nextRight);
    if (!nextLeftTable || !nextRightTable || nextLeftTable.name === nextRightTable.name) return;
    setOutputColumns(suggestJoinOutputColumns(nextLeftTable, nextRightTable));
  };

  const submit = (values: SpatialJoinFormValues) => {
    onApply({
      id: node.id,
      type: node.type,
      configuration: toConfiguration(values),
    });
    onDirtyChange(false);
  };

  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(
    inspectorRef,
    () => ({
      apply: async () => {
        try {
          void form.validateFields().catch(() => undefined);
          submit(form.getFieldsValue(true));
          return true;
        } catch {
          return false;
        }
      },
    }),
  );

  return (
    <Space orientation="vertical" size={12} className="canvas-inspector-content">
      <CanvasNodeValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <Alert
        showIcon
        type="info"
        title="左表作为目标要素，右表作为连接要素"
        description="至少配置一个空间拓扑条件或空间 Near；还可追加属性等值和时间关系，全部按 AND 组合。两侧 Geometry 的 CRS 和 dimension 必须完全一致。"
      />
      <Form<SpatialJoinFormValues>
        form={form}
        layout="vertical"
        autoComplete="off"
        initialValues={{
          ...node.configuration,
          joinOperation: node.configuration.joinOperation ?? 'JOIN_ONE_TO_MANY',
        }}
        onFinish={submit}
        onValuesChange={(_changed, values) => markDirty(values)}
      >
        <Form.Item
          name="leftTableName"
          label="目标表（左侧）"
          rules={[{ required: true, message: '请选择目标表' }]}
        >
          <Select
            disabled={!validation}
            placeholder={validation ? '选择目标表' : '等待 Task Engine 计算上游表'}
            options={tables.map((table) => ({ value: table.name, label: table.name }))}
            onChange={(value) => {
              form.setFieldValue('conditions', []);
              suggestOutputColumnsIfEmpty(value, rightTableName);
            }}
          />
        </Form.Item>
        <Form.Item
          name="rightTableName"
          label="连接表（右侧）"
          rules={[{ required: true, message: '请选择连接表' }]}
        >
          <Select
            disabled={!validation}
            placeholder={validation ? '选择连接表' : '等待 Task Engine 计算上游表'}
            options={tables
              .filter((table) => table.name !== leftTableName)
              .map((table) => ({ value: table.name, label: table.name }))}
            onChange={(value) => {
              form.setFieldValue('conditions', []);
              suggestOutputColumnsIfEmpty(leftTableName, value);
            }}
          />
        </Form.Item>
        <Form.Item
          name="joinType"
          label="结果范围"
          extra="LEFT 会保留未匹配的目标记录；一对多的右侧字段填 NULL，一对一汇总的 Join Count 为 0。"
        >
          <Select options={[
            { value: 'INNER', label: '仅保留匹配的目标要素（INNER）' },
            { value: 'LEFT', label: '保留全部目标要素（LEFT）' },
          ]} />
        </Form.Item>
        <Form.Item label="连接粒度" extra="一对多保留全部匹配组合；一对一为每个目标要素最多输出一行。">
          <Space.Compact style={{ width: '100%' }}>
            <Form.Item name="joinOperation" noStyle>
              <Select
                style={{ flex: 1 }}
                options={[
                  { value: 'JOIN_ONE_TO_MANY', label: '一对多 · 保留全部匹配组合' },
                  { value: 'JOIN_ONE_TO_ONE', label: '一对一 · 汇总或确定性保留' },
                ]}
                onChange={(next) => {
                  if (next === 'JOIN_ONE_TO_ONE' && !form.getFieldValue('oneToOne')) {
                    const created = createSpatialJoinOneToOneOptions();
                    form.setFieldValue('oneToOne', created);
                    markDirty({ ...form.getFieldsValue(true), joinOperation: next, oneToOne: created });
                  }
                }}
              />
            </Form.Item>
            {joinOperation === 'JOIN_ONE_TO_ONE' && (
              <Button icon={<SettingOutlined />} onClick={() => setOneToOneOpen(true)}>
                设置
              </Button>
            )}
          </Space.Compact>
          {joinOperation === 'JOIN_ONE_TO_ONE' && (
            <Typography.Text type="secondary">
              {spatialJoinOneToOneSummary(oneToOne)}
            </Typography.Text>
          )}
        </Form.Item>
        <Form.Item
          name="outputTableName"
          label="输出表名"
          rules={[
            { required: true, whitespace: true, message: '请输入输出表名' },
            { pattern: /^[A-Za-z_][A-Za-z0-9_]{0,127}$/, message: '只能包含字母、数字和下划线' },
          ]}
        >
          <Input placeholder="例如 orders_with_region" />
        </Form.Item>
        <Typography.Text strong>空间条件</Typography.Text>
        <Form.List
          name="conditions"
          rules={[{
            validator: (_, value: SpatialJoinCondition[] | undefined) => (
              value && value.length <= 8
                ? Promise.resolve()
                : Promise.reject(new Error('空间拓扑条件不能超过 8 项'))
            ),
          }]}
        >
          {(fields, { add, remove }, { errors }) => (
            <Space orientation="vertical" size={8} className="canvas-condition-list">
              {fields.map((field, index) => (
                <Card
                  key={field.key}
                  size="small"
                  title={`条件 ${index + 1}`}
                  extra={(
                    <Button
                      type="text"
                      danger
                      icon={<DeleteOutlined />}
                      aria-label={`删除空间条件 ${index + 1}`}
                      onClick={() => remove(field.name)}
                    />
                  )}
                >
                  <Form.Item
                    name={[field.name, 'leftGeometryColumnName']}
                    rules={[{ required: true, message: '请选择左侧 Geometry 字段' }]}
                  >
                    <Select
                      disabled={!validation || !leftTable}
                      placeholder="左侧 Geometry 字段"
                      options={leftGeometryColumns.map((column) => ({
                        value: column.name,
                        label: `${column.name} · ${column.geometry?.kind ?? 'GEOMETRY'} · EPSG:${column.geometry?.crs.code ?? '-'}`,
                      }))}
                    />
                  </Form.Item>
                  <Form.Item
                    name={[field.name, 'predicate']}
                    rules={[{ required: true, message: '请选择空间谓词' }]}
                  >
                    <Select placeholder="空间谓词" options={[...predicates]} />
                  </Form.Item>
                  <Form.Item
                    name={[field.name, 'rightGeometryColumnName']}
                    rules={[{ required: true, message: '请选择右侧 Geometry 字段' }]}
                  >
                    <Select
                      disabled={!validation || !rightTable}
                      placeholder="右侧 Geometry 字段"
                      options={rightGeometryColumns.map((column) => ({
                        value: column.name,
                        label: `${column.name} · ${column.geometry?.kind ?? 'GEOMETRY'} · EPSG:${column.geometry?.crs.code ?? '-'}`,
                      }))}
                    />
                  </Form.Item>
                </Card>
              ))}
              <Button
                icon={<PlusOutlined />}
                disabled={conditions.length >= 8}
                onClick={() => add({
                  leftGeometryColumnName: '',
                  predicate: 'INTERSECTS',
                  rightGeometryColumnName: '',
                })}
              >
                添加空间条件
              </Button>
              <Form.ErrorList errors={errors} />
            </Space>
          )}
        </Form.List>
        <div className="canvas-processor-section-header">
          <Space size={6}>
            <Typography.Text strong>空间 Near</Typography.Text>
            <Tag color={spatialNear ? 'cyan' : undefined}>{spatialNear ? '已启用' : '可选'}</Tag>
          </Space>
          <Button size="small" icon={<SettingOutlined />} onClick={() => setSpatialNearOpen(true)}>
            {spatialNear ? '设置' : '添加'}
          </Button>
        </div>
        <Typography.Text type="secondary" ellipsis={{ tooltip: spatialJoinNearSummary(spatialNear) }}>
          {spatialJoinNearSummary(spatialNear)}
        </Typography.Text>
        <div style={{ marginTop: 12 }}>
          <Space size={6}>
            <Typography.Text strong>属性匹配</Typography.Text>
            <Tag>{attributeConditions.length} 项</Tag>
            <Typography.Text type="secondary">可选，全部与空间条件按 AND 组合</Typography.Text>
          </Space>
        </div>
        <Form.List name="attributeConditions">
          {(fields, { add, remove }) => (
            <Space orientation="vertical" size={6} style={{ width: '100%', marginTop: 8 }}>
              {fields.map((field, index) => (
                <div
                  key={field.key}
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'minmax(0, 1fr) 24px minmax(0, 1fr) 32px',
                    gap: 6,
                    alignItems: 'start',
                  }}
                >
                  <Form.Item
                    name={[field.name, 'leftColumnName']}
                    rules={[{ required: true, message: '请选择左侧字段' }]}
                    style={{ marginBottom: 0 }}
                  >
                    <Select
                      disabled={!validation || !leftTable}
                      placeholder="左侧属性字段"
                      options={leftAttributeColumns.map((column) => ({
                        value: column.name,
                        label: `${column.name} · ${column.fieldType}`,
                      }))}
                    />
                  </Form.Item>
                  <Typography.Text style={{ lineHeight: '32px', textAlign: 'center' }}>=</Typography.Text>
                  <Form.Item
                    name={[field.name, 'rightColumnName']}
                    rules={[{ required: true, message: '请选择右侧字段' }]}
                    style={{ marginBottom: 0 }}
                  >
                    <Select
                      disabled={!validation || !rightTable}
                      placeholder="右侧属性字段"
                      options={rightAttributeColumns.map((column) => ({
                        value: column.name,
                        label: `${column.name} · ${column.fieldType}`,
                      }))}
                    />
                  </Form.Item>
                  <Form.Item name={[field.name, 'operator']} hidden><Input /></Form.Item>
                  <Button
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    aria-label={`删除属性匹配条件 ${index + 1}`}
                    onClick={() => remove(field.name)}
                  />
                </div>
              ))}
              <Button
                icon={<PlusOutlined />}
                disabled={attributeConditions.length >= 8}
                onClick={() => add({
                  leftColumnName: '',
                  operator: 'EQUALS',
                  rightColumnName: '',
                })}
              >
                添加属性匹配
              </Button>
              <Typography.Text type="secondary">
                使用 Spark SQL 普通等号；任一侧为 NULL 时不匹配，字段类型兼容性由执行计划分析。
              </Typography.Text>
            </Space>
          )}
        </Form.List>
        <div className="canvas-processor-section-header">
          <Space size={6}>
            <ClockCircleOutlined />
            <Typography.Text strong>时间关系</Typography.Text>
            <Tag color={temporalCondition ? 'blue' : undefined}>
              {temporalCondition ? '已启用' : '可选'}
            </Tag>
          </Space>
          <Button
            size="small"
            icon={<SettingOutlined />}
            onClick={() => setTemporalOpen(true)}
          >
            {temporalCondition ? '设置' : '添加'}
          </Button>
        </div>
        <Typography.Text
          type="secondary"
          ellipsis={{ tooltip: spatialJoinTemporalSummary(temporalCondition) }}
        >
          {spatialJoinTemporalSummary(temporalCondition)}
        </Typography.Text>
        <div className="canvas-processor-section-header">
          <Space size={6}>
            <Typography.Text strong>距离输出</Typography.Text>
            <Tag color={distanceOutput?.enabled ? 'blue' : undefined}>
              {distanceOutput?.enabled ? '已启用' : '关闭'}
            </Tag>
          </Space>
          <Button size="small" icon={<SettingOutlined />} onClick={() => setDistanceOutputOpen(true)}>
            设置
          </Button>
        </div>
        <Typography.Text
          type="secondary"
          ellipsis={{ tooltip: spatialJoinDistanceOutputSummary(
            distanceOutput,
            Boolean(spatialNear),
            Boolean(temporalCondition && ['NEAR', 'NEAR_BEFORE', 'NEAR_AFTER'].includes(temporalCondition.relationship ?? '')),
          ) }}
        >
          {spatialJoinDistanceOutputSummary(
            distanceOutput,
            Boolean(spatialNear),
            Boolean(temporalCondition && ['NEAR', 'NEAR_BEFORE', 'NEAR_AFTER'].includes(temporalCondition.relationship ?? '')),
          )}
        </Typography.Text>
        <div className="canvas-processor-section-header">
          <Space size={6}>
            <Typography.Text strong>输出字段</Typography.Text>
            <Tag>{projectionConfigured
              ? `${outputColumns.filter((column) => column.included).length}/${outputColumns.length}`
              : '旧版全字段'}</Tag>
          </Space>
          <Button
            size="small"
            aria-label="设置空间连接输出字段"
            icon={<SettingOutlined />}
            onClick={() => setProjectionOpen(true)}
          >
            设置
          </Button>
        </div>
        <Typography.Text type="secondary">
          {joinOperation === 'JOIN_ONE_TO_ONE' && oneToOne?.mode === 'SUMMARIZE_MATCHES'
            ? '汇总模式只允许直接选择左侧目标表字段；右侧连接表字段请配置为统计项。'
            : '左侧字段默认保持原名；右侧重名字段建议使用“右表完整逻辑表名_字段名”。'}
        </Typography.Text>
        <Modal
          open={projectionOpen}
          width={860}
          title="设置空间连接输出字段"
          okText="完成"
          cancelText="关闭"
          onOk={() => setProjectionOpen(false)}
          onCancel={() => setProjectionOpen(false)}
        >
          {projectionConfigured ? (
            <JoinOutputColumnsEditor
              left={leftTable}
              right={rightTable}
              conditions={[]}
              outputColumns={outputColumns}
              leftLabel="左侧"
              rightLabel="右侧"
              showExcludeRightJoinKeys={false}
              onProgrammaticChange={setOutputColumns}
            />
          ) : (
            <Space orientation="vertical" size={12} style={{ width: '100%' }}>
              <Alert
                type="warning"
                showIcon
                title="当前使用旧版全字段输出"
                description="旧版按左表字段后接右表字段输出；存在同名字段时无法编译。启用字段投影后可排除、改名并调整顺序。"
              />
              <Button
                type="primary"
                disabled={!leftTable || !rightTable}
                onClick={() => {
                  if (!leftTable || !rightTable) return;
                  setOutputColumns(suggestJoinOutputColumns(leftTable, rightTable));
                }}
              >
                启用字段投影并生成建议
              </Button>
            </Space>
          )}
        </Modal>
        <OneToOneOptionsModal
          open={oneToOneOpen}
          value={oneToOne}
          rightTable={rightTable}
          onCancel={() => setOneToOneOpen(false)}
          onSave={(next) => {
            form.setFieldValue('oneToOne', next);
            markDirty({ ...form.getFieldsValue(true), oneToOne: next });
            setOneToOneOpen(false);
          }}
        />
        <TemporalConditionModal
          open={temporalOpen}
          value={temporalCondition}
          leftTable={leftTable}
          rightTable={rightTable}
          onCancel={() => setTemporalOpen(false)}
          onRemove={() => {
            form.setFieldValue('temporalCondition', null);
            markDirty({ ...form.getFieldsValue(true), temporalCondition: null });
            setTemporalOpen(false);
          }}
          onSave={(next) => {
            form.setFieldValue('temporalCondition', next);
            markDirty({ ...form.getFieldsValue(true), temporalCondition: next });
            setTemporalOpen(false);
          }}
        />
        <SpatialNearConditionModal
          open={spatialNearOpen}
          value={spatialNear}
          leftTable={leftTable}
          rightTable={rightTable}
          onCancel={() => setSpatialNearOpen(false)}
          onRemove={() => {
            form.setFieldValue('spatialNear', null);
            markDirty({ ...form.getFieldsValue(true), spatialNear: null });
            setSpatialNearOpen(false);
          }}
          onSave={(next) => {
            form.setFieldValue('spatialNear', next);
            markDirty({ ...form.getFieldsValue(true), spatialNear: next });
            setSpatialNearOpen(false);
          }}
        />
        <DistanceOutputModal
          open={distanceOutputOpen}
          value={distanceOutput}
          spatialNearEnabled={Boolean(spatialNear)}
          temporalNearEnabled={Boolean(
            temporalCondition
            && ['NEAR', 'NEAR_BEFORE', 'NEAR_AFTER'].includes(temporalCondition.relationship ?? ''),
          )}
          oneToMany={joinOperation !== 'JOIN_ONE_TO_ONE'}
          geodesic={spatialNear?.distanceMethod === 'GEODESIC'}
          onCancel={() => setDistanceOutputOpen(false)}
          onRemove={() => {
            form.setFieldValue('distanceOutput', null);
            markDirty({ ...form.getFieldsValue(true), distanceOutput: null });
            setDistanceOutputOpen(false);
          }}
          onSave={(next) => {
            form.setFieldValue('distanceOutput', next);
            markDirty({ ...form.getFieldsValue(true), distanceOutput: next });
            setDistanceOutputOpen(false);
          }}
        />
      </Form>
    </Space>
  );
};

export default SpatialJoinInspector;
