import { DownOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Checkbox, Input, Modal, Space, Table, Tooltip, Typography } from 'antd';
import { useState } from 'react';
import type { CanvasColumnSchema, SpatialCenterFeatureColumn } from '../../canvasTypes';
import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';

function suggestCenterFeatureColumns(columns: CanvasColumnSchema[], geometry: string): SpatialCenterFeatureColumn[] {
  return columns.filter(c => c.name !== geometry).map(c => ({ sourceColumnName: c.name, outputColumnName: c.name, included: true }));
}

export default function FeatureColumnsModal({ value, columns, geometry, outputGeometry, onSave, onCancel }: {
  value: SpatialCenterFeatureColumn[] | null | undefined;
  columns: CanvasColumnSchema[];
  geometry: string;
  outputGeometry: string;
  onSave: (fields: SpatialCenterFeatureColumn[]) => void;
  onCancel: () => void;
}) {
  const [draft, setDraft] = useState(() => (value ?? suggestCenterFeatureColumns(columns, geometry)).map(c => ({ ...c })));
  const sources = new Set(columns.map(c => c.name));
  const update = (index: number, field: SpatialCenterFeatureColumn) => setDraft(items => items.map((item, i) => i === index ? field : item));
  const move = (index: number, offset: number) => setDraft(items => {
    const next = [...items]; const [field] = next.splice(index, 1); next.splice(index + offset, 0, field); return next;
  });
  const invalidName = (field: SpatialCenterFeatureColumn) => field.included && (!field.outputColumnName.trim()
    || field.outputColumnName.toLowerCase() === outputGeometry.toLowerCase()
    || draft.filter(c => c.included && c.outputColumnName.toLowerCase() === field.outputColumnName.toLowerCase()).length > 1);
  return <Modal open width={760} title="中央要素 · 原始字段" okText="保存字段草稿" cancelText="取消" onCancel={onCancel} onOk={() => onSave(draft)}>
    <Space orientation="vertical" style={{ width: '100%' }}>
      <Space wrap>
        <Typography.Text>保留 {draft.filter(c => c.included).length} 个原始字段</Typography.Text>
        <ContextHelp ariaLabel="中央要素字段说明" presentation="popover" content="字段均来自同一条选中的原始记录。分组和 ID 也可以排除或改名；结果 Geometry 始终单独输出。保留来源事件时间字段时同步改名元数据，排除时清除；不产生 Watermark。切换分析类型或旧宽表模式保留但不使用此配置。" />
        <Button size="small" disabled={!columns.length} onClick={() => Modal.confirm({ title: '按当前来源重建字段建议？',
          content: '将替换当前排除、改名和顺序设置；结果 Geometry 不重复添加。', okText: '重建建议', cancelText: '取消',
          onOk: () => setDraft(suggestCenterFeatureColumns(columns, geometry)) })}>重建建议</Button>
      </Space>
      <Table size="small" pagination={false} scroll={{ y: 360 }} rowKey="rowIndex" dataSource={draft.map((field, rowIndex) => ({ ...field, rowIndex }))} columns={[
        { title: '保留', width: 52, render: (_, field) => <Checkbox aria-label={`保留原字段 ${field.rowIndex + 1}`} checked={field.included}
          onChange={e => update(field.rowIndex, { sourceColumnName: field.sourceColumnName, outputColumnName: field.outputColumnName, included: e.target.checked })} /> },
        { title: '原始字段', ellipsis: true, render: (_, field) => <Tooltip title={field.sourceColumnName}>
          <Typography.Text type={field.included && !sources.has(field.sourceColumnName) ? 'danger' : undefined}>{field.sourceColumnName}{!sources.has(field.sourceColumnName) && '（不可用）'}</Typography.Text>
        </Tooltip> },
        { title: '输出字段', render: (_, field) => <Input autoComplete="off" aria-label={`原字段输出名 ${field.rowIndex + 1}`} value={field.outputColumnName}
          status={invalidName(field) ? 'error' : undefined} disabled={!field.included} onChange={e => update(field.rowIndex, {
            sourceColumnName: field.sourceColumnName, outputColumnName: e.target.value, included: field.included,
          })} /> },
        { title: '顺序', width: 76, render: (_, field) => <Space size={0}>
          <Tooltip title="上移"><Button type="text" size="small" aria-label={`上移原字段 ${field.rowIndex + 1}`} icon={<UpOutlined />} disabled={field.rowIndex === 0} onClick={() => move(field.rowIndex, -1)} /></Tooltip>
          <Tooltip title="下移"><Button type="text" size="small" aria-label={`下移原字段 ${field.rowIndex + 1}`} icon={<DownOutlined />} disabled={field.rowIndex === draft.length - 1} onClick={() => move(field.rowIndex, 1)} /></Tooltip>
        </Space> },
      ]} />
      <Typography.Text type="secondary">另输出结果 Geometry：{outputGeometry || '待设置'}。取消不修改 Inspector 草稿。</Typography.Text>
    </Space>
  </Modal>;
}
