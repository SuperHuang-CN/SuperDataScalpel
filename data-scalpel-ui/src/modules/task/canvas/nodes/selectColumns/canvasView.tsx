import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { inputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SelectColumns>) => {
  const operations = data.configuration.operations ?? [];
  if (operations.length === 0) return <NodeEmpty>请选择来源表和保留字段</NodeEmpty>;
  const operation = operations[0];
  const source = inputTable(data, operation.sourceTableName);
  return <NodeContent variant="rules">
    <NodeFlow source={operation.sourceTableName} operation="SELECT" target={operation.output.outputTableName ?? operation.sourceTableName} />
    <NodePreviewList items={operations.slice(0, 2).map((item) => ({ key: item.operationId, label: item.sourceTableName, value: `${item.columns.length} 字段`, meta: item.output.mode === 'REPLACE_SOURCE' ? '更新原表' : '生成新表' }))} total={operations.length} />
    <NodeBadges><NodeBadge tone="strong">保留 {operation.columns.length} / {source?.columns.length ?? '?'}</NodeBadge><NodeBadge>{operations.length} 张表</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const selectColumnsCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.SelectColumns> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 336, maxHeight: 210, configured: (configuration.operations ?? []).length > 0, listCount: (configuration.operations ?? []).length }),
  Body: body,
};
