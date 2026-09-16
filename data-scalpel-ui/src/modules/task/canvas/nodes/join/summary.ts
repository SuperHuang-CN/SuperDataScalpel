import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeJoin = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.Join>,
) => {
  const { leftTableName, rightTableName, outputTableName, joinType } = data.configuration;
  return !leftTableName || !rightTableName || !outputTableName || !joinType
    ? '请配置 Join'
    : `${leftTableName} ${joinType} ${rightTableName} → ${outputTableName}`;
};
