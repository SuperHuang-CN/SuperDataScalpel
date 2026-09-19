import { DeleteOutlined, DownOutlined, UpOutlined, WarningOutlined } from '@ant-design/icons';
import { Button, Input, Popconfirm, Select, Space, Table, Tooltip } from 'antd';
import { ContextHelp } from '../../../../../shared/components/ContextualFeedback';
import type { CanvasColumnSchema, SpatialWithinStatistic } from '../../canvasTypes';
import { spatialColumnOptions } from '../spatialInspectorOptions';
import { supportsWithinWeighting, withinStatisticNeedsSource, withinStatisticProblems, withinWeightedDispersionHelp } from './statisticOptions';

const kinds: SpatialWithinStatistic['kind'][] = [
  'COUNT', 'COUNT_FIELD', 'ANY', 'SUM', 'MEAN', 'MIN', 'MAX', 'RANGE', 'STDDEV', 'VARIANCE',
  'LENGTH_WITHIN', 'AREA_WITHIN',
];
const labels: Partial<Record<SpatialWithinStatistic['kind'], string>> = {
  COUNT: 'COUNT · 要素数', COUNT_FIELD: 'COUNT · 非空字段', ANY: 'ANY · 字符串样本',
};

export const WithinStatisticEditor = ({ value, columns, lineOrPolygon, onChange }: {
  value: SpatialWithinStatistic[];
  columns: CanvasColumnSchema[];
  lineOrPolygon?: boolean;
  onChange: (next: SpatialWithinStatistic[]) => void;
}) => {
  const update = (index: number, patch: Partial<SpatialWithinStatistic>) =>
    onChange(value.map((item, i) => i === index ? { ...item, ...patch } : item));
  const move = (from: number, to: number) => {
    const next = [...value];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    onChange(next);
  };
  return <Table<SpatialWithinStatistic>
    size="small" pagination={false} rowKey="statisticId" dataSource={value}
    tableLayout="fixed" scroll={{ x: 804 }}
    columns={[
      { title: '函数', width: 150, render: (_, item, index) => <Select
        aria-label={`统计 ${index + 1} 函数`} style={{ width: '100%' }} value={item.kind}
        options={kinds.map((kind) => ({ value: kind, label: labels[kind] ?? kind }))}
        onChange={(kind) => update(index, { kind })}
      /> },
      { title: '来源字段', width: 142, render: (_, item, index) => <Select
        aria-label={`统计 ${index + 1} 来源字段`} style={{ width: '100%' }}
        showSearch optionFilterProp="label" allowClear value={item.sourceColumnName}
        placeholder={withinStatisticNeedsSource(item.kind) ? '选择字段' : '无需字段'}
        status={item.sourceColumnName && (!withinStatisticNeedsSource(item.kind)
          || !columns.some((column) => column.name === item.sourceColumnName)) ? 'error' : undefined}
        options={spatialColumnOptions(columns, item.sourceColumnName ?? '', (column) =>
          column.fieldType !== 'GEOMETRY' && (item.kind !== 'ANY' || column.fieldType === 'STRING'))}
        onChange={(sourceColumnName) => update(index, { sourceColumnName: sourceColumnName ?? null })}
      /> },
      { title: <Space size={2}>数量处理<ContextHelp ariaLabel="数量分摊公式" content={
        '原值：直接使用 x，适用于率值/指数或不分摊统计。总量分摊：x′=p×x，p=交叠长度或面积/整个来源要素的长度或面积。零测度来源的分摊值为 NULL。'
      } /></Space>, width: 126, render: (_, item, index) => <Select
        aria-label={`统计 ${index + 1} 数量处理`} style={{ width: '100%' }}
        value={item.valueTreatment ?? 'ORIGINAL_VALUE'}
        status={withinStatisticProblems(item, lineOrPolygon).length > 0 ? 'error' : undefined}
        options={[
          { value: 'ORIGINAL_VALUE', label: '原值 / 率值' },
          { value: 'APPORTION_TOTAL', label: '总量分摊', disabled: lineOrPolygon === false
            || ['COUNT', 'COUNT_FIELD', 'ANY', 'LENGTH_WITHIN', 'AREA_WITHIN'].includes(item.kind) },
        ]}
        onChange={(valueTreatment) => update(index, { valueTreatment })}
      /> },
      { title: <Space size={2}>加权<ContextHelp ariaLabel="地理加权公式" content={withinWeightedDispersionHelp} /></Space>, width: 120, render: (_, item, index) => <Select
        aria-label={`统计 ${index + 1} 加权`} style={{ width: '100%' }}
        value={item.weighting ?? 'NONE'} options={[
          { value: 'NONE', label: '不加权' },
          { value: 'INTERSECTION_FRACTION', label: '交叠比例', disabled: !supportsWithinWeighting(item.kind)
            || lineOrPolygon === false || item.valueTreatment === 'APPORTION_TOTAL' },
        ]}
        onChange={(weighting) => update(index, { weighting })}
      /> },
      { title: '输出字段', width: 146, render: (_, item, index) => <Input
        aria-label={`统计 ${index + 1} 输出字段`} autoComplete="off" value={item.outputColumnName}
        status={!item.outputColumnName.trim() || value.some((other, i) => i !== index
          && other.outputColumnName.toLowerCase() === item.outputColumnName.toLowerCase()) ? 'error' : undefined}
        onChange={(event) => update(index, { outputColumnName: event.target.value })}
      /> },
      { title: '操作', width: 120, render: (_, item, index) => {
        const problems = withinStatisticProblems(item, lineOrPolygon);
        return <Space size={0}>
          {problems.length > 0 && <Tooltip title={problems.join('；')}>
            <Button type="text" danger size="small" icon={<WarningOutlined />}
              aria-label={`统计 ${index + 1} 有 ${problems.length} 个配置问题`} />
          </Tooltip>}
          <Tooltip title="上移"><Button type="text" size="small" icon={<UpOutlined />}
            aria-label={`上移统计 ${index + 1}`} disabled={index === 0} onClick={() => move(index, index - 1)} /></Tooltip>
          <Tooltip title="下移"><Button type="text" size="small" icon={<DownOutlined />}
            aria-label={`下移统计 ${index + 1}`} disabled={index === value.length - 1}
            onClick={() => move(index, index + 1)} /></Tooltip>
          <Popconfirm title={`删除统计 ${item.outputColumnName || index + 1}？`} description="该项字段与统计配置将一并删除。"
            onConfirm={() => onChange(value.filter((_, i) => i !== index))}>
            <Button type="text" danger size="small" icon={<DeleteOutlined />} aria-label={`删除统计 ${index + 1}`} />
          </Popconfirm>
        </Space>;
      } },
    ]}
  />;
};
