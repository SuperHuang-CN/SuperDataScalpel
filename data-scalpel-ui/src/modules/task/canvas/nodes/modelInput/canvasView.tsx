import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { fieldCountText, metadataSourceName, outputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.ModelInput>) => {
  if (!data.configuration.modelId) return <NodeEmpty>请选择来源模型</NodeEmpty>;
  const result = outputTable(data);
  const summary = data.summary?.kind === 'MODEL' ? data.summary : null;
  return <NodeContent variant="source">
    <NodeTitleLine primary={metadataSourceName(data)} secondary={summary ? `${summary.modelCode} · v${summary.modelSchemaVersion}` : '等待模型元数据'} accent />
    <NodeFlow source={summary?.qualifiedTableName ?? '等待物理表'} operation="MODEL" target={result?.name ?? '等待输出表'} />
    <NodeBadges><NodeBadge tone="info">Schema v{summary?.modelSchemaVersion ?? '?'}</NodeBadge><NodeBadge>{fieldCountText(result)}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const modelInputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.ModelInput> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 300, maxHeight: 160, configured: Boolean(configuration.modelId) }),
  Body: body,
};
