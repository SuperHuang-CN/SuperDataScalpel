import type { KafkaInputConfiguration } from "../../canvasTypes";

export const createKafkaInputConfiguration = (): KafkaInputConfiguration => ({
  dataSourceId: '',
  topic: '',
  valueSchema: { columns: [] },
  outputTableName: '',
  startingOffsets: null,
  triggerIntervalSeconds: 10,
  valueFormat: 'JSON',
  metadataFields: ['KEY', 'TOPIC', 'PARTITION', 'OFFSET', 'TIMESTAMP'],
});
