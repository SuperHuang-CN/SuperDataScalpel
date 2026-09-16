import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSpatialSimilarLocations = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialSimilarLocations>,
) => {
  const configuration = data.configuration;
  if (!configuration.referenceTableName || !configuration.candidateTableName
    || !configuration.outputTableName) {
    return '请选择参考位置、候选位置并设置输出表';
  }
  const method = configuration.matchMethod === 'ATTRIBUTE_PROFILES' ? '属性轮廓' : '属性值';
  const range = configuration.resultMode === 'LEAST_SIMILAR' ? '最不相似'
    : configuration.resultMode === 'BOTH' ? '相似与不相似两端' : '最相似';
  return `${configuration.referenceTableName} + ${configuration.candidateTableName}`
    + ` → ${configuration.outputTableName} · ${method} · ${range}`
    + ` · ${configuration.analysisFields.length} 个分析字段`;
};
