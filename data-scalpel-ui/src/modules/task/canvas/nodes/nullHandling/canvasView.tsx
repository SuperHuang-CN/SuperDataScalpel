import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.NullHandling>) => {
  const { sourceTableName, outputTableName, rules } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表并配置空值规则</NodeEmpty>;
  const dropCount = rules.filter((rule) => rule.kind === 'DROP_ROW').length;
  return <NodeContent variant="rules">
    <NodeFlow source={sourceTableName} operation="NULL" target={outputTableName} />
    <NodePreviewList items={rules.slice(0, 2).map((rule, index) => rule.kind === 'DROP_ROW'
      ? { key: `${index}`, label: rule.columnNames.slice(0, 2).join(', ') || '字段', value: '删除行', meta: rule.matchMode }
      : { key: `${index}`, label: rule.columnName || '字段', value: '填充值', meta: '值已配置' })} total={rules.length} />
    <NodeBadges><NodeBadge tone="warning">删除 {dropCount}</NodeBadge><NodeBadge tone="info">填充 {rules.length - dropCount}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const nullHandlingCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.NullHandling> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 344, maxHeight: 216, configured: Boolean(configuration.sourceTableName), listCount: configuration.rules.length }),
  Body: body,
};
