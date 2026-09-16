import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSpatialCenterDispersion = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialCenterDispersion>,
) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName || configuration.resultMode !== 'ANALYSIS_TABLES' && !configuration.outputTableName) {
    return '请选择来源并设置中心与离散输出';
  }
  return `${configuration.sourceTableName} → ${configuration.resultMode === 'ANALYSIS_TABLES' ? `${configuration.analyses.length} 张独立结果表` : configuration.outputTableName}`
    + ` · ${configuration.analyses.length} 个分析项`
    + ` · ${configuration.groupByColumns.length > 0
      ? `${configuration.groupByColumns.length} 个分组字段` : '全局统计'}`
    + `${configuration.weightColumnName ? ' · 加权' : ''}`;
};
