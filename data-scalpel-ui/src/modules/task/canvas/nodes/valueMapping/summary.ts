import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { summarizeProcessorOperations } from '../operationSummary';

export const summarizeValueMapping = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.ValueMapping>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表并配置值映射');
