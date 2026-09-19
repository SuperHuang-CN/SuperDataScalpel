import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize, sortText } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.Deduplicate>) => {
  const operations = data.configuration.operations ?? [];
  if (operations.length === 0) return <NodeEmpty>请选择来源表并配置去重</NodeEmpty>;
  return <NodeContent variant="rules">
    <NodeFlow source={operations[0].sourceTableName} operation="DEDUP" target={operations[0].output.outputTableName ?? operations[0].sourceTableName} />
    <NodePreviewList items={operations.slice(0, 2).map((item) => ({ key: item.operationId, label: item.sourceTableName, value: item.keepStrategy ?? '待设置', meta: `${item.keyColumns.length || '全部'} 个键` }))} total={operations.length} />
    <NodeBadges><NodeBadge tone="strong">{operations.length} 张表</NodeBadge><NodeBadge>{sortText(operations[0].orderBy[0])}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const deduplicateCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.Deduplicate> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 344, maxHeight: 216, configured: (configuration.operations ?? []).length > 0, listCount: (configuration.operations ?? []).length }),
  Body: body,
};
