import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeJdbcInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.JdbcInput>,
) => {
  const { tables } = data.configuration;
  if (tables.length === 0) return '请选择来源表';
  const preview = tables.slice(0, 2).map((table) => table.tableName).join('、');
  const tableText = tables.length <= 2 ? preview : `${preview} 等 ${tables.length} 张表`;
  return data.summary?.kind === 'JDBC'
    ? `${data.summary.dataSourceName} · ${tableText}`
    : `未知数据源 · ${tableText}`;
};
