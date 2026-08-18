import { KeyOutlined, TableOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';
import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodePreviewList, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { columnTypeText, fieldCountText, metadataSourceName, outputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.JdbcInput>) => {
  if (!data.configuration.tableName) return <NodeEmpty>请选择数据源和物理表</NodeEmpty>;
  const result = outputTable(data, data.configuration.tableName) ?? outputTable(data);
  const summary = data.summary?.kind === 'JDBC' ? data.summary : null;
  const sourceType = summary?.dataSourceType ?? 'JDBC';
  const primaryKeyOrder = new Map(
    (summary?.primaryKeyColumns ?? []).map((columnName, index) => [columnName, index]),
  );
  const previewColumns = (result?.columns ?? [])
    .map((column, index) => ({ column, index }))
    .sort((left, right) => {
      const leftPrimaryOrder = primaryKeyOrder.get(left.column.name);
      const rightPrimaryOrder = primaryKeyOrder.get(right.column.name);
      if (leftPrimaryOrder !== undefined || rightPrimaryOrder !== undefined) {
        if (leftPrimaryOrder === undefined) return 1;
        if (rightPrimaryOrder === undefined) return -1;
        return leftPrimaryOrder - rightPrimaryOrder;
      }
      if (left.column.nullable !== right.column.nullable) return left.column.nullable ? 1 : -1;
      return left.index - right.index;
    })
    .map(({ column }) => ({
      key: column.name,
      label: column.name,
      value: columnTypeText(result, column.name),
      meta: primaryKeyOrder.has(column.name)
        ? <span className="canvas-jdbc-input-field-status is-primary"><KeyOutlined /> PK</span>
        : !column.nullable
          ? <span className="canvas-jdbc-input-field-status">非空</span>
          : undefined,
    }));
  return <NodeContent variant="source">
    <NodeTitleLine primary={metadataSourceName(data)} secondary="JDBC 数据源" accent />
    <Tooltip title={data.configuration.tableName}>
      <div className="canvas-jdbc-input-table">
        <TableOutlined />
        <span>{data.configuration.tableName}</span>
      </div>
    </Tooltip>
    <NodePreviewList
      items={previewColumns}
      total={result?.columns.length ?? 0}
      limit={3}
      empty="等待字段解析"
      moreLabel={(remaining) => `另 ${remaining} 个字段`}
    />
    <NodeBadges><NodeBadge tone="info">{sourceType}</NodeBadge><NodeBadge>{fieldCountText(result)}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const jdbcInputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.JdbcInput> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 320, maxHeight: 242, configured: Boolean(configuration.tableName) }),
  Body: body,
};
