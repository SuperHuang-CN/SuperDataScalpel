import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSpatialClip = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialClip>,
) => {
  const {
    sourceTableName,
    maskTableName,
    outputTableName,
    sourceGeometryColumnName,
    maskGeometryColumnName,
    outputColumnName,
    geometryPolicy,
    maskCombination,
  } = data.configuration;
  if (!sourceTableName || !maskTableName || !outputTableName
    || !sourceGeometryColumnName || !maskGeometryColumnName || !outputColumnName) {
    return '请选择来源 Geometry 和面状 Mask';
  }
  const policy = geometryPolicy === 'SOURCE_FAMILY_2D' ? '保持来源家族' : '通用 Geometry';
  const masks = maskCombination === 'DISSOLVE_ALL' ? '合并 Mask' : '逐条 Mask';
  return `${sourceTableName}.${sourceGeometryColumnName} ∩ ${maskTableName}.${maskGeometryColumnName} → ${outputTableName}.${outputColumnName} · ${policy} · ${masks}`;
};
