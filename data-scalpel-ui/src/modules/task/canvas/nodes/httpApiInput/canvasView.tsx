import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { fieldCountText, metadataObjectName, metadataSourceName, outputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.HttpApiInput>) => {
  const { dataSourceId, resourceId, outputTableName, runtimeParameters } = data.configuration;
  if (!dataSourceId && !resourceId) return <NodeEmpty>请选择 HTTP API 资源</NodeEmpty>;
  const result = outputTable(data, outputTableName);
  return <NodeContent variant="source">
    <NodeTitleLine primary={metadataSourceName(data)} secondary={metadataObjectName(data)} accent />
    <NodeFlow source="HTTP API" operation="REQUEST" target={outputTableName || '待设置输出表'} />
    <NodeBadges><NodeBadge tone="info">{runtimeParameters.length} 个运行参数</NodeBadge><NodeBadge>{fieldCountText(result)}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const httpApiInputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.HttpApiInput> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 320, maxHeight: 184, configured: Boolean(configuration.dataSourceId || configuration.resourceId) }),
  Body: body,
};
