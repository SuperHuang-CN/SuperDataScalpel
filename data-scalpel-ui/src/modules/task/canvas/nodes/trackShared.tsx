import { DeleteOutlined, DownOutlined, PlusOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Form, Input, InputNumber, Modal, Select, Space, Switch, Typography } from 'antd';
import { useRef } from 'react';
import { ContextHelp } from '../../../../shared/components/ContextualFeedback';
import type {
  CanvasColumnSchema,
  TrackBoundaryConfiguration,
  TrackFixedTimeBoundary,
  TrackTimeBoundaryUnit,
  TrackSummaryStatistic,
  TrackSummaryStatisticKind,
} from '../canvasTypes';
import { spatialColumnOptions } from './spatialInspectorOptions';
import { trackDistanceUnitOptions, trackDurationUnitOptions } from './trackOptions';
import { spatialUnitHelp } from './spatialUnits';
import { trackTimeBoundaryOptions } from './trackTimeBoundary';

export const TrackBoundaryEditor = ({
  value,
  onChange,
}: {
  value: TrackBoundaryConfiguration;
  onChange: (value: TrackBoundaryConfiguration) => void;
}) => {
  const previousFixed = useRef<TrackFixedTimeBoundary>({ interval: 1, unit: 'DAYS', referenceTime: null, timeZone: 'UTC' });
  const fixed = value.fixedTimeBoundary;
  return (
  <Space orientation="vertical" size={10} style={{ width: '100%' }}>
    <Space size={4}><Typography.Text strong>相邻观测间隔</Typography.Text>
      <ContextHelp ariaLabel="相邻观测间隔说明" content={`相邻时间或距离超过阈值时拆分轨迹；不是按日、月重置的固定边界。清空数值及单位表示不限制。${spatialUnitHelp}`} /></Space>
    <div className="canvas-spatial-pair-grid">
      <Space.Compact block>
        <InputNumber min={Number.MIN_VALUE} value={value.maximumTimeGap} placeholder="最大时间间隔"
          onChange={(maximumTimeGap) => onChange({ ...value, maximumTimeGap })} />
        <Select allowClear aria-label="最大时间间隔单位" value={value.maximumTimeGapUnit} options={trackDurationUnitOptions}
          onChange={(maximumTimeGapUnit) => onChange({ ...value, maximumTimeGapUnit })} />
      </Space.Compact>
      <Space.Compact block>
        <InputNumber min={Number.MIN_VALUE} value={value.maximumDistanceGap} placeholder="最大空间间隔"
          onChange={(maximumDistanceGap) => onChange({ ...value, maximumDistanceGap })} />
        <Select allowClear value={value.maximumDistanceGapUnit} options={trackDistanceUnitOptions}
          onChange={(maximumDistanceGapUnit) => onChange({ ...value, maximumDistanceGapUnit })} />
      </Space.Compact>
    </div>
    <Space size={6}><Typography.Text strong>固定时间边界</Typography.Text>
      <ContextHelp ariaLabel="固定时间边界说明" content="以参考时间对齐周期，边界时刻属于新片段，与相邻间隔共同生效。默认参考 Unix Epoch。日、周、月、年按指定时区的日历推进；小时及更小单位按实际时长。缺时间的观测不参与分析。" />
      <Switch size="small" aria-label="启用固定时间边界" checked={fixed != null} onChange={(enabled) => {
        if (fixed) previousFixed.current = fixed;
        onChange({ ...value, fixedTimeBoundary: enabled ? previousFixed.current : null });
      }} /></Space>
    {fixed && <>
      <Form.Item label="重置周期" validateStatus={fixed.interval == null || fixed.interval <= 0 || !fixed.unit ? 'error' : undefined}
        help={fixed.interval == null || fixed.interval <= 0 || !fixed.unit ? '请填写正整数周期并选择单位' : undefined}>
        <Space.Compact block>
          <InputNumber min={1} max={2147483647} precision={0} value={fixed.interval} aria-label="固定边界周期"
            onChange={(interval) => onChange({ ...value, fixedTimeBoundary: { ...fixed, interval } })} />
          <Select<TrackTimeBoundaryUnit> value={fixed.unit} options={trackTimeBoundaryOptions} aria-label="固定边界单位"
            onChange={(unit) => onChange({ ...value, fixedTimeBoundary: { ...fixed, unit } })} />
        </Space.Compact>
      </Form.Item>
      <div className="canvas-spatial-pair-grid">
        <Form.Item label="参考时间（可选）"><Input autoComplete="off" value={fixed.referenceTime ?? ''}
          placeholder="1970-01-01T00:00:00Z" onChange={(event) => onChange({ ...value,
            fixedTimeBoundary: { ...fixed, referenceTime: event.target.value === '' ? null : event.target.value } })} /></Form.Item>
        <Form.Item label="日历时区"><Input autoComplete="off" value={fixed.timeZone ?? ''} placeholder="UTC"
          onChange={(event) => onChange({ ...value,
            fixedTimeBoundary: { ...fixed, timeZone: event.target.value === '' ? null : event.target.value } })} /></Form.Item>
      </div>
    </>}
  </Space>
  );
};

const summaryKinds: Array<{ value: TrackSummaryStatisticKind; label: string }> = [
  { value: 'COUNT', label: 'COUNT · 点数' },
  { value: 'COUNT_FIELD', label: 'COUNT_FIELD · 非空数' },
  { value: 'ANY', label: 'ANY · 样本值' },
  ...(['SUM', 'MEAN', 'MIN', 'MAX', 'RANGE', 'STDDEV', 'VARIANCE', 'FIRST', 'LAST'] as const).map(value => ({ value, label: value })),
];
const numericSummaryField = (column: CanvasColumnSchema) => ['BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL'].includes(column.fieldType);

export const TrackSummaryEditor = ({
  value,
  columns,
  onChange,
  allowNumericAny = false,
  validationAvailable = false,
}: {
  value: TrackSummaryStatistic[];
  columns: CanvasColumnSchema[];
  onChange: (value: TrackSummaryStatistic[]) => void;
  allowNumericAny?: boolean;
  validationAvailable?: boolean;
}) => {
  const sourceDrafts = useRef(new Map<string, string | null>());
  const [modal, modalContext] = Modal.useModal();
  const update = (index: number, next: TrackSummaryStatistic) => {
    onChange(value.map((item, itemIndex) => itemIndex === index ? next : item));
  };
  const move = (from: number, to: number) => {
    const next = [...value];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    onChange(next);
  };
  return <Space orientation="vertical" size={6} style={{ width: '100%' }}>
    {modalContext}
    <div className="canvas-spatial-modal-toolbar">
      <Space size={4}><Typography.Text type="secondary">片段字段统计</Typography.Text><ContextHelp ariaLabel="轨迹汇总统计说明"
        content={<><p>COUNT 统计成员点数；COUNT_FIELD 统计字段非 NULL 数，不去重，空字符串也计数。Any 返回一个非 NULL 样本，不保证重跑相同。</p>
          <p>{allowNumericAny ? '驻留 Any 支持字符串和数值，保留来源类型。' : '轨迹重建 Any 只支持字符串。'} FIRST/LAST 按时间及已配置次序取首末观测，首末值为 NULL 时保留 NULL。旧策略同时间平局不保证确定。</p>
          <p>STDDEV/VARIANCE 使用样本公式，少于两个有效值时 NULL；SUM/MEAN 的结果类型按 Spark 提升。配置对应 summaryFields；统计结果不会显示在节点卡片。</p></>} /></Space>
      <Button aria-label="添加汇总" size="small" type="primary" icon={<PlusOutlined />} disabled={value.length >= 32}
        onClick={() => onChange([...value, {
          statisticId: crypto.randomUUID(), kind: 'COUNT', sourceColumnName: null,
          outputColumnName: `summary_${value.length + 1}`,
        }])}>添加汇总</Button>
    </div>
    {value.map((statistic, index) => {
      const needsSource = statistic.kind !== 'COUNT';
      const eligible = (column: CanvasColumnSchema) => statistic.kind === 'ANY'
        ? column.fieldType === 'STRING' || allowNumericAny && numericSummaryField(column) : column.fieldType !== 'GEOMETRY';
      const invalidSource = needsSource && (!statistic.sourceColumnName || validationAvailable
        && !columns.some(column => column.name === statistic.sourceColumnName && eligible(column)));
      const invalidOutput = !statistic.outputColumnName.trim() || value.some((item, at) => at !== index
        && item.outputColumnName.toLowerCase() === statistic.outputColumnName.toLowerCase());
      const sourceOptions = spatialColumnOptions(columns, statistic.sourceColumnName ?? '', eligible).map(option =>
        !validationAvailable && option.disabled ? { ...option, label: `${option.value}（等待解析）` } : option);
      return <div className="canvas-track-summary-row" key={statistic.statisticId}>
        <Select aria-label={`汇总类型 ${index + 1}`} value={statistic.kind} options={summaryKinds}
          onChange={(kind) => {
            if (statistic.kind !== 'COUNT') sourceDrafts.current.set(statistic.statisticId, statistic.sourceColumnName);
            update(index, { ...statistic, kind, sourceColumnName: kind === 'COUNT' ? null
              : statistic.kind === 'COUNT' ? sourceDrafts.current.get(statistic.statisticId) ?? null : statistic.sourceColumnName });
          }} />
        <Select aria-label={`汇总来源字段 ${index + 1}`} status={invalidSource ? 'error' : undefined}
          showSearch optionFilterProp="label" allowClear disabled={!needsSource}
          value={statistic.sourceColumnName} options={sourceOptions} onChange={(sourceColumnName) => update(index, {
            ...statistic, sourceColumnName: sourceColumnName ?? null,
          })} />
        <Input aria-label={`汇总输出字段 ${index + 1}`} autoComplete="off" status={invalidOutput ? 'error' : undefined}
          title={invalidOutput ? '输出字段必填且不能重名' : undefined} value={statistic.outputColumnName} placeholder="输出字段"
          onChange={(event) => update(index, { ...statistic, outputColumnName: event.target.value })} />
        <Space size={0}>
          <Button type="text" size="small" icon={<UpOutlined />} disabled={index === 0}
            aria-label={`上移汇总 ${index + 1}`} onClick={() => move(index, index - 1)} />
          <Button type="text" size="small" icon={<DownOutlined />} disabled={index === value.length - 1}
            aria-label={`下移汇总 ${index + 1}`} onClick={() => move(index, index + 1)} />
          <Button type="text" danger size="small" icon={<DeleteOutlined />}
            aria-label={`删除汇总 ${index + 1}`}
            onClick={() => modal.confirm({ title: `删除汇总 ${statistic.outputColumnName || index + 1}？`,
              content: '该输出字段将被移除，下游引用可能失效。', okText: '删除', cancelText: '取消', okButtonProps: { danger: true },
              onOk: () => onChange(value.filter((_, itemIndex) => itemIndex !== index)) })} />
        </Space>
      </div>;
    })}
    {value.length === 0 && <Typography.Text type="secondary">未配置片段汇总。</Typography.Text>}
  </Space>;
};
