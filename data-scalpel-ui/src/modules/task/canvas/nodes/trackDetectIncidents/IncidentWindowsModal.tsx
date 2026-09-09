import { ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Input, InputNumber, Modal, Select, Space, Table, Tooltip, Typography } from 'antd';
import { useState } from 'react';
import { ContextHelp, InlineFeedback } from '../../../../../shared/components/ContextualFeedback';
import type { CanvasColumnSchema, TrackIncidentWindow } from '../../canvasTypes';
import { spatialColumnOptions } from '../spatialInspectorOptions';
import { incidentWindowErrors, incidentWindowKinds } from './conditionWindows';

type DraftRow = { key: string; value: TrackIncidentWindow };
export function IncidentWindowsModal({ value, columns, schemaAvailable = true, onSave, onCancel }: {
  value: TrackIncidentWindow[]; columns: CanvasColumnSchema[];
  schemaAvailable?: boolean;
  onSave: (value: TrackIncidentWindow[]) => void; onCancel: () => void;
}) {
  const [rows, setRows] = useState<DraftRow[]>(() => value.map(item => ({ key: crypto.randomUUID(), value: { ...item } })));
  const errors = incidentWindowErrors(rows.map(row => row.value), columns, schemaAvailable);
  const count = errors.reduce((sum, row) => sum + Object.keys(row).length, 0);
  const update = (index: number, patch: Partial<TrackIncidentWindow>) => setRows(current => current.map((row, i) =>
    i === index ? { ...row, value: { ...row.value, ...patch } } : row));
  const move = (index: number, offset: number) => setRows(current => {
    const next = [...current]; [next[index], next[index + offset]] = [next[index + offset], next[index]]; return next;
  });
  const remove = (index: number) => Modal.confirm({ title: `删除窗口指标 ${rows[index].value.bindingName || index + 1}？`,
    content: '引用它的开始或结束条件会保留，之后需要手动修正。', okText: '删除', cancelText: '取消', okButtonProps: { danger: true },
    onOk: () => setRows(current => current.filter((_, i) => i !== index)) });
  return <Modal open width={860} title="配置事件窗口指标" okText="保存窗口草稿" cancelText="取消"
    onOk={() => onSave(rows.map(row => row.value))} onCancel={onCancel}>
    <Space orientation="vertical" size={8} style={{ width: '100%' }}>
      <Space size={6}><Typography.Text>观测偏移 [起点, 终点)</Typography.Text>
        <ContextHelp ariaLabel="事件窗口指标说明" content={<>
          <p>0 是当前观测，负数为过去，正数为未来。左闭右开：[-5, 0) 为前 5 条，不含当前；[-1, 2) 为前一条、当前和后一条。</p>
          <p>先按轨迹、拆分片段及时间次序形成窗口；边缘窗口只取实际存在的观测。指标仅用于条件，不写入输出表，也不能相互引用。</p>
          <p>非空值数忽略 NULL；首末值保留 NULL；其他聚合忽略 NULL。空窗口除计数为 0 外均为 NULL。总体方差与总体标准差不是样本统计。</p>
          <p>这是受控字段窗口，不执行 Arcade 脚本；完整几何、运动与时间窗口表达式仍需后续补齐。统计结果类型由 Spark 解析。</p>
        </>} />
        <Button size="small" aria-label="添加窗口指标" icon={<PlusOutlined />} onClick={() => setRows(current => [...current, { key: crypto.randomUUID(),
          value: { bindingName: '', sourceColumnName: '', kind: 'MEAN', startOffset: -5, endOffset: 0 } }])}>添加窗口指标</Button>
      </Space>
      {count > 0 && <InlineFeedback tone="warning" label={`${count} 个窗口配置问题`} detail={errors.flatMap((row, index) =>
        Object.values(row).map(message => <div key={`${index}-${message}`}>第 {index + 1} 项：{message}</div>))} />}
      <Table<DraftRow> size="small" pagination={false} dataSource={rows} rowKey="key" scroll={{ y: 360 }} columns={[
        { title: '指标名', width: 130, render: (_, row, index) => <Tooltip title={errors[index].bindingName}>
          <Input size="small" autoComplete="off" aria-label={`窗口指标名 ${index + 1}`} value={row.value.bindingName}
            status={errors[index].bindingName ? 'error' : undefined} onChange={event => update(index, { bindingName: event.target.value })} /></Tooltip> },
        { title: '原始字段', width: 150, render: (_, row, index) => <Tooltip title={errors[index].sourceColumnName}>
          <Select size="small" showSearch optionFilterProp="label" style={{ width: '100%' }} aria-label={`窗口来源字段 ${index + 1}`}
            value={row.value.sourceColumnName || undefined} status={errors[index].sourceColumnName ? 'error' : undefined}
            options={schemaAvailable ? spatialColumnOptions(columns, row.value.sourceColumnName, () => true)
              : row.value.sourceColumnName ? [{ value: row.value.sourceColumnName, label: `${row.value.sourceColumnName}（等待解析）`, disabled: true }] : []}
            onChange={sourceColumnName => update(index, { sourceColumnName })} /></Tooltip> },
        { title: '函数', width: 126, render: (_, row, index) => <Select size="small" style={{ width: '100%' }}
          aria-label={`窗口函数 ${index + 1}`} options={incidentWindowKinds} value={row.value.kind} status={errors[index].kind ? 'error' : undefined}
          onChange={kind => update(index, { kind })} /> },
        { title: '起点含 / 终点不含', width: 170, render: (_, row, index) => <Space size={4}>
          <Tooltip title={errors[index].startOffset}><InputNumber size="small" style={{ width: 76 }} aria-label={`窗口起点 ${index + 1}`}
            value={row.value.startOffset} status={errors[index].startOffset ? 'error' : undefined} onChange={startOffset => update(index, { startOffset })} /></Tooltip>
          <Tooltip title={errors[index].endOffset}><InputNumber size="small" style={{ width: 76 }} aria-label={`窗口终点 ${index + 1}`}
            value={row.value.endOffset} status={errors[index].endOffset ? 'error' : undefined} onChange={endOffset => update(index, { endOffset })} /></Tooltip>
        </Space> },
        { title: '操作', width: 100, render: (_, row, index) => <Space size={0}>
          <Button type="text" size="small" aria-label={`上移窗口指标 ${index + 1}`} icon={<ArrowUpOutlined />} disabled={index === 0} onClick={() => move(index, -1)} />
          <Button type="text" size="small" aria-label={`下移窗口指标 ${index + 1}`} icon={<ArrowDownOutlined />} disabled={index === rows.length - 1} onClick={() => move(index, 1)} />
          <Button type="text" size="small" danger aria-label={`删除窗口指标 ${row.value.bindingName || index + 1}`} icon={<DeleteOutlined />} onClick={() => remove(index)} />
        </Space> },
      ]} />
    </Space>
  </Modal>;
}
