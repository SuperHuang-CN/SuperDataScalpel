import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.MaskFields>) => {
  const operations = data.configuration.operations ?? [];
  if (operations.length === 0) return <NodeEmpty>请选择来源表并配置脱敏规则</NodeEmpty>;
  const fieldRules = operations.flatMap((operation) => operation.fieldRules);
  const globalCount = fieldRules.filter((item) => item.ruleSource === 'GLOBAL').length;
  return <NodeContent variant="rules">
    <NodeFlow source={operations[0].sourceTableName} operation="MASK" target={operations[0].output.outputTableName ?? operations[0].sourceTableName} />
    <NodePreviewList items={operations.slice(0, 2).map((item) => ({ key: item.operationId, label: item.sourceTableName, value: `${item.fieldRules.length} 个字段`, meta: item.output.mode === 'REPLACE_SOURCE' ? '更新原表' : '生成新表' }))} total={operations.length} />
    <NodeBadges><NodeBadge tone="info">全局 {globalCount}</NodeBadge><NodeBadge tone="strong">内联 {fieldRules.length - globalCount}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const maskFieldsCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.MaskFields> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 352, maxHeight: 224, configured: (configuration.operations ?? []).length > 0, listCount: (configuration.operations ?? []).length }),
  Body: body,
};
