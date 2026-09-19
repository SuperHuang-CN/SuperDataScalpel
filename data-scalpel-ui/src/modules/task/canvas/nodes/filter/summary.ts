import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { summarizeProcessorOperations } from '../operationSummary';

export const summarizeFilter = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.Filter>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表并配置筛选条件');
