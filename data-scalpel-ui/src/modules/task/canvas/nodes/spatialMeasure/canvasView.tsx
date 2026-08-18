import { CanvasNodeType, type SpatialMeasurement } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const measurementField = (item: SpatialMeasurement): string => (
  item.kind === 'DISTANCE' ? `${item.leftGeometryColumnName} ↔ ${item.rightGeometryColumnName}` : item.geometryColumnName
);

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialMeasure>) => {
  const { sourceTableName, outputTableName, measurements } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表并配置空间测量</NodeEmpty>;
  const modes = [...new Set(measurements.flatMap((item) => 'mode' in item ? [item.mode] : []))];
  return <NodeContent variant="spatial">
    <NodeFlow source={sourceTableName} operation="MEASURE" target={outputTableName} />
    <NodePreviewList items={measurements.slice(0, 2).map((item, index) => ({ key: `${index}`, label: `${item.kind} · ${measurementField(item) || '字段'}`, value: '→', meta: item.outputColumnName || '输出字段' }))} total={measurements.length} />
    <NodeBadges>{modes.map((mode) => <NodeBadge key={mode} tone="spatial">{mode}</NodeBadge>)}<NodeBadge>{measurements.length} 个测量项</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const spatialMeasureCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.SpatialMeasure> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 344, maxHeight: 216, configured: Boolean(configuration.sourceTableName), listCount: configuration.measurements.length }),
  Body: body,
};
