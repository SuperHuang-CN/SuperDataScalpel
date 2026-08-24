import { CanvasNodeType } from '../../canvasTypes';
import {
  NodeBadge,
  NodeBadges,
  NodeContent,
  NodeEmpty,
  NodeFlow,
} from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const escapeRegExp = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

const referencesTable = (sql: string, tableName: string) => {
  const quoted = `\`${tableName.replaceAll('`', '``')}\``;
  if (sql.includes(quoted)) return true;
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(tableName)) return false;
  return new RegExp(`(^|[^A-Za-z0-9_$])${escapeRegExp(tableName)}(?=$|[^A-Za-z0-9_$])`, 'u')
    .test(sql);
};

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SqlTransform>) => {
  const { outputTableName, sql } = data.configuration;
  if (!outputTableName || !sql.trim()) {
    return <NodeEmpty>设置输出表并配置 SELECT 查询</NodeEmpty>;
  }
  const inputTables = data.compilation?.inputTables ?? [];
  const referencedTableCount = inputTables.filter((table) => referencesTable(sql, table.name)).length;
  const output = data.compilation?.outputTables.find((table) => table.name === outputTableName);
  return <NodeContent variant="rules">
    <NodeFlow
      source={referencedTableCount > 0 ? `${referencedTableCount} 张引用表` : '上游表'}
      operation="SQL"
      target={outputTableName}
    />
    <NodeBadges>
      <NodeBadge tone="strong">{referencedTableCount} 张引用表</NodeBadge>
      <NodeBadge>{output?.columns.length ?? 0} 个字段</NodeBadge>
    </NodeBadges>
  </NodeContent>;
};

export const sqlTransformCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.SqlTransform> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 320,
    maxHeight: 156,
    configured: Boolean(configuration.outputTableName && configuration.sql.trim()),
    listCount: 1,
  }),
  Body: body,
};
