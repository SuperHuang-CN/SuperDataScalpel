import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Input, InputNumber, Popconfirm, Select, Space, Switch, Table, Typography } from 'antd';
import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import type { CanvasColumnSchema, TrackFieldWindowBinding, TrackSplitExpression } from '../../canvasTypes';
import { spatialColumnOptions } from '../spatialInspectorOptions';

export const TrackSplitEditor = ({ value, columns, onChange }: {
  value: TrackSplitExpression; columns: CanvasColumnSchema[]; onChange: (value: TrackSplitExpression) => void;
}) => {
  const update = (index: number, patch: Partial<TrackFieldWindowBinding>) => onChange({ ...value,
    bindings: value.bindings.map((item, i) => i === index ? { ...item, ...patch } : item) });
  const error = (item: TrackFieldWindowBinding, index: number) => {
    const messages: string[] = [];
    if (!/^[A-Za-z_][A-Za-z0-9_]{0,127}$/.test(item.name)) messages.push('绑定名格式错误');
    if (columns.some(c => c.name.toLowerCase() === item.name.toLowerCase())
      || value.bindings.some((b, i) => i !== index && b.name.toLowerCase() === item.name.toLowerCase())) messages.push('绑定名重复');
    if (!columns.some(c => c.name === item.sourceColumnName)) messages.push('来源字段未配置或失效');
    if (item.offset == null || !Number.isInteger(item.offset) || Math.abs(item.offset) > 1000) messages.push('偏移应为 -1000～1000 的整数');
    return messages;
  };
  return <Space orientation="vertical" size={10} style={{ width: '100%' }}>
    <Space><Switch aria-label="启用轨迹表达式拆分" checked={value.enabled !== false}
      onChange={enabled => onChange({ ...value, enabled })} /><Typography.Text>表达式拆分</Typography.Text>
      <ContextHelp ariaLabel="轨迹拆分表达式说明" content={<>
        <p>填写单个 Spark SQL 布尔表达式。true 从当前观测前拆分，false/NULL 不拆分。不是 Arcade 脚本，不允许完整 SQL、OVER 或随机条件。</p>
        <p>可直接使用来源字段；窗口绑定的 -1 表示上一个观测，0 为当前，1 为下一个。按轨迹和时间/次序字段排序，不跨固定周期；越界为 NULL。</p>
        <p>例如绑定 previous_speed 到 speed、偏移 -1，可写 previous_speed * 2 &lt; speed。时间和 Geometry 字段也可绑定并用于受支持的 Spark/Sedona 函数。</p>
      </>} /></Space>
    <Input.TextArea aria-label="轨迹拆分表达式" autoComplete="off" autoSize={{ minRows: 3, maxRows: 8 }}
      value={value.expression} placeholder="previous_speed * 2 < speed"
      status={value.enabled !== false && !value.expression.trim() ? 'error' : undefined}
      onChange={event => onChange({ ...value, expression: event.target.value })} />
    <Table size="small" pagination={false} rowKey="index" dataSource={value.bindings.map((item, index) => ({ ...item, index }))}
      columns={[
        { title: '绑定名', width: 185, render: (_, row) => <Input aria-label={`绑定 ${row.index + 1} 名称`} autoComplete="off"
          value={row.name} onChange={event => update(row.index, { name: event.target.value })} /> },
        { title: '来源字段', width: 220, render: (_, row) => <Select aria-label={`绑定 ${row.index + 1} 来源`} style={{ width: '100%' }}
          value={row.sourceColumnName || undefined} showSearch optionFilterProp="label"
          options={spatialColumnOptions(columns, row.sourceColumnName, () => true)} onChange={sourceColumnName => update(row.index, { sourceColumnName })} /> },
        { title: '观测偏移', width: 110, render: (_, row) => <InputNumber aria-label={`绑定 ${row.index + 1} 偏移`} style={{ width: '100%' }}
          value={row.offset} onChange={offset => update(row.index, { offset })} /> },
        { title: '状态', width: 70, render: (_, row) => error(row, row.index).length > 0
          ? <ContextHelp ariaLabel={`绑定 ${row.index + 1} 配置问题`} content={error(row, row.index).join('；')} /> : '—' },
        { title: '', width: 36, render: (_, row) => <Popconfirm title={`删除绑定 ${row.name || row.index + 1}？`} description="表达式中的引用不会自动替换。"
          onConfirm={() => onChange({ ...value, bindings: value.bindings.filter((_, i) => i !== row.index) })}>
          <Button type="text" danger icon={<DeleteOutlined />} aria-label={`删除绑定 ${row.index + 1}`} /></Popconfirm> },
      ]} />
    <Button size="small" aria-label="添加窗口绑定" icon={<PlusOutlined />} disabled={value.bindings.length >= 32}
      onClick={() => onChange({ ...value, bindings: [...value.bindings, { name: '', sourceColumnName: '', offset: -1 }] })}>添加窗口绑定</Button>
    {value.bindings.some((b, i) => error(b, i).length > 0) && <Typography.Text type="danger">窗口绑定有配置问题，仍可保存草稿。</Typography.Text>}
  </Space>;
};
