import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeHeroMetric } from '../../components/nodeView/CanvasNodePrimitives';
import { geometryColumn, inputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialTransform>) => {
  const { sourceTableName, outputTableName, geometryColumnName, targetCrs } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择 Geometry 字段和目标 CRS</NodeEmpty>;
  const sourceGeometry = inputTable(data, sourceTableName)?.columns.find((column) => column.name === geometryColumnName)?.geometry
    ?? geometryColumn(inputTable(data, sourceTableName))?.geometry;
  const sourceCrs = sourceGeometry ? `${sourceGeometry.crs.authority}:${sourceGeometry.crs.code}` : '等待源 CRS';
  const target = targetCrs ? `${targetCrs.authority}:${targetCrs.code}` : '待设置目标 CRS';
  return <NodeContent variant="spatial">
    <NodeFlow source={`${sourceTableName}.${geometryColumnName || '?'}`} operation="TRANSFORM" target={`${outputTableName || '?'}.${geometryColumnName || '?'}`} />
    <NodeHeroMetric value={`${sourceCrs} → ${target}`} label="坐标参考系转换" />
    <NodeBadges><NodeBadge tone="spatial">{sourceGeometry?.kind ?? 'GEOMETRY'}</NodeBadge><NodeBadge>{sourceGeometry?.dimension ?? '等待维度'}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const spatialTransformCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.SpatialTransform> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 320, maxHeight: 180, configured: Boolean(configuration.sourceTableName) }),
  Body: body,
};
