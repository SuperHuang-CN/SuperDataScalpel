import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.Rename>) => {
  const operations = data.configuration.operations ?? [];
  if (operations.length === 0) return <NodeEmpty>请选择来源表并设置新名称</NodeEmpty>;
  const operation = operations[0];
  return <NodeContent variant="rules">
    <NodeFlow source={operation.sourceTableName} operation="RENAME" target={operation.output.outputTableName ?? operation.sourceTableName} />
    <NodePreviewList items={operations.slice(0, 2).map((item) => ({ key: item.operationId, label: item.sourceTableName, value: item.output.outputTableName ?? '表名不变', meta: `${item.columnMappings.length} 个字段` }))} total={operations.length} empty="仅重命名数据表" />
    <NodeBadges><NodeBadge tone="strong">{operations.length} 张表</NodeBadge><NodeBadge>{operations.reduce((total, item) => total + item.columnMappings.length, 0)} 个字段</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const renameCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.Rename> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 336, maxHeight: 210, configured: (configuration.operations ?? []).length > 0, listCount: (configuration.operations ?? []).length }),
  Body: body,
};
