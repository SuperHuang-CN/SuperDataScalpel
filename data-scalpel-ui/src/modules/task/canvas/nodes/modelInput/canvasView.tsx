import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFieldCount, NodePreviewList, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { resourceListNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.ModelInput>) => {
  const models = data.configuration.models;
  if (models.length === 0) return <NodeEmpty>请选择一个或多个来源模型</NodeEmpty>;
  const summary = data.summary?.kind === 'MODEL' ? data.summary : null;
  const items = models.map((model, index) => {
    const table = data.compilation?.outputTables[index];
    return {
      key: model.modelId,
      label: table?.name ?? (index === 0 ? summary?.modelCode : null) ?? model.modelId.slice(0, 8),
      value: table?.origin?.kind === 'MODEL' ? `v${table.origin.modelSchemaVersion}` : undefined,
      meta: <NodeFieldCount table={table} />,
    };
  });
  return <NodeContent variant="source">
    <NodeTitleLine
      primary={models.length === 1 ? summary?.modelName ?? '等待模型元数据' : `${models.length} 个来源模型`}
      secondary={models.length === 1 ? summary?.dataSourceName : '允许跨数据源'}
      accent
    />
    <NodePreviewList items={items} total={models.length} limit={3} moreLabel={(remaining) => `另 ${remaining} 个模型`} />
    <NodeBadges><NodeBadge tone="info">模型输入</NodeBadge><NodeBadge tone="strong">{models.length} 张表</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const modelInputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.ModelInput> = {
  resolveSize: (configuration) => resourceListNodeSize({ width: 336, count: configuration.models.length }),
  Body: body,
};
