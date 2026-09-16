import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeModelInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.ModelInput>,
) => {
  const models = data.configuration.models;
  if (models.length === 0) return '请选择来源模型';
  const preview = models.slice(0, 2).map((model) => model.modelId).join('、');
  return models.length <= 2 ? `模型 ${preview}` : `模型 ${preview} 等 ${models.length} 个`;
};
