import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { summarizeProcessorOperations } from '../operationSummary';

export const summarizeNullHandling = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.NullHandling>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表并配置空值处理');
