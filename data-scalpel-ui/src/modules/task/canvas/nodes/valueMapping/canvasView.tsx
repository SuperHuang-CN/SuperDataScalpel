import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.ValueMapping>) => {
  const operations = data.configuration.operations ?? [];
  if (operations.length === 0) return <NodeEmpty>请选择来源表并配置值映射</NodeEmpty>;
  const rules = operations.flatMap((operation) => operation.rules);
  const entries = rules.reduce((count, rule) => count + rule.entries.length, 0);
  return <NodeContent variant="rules">
    <NodeFlow source={operations[0].sourceTableName} operation="MAP" target={operations[0].output.outputTableName ?? operations[0].sourceTableName} />
    <NodePreviewList items={operations.slice(0, 2).map((item) => ({ key: item.operationId, label: item.sourceTableName, value: `${item.rules.length} 个字段`, meta: item.output.mode === 'REPLACE_SOURCE' ? '更新原表' : '生成新表' }))} total={operations.length} />
    <NodeBadges><NodeBadge tone="strong">{operations.length} 张表</NodeBadge><NodeBadge>{entries} 个映射项</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const valueMappingCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.ValueMapping> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 352, maxHeight: 224, configured: (configuration.operations ?? []).length > 0, listCount: (configuration.operations ?? []).length }),
  Body: body,
};
