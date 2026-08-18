import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { metadataObjectName, metadataSourceName, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.ModelOutput>) => {
  const { sourceTableName, targetModelId, writeMode, columnMappings } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表和目标模型</NodeEmpty>;
  const summary = data.summary?.kind === 'MODEL' ? data.summary : null;
  return <NodeContent variant="output">
    <NodeTitleLine primary={targetModelId ? metadataSourceName(data) : '待选择目标模型'} secondary={summary ? `${summary.modelCode} · v${summary.modelSchemaVersion}` : undefined} accent />
    <NodeFlow source={sourceTableName} operation={writeMode ?? 'WRITE'} target={targetModelId ? metadataObjectName(data) : '待选择模型'} />
    <NodePreviewList items={columnMappings.slice(0, 2).map((item, index) => ({ key: `${index}`, label: item.sourceColumnName || '来源字段', value: '→', meta: item.targetColumnName || '目标字段' }))} total={columnMappings.length} />
    <NodeBadges><NodeBadge tone="strong">{writeMode ?? '待设置模式'}</NodeBadge><NodeBadge>{columnMappings.length} 个字段映射</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const modelOutputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.ModelOutput> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 336, maxHeight: 200, emptyHeight: 112, configured: Boolean(configuration.sourceTableName), listCount: configuration.columnMappings.length }),
  Body: body,
};
