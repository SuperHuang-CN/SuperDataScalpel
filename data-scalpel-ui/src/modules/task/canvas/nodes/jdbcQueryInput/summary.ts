import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeJdbcQueryInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.JdbcQueryInput>,
) => {
  const { dataSourceId, outputTableName, outputColumns } = data.configuration;
  if (!dataSourceId || !outputTableName || outputColumns.length === 0) return '请分析 SQL 并设置输出表';
  return data.summary?.kind === 'JDBC'
    ? `${data.summary.dataSourceName} · ${outputTableName} · ${outputColumns.length} 个字段`
    : `${outputTableName} · ${outputColumns.length} 个字段`;
};
