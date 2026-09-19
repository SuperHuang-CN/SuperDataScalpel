import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeJdbcOutput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.JdbcOutput>,
) => {
  const writes = data.configuration.writes ?? [];
  const first = writes[0];
  if (!first) return '请至少配置一条 JDBC 写入';
  const target = data.summary?.kind === 'JDBC'
    ? data.summary.qualifiedTableName
    : first.targetTableName || '待选择目标表';
  const preview = `${first.sourceTableName || '待选择来源'} → ${target} (${first.writeMode ?? '待设置'})`;
  return writes.length > 1 ? `${preview}，另 ${writes.length - 1} 条写入` : preview;
};
