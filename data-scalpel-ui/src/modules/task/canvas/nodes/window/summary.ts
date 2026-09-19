import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeWindow = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.Window>,
) => {
  const {
    sourceTableName,
    outputTableName,
    partitionByColumns,
    orderBy,
    functions,
  } = data.configuration;
  if (!sourceTableName || !outputTableName) return '请选择来源表并配置窗口计算';
  const kinds = [...new Set(functions.map((item) => item.kind))].join('/');
  return `${sourceTableName} → ${outputTableName} · 分区 ${partitionByColumns.length} · 排序 ${orderBy.length} · ${functions.length} 个函数${kinds ? ` (${kinds})` : ''}`;
};
