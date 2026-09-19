import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeAggregate = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.Aggregate>,
) => {
  const { sourceTableName, outputTableName, groupByColumns, aggregations } = data.configuration;
  return !sourceTableName || !outputTableName
    ? '请选择来源表并配置聚合'
    : `${sourceTableName} → ${outputTableName} · ${groupByColumns.length} 个分组字段 · ${aggregations.length} 个指标`;
};
