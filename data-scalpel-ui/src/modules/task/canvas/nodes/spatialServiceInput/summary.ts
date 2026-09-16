import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSpatialServiceInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialServiceInput>,
) => {
  const { dataSourceId, resources } = data.configuration;
  if (!dataSourceId || resources.length === 0) return '请选择空间要素资源并设置输出表';
  const preview = resources.slice(0, 2).map((resource) => resource.outputTableName).join('、');
  return `${data.summary?.kind === 'HTTP_API' ? data.summary.dataSourceName : '空间服务'} · ${preview}${resources.length > 2 ? ` 等 ${resources.length} 个` : ''}`;
};
