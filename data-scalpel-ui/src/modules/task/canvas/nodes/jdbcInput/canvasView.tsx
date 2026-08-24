import { SettingOutlined } from '@ant-design/icons';
import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFieldCount, NodePreviewList, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { metadataSourceName, outputTable, resourceListNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.JdbcInput>) => {
  const selectedTables = data.configuration.tables;
  if (selectedTables.length === 0 || selectedTables.every((table) => !table.tableName.trim())) {
    return <NodeEmpty>请选择数据源和物理表</NodeEmpty>;
  }
  const summary = data.summary?.kind === 'JDBC' ? data.summary : null;
  const sourceType = summary?.dataSourceType ?? 'JDBC';
  const tunedTableCount = selectedTables.filter((table) => table.readOptions.length > 0).length;
  const tableItems = selectedTables.map(({ tableName, readOptions }, index) => {
    const table = outputTable(data, tableName);
    const primaryKeyCount = summary?.tables?.find(
      (item) => item.tableName === tableName,
    )?.primaryKeyColumns.length ?? 0;
    const details = [
      readOptions.length > 0 ? `参数 ${readOptions.length}` : null,
      primaryKeyCount > 0 ? `PK ${primaryKeyCount}` : null,
    ].filter(Boolean).join(' · ');
    return {
      key: `${index}:${tableName}`,
      label: tableName || '未配置表名',
      value: details || undefined,
      meta: <NodeFieldCount table={table} />,
    };
  });
  return <NodeContent variant="source">
    <NodeTitleLine primary={metadataSourceName(data)} secondary="JDBC 数据源" accent />
    <NodePreviewList
      items={tableItems}
      total={selectedTables.length}
      limit={3}
      empty="等待表结构解析"
      moreLabel={(remaining) => `另 ${remaining} 张表`}
    />
    <NodeBadges>
      <NodeBadge tone="info">{sourceType}</NodeBadge>
      <NodeBadge tone="strong">{selectedTables.length} 张表</NodeBadge>
      {tunedTableCount > 0 && <NodeBadge tone="warning"><SettingOutlined /> {tunedTableCount} 张表已调优</NodeBadge>}
    </NodeBadges>
  </NodeContent>;
};

export const jdbcInputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.JdbcInput> = {
  resolveSize: (configuration) => resourceListNodeSize({ width: 344, count: configuration.tables.length }),
  Body: body,
};
