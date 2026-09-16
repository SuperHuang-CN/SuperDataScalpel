import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { summarizeProcessorOperations } from '../operationSummary';

export const summarizeJsonExtract = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.JsonExtract>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择 JSON 来源字段并配置提取项');
