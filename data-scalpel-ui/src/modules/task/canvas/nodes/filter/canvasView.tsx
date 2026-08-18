import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { filterPredicates, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.Filter>) => {
  const { sourceTableName, outputTableName, condition } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表并配置筛选</NodeEmpty>;
  const predicates = filterPredicates(condition);
  const root = condition.kind === 'GROUP' ? condition.operator : 'SINGLE';
  return <NodeContent variant="rules">
    <NodeFlow source={sourceTableName} operation="FILTER" target={outputTableName} />
    <NodePreviewList items={predicates.slice(0, 2).map((item, index) => ({ key: `${index}`, label: item.columnName || '字段', value: item.operator, meta: item.valueCount > 0 ? `${item.valueCount} 个值` : '无字面值' }))} total={predicates.length} empty="尚未配置筛选条件" />
    <NodeBadges><NodeBadge tone="strong">{root}</NodeBadge><NodeBadge>{predicates.length} 个条件</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const filterCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.Filter> = {
  resolveSize: (configuration) => {
    const count = filterPredicates(configuration.condition).length;
    return resolvedNodeSize({ width: 344, maxHeight: 216, configured: Boolean(configuration.sourceTableName), listCount: count });
  },
  Body: body,
};
