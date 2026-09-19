import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { filterPredicates, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.Filter>) => {
  const operations = data.configuration.operations ?? [];
  if (operations.length === 0) return <NodeEmpty>请选择来源表并配置筛选</NodeEmpty>;
  const ruleCount = operations.reduce((count, operation) => (
    count + (operation.mode === 'SQL_EXPRESSION' ? 1 : filterPredicates(operation.condition).length)
  ), 0);
  return <NodeContent variant="rules">
    <NodeFlow source={operations[0].sourceTableName} operation="FILTER" target={operations[0].output.outputTableName ?? operations[0].sourceTableName} />
    <NodePreviewList items={operations.slice(0, 2).map((item) => ({ key: item.operationId, label: item.sourceTableName, value: item.output.mode === 'REPLACE_SOURCE' ? '更新原表' : '生成新表', meta: item.mode === 'SQL_EXPRESSION' ? 'SQL 表达式' : `${filterPredicates(item.condition).length} 个条件` }))} total={operations.length} />
    <NodeBadges><NodeBadge tone="strong">{operations.length} 张表</NodeBadge><NodeBadge>{ruleCount} 条筛选</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const filterCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.Filter> = {
  resolveSize: (configuration) => {
    return resolvedNodeSize({ width: 344, maxHeight: 216, configured: (configuration.operations ?? []).length > 0, listCount: (configuration.operations ?? []).length });
  },
  Body: body,
};
