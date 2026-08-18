import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { metadataSourceName, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.JdbcOutput>) => {
  const { sourceTableName, dataSourceId, targetTableName, writeMode, columnMappings, upsertKeyColumns } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表和 JDBC 目标</NodeEmpty>;
  return <NodeContent variant="output">
    <NodeTitleLine primary={dataSourceId ? metadataSourceName(data) : '待选择目标数据源'} secondary={targetTableName || '待选择目标表'} accent />
    <NodeFlow source={sourceTableName} operation={writeMode ?? 'WRITE'} target={targetTableName} />
    <NodePreviewList items={columnMappings.slice(0, 2).map((item, index) => ({ key: `${index}`, label: item.sourceColumnName || '来源字段', value: '→', meta: item.targetColumnName || '目标字段' }))} total={columnMappings.length} />
    <NodeBadges><NodeBadge tone="strong">{writeMode ?? '待设置模式'}</NodeBadge><NodeBadge>{columnMappings.length} 个映射</NodeBadge>{writeMode === 'UPSERT' && <NodeBadge tone="warning">键 {upsertKeyColumns.slice(0, 2).join(', ') || '待配置'}</NodeBadge>}</NodeBadges>
  </NodeContent>;
};

export const jdbcOutputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.JdbcOutput> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 352, maxHeight: 216, emptyHeight: 112, configured: Boolean(configuration.sourceTableName), listCount: configuration.columnMappings.length }),
  Body: body,
};
