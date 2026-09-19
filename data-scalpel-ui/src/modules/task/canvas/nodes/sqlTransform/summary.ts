import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSqlTransform = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SqlTransform>,
) => {
  const { outputTableName, sql } = data.configuration;
  if (!outputTableName || !sql.trim()) return '请设置输出表并配置 SELECT 查询';
  const output = data.compilation?.outputTables.find((table) => table.name === outputTableName);
  return `${outputTableName} · ${output?.columns.length ?? 0} 个字段`;
};
