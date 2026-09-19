import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeHeroMetric } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';
import { spatialDistanceUnitLabels } from '../spatialUnits';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.GeometryBuffer>) => {
  const { sourceTableName, outputTableName, geometryColumnName, outputColumnName, distance,
    mode, distanceUnit, distanceFieldName } = data.configuration;
  const effectiveUnit = distanceUnit ?? (mode === 'SPHEROID' ? 'METERS' : 'SOURCE_CRS_UNIT');
  const distanceSource = data.configuration.distanceSource ?? 'CONSTANT';
  const distanceValue = distanceSource === 'CONSTANT'
    ? `${distance} ${spatialDistanceUnitLabels[effectiveUnit]}`
    : distanceSource === 'FIELD'
      ? `${distanceFieldName || '?'} · ${spatialDistanceUnitLabels[effectiveUnit]}`
      : `逐行表达式 · ${spatialDistanceUnitLabels[effectiveUnit]}`;
  const distanceLabel = distanceSource === 'CONSTANT' ? '固定距离'
    : distanceSource === 'FIELD' ? '距离字段' : '动态距离';
  if (!sourceTableName) return <NodeEmpty>请选择 Geometry 字段并设置 Buffer</NodeEmpty>;
  return <NodeContent variant="spatial">
    <NodeFlow source={`${sourceTableName}.${geometryColumnName || '?'}`} operation="BUFFER" target={`${outputTableName || '?'}.${outputColumnName || '?'}`} />
    <NodeHeroMetric value={distanceValue} label={distanceLabel} />
    <NodeBadges><NodeBadge tone="spatial">◎ BUFFER</NodeBadge><NodeBadge tone="strong">{mode}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const geometryBufferCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.GeometryBuffer> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 320, maxHeight: 188, configured: Boolean(configuration.sourceTableName) }),
  Body: body,
};
