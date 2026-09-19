import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeUnion = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.Union>,
) => {
  const { inputTableNames, outputTableName, mode, mergingTables } = data.configuration;
  return inputTableNames.length < 2 || !outputTableName || !mode
    ? '请选择至少两张输入表'
    : `${inputTableNames.length} 张表 → ${outputTableName} · ${mode} · ${mergingTables === null ? '严格 Schema' : 'Merge Layers'}`;
};
