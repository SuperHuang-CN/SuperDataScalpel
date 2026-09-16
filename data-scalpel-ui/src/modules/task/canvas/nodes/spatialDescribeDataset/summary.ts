import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSpatialDescribeDataset = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialDescribeDataset>,
) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName
    || !configuration.statisticsTableName
    || !configuration.descriptionTableName) {
    return '请选择来源表并设置字段统计和数据集描述表';
  }
  const resultCount = 2 + Number(configuration.sampleSize > 0)
    + Number(configuration.extentOutput);
  return `${configuration.sourceTableName} → ${resultCount} 个剖析结果`
    + `${configuration.sampleSize > 0 ? ` · 样本 ${configuration.sampleSize} 行` : ''}`
    + `${configuration.extentOutput ? ' · 空间范围' : ''}`;
};
