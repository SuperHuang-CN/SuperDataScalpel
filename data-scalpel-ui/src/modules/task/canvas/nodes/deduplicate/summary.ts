import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { summarizeProcessorOperations } from '../operationSummary';

export const summarizeDeduplicate = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.Deduplicate>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表并配置去重');
