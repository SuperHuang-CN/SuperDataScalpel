import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeDualFlow, NodeEmpty, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { fieldCountText, outputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialJoin>) => {
  const { leftTableName, rightTableName, outputTableName, conditions } = data.configuration;
  if (!leftTableName && !rightTableName) return <NodeEmpty>请选择两张空间表</NodeEmpty>;
  const result = outputTable(data, outputTableName);
  return <NodeContent variant="dual">
    <NodeDualFlow left={leftTableName} right={rightTableName} leftLabel="LEFT GEOMETRY" rightLabel="RIGHT GEOMETRY" operation="SPATIAL JOIN" target={outputTableName} />
    <NodePreviewList items={conditions.slice(0, 2).map((item, index) => ({ key: `${index}`, label: item.leftGeometryColumnName || '左 Geometry', value: item.predicate ?? '?', meta: item.rightGeometryColumnName || '右 Geometry' }))} total={conditions.length} />
    <NodeBadges><NodeBadge tone="spatial">INNER</NodeBadge><NodeBadge>{conditions.length} 个空间谓词</NodeBadge><NodeBadge>{fieldCountText(result)}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const spatialJoinCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.SpatialJoin> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 368, maxHeight: 224, emptyHeight: 116, configured: Boolean(configuration.leftTableName || configuration.rightTableName), listCount: configuration.conditions.length }),
  Body: body,
};
