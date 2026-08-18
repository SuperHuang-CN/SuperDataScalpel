import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.MaskFields>) => {
  const { sourceTableName, outputTableName, fieldRules } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表并配置脱敏规则</NodeEmpty>;
  const globalCount = fieldRules.filter((item) => item.ruleSource === 'GLOBAL').length;
  return <NodeContent variant="rules">
    <NodeFlow source={sourceTableName} operation="MASK" target={outputTableName} />
    <NodePreviewList items={fieldRules.slice(0, 2).map((item, index) => ({ key: `${index}`, label: item.fieldName || '字段', value: item.definition.strategy, meta: item.ruleSource === 'GLOBAL' ? '全局规则' : '内联规则' }))} total={fieldRules.length} />
    <NodeBadges><NodeBadge tone="info">全局 {globalCount}</NodeBadge><NodeBadge tone="strong">内联 {fieldRules.length - globalCount}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const maskFieldsCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.MaskFields> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 352, maxHeight: 224, configured: Boolean(configuration.sourceTableName), listCount: configuration.fieldRules.length }),
  Body: body,
};
