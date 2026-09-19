import { createUuid } from '../../../../../shared/browser/createUuid';
import { ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Input, InputNumber, Modal, Select, Space, Table, Tooltip, Typography } from 'antd';
import { useState } from 'react';
import { ContextHelp, InlineFeedback } from '../../../../../shared/components/ContextualFeedback';
import type { CanvasColumnSchema, TrackIncidentScalar, TrackIncidentWindow } from '../../canvasTypes';
import { incidentScalarErrors, incidentScalarSources, isIncidentPointCoordinateScalar } from './conditionWindows';

type DraftRow = { key: string; value: TrackIncidentScalar };

export function IncidentScalarsModal({ value, columns, windows, pointGeometryColumnName = null,
  schemaAvailable = true, onSave, onCancel }: {
  value: TrackIncidentScalar[];
  columns: CanvasColumnSchema[];
  windows: TrackIncidentWindow[];
  pointGeometryColumnName?: string | null;
  schemaAvailable?: boolean;
  onSave: (value: TrackIncidentScalar[]) => void;
  onCancel: () => void;
}) {
  const [rows, setRows] = useState<DraftRow[]>(() => value.map(item => ({
    key: createUuid(), value: { ...item },
  })));
  const errors = incidentScalarErrors(rows.map(row => row.value), columns, windows,
    schemaAvailable, pointGeometryColumnName);
  const count = errors.reduce((sum, row) => sum + Object.keys(row).length, 0);
  const update = (index: number, patch: Partial<TrackIncidentScalar>) => setRows(current => current.map((row, i) => (
    i === index ? { ...row, value: { ...row.value, ...patch } } : row
  )));
  const move = (index: number, offset: number) => setRows(current => {
    const next = [...current];
    [next[index], next[index + offset]] = [next[index + offset], next[index]];
    return next;
  });
  const remove = (index: number) => Modal.confirm({
    title: `删除轨迹标量 ${rows[index].value.bindingName || index + 1}？`,
    content: '引用它的开始或结束条件会保留，之后需要手动修正。',
    okText: '删除', cancelText: '取消', okButtonProps: { danger: true },
    onOk: () => setRows(current => current.filter((_, i) => i !== index)),
  });
  return <Modal open width={680} title="配置轨迹条件标量" okText="保存标量草稿" cancelText="取消"
    onOk={() => onSave(rows.map(row => row.value))} onCancel={onCancel}>
    <Space orientation="vertical" size={8} style={{ width: '100%' }}>
      <Space size={6}><Typography.Text>按当前轨迹片段逐观测计算</Typography.Text>
        <ContextHelp ariaLabel="轨迹条件标量说明" content={<>
          <p>开始时间和当前时间使用 Unix Epoch 毫秒；轨迹时长是片段起点到当前观测的毫秒数；观测序号从 0 开始。</p>
          <p>Point X/Y 可读取当前片段内相对观测的坐标：0 是当前，负数回看，正数前看；超出片段或 Geometry 为空时结果为 NULL，坐标单位跟随来源 CRS。</p>
          <p>固定时间边界、时间/距离间隔形成的新片段会重新计算开始时间、时长和序号。标量仅用于开始/结束条件，不写入输出表。</p>
          <p>这是 ArcGIS TrackStartTime、TrackDuration、TrackCurrentTime、TrackIndex 与 TrackGeometryWindow 点坐标访问的受控入口，不返回 Geometry 数组，也不执行 Arcade 脚本。</p>
        </>} />
        <Button size="small" aria-label="添加轨迹标量" icon={<PlusOutlined />} onClick={() => setRows(current => [
          ...current, { key: createUuid(), value: { bindingName: '', source: 'TRACK_DURATION' } },
        ])}>添加轨迹标量</Button>
      </Space>
      {count > 0 && <InlineFeedback tone="warning" label={`${count} 个标量配置问题`} detail={errors.flatMap((row, index) => (
        Object.values(row).map(message => <div key={`${index}-${message}`}>第 {index + 1} 项：{message}</div>)
      ))} />}
      <Table<DraftRow> size="small" pagination={false} dataSource={rows} rowKey="key" scroll={{ y: 320 }} columns={[
        { title: '条件字段名', width: 170, render: (_, row, index) => <Tooltip title={errors[index].bindingName}>
          <Input size="small" autoComplete="off" aria-label={`轨迹标量名 ${index + 1}`} value={row.value.bindingName}
            status={errors[index].bindingName ? 'error' : undefined}
            onChange={event => update(index, { bindingName: event.target.value })} /></Tooltip> },
        { title: '标量来源', width: 220, render: (_, row, index) => <Tooltip title={errors[index].source}>
          <Select size="small" style={{ width: '100%' }} aria-label={`轨迹标量来源 ${index + 1}`}
            value={row.value.source} status={errors[index].source ? 'error' : undefined}
            options={incidentScalarSources} onChange={source => update(index, {
              source,
              ...(source === 'TRACK_POINT_X_AT' || source === 'TRACK_POINT_Y_AT'
                ? { offset: row.value.offset ?? 0 } : {}),
            })} /></Tooltip> },
        { title: '观测偏移', width: 96, render: (_, row, index) => isIncidentPointCoordinateScalar(row.value)
          ? <Tooltip title={errors[index].offset}><InputNumber size="small" controls={false} precision={0}
              style={{ width: '100%' }} aria-label={`轨迹坐标观测偏移 ${index + 1}`}
              value={row.value.offset} status={errors[index].offset ? 'error' : undefined}
              onChange={offset => update(index, { offset })} /></Tooltip>
          : <Typography.Text type="secondary">—</Typography.Text> },
        { title: '操作', width: 100, render: (_, row, index) => <Space size={0}>
          <Button type="text" size="small" aria-label={`上移轨迹标量 ${index + 1}`} icon={<ArrowUpOutlined />}
            disabled={index === 0} onClick={() => move(index, -1)} />
          <Button type="text" size="small" aria-label={`下移轨迹标量 ${index + 1}`} icon={<ArrowDownOutlined />}
            disabled={index === rows.length - 1} onClick={() => move(index, 1)} />
          <Button type="text" size="small" danger aria-label={`删除轨迹标量 ${row.value.bindingName || index + 1}`}
            icon={<DeleteOutlined />} onClick={() => remove(index)} />
        </Space> },
      ]} />
    </Space>
  </Modal>;
}
