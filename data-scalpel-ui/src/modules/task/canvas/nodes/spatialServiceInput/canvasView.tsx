import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFieldCount, NodePreviewList, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { geometryColumn, geometryText, metadataSourceName, outputTable, resourceListNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialServiceInput>) => {
  const { dataSourceId, resources } = data.configuration;
  if (!dataSourceId && resources.length === 0) return <NodeEmpty>请选择空间要素资源</NodeEmpty>;
  const items = resources.map((resource) => {
    const table = outputTable(data, resource.outputTableName);
    return {
      key: resource.resourceId,
      label: resource.outputTableName || resource.resourceId.slice(0, 8),
      value: geometryText(geometryColumn(table)) ?? 'FEATURE',
      meta: <NodeFieldCount table={table} />,
    };
  });
  return <NodeContent variant="spatial">
    <NodeTitleLine primary={metadataSourceName(data)} secondary={`${resources.length} 个空间资源`} accent />
    <NodePreviewList items={items} total={items.length} limit={3} moreLabel={(remaining) => `另 ${remaining} 个资源`} />
    <NodeBadges><NodeBadge tone="spatial">空间服务</NodeBadge><NodeBadge tone="strong">{resources.length} 张表</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const spatialServiceInputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.SpatialServiceInput> = {
  resolveSize: (configuration) => resourceListNodeSize({ width: 360, count: configuration.resources.length }),
  Body: body,
};
