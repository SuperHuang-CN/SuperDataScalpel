import { Checkbox, Input, InputNumber, Modal, Select, Space } from 'antd';
import type {
  CanvasColumnSchema,
  SpatialCalendarWindowOptions,
  SpatialGroupSummary,
  SpatialTemporalSlicing,
} from '../canvasTypes';
import { spatialDurationUnitOptions } from './spatialAggregationOptions';
import { spatialColumnOptions } from './spatialInspectorOptions';
import { ContextHelp } from '../../../../shared/components/ContextualFeedback';
import { calendarWindowHelp, calendarWindowUnits, usesCalendarWindow } from './spatialCalendarWindow';

export const SpatialGroupSummaryEditor = ({
  value,
  columns,
  onChange,
}: {
  value: SpatialGroupSummary;
  columns: CanvasColumnSchema[];
  onChange: (value: SpatialGroupSummary) => void;
}) => (
  <Space orientation="vertical" size={10} style={{ width: '100%' }}>
    <Select
      showSearch
      optionFilterProp="label"
      value={value.groupByColumnName}
      options={spatialColumnOptions(
        columns,
        value.groupByColumnName,
        (column) => column.fieldType !== 'GEOMETRY',
      )}
      onChange={(groupByColumnName) => onChange({ ...value, groupByColumnName })}
      placeholder="分组字段"
    />
    <Checkbox
      checked={value.includeMinorityMajority}
      onChange={(event) => onChange({ ...value, includeMinorityMajority: event.target.checked })}
    >
      输出少数/多数标记
    </Checkbox>
    {value.includeMinorityMajority && (
      <div className="canvas-spatial-pair-grid">
        <Input
          value={value.minorityFlagColumnName ?? ''}
          placeholder="少数组字段"
          onChange={(event) => onChange({ ...value, minorityFlagColumnName: event.target.value })}
        />
        <Input
          value={value.majorityFlagColumnName ?? ''}
          placeholder="多数组字段"
          onChange={(event) => onChange({ ...value, majorityFlagColumnName: event.target.value })}
        />
      </div>
    )}
    <Checkbox
      checked={value.includeGroupPercentage}
      onChange={(event) => onChange({ ...value, includeGroupPercentage: event.target.checked })}
    >
      输出组百分比
    </Checkbox>
    {value.includeGroupPercentage && (
      <Input
        value={value.groupPercentageColumnName ?? ''}
        placeholder="组百分比字段"
        onChange={(event) => onChange({ ...value, groupPercentageColumnName: event.target.value })}
      />
    )}
  </Space>
);

export const SpatialTemporalSlicingEditor = ({
  value,
  columns,
  onChange,
}: {
  value: SpatialTemporalSlicing;
  columns: CanvasColumnSchema[];
  onChange: (value: SpatialTemporalSlicing) => void;
}) => {
  const calendar = usesCalendarWindow(value);
  const changeUnit = (unit: SpatialCalendarWindowOptions['intervalUnit'], repeat: boolean) => {
    if (!value.calendar) return;
    onChange({ ...value, calendar: { ...value.calendar, [repeat ? 'repeatIntervalUnit' : 'intervalUnit']: unit } });
  };
  return (
  <Space orientation="vertical" size={10} style={{ width: '100%' }}>
    <span>窗口与重复间隔 <ContextHelp ariaLabel="空间时间切片说明"
      content={calendarWindowHelp} /></span>
    <Select aria-label="时间窗口语义" status={value.calendar && !value.calendar.mode ? 'error' : undefined}
      value={value.calendar ? value.calendar.mode : 'FIXED_DURATION'}
      options={[{ value: 'FIXED_DURATION', label: '固定时长' }, { value: 'CALENDAR', label: '日历周期' }]}
      onChange={(mode: NonNullable<SpatialCalendarWindowOptions['mode']>) => {
        Modal.confirm({ title: '切换时间窗口语义？',
          content: '长度和重复间隔的数值保持不变，单位按所选模式解释。两种模式的单位草稿分别保留，切换可能改变统计结果。',
          okText: '确认切换', cancelText: '取消',
          onOk: () => onChange({ ...value, calendar: { intervalUnit: value.intervalUnit,
            repeatIntervalUnit: value.repeatIntervalUnit, ...value.calendar, mode } }),
        });
      }} />
    <Select
      showSearch
      optionFilterProp="label"
      value={value.timeColumnName}
      options={spatialColumnOptions(
        columns,
        value.timeColumnName,
        (column) => column.fieldType === 'TIMESTAMP',
      )}
      onChange={(timeColumnName) => onChange({ ...value, timeColumnName })}
      placeholder="TIMESTAMP 字段"
    />
    <div className="canvas-spatial-pair-grid">
      <Space.Compact block>
        <InputNumber
          min={1}
          precision={0}
          aria-label="窗口长度"
          status={value.interval <= 0 ? 'error' : undefined}
          value={value.interval}
          onChange={(interval) => onChange({ ...value, interval: interval ?? 0 })}
        />
        {calendar ? <Select aria-label="日历窗口单位" value={value.calendar?.intervalUnit}
          status={!value.calendar?.intervalUnit ? 'error' : undefined} options={calendarWindowUnits}
          onChange={(unit) => changeUnit(unit, false)} /> : <Select
          aria-label="固定窗口单位"
          value={value.intervalUnit}
          options={spatialDurationUnitOptions}
          onChange={(intervalUnit) => onChange({ ...value, intervalUnit })}
        />}
      </Space.Compact>
      <Space.Compact block>
        <InputNumber
          min={1}
          precision={0}
          value={value.repeatInterval}
          placeholder="重复间隔"
          aria-label="重复间隔"
          status={value.repeatInterval != null && value.repeatInterval <= 0 ? 'error' : undefined}
          onChange={(repeatInterval) => onChange({ ...value, repeatInterval })}
        />
        {calendar ? <Select aria-label="日历重复单位" allowClear value={value.calendar?.repeatIntervalUnit}
          status={value.repeatInterval != null && !value.calendar?.repeatIntervalUnit ? 'error' : undefined}
          placeholder="默认同窗宽" options={calendarWindowUnits}
          onChange={(unit) => changeUnit(unit ?? null, true)} /> : <Select
          aria-label="固定重复单位"
          allowClear
          value={value.repeatIntervalUnit}
          options={spatialDurationUnitOptions}
          onChange={(repeatIntervalUnit) => onChange({ ...value, repeatIntervalUnit: repeatIntervalUnit ?? null })}
        />}
      </Space.Compact>
    </div>
    <Input
      value={value.referenceTime ?? ''}
      placeholder="参考时间（可选，ISO-8601）"
      onChange={(event) => onChange({ ...value, referenceTime: event.target.value || null })}
    />
    <Input
      value={value.timeZone}
      placeholder="IANA 时区，例如 Asia/Shanghai"
      onChange={(event) => onChange({ ...value, timeZone: event.target.value })}
    />
    <div className="canvas-spatial-pair-grid">
      <Input
        value={value.windowStartColumnName}
        placeholder="窗口开始字段"
        onChange={(event) => onChange({ ...value, windowStartColumnName: event.target.value })}
      />
      <Input
        value={value.windowEndColumnName}
        placeholder="窗口结束字段"
        onChange={(event) => onChange({ ...value, windowEndColumnName: event.target.value })}
      />
    </div>
  </Space>
  );
};
