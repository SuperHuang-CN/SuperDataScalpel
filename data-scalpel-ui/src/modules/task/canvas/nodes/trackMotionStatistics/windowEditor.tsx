import { DownOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Checkbox, Form, Input, Modal, Select, Space, Table, Typography } from 'antd';
import type { CanvasColumnSchema, TrackMotionStatisticGroup, TrackMotionWindowOptions } from '../../canvasTypes';
import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import { spatialColumnOptions } from '../spatialInspectorOptions';
import { trackDistanceUnitOptions, trackDurationUnitOptions } from '../trackOptions';
import { spatialUnitHelp } from '../spatialUnits';
import { accelerationUnits, createMotionGroup, motionGroupLabels, motionStatisticGroups, selectedMotionGroups, speedUnits } from './windowOptions';

export default function MotionWindowEditor({ value, columns, onChange }: {
  value: TrackMotionWindowOptions; columns: CanvasColumnSchema[]; onChange: (value: TrackMotionWindowOptions) => void;
}) {
  const groups = selectedMotionGroups(value);
  const set = <K extends keyof TrackMotionWindowOptions>(key: K, next: TrackMotionWindowOptions[K]) => onChange({ ...value, [key]: next });
  const toggle = (group: TrackMotionStatisticGroup, enabled: boolean) => {
    if (enabled) set('statistics', [...value.statistics, ...createMotionGroup(group)]);
    else Modal.confirm({ title: `移除${motionGroupLabels[group]}指标组？`,
      content: '该组输出字段配置将删除，单位和其他组配置保留。', okText: '移除指标组', cancelText: '取消',
      onOk: () => set('statistics', value.statistics.filter(s => !(motionStatisticGroups[group] as readonly string[]).includes(s.kind))) });
  };
  const move = (from: number, to: number) => {
    const next = [...value.statistics]; const [item] = next.splice(from, 1); next.splice(to, 0, item); set('statistics', next);
  };
  const vertical = groups.includes('ELEVATION') || groups.includes('SLOPE');
  const linearUnits = trackDistanceUnitOptions.filter(unit => unit.value !== 'SOURCE_CRS_UNIT');
  return <Space orientation="vertical" style={{ width: '100%' }}>
    <Space wrap>{(Object.keys(motionStatisticGroups) as TrackMotionStatisticGroup[]).map(group =>
      <Checkbox key={group} checked={groups.includes(group)} onChange={event => toggle(group, event.target.checked)}>
        {motionGroupLabels[group]}
      </Checkbox>)}</Space>
    <Typography.Text type="secondary">{value.statistics.length} 个输出字段
      <ContextHelp ariaLabel="历史窗口指标说明" content="瞬时指标使用上一观测；N 点窗口最多含 N−1 个完整运动段。平均速度按有效段总距离/总时长；坡度是比值，不乘 100。静止要求相邻距离严格小于距离阈值且时长严格大于时间阈值。" />
    </Typography.Text>
    <Table size="small" pagination={false} rowKey="statisticId" dataSource={value.statistics} scroll={{ y: 300 }}
      columns={[
        { title: '指标', dataIndex: 'kind', width: 200 },
        { title: '输出字段', render: (_, item, index) => <Input aria-label={`输出字段 ${item.kind}`} value={item.outputColumnName}
          status={!item.outputColumnName || value.statistics.some((s, i) => i !== index
            && s.outputColumnName.toLowerCase() === item.outputColumnName.toLowerCase()) ? 'error' : undefined}
          onChange={event => set('statistics', value.statistics.map((s, i) => i === index ? { ...s, outputColumnName: event.target.value } : s))} /> },
        { title: '顺序', width: 80, render: (_, item, index) => <Space size={0}>
          <Button size="small" type="text" aria-label={`上移 ${item.kind}`} icon={<UpOutlined />} disabled={index === 0} onClick={() => move(index, index - 1)} />
          <Button size="small" type="text" aria-label={`下移 ${item.kind}`} icon={<DownOutlined />} disabled={index === value.statistics.length - 1} onClick={() => move(index, index + 1)} />
        </Space> },
      ]} />
    <div className="canvas-spatial-pair-grid">
      {groups.includes('DISTANCE') && <Form.Item label={<span className="canvas-inspector-field-label">距离输出单位<ContextHelp ariaLabel="运动距离单位说明" content={spatialUnitHelp} /></span>}><Select aria-label="距离输出单位" value={value.distanceUnit}
        options={trackDistanceUnitOptions} onChange={v => set('distanceUnit', v)} /></Form.Item>}
      {(groups.includes('DURATION') || groups.includes('IDLE')) && <Form.Item label="时长输出单位"><Select aria-label="时长输出单位"
        value={value.durationUnit} options={trackDurationUnitOptions} onChange={v => set('durationUnit', v)} /></Form.Item>}
      {groups.includes('SPEED') && <Form.Item label="速度输出单位"><Select aria-label="速度输出单位"
        value={value.speedUnit} options={[...speedUnits]} onChange={v => set('speedUnit', v)} /></Form.Item>}
      {groups.includes('ACCELERATION') && <Form.Item label="加速度输出单位"><Select aria-label="加速度输出单位"
        value={value.accelerationUnit} options={[...accelerationUnits]} onChange={v => set('accelerationUnit', v)} /></Form.Item>}
      {vertical && <>
        <Form.Item label="高程来源"><Select aria-label="高程来源" allowClear placeholder="Geometry Z"
          value={value.elevationColumnName ?? undefined}
          options={spatialColumnOptions(columns, value.elevationColumnName ?? '', c => c.fieldType !== 'GEOMETRY')}
          onChange={v => set('elevationColumnName', v ?? null)} /></Form.Item>
        <Form.Item label="输入高程单位" required><Select aria-label="输入高程单位" value={value.inputElevationUnit}
          options={linearUnits} onChange={v => set('inputElevationUnit', v)} /></Form.Item>
      </>}
      {groups.includes('ELEVATION') && <Form.Item label="高程输出单位"><Select aria-label="高程输出单位"
        value={value.elevationUnit} options={linearUnits} onChange={v => set('elevationUnit', v)} /></Form.Item>}
    </div>
  </Space>;
}
