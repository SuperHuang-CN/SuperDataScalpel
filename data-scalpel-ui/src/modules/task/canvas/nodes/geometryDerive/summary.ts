import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeGeometryDerive = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.GeometryDerive>,
) => {
  const { sourceTableName, outputTableName, derivations } = data.configuration;
  if (!sourceTableName || !outputTableName || derivations.length === 0) {
    return '请选择来源表并配置 Geometry 派生字段';
  }
  const kinds = [...new Set(derivations.map((item) => item.kind))];
  return `${sourceTableName} → ${outputTableName} · ${derivations.length} 个派生字段 · ${kinds.join('/')}`;
};
