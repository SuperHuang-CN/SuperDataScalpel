import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeStreamJoin = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.StreamJoin>,
) => {
  const {
    leftTableName,
    rightTableName,
    outputTableName,
    joinType,
    outputColumns,
  } = data.configuration;
  return !leftTableName || !rightTableName || !outputTableName || !joinType
    ? '请配置流-维 Join'
    : `${leftTableName} ${joinType} ${rightTableName} → ${outputTableName} · ${outputColumns.filter((column) => column.included).length} 个输出字段`;
};
