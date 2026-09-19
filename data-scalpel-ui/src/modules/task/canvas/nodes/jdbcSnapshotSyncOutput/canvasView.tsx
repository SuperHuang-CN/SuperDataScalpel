import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList, NodeSplit, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { metadataSourceName, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.JdbcSnapshotSyncOutput>) => {
  const { sourceTableName, dataSourceId, targetTableName, keyColumns, columnMappings, deletePolicy } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表和 JDBC 快照目标</NodeEmpty>;
  const limits = deletePolicy.action === 'DELETE'
    ? `${deletePolicy.maxDeleteRows ?? '∞'} 行 · ${deletePolicy.maxDeleteRatio ?? '∞'} 比例`
    : '保留目标多余记录';
  return <NodeContent variant="output">
    <NodeTitleLine primary={dataSourceId ? metadataSourceName(data) : '待选择目标数据源'} secondary={targetTableName || '待选择目标表'} accent />
    <NodeFlow source={sourceTableName} operation="SNAPSHOT SYNC" target={targetTableName} />
    <NodeSplit leftLabel="业务键" left={keyColumns.slice(0, 2).join(', ') || '待配置'} rightLabel="目标多余记录" right={deletePolicy.action} />
    <NodePreviewList items={columnMappings.slice(0, 2).map((item, index) => ({ key: `${index}`, label: item.sourceColumnName || '来源字段', value: '→', meta: item.targetColumnName || '目标字段' }))} total={columnMappings.length} />
    <NodeBadges><NodeBadge tone={deletePolicy.action === 'DELETE' ? 'warning' : 'success'}>{deletePolicy.action}</NodeBadge><NodeBadge>{limits}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const jdbcSnapshotSyncOutputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.JdbcSnapshotSyncOutput> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 368, maxHeight: 232, emptyHeight: 112, configured: Boolean(configuration.sourceTableName), listCount: configuration.columnMappings.length }),
  Body: body,
};
