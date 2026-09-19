import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeDeriveColumns = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.DeriveColumns>,
) => {
  const operations = data.configuration.operations ?? [];
  const globalCount = data.configuration.globalDerivations?.length ?? 0;
  const localCount = operations.reduce((count, operation) => count + operation.derivations.length, 0);
  return operations.length === 0
    ? '请选择来源表并配置派生字段'
    : `${operations.length} 张处理表 · 全局 ${globalCount} 条 · 本表 ${localCount} 条`;
};
