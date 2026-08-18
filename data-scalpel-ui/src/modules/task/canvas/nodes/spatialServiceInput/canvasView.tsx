import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { fieldCountText, geometryColumn, geometryText, metadataObjectName, metadataSourceName, outputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialServiceInput>) => {
  const { dataSourceId, resourceId, outputTableName } = data.configuration;
  if (!dataSourceId && !resourceId) return <NodeEmpty>请选择空间要素资源</NodeEmpty>;
  const result = outputTable(data, outputTableName);
  const spatial = geometryText(geometryColumn(result));
  return <NodeContent variant="spatial">
    <NodeTitleLine primary={metadataSourceName(data)} secondary={metadataObjectName(data)} accent />
    <NodeFlow source="FEATURE" operation="SPATIAL" target={outputTableName || '待设置输出表'} />
    <NodeBadges>{spatial && <NodeBadge tone="spatial">{spatial}</NodeBadge>}<NodeBadge>{fieldCountText(result)}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const spatialServiceInputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.SpatialServiceInput> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 332, maxHeight: 196, configured: Boolean(configuration.dataSourceId || configuration.resourceId) }),
  Body: body,
};
