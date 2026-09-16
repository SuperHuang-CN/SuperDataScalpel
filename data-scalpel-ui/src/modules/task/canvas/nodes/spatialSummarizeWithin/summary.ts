import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { usesLinkedWithinGroups } from "./groupResult";

export const summarizeSpatialSummarizeWithin = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialSummarizeWithin>,
) => {
  const configuration = data.configuration;
  const grid = configuration.regions?.mode === 'PLANAR_GRID';
  if ((!grid && !configuration.areaTableName) || !configuration.summaryTableName
    || !configuration.outputTableName) {
    return '请选择区域表、被汇总要素表和输出表';
  }
  return `${grid ? '规则格网' : configuration.areaTableName} × ${configuration.summaryTableName}`
    + ` · ${configuration.statistics.length} 个统计项`
    + ` · 分摊 ${configuration.statistics.filter((item) => item.valueTreatment === 'APPORTION_TOTAL').length}`
    + ` / 加权 ${configuration.statistics.filter((item) => item.weighting === 'INTERSECTION_FRACTION').length}`
    + `${configuration.groupSummary ? usesLinkedWithinGroups(configuration) ? ' · 关联双表' : ' · 扁平分组' : ''}`
    + `${configuration.temporalSlicing ? ' · 时间切片' : ''}`
    + ` → ${configuration.outputTableName}`
    + (usesLinkedWithinGroups(configuration) ? ` / ${configuration.groupResult?.outputTableName || '待配置组表'}` : '');
};
