import type { KafkaOutputConfiguration } from "../../canvasTypes";

export const createKafkaOutputConfiguration = (): KafkaOutputConfiguration => ({
  dataSourceId: '',
  writes: [],
  sourceTableName: '', topic: '', valueSchema: { columns: [] }, keyColumnName: '', columnMappings: [],
});
