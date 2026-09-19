import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { summarizeProcessorOperations } from '../operationSummary';

export const summarizeSelectColumns = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SelectColumns>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表和保留字段');
