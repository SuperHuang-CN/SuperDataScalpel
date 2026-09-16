import type { TdEngineTmqInputConfiguration } from "../../canvasTypes";

export const createTdEngineTmqInputConfiguration = (): TdEngineTmqInputConfiguration => ({
  dataSourceId: '',
  topicName: '',
  catalogName: '',
  supertableName: '',
  topicDefinitionFingerprint: '',
  outputTableName: '',
  startingOffsets: 'EARLIEST',
  maxOffsetsPerVGroupPerTrigger: 10_000,
  triggerIntervalSeconds: 10,
  eventTimeColumn: null,
  watermarkDelaySeconds: null,
});
