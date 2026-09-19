import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { summarizeProcessorOperations } from '../operationSummary';

export const summarizeMaskFields = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.MaskFields>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表并配置字段脱敏');
