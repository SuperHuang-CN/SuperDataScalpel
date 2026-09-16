import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { summarizeProcessorOperations } from '../operationSummary';

export const summarizeTypeCast = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.TypeCast>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表并配置类型转换');
