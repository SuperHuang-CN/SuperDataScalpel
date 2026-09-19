import { createUuid } from '../../../../../shared/browser/createUuid';
import { CompactAlert as Alert } from '../../../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  UpOutlined,
} from '@ant-design/icons';
import { Button, Form, Input, Modal, Select, Space, Tag, Typography } from 'antd';
import { useEffect } from 'react';
import type {
  CanvasNodeValidationResult,
  SpatialJoinKeepStrategy,
  SpatialJoinOneToOneOptions,
  SpatialJoinSummaryStatisticKind,
} from '../../canvasTypes';
import { createSpatialJoinOneToOneOptions } from './oneToOneOptions';

type CanvasTable = CanvasNodeValidationResult['inputTables'][number];

const summaryKinds: readonly {
  value: SpatialJoinSummaryStatisticKind;
  label: string;
}[] = [
  { value: 'SUM', label: 'SUM · 求和' },
  { value: 'MIN', label: 'MIN · 最小值' },
  { value: 'MAX', label: 'MAX · 最大值' },
  { value: 'MEAN', label: 'MEAN · 平均值' },
  { value: 'STDDEV', label: 'STDDEV · 样本标准差' },
];

const keepStrategies: readonly {
  value: SpatialJoinKeepStrategy;
  label: string;
}[] = [
  { value: 'FIRST', label: 'FIRST · 按稳定顺序取第一条' },
  { value: 'LARGEST', label: 'LARGEST · 数值最大' },
  { value: 'SMALLEST', label: 'SMALLEST · 数值最小' },
  { value: 'NEWEST', label: 'NEWEST · 日期时间最新' },
  { value: 'OLDEST', label: 'OLDEST · 日期时间最旧' },
];

const normalize = (
  value: SpatialJoinOneToOneOptions | null | undefined,
): SpatialJoinOneToOneOptions => {
  const defaults = createSpatialJoinOneToOneOptions();
  if (!value) return defaults;
  return {
    mode: value.mode ?? defaults.mode,
    joinCountColumnName: value.joinCountColumnName ?? '',
    summaryStatistics: (value.summaryStatistics ?? []).map((item) => ({ ...item })),
    keepRule: value.keepRule
      ? {
          strategy: value.keepRule.strategy,
          orderByColumnName: value.keepRule.orderByColumnName ?? null,
          stableOrder: (value.keepRule.stableOrder ?? []).map((item) => ({ ...item })),
        }
      : defaults.keepRule,
  };
};

export const OneToOneOptionsModal = ({
  open,
  value,
  rightTable,
  onCancel,
  onSave,
}: {
  open: boolean;
  value: SpatialJoinOneToOneOptions | null | undefined;
  rightTable: CanvasTable | undefined;
  onCancel: () => void;
  onSave: (value: SpatialJoinOneToOneOptions) => void;
}) => {
  const [form] = Form.useForm<SpatialJoinOneToOneOptions>();
  const mode = Form.useWatch('mode', form);
  const strategy = Form.useWatch(['keepRule', 'strategy'], form);
  const summaryStatistics = Form.useWatch('summaryStatistics', form) ?? [];
  const stableOrder = Form.useWatch(['keepRule', 'stableOrder'], form) ?? [];
  const scalarColumns = rightTable?.columns.filter(
    (column) => column.fieldType !== 'GEOMETRY',
  ) ?? [];
  const numericColumns = scalarColumns.filter((column) => [
    'BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL',
  ].includes(column.fieldType));
  const temporalColumns = scalarColumns.filter((column) => [
    'DATE', 'TIMESTAMP', 'TIMESTAMP_NTZ',
  ].includes(column.fieldType));
  const primaryColumns = strategy === 'LARGEST' || strategy === 'SMALLEST'
    ? numericColumns
    : strategy === 'NEWEST' || strategy === 'OLDEST'
      ? temporalColumns
      : [];

  useEffect(() => {
    if (open) form.setFieldsValue(normalize(value));
  }, [form, open, value]);

  return (
    <Modal
      open={open}
      width={760}
      title="一对一空间连接"
      okText="保存配置"
      cancelText="取消"
      onCancel={onCancel}
      onOk={() => {
        void form.validateFields().catch(() => undefined);
        onSave(normalize(form.getFieldsValue(true)));
      }}
    >
      <Form<SpatialJoinOneToOneOptions>
        form={form}
        layout="vertical"
        autoComplete="off"
      >
        <Form.Item name="mode" label="一对一处理方式">
          <Select options={[
            { value: 'SUMMARIZE_MATCHES', label: '汇总全部匹配记录' },
            { value: 'KEEP_ONE', label: '按确定性规则保留一条记录' },
          ]} />
        </Form.Item>

        {mode === 'SUMMARIZE_MATCHES' ? (
          <>
            <Alert
              type="info"
              showIcon
              title="每个目标要素输出一行"
              description="Join Count 统计匹配记录数；数值统计忽略 NULL，STDDEV 使用样本标准差。输出字段中只能直接保留左侧目标表字段，右侧字段需在此转换为统计项。"
            />
            <Form.Item
              name="joinCountColumnName"
              label="Join Count 输出字段"
              rules={[{ required: true, whitespace: true, message: '请输入 Join Count 字段名' }]}
              style={{ marginTop: 12 }}
            >
              <Input placeholder="join_count" />
            </Form.Item>
            <div className="canvas-processor-section-header">
              <Space size={6}>
                <Typography.Text strong>数值统计</Typography.Text>
                <Tag>{summaryStatistics.length}/32</Tag>
              </Space>
            </div>
            <Form.List name="summaryStatistics">
              {(fields, { add, move, remove }) => (
                <Space orientation="vertical" size={6} style={{ width: '100%' }}>
                  {fields.map((field, index) => (
                    <div
                      key={field.key}
                      style={{
                        display: 'grid',
                        gridTemplateColumns: '128px minmax(130px, 1fr) minmax(130px, 1fr) 84px',
                        gap: 6,
                        alignItems: 'start',
                      }}
                    >
                      <Form.Item name={[field.name, 'statisticId']} hidden><Input /></Form.Item>
                      <Form.Item
                        name={[field.name, 'kind']}
                        rules={[{ required: true, message: '请选择统计类型' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <Select placeholder="统计类型" options={[...summaryKinds]} />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'sourceColumnName']}
                        rules={[{ required: true, message: '请选择数值字段' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <Select
                          showSearch
                          optionFilterProp="label"
                          placeholder="连接表数值字段"
                          options={numericColumns.map((column) => ({
                            value: column.name,
                            label: `${column.name} · ${column.fieldType}`,
                          }))}
                        />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'outputColumnName']}
                        rules={[{ required: true, whitespace: true, message: '请输入输出字段名' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <Input placeholder="输出字段名" />
                      </Form.Item>
                      <Space size={0}>
                        <Button
                          type="text"
                          size="small"
                          icon={<UpOutlined />}
                          disabled={index === 0}
                          aria-label={`上移统计项 ${index + 1}`}
                          onClick={() => move(index, index - 1)}
                        />
                        <Button
                          type="text"
                          size="small"
                          icon={<DownOutlined />}
                          disabled={index === fields.length - 1}
                          aria-label={`下移统计项 ${index + 1}`}
                          onClick={() => move(index, index + 1)}
                        />
                        <Button
                          type="text"
                          size="small"
                          danger
                          icon={<DeleteOutlined />}
                          aria-label={`删除统计项 ${index + 1}`}
                          onClick={() => remove(field.name)}
                        />
                      </Space>
                    </div>
                  ))}
                  <Button
                    icon={<PlusOutlined />}
                    disabled={summaryStatistics.length >= 32}
                    onClick={() => add({
                      statisticId: createUuid(),
                      kind: 'SUM',
                      sourceColumnName: numericColumns[0]?.name ?? '',
                      outputColumnName: `summary_${summaryStatistics.length + 1}`,
                    })}
                  >
                    添加数值统计
                  </Button>
                </Space>
              )}
            </Form.List>
          </>
        ) : (
          <>
            <Alert
              type="warning"
              showIcon
              title="保留规则必须能够唯一确定一条记录"
              description="平台不使用 Spark 输入顺序。主规则相同后继续按稳定排序；完整排序仍并列时任务会失败，并提示继续增加唯一键等稳定字段。"
            />
            <Form.Item
              name={['keepRule', 'strategy']}
              label="保留策略"
              rules={[{ required: true, message: '请选择保留策略' }]}
              style={{ marginTop: 12 }}
            >
              <Select
                options={[...keepStrategies]}
                onChange={(next: SpatialJoinKeepStrategy) => {
                  if (next === 'FIRST') {
                    form.setFieldValue(['keepRule', 'orderByColumnName'], null);
                  }
                }}
              />
            </Form.Item>
            {strategy !== 'FIRST' && (
              <Form.Item
                name={['keepRule', 'orderByColumnName']}
                label="主排序字段"
                rules={[{ required: true, message: '请选择主排序字段' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder={strategy === 'NEWEST' || strategy === 'OLDEST'
                    ? '选择日期或时间字段'
                    : '选择数值字段'}
                  options={primaryColumns.map((column) => ({
                    value: column.name,
                    label: `${column.name} · ${column.fieldType}`,
                  }))}
                />
              </Form.Item>
            )}
            <div className="canvas-processor-section-header">
              <Space size={6}>
                <Typography.Text strong>稳定排序</Typography.Text>
                <Tag>{stableOrder.length} 项</Tag>
              </Space>
            </div>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 8 }}>
              FIRST 完全按这里的顺序取第一条；其他策略用它消除主值并列。建议最后加入连接表主键。
            </Typography.Paragraph>
            <Form.List name={['keepRule', 'stableOrder']}>
              {(fields, { add, move, remove }) => (
                <Space orientation="vertical" size={6} style={{ width: '100%' }}>
                  {fields.map((field, index) => (
                    <div
                      key={field.key}
                      style={{
                        display: 'grid',
                        gridTemplateColumns: 'minmax(160px, 1fr) 100px 100px 84px',
                        gap: 6,
                        alignItems: 'start',
                      }}
                    >
                      <Form.Item
                        name={[field.name, 'columnName']}
                        rules={[{ required: true, message: '请选择排序字段' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <Select
                          showSearch
                          optionFilterProp="label"
                          placeholder="连接表字段"
                          options={scalarColumns.map((column) => ({
                            value: column.name,
                            label: `${column.name} · ${column.fieldType}`,
                          }))}
                        />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'direction']}
                        rules={[{ required: true, message: '请选择方向' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <Select options={[
                          { value: 'ASC', label: '升序' },
                          { value: 'DESC', label: '降序' },
                        ]} />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'nullOrdering']}
                        rules={[{ required: true, message: '请选择 NULL 位置' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <Select options={[
                          { value: 'FIRST', label: 'NULL 在前' },
                          { value: 'LAST', label: 'NULL 在后' },
                        ]} />
                      </Form.Item>
                      <Space size={0}>
                        <Button
                          type="text"
                          size="small"
                          icon={<UpOutlined />}
                          disabled={index === 0}
                          aria-label={`上移稳定排序 ${index + 1}`}
                          onClick={() => move(index, index - 1)}
                        />
                        <Button
                          type="text"
                          size="small"
                          icon={<DownOutlined />}
                          disabled={index === fields.length - 1}
                          aria-label={`下移稳定排序 ${index + 1}`}
                          onClick={() => move(index, index + 1)}
                        />
                        <Button
                          type="text"
                          size="small"
                          danger
                          icon={<DeleteOutlined />}
                          aria-label={`删除稳定排序 ${index + 1}`}
                          onClick={() => remove(field.name)}
                        />
                      </Space>
                    </div>
                  ))}
                  <Button
                    icon={<PlusOutlined />}
                    onClick={() => add({
                      columnName: '',
                      direction: 'ASC',
                      nullOrdering: 'LAST',
                    })}
                  >
                    添加稳定排序字段
                  </Button>
                </Space>
              )}
            </Form.List>
          </>
        )}
      </Form>
    </Modal>
  );
};
