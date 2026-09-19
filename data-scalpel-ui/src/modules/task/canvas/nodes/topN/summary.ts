import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { summarizeProcessorOperations } from '../operationSummary';

export const summarizeTopN = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.TopN>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表并配置 Top N');
