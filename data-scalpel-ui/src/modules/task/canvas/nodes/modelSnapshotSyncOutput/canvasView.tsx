import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList, NodeSplit, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { metadataObjectName, metadataSourceName, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.ModelSnapshotSyncOutput>) => {
  const { sourceTableName, targetModelId, keyColumns, columnMappings, deletePolicy } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表和模型快照目标</NodeEmpty>;
  const summary = data.summary?.kind === 'MODEL' ? data.summary : null;
  return <NodeContent variant="output">
    <NodeTitleLine primary={targetModelId ? metadataSourceName(data) : '待选择目标模型'} secondary={summary ? `${summary.modelCode} · v${summary.modelSchemaVersion}` : undefined} accent />
    <NodeFlow source={sourceTableName} operation="SNAPSHOT SYNC" target={targetModelId ? metadataObjectName(data) : '待选择模型'} />
    <NodeSplit leftLabel="业务键" left={keyColumns.slice(0, 2).join(', ') || '待配置'} rightLabel="目标多余记录" right={deletePolicy.action} />
    <NodePreviewList items={columnMappings.slice(0, 2).map((item, index) => ({ key: `${index}`, label: item.sourceColumnName || '来源字段', value: '→', meta: item.targetColumnName || '目标字段' }))} total={columnMappings.length} />
    <NodeBadges><NodeBadge tone={deletePolicy.action === 'DELETE' ? 'warning' : 'success'}>{deletePolicy.action}</NodeBadge><NodeBadge>{deletePolicy.action === 'DELETE' ? `${deletePolicy.maxDeleteRows ?? '∞'} 行 · ${deletePolicy.maxDeleteRatio ?? '∞'} 比例` : '保留目标多余记录'}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const modelSnapshotSyncOutputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.ModelSnapshotSyncOutput> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 368, maxHeight: 232, emptyHeight: 112, configured: Boolean(configuration.sourceTableName), listCount: configuration.columnMappings.length }),
  Body: body,
};
