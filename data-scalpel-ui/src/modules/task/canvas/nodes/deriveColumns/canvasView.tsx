import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.DeriveColumns>) => {
  const operations = data.configuration.operations ?? [];
  if (operations.length === 0) return <NodeEmpty>请选择来源表并配置派生字段</NodeEmpty>;
  const globalDerivations = data.configuration.globalDerivations ?? [];
  const localDerivationCount = operations.reduce(
    (count, operation) => count + operation.derivations.length,
    0,
  );
  return <NodeContent variant="rules">
    <NodeFlow source={operations[0].sourceTableName} operation="DERIVE" target={operations[0].output.outputTableName ?? operations[0].sourceTableName} />
    <NodePreviewList items={operations.slice(0, 2).map((item) => ({ key: item.operationId, label: item.sourceTableName, value: `全局 ${globalDerivations.length} · 本表 ${item.derivations.length}`, meta: item.output.mode === 'REPLACE_SOURCE' ? '更新原表' : '生成新表' }))} total={operations.length} />
    <NodeBadges><NodeBadge tone="info">全局 {globalDerivations.length}</NodeBadge><NodeBadge tone="success">本表 {localDerivationCount}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const deriveColumnsCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.DeriveColumns> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 352, maxHeight: 224, configured: (configuration.operations ?? []).length > 0, listCount: (configuration.operations ?? []).length }),
  Body: body,
};
