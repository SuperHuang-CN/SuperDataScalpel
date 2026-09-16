import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSpatialOverlay = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialOverlay>,
) => {
  const configuration = data.configuration;
  if (!configuration.leftTableName || !configuration.rightTableName
    || !configuration.outputTableName) {
    return '请选择左右图层和输出表';
  }
  const labels = { INTERSECTION: '相交', ERASE: '擦除', UNION: '联合', IDENTITY: '标识', SYMMETRICAL_DIFFERENCE: '对称差' } as const;
  return `${configuration.leftTableName} × ${configuration.rightTableName}`
    + ` · ${configuration.operation ? labels[configuration.operation] : '待选方式'}`
    + ` · ${configuration.outputColumns.filter((item) => item.included).length} 个属性字段`
    + ` → ${configuration.outputTableName}`;
};
