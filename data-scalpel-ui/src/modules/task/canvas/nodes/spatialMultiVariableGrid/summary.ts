import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSpatialMultiVariableGrid = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialMultiVariableGrid>,
) => {
  const configuration = data.configuration;
  if (configuration.variables.length === 0 || !configuration.outputTableName) {
    return '请配置多来源格网变量和输出表';
  }
  const sourceCount = new Set(configuration.variables.map(variable => variable.sourceTableName)
    .filter(Boolean)).size;
  return `${sourceCount} 张来源表 → ${configuration.outputTableName}`
    + ` · ${configuration.variables.length} 个变量`
    + ` · ${configuration.binShape === 'HEXAGON' ? '六边形' : '方格'}`;
};
