import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { summarizeProcessorOperations } from '../operationSummary';

export const summarizeRename = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.Rename>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表并设置新名称');
