import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.JsonExtract>) => {
  const operations = data.configuration.operations ?? [];
  if (operations.length === 0) return <NodeEmpty>请选择 JSON 来源字段</NodeEmpty>;
  const operation = operations[0];
  const extractions = operations.flatMap((item) => item.extractions);
  return <NodeContent variant="rules">
    <NodeFlow source={`${operation.sourceTableName}.${operation.sourceColumnName || '?'}`} operation="JSON PATH" target={operation.output.outputTableName ?? operation.sourceTableName} />
    <NodePreviewList items={operations.slice(0, 2).map((item) => ({ key: item.operationId, label: item.sourceTableName, value: `${item.extractions.length} 个字段`, meta: item.failureStrategy }))} total={operations.length} />
    <NodeBadges><NodeBadge tone="strong">{operations.length} 张表</NodeBadge><NodeBadge>{extractions.length} 个提取字段</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const jsonExtractCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.JsonExtract> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 352, maxHeight: 224, configured: (configuration.operations ?? []).length > 0, listCount: (configuration.operations ?? []).length }),
  Body: body,
};
