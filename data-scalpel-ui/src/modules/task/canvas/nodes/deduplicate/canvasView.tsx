import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize, sortText } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.Deduplicate>) => {
  const { sourceTableName, outputTableName, keyColumns, keepStrategy, orderBy } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表并配置去重</NodeEmpty>;
  const keys = keyColumns.length > 0 ? keyColumns : ['全部字段'];
  return <NodeContent variant="rules">
    <NodeFlow source={sourceTableName} operation="DEDUP" target={outputTableName} />
    <NodePreviewList items={keys.slice(0, 2).map((key, index) => ({ key: `${index}`, label: key, value: '业务键' }))} total={keys.length} />
    <NodeBadges><NodeBadge tone="strong">{keepStrategy ?? '待设置保留策略'}</NodeBadge><NodeBadge>{sortText(orderBy[0])}</NodeBadge><NodeBadge>{orderBy.length} 个排序</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const deduplicateCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.Deduplicate> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 344, maxHeight: 216, configured: Boolean(configuration.sourceTableName), listCount: Math.max(1, configuration.keyColumns.length) }),
  Body: body,
};
