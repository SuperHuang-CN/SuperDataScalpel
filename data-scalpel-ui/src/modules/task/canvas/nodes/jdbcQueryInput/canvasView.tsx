import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { fieldCountText, metadataSourceName, outputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.JdbcQueryInput>) => {
  const { dataSourceId, outputTableName, analyzedSqlSha256, outputColumns } = data.configuration;
  if (!dataSourceId && !outputTableName) return <NodeEmpty>请选择数据源并分析 SQL</NodeEmpty>;
  const result = outputTable(data, outputTableName);
  const analyzed = Boolean(analyzedSqlSha256 && outputColumns.length > 0);
  return <NodeContent variant="source">
    <NodeTitleLine primary={metadataSourceName(data)} secondary={analyzed ? 'SQL 已分析' : 'SQL 待分析'} accent />
    <NodeFlow source="SQL QUERY" operation={analyzed ? 'ANALYZED' : 'PENDING'} target={outputTableName || '待设置输出表'} />
    <NodeBadges><NodeBadge tone={analyzed ? 'success' : 'warning'}>{analyzed ? '分析通过' : '待分析'}</NodeBadge><NodeBadge>{analyzedSqlSha256 ? '指纹已记录' : '无分析指纹'}</NodeBadge><NodeBadge>{result ? fieldCountText(result) : `${outputColumns.length} 个字段`}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const jdbcQueryInputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.JdbcQueryInput> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 316, maxHeight: 180, configured: Boolean(configuration.dataSourceId || configuration.outputTableName) }),
  Body: body,
};
