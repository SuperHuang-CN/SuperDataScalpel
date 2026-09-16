import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeKafkaInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.KafkaInput>,
) => {
  const { dataSourceId, topic, valueSchema, outputTableName } = data.configuration;
  const valueFormat = data.configuration.valueFormat ?? 'JSON';
  const metadataFields = data.configuration.metadataFields ?? [];
  const payloadFieldCount = valueFormat === 'JSON' ? valueSchema.columns.length : 1;
  if (!dataSourceId || !topic
    || (valueFormat === 'JSON' && payloadFieldCount === 0) || !outputTableName) {
    return '请配置 Kafka 输入';
  }
  const fieldCount = payloadFieldCount + metadataFields.length;
  return data.summary?.kind === 'KAFKA'
    ? `${data.summary.dataSourceName} · ${topic} → ${outputTableName} · ${valueFormat} · ${fieldCount} 字段`
    : `${topic} → ${outputTableName} · ${valueFormat} · ${fieldCount} 字段`;
};
