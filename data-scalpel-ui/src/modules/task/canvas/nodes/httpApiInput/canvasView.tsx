import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFieldCount, NodePreviewList, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { metadataSourceName, outputTable, resourceListNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.HttpApiInput>) => {
  const { dataSourceId, resources } = data.configuration;
  if (!dataSourceId && resources.length === 0) return <NodeEmpty>请选择 HTTP API 资源</NodeEmpty>;
  const parameterCount = resources.reduce((total, resource) => total + resource.runtimeParameters.length, 0);
  const items = resources.map((resource) => ({
    key: resource.resourceId,
    label: resource.outputTableName || resource.resourceId.slice(0, 8),
    value: resource.runtimeParameters.length > 0 ? `参数 ${resource.runtimeParameters.length}` : undefined,
    meta: <NodeFieldCount table={outputTable(data, resource.outputTableName)} />,
  }));
  return <NodeContent variant="source">
    <NodeTitleLine primary={metadataSourceName(data)} secondary={`${resources.length} 个 API 资源`} accent />
    <NodePreviewList items={items} total={items.length} limit={3} moreLabel={(remaining) => `另 ${remaining} 个资源`} />
    <NodeBadges><NodeBadge tone="info">HTTP API</NodeBadge><NodeBadge tone="strong">{resources.length} 张表</NodeBadge>{parameterCount > 0 && <NodeBadge tone="warning">参数 {parameterCount}</NodeBadge>}</NodeBadges>
  </NodeContent>;
};

export const httpApiInputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.HttpApiInput> = {
  resolveSize: (configuration) => resourceListNodeSize({ width: 344, count: configuration.resources.length }),
  Body: body,
};
