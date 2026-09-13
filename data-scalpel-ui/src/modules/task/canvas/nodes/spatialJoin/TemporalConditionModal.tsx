import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import { Button, Form, InputNumber, Modal, Select, Space, Typography } from 'antd';
import { useEffect } from 'react';
import type {
  CanvasNodeValidationResult,
  SpatialDurationUnit,
  SpatialJoinTemporalCondition,
  SpatialJoinTemporalRelationship,
} from '../../canvasTypes';
import {
  createSpatialJoinTemporalCondition,
  isSpatialJoinTemporalNear,
} from './temporalCondition';

type CanvasTable = CanvasNodeValidationResult['inputTables'][number];

const relationships: readonly {
  value: SpatialJoinTemporalRelationship;
  label: string;
}[] = [
  { value: 'EQUALS', label: 'EQUALS · 起止时间完全相同' },
  { value: 'INTERSECTS', label: 'INTERSECTS · 时间范围相交或接触' },
  { value: 'DURING', label: 'DURING · 目标时间严格位于连接时间内' },
  { value: 'CONTAINS', label: 'CONTAINS · 目标时间严格包含连接时间' },
  { value: 'FINISHES', label: 'FINISHES · 同时结束，目标开始更晚' },
  { value: 'FINISHED_BY', label: 'FINISHED BY · 同时结束，目标开始更早' },
  { value: 'MEETS', label: 'MEETS · 目标结束等于连接开始' },
  { value: 'MET_BY', label: 'MET BY · 目标开始等于连接结束' },
  { value: 'OVERLAPS', label: 'OVERLAPS · 目标先开始并跨入连接时间' },
  { value: 'OVERLAPPED_BY', label: 'OVERLAPPED BY · 连接先开始并跨入目标时间' },
  { value: 'STARTS', label: 'STARTS · 同时开始，目标结束更早' },
  { value: 'STARTED_BY', label: 'STARTED BY · 同时开始，目标结束更晚' },
  { value: 'NEAR', label: 'NEAR · 相交或前后间隔不超过阈值' },
  { value: 'NEAR_BEFORE', label: 'NEAR BEFORE · 目标在连接之前且间隔不超过阈值' },
  { value: 'NEAR_AFTER', label: 'NEAR AFTER · 目标在连接之后且间隔不超过阈值' },
];

const durationUnits: readonly { value: SpatialDurationUnit; label: string }[] = [
  { value: 'MILLISECONDS', label: '毫秒' },
  { value: 'SECONDS', label: '秒' },
  { value: 'MINUTES', label: '分钟' },
  { value: 'HOURS', label: '小时' },
  { value: 'DAYS', label: '天（固定 24 小时）' },
  { value: 'WEEKS', label: '周（固定 7 天）' },
];

const temporalColumns = (table: CanvasTable | undefined) => table?.columns.filter(
  (column) => ['DATE', 'TIMESTAMP', 'TIMESTAMP_NTZ'].includes(column.fieldType),
) ?? [];

const optionsWithStale = (
  table: CanvasTable | undefined,
  current: string | null | undefined,
) => {
  const options = temporalColumns(table).map((column) => ({
    value: column.name,
    label: `${column.name} · ${column.fieldType}`,
  }));
  if (current && !options.some((option) => option.value === current)) {
    options.unshift({ value: current, label: `${current} · 已失效` });
  }
  return options;
};

const normalize = (
  value: SpatialJoinTemporalCondition | null | undefined,
  leftTable: CanvasTable | undefined,
  rightTable: CanvasTable | undefined,
): SpatialJoinTemporalCondition => {
  const defaults = createSpatialJoinTemporalCondition(
    temporalColumns(leftTable)[0]?.name ?? '',
    temporalColumns(rightTable)[0]?.name ?? '',
  );
  if (!value) return defaults;
  return {
    relationship: value.relationship ?? null,
    leftStartColumnName: value.leftStartColumnName ?? '',
    leftEndColumnName: value.leftEndColumnName || null,
    rightStartColumnName: value.rightStartColumnName ?? '',
    rightEndColumnName: value.rightEndColumnName || null,
    nearDistance: value.nearDistance ?? null,
    nearDistanceUnit: value.nearDistanceUnit ?? null,
  };
};

export const TemporalConditionModal = ({
  open,
  value,
  leftTable,
  rightTable,
  onCancel,
  onRemove,
  onSave,
}: {
  open: boolean;
  value: SpatialJoinTemporalCondition | null | undefined;
  leftTable: CanvasTable | undefined;
  rightTable: CanvasTable | undefined;
  onCancel: () => void;
  onRemove: () => void;
  onSave: (value: SpatialJoinTemporalCondition) => void;
}) => {
  const [form] = Form.useForm<SpatialJoinTemporalCondition>();
  const relationship = Form.useWatch('relationship', form);
  const leftStart = Form.useWatch('leftStartColumnName', form);
  const leftEnd = Form.useWatch('leftEndColumnName', form);
  const rightStart = Form.useWatch('rightStartColumnName', form);
  const rightEnd = Form.useWatch('rightEndColumnName', form);

  useEffect(() => {
    if (open) form.setFieldsValue(normalize(value, leftTable, rightTable));
  }, [form, leftTable, open, rightTable, value]);

  return (
    <Modal
      open={open}
      width={760}
      title="时间关系"
      okText="保存配置"
      cancelText="取消"
      onCancel={onCancel}
      onOk={() => {
        void form.validateFields().catch(() => undefined);
        onSave(normalize(form.getFieldsValue(true), leftTable, rightTable));
      }}
    >
      <Space orientation="vertical" size={12} style={{ width: '100%' }}>
        <Alert
          type="info"
          showIcon
          title="时间关系与空间、属性条件按 AND 组合"
          description="每侧留空结束字段表示瞬时；设置结束字段表示闭区间。NULL、开始晚于结束或字段类型不一致的记录不会匹配。"
        />
        {value && (
          <div style={{ textAlign: 'right' }}>
            <Button danger type="text" onClick={onRemove}>移除时间关系</Button>
          </div>
        )}
        <Form<SpatialJoinTemporalCondition>
          form={form}
          layout="vertical"
          autoComplete="off"
        >
          <Form.Item
            name="relationship"
            label="时间关系"
            rules={[{ required: true, message: '请选择时间关系' }]}
          >
            <Select showSearch optionFilterProp="label" options={[...relationships]} />
          </Form.Item>
          <Typography.Text strong>目标表（左侧）</Typography.Text>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
            <Form.Item
              name="leftStartColumnName"
              label="开始 / 瞬时字段"
              rules={[{ required: true, message: '请选择左侧开始时间字段' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                options={optionsWithStale(leftTable, leftStart)}
              />
            </Form.Item>
            <Form.Item name="leftEndColumnName" label="结束字段（可选）">
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="留空表示瞬时"
                options={optionsWithStale(leftTable, leftEnd)}
              />
            </Form.Item>
          </div>
          <Typography.Text strong>连接表（右侧）</Typography.Text>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
            <Form.Item
              name="rightStartColumnName"
              label="开始 / 瞬时字段"
              rules={[{ required: true, message: '请选择右侧开始时间字段' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                options={optionsWithStale(rightTable, rightStart)}
              />
            </Form.Item>
            <Form.Item name="rightEndColumnName" label="结束字段（可选）">
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                placeholder="留空表示瞬时"
                options={optionsWithStale(rightTable, rightEnd)}
              />
            </Form.Item>
          </div>
          {isSpatialJoinTemporalNear(relationship) && (
            <Form.Item label="时间邻近阈值" required>
              <Space.Compact style={{ width: '100%' }}>
                <Form.Item
                  name="nearDistance"
                  noStyle
                  rules={[{ required: true, message: '请输入正整数时间距离' }]}
                >
                  <InputNumber min={1} precision={0} style={{ width: '45%' }} />
                </Form.Item>
                <Form.Item
                  name="nearDistanceUnit"
                  noStyle
                  rules={[{ required: true, message: '请选择时间单位' }]}
                >
                  <Select style={{ width: '55%' }} options={[...durationUnits]} />
                </Form.Item>
              </Space.Compact>
            </Form.Item>
          )}
        </Form>
      </Space>
    </Modal>
  );
};
