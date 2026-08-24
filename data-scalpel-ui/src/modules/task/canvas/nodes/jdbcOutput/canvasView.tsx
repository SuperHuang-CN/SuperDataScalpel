import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeOutputTarget, NodePreviewList, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { inputTable, metadataSourceName, resourceListNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.JdbcOutput>) => {
  const { dataSourceId } = data.configuration;
  const writes = data.configuration.writes ?? [];
  const first = writes[0];
  if (!first) return <NodeEmpty>请选择来源表并添加 JDBC 目标</NodeEmpty>;
  const modeSummary = [...new Set(writes.map((write) => write.writeMode ?? '待设置'))].join(' · ');
  return <NodeContent variant="output">
    <NodeTitleLine primary={dataSourceId ? metadataSourceName(data) : '待选择目标数据源'} secondary={`${writes.length} 个目标`} accent />
    <NodePreviewList
      items={writes.map((write) => ({
        key: write.writeId,
        label: write.sourceTableName || '来源表',
        value: write.writeMode ?? '待设置',
        meta: <NodeOutputTarget
          target={write.targetTableName || '目标表'}
          sourceTable={inputTable(data, write.sourceTableName)}
          mappedCount={write.columnMappings.length}
          mappedColumnNames={write.columnMappings.map((mapping) => mapping.sourceColumnName)}
        />,
      }))}
      total={writes.length}
      limit={3}
      moreLabel={(remaining) => `另 ${remaining} 个写入`}
    />
    <NodeBadges><NodeBadge tone="strong">{writes.length} 个写入</NodeBadge><NodeBadge>{modeSummary}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const jdbcOutputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.JdbcOutput> = {
  resolveSize: (configuration) => resourceListNodeSize({ width: 400, count: configuration.writes?.length ?? 0, emptyHeight: 112 }),
  Body: body,
};
