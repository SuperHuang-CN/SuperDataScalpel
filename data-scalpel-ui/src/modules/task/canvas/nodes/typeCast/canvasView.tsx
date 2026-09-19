import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.TypeCast>) => {
  const operations = data.configuration.operations ?? [];
  if (operations.length === 0) return <NodeEmpty>请选择来源表并配置类型转换</NodeEmpty>;
  const operation = operations[0];
  const casts = operations.flatMap((item) => item.casts);
  const setNullCount = casts.filter((item) => item.failureStrategy === 'SET_NULL').length;
  return <NodeContent variant="rules">
    <NodeFlow source={operation.sourceTableName} operation="CAST" target={operation.output.outputTableName ?? operation.sourceTableName} />
    <NodePreviewList items={operations.slice(0, 2).map((item) => ({ key: item.operationId, label: item.sourceTableName, value: `${item.casts.length} 个转换`, meta: item.output.mode === 'REPLACE_SOURCE' ? '更新原表' : '生成新表' }))} total={operations.length} />
    <NodeBadges><NodeBadge tone="warning">FAIL {casts.length - setNullCount}</NodeBadge><NodeBadge tone="info">SET_NULL {setNullCount}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const typeCastCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.TypeCast> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 352, maxHeight: 224, configured: (configuration.operations ?? []).length > 0, listCount: (configuration.operations ?? []).length }),
  Body: body,
};
