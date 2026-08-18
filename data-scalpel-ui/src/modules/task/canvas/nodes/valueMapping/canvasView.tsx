import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.ValueMapping>) => {
  const { sourceTableName, outputTableName, rules } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表并配置值映射</NodeEmpty>;
  const entries = rules.reduce((count, rule) => count + rule.entries.length, 0);
  return <NodeContent variant="rules">
    <NodeFlow source={sourceTableName} operation="MAP" target={outputTableName} />
    <NodePreviewList items={rules.slice(0, 2).map((rule, index) => ({ key: `${index}`, label: rule.columnName || '字段', value: `${rule.entries.length} 项`, meta: rule.unmatchedStrategy }))} total={rules.length} />
    <NodeBadges><NodeBadge tone="strong">{rules.length} 个字段</NodeBadge><NodeBadge>{entries} 个映射项</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const valueMappingCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.ValueMapping> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 352, maxHeight: 224, configured: Boolean(configuration.sourceTableName), listCount: configuration.rules.length }),
  Body: body,
};
