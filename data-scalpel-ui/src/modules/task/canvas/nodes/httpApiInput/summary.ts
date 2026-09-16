import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeHttpApiInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.HttpApiInput>,
) => {
  const { dataSourceId, resources } = data.configuration;
  if (!dataSourceId || resources.length === 0) return '请选择 API 资源并设置输出表';
  const preview = resources.slice(0, 2).map((resource) => resource.outputTableName).join('、');
  return `${data.summary?.kind === 'HTTP_API' ? data.summary.dataSourceName : 'API'} · ${preview}${resources.length > 2 ? ` 等 ${resources.length} 个` : ''}`;
};
