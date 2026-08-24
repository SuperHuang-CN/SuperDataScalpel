import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.NullHandling>) => {
  const operations = data.configuration.operations ?? [];
  if (operations.length === 0) return <NodeEmpty>请选择来源表并配置空值规则</NodeEmpty>;
  const rules = operations.flatMap((operation) => operation.rules);
  const dropCount = rules.filter((rule) => rule.kind === 'DROP_ROW').length;
  return <NodeContent variant="rules">
    <NodeFlow source={operations[0].sourceTableName} operation="NULL" target={operations[0].output.outputTableName ?? operations[0].sourceTableName} />
    <NodePreviewList items={operations.slice(0, 2).map((operation) => ({ key: operation.operationId, label: operation.sourceTableName, value: operation.output.mode === 'REPLACE_SOURCE' ? '更新原表' : '生成新表', meta: `${operation.rules.length} 条规则` }))} total={operations.length} />
    <NodeBadges><NodeBadge tone="warning">删除 {dropCount}</NodeBadge><NodeBadge tone="info">填充 {rules.length - dropCount}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const nullHandlingCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.NullHandling> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 344, maxHeight: 216, configured: (configuration.operations ?? []).length > 0, listCount: (configuration.operations ?? []).length }),
  Body: body,
};
