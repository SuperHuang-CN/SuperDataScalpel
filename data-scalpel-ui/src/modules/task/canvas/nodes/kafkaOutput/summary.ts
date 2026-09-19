import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeKafkaOutput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.KafkaOutput>,
) => {
  const writes = data.configuration.writes ?? [];
  const first = writes[0];
  if (!first) return '请至少配置一条 Kafka 写入';
  const target = data.summary?.kind === 'KAFKA'
    ? `${data.summary.dataSourceName} · ${first.topic || '待选择 Topic'}`
    : first.topic || '待选择 Topic';
  const fieldCount = first.valueFormat == null
    ? first.valueSchema?.columns.length ?? 0
    : first.valueColumnNames.length;
  const preview = `${first.sourceTableName || '待选择来源'} → ${target} · ${first.valueFormat ?? '旧版 JSON'} · ${fieldCount} 字段`;
  return writes.length > 1 ? `${preview}，另 ${writes.length - 1} 条写入` : preview;
};
