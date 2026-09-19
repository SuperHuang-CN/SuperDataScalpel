import { CanvasNodeType } from '../../canvasTypes';
import {
  NodeBadge,
  NodeBadges,
  NodeContent,
  NodeEmpty,
  NodeFlow,
  NodeHeroMetric,
  NodePreviewList,
} from '../../components/nodeView/CanvasNodePrimitives';
import { inputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialDescribeDataset>) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName) {
    return <NodeEmpty>请选择需要剖析的来源表</NodeEmpty>;
  }
  const source = inputTable(data, configuration.sourceTableName);
  const profileFieldCount = source?.columns.filter(column => (
    column.fieldType !== 'GEOMETRY' && column.fieldType !== 'BINARY'
  )).length;
  const results = [
    { key: 'statistics', label: '字段统计', value: '→', meta: configuration.statisticsTableName || '待设置' },
    { key: 'description', label: '数据集描述', value: '→', meta: configuration.descriptionTableName || '待设置' },
    ...(configuration.sampleSize > 0 ? [{
      key: 'sample', label: `样本 ${configuration.sampleSize} 行`, value: '→',
      meta: configuration.sampleTableName || '待设置',
    }] : []),
    ...(configuration.extentOutput ? [{
      key: 'extent', label: '空间范围', value: '→', meta: configuration.extentTableName || '待设置',
    }] : []),
  ];
  return <NodeContent variant="spatial">
    <NodeFlow source={configuration.sourceTableName} operation="剖析" target={`${results.length} 个结果`} />
    <NodePreviewList items={results} total={results.length} moreLabel={count => `另 ${count} 个结果`} />
    <NodeHeroMetric value={profileFieldCount ?? '—'} label="可统计字段" />
    <NodeBadges>
      <NodeBadge tone="spatial">字段统计 + 描述</NodeBadge>
      {configuration.sampleSize > 0 && <NodeBadge>样本 {configuration.sampleSize} 行</NodeBadge>}
      {configuration.extentOutput && <NodeBadge tone="strong">XY 范围</NodeBadge>}
      {configuration.geometryColumnName && <NodeBadge>{configuration.geometryColumnName}</NodeBadge>}
    </NodeBadges>
  </NodeContent>;
};

export const spatialDescribeDatasetCanvasView: CanvasNodeCanvasView<
  typeof CanvasNodeType.SpatialDescribeDataset
> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 376,
    maxHeight: 232,
    configured: Boolean(configuration.sourceTableName),
    listCount: 2 + Number(configuration.sampleSize > 0) + Number(configuration.extentOutput),
  }),
  Body: body,
};
