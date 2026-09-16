import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSpatialAggregate = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialAggregate>,
) => {
  const {
    sourceTableName,
    outputTableName,
    groupByColumns,
    aggregations,
    dissolve,
  } = data.configuration;
  if (!sourceTableName || !outputTableName || aggregations.length === 0) {
    return '请选择来源表并配置空间聚合';
  }
  const kinds = [...new Set(aggregations.map((item) => item.kind))].join('/');
  if (dissolve?.enabled) {
    const grouping = dissolve.groupingMode === 'CONNECTED_COMPONENTS'
      ? 'Connected'
      : groupByColumns.length === 0 ? 'All' : 'List';
    return `${sourceTableName} → ${outputTableName} · Dissolve ${grouping} · ${dissolve.multipart ? 'Multipart' : 'Singlepart'} · ${dissolve.summaryStatistics.length} 项统计`;
  }
  return `${sourceTableName} → ${outputTableName} · ${groupByColumns.length} 个分组字段 · ${aggregations.length} 项 ${kinds}`;
};
