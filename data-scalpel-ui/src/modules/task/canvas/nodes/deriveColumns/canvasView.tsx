import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { expressionSignature, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.DeriveColumns>) => {
  const { sourceTableName, outputTableName, derivations } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表并配置派生字段</NodeEmpty>;
  const replaceCount = derivations.filter((item) => item.replaceExisting).length;
  return <NodeContent variant="rules">
    <NodeFlow source={sourceTableName} operation="DERIVE" target={outputTableName} />
    <NodePreviewList items={derivations.slice(0, 2).map((item, index) => ({ key: `${index}`, label: item.targetColumnName || '目标字段', value: expressionSignature(item.expression), meta: item.replaceExisting ? '覆盖' : '新增' }))} total={derivations.length} />
    <NodeBadges><NodeBadge tone="success">新增 {derivations.length - replaceCount}</NodeBadge><NodeBadge tone="warning">覆盖 {replaceCount}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const deriveColumnsCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.DeriveColumns> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 352, maxHeight: 224, configured: Boolean(configuration.sourceTableName), listCount: configuration.derivations.length }),
  Body: body,
};
