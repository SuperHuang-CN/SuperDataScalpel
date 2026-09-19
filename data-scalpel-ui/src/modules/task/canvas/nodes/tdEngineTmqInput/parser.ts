import { stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'TDENGINE_TMQ_INPUT'>>(value, path, (configuration, errors) => {
      const fingerprint = stringValue(configuration.topicDefinitionFingerprint);
      if (fingerprint && !/^(?:[0-9a-f]{64}|v2:[0-9a-f]{64})$/.test(fingerprint)) {
        errors.push(`${path}.topicDefinitionFingerprint 必须是旧版 SHA-256 或 v2 指纹`);
      }
      const maximum = typeof configuration.maxOffsetsPerVGroupPerTrigger === 'number'
        ? configuration.maxOffsetsPerVGroupPerTrigger
        : 10_000;
      if (!Number.isInteger(maximum) || maximum < 1 || maximum > 1_000_000) {
        errors.push(`${path}.maxOffsetsPerVGroupPerTrigger 必须是 1 到 1000000 的整数`);
      }
      const watermarkDelaySeconds = configuration.watermarkDelaySeconds === null
        || configuration.watermarkDelaySeconds === undefined
        ? null : Number(configuration.watermarkDelaySeconds);
      if (watermarkDelaySeconds !== null
        && (!Number.isInteger(watermarkDelaySeconds)
          || watermarkDelaySeconds < 1 || watermarkDelaySeconds > 2_592_000)) {
        errors.push(`${path}.watermarkDelaySeconds 必须是 1 到 2592000 的整数`);
      }
      return {
        dataSourceId: validateOptionalUuid(stringValue(configuration.dataSourceId), `${path}.dataSourceId`, errors),
        topicName: stringValue(configuration.topicName),
        catalogName: stringValue(configuration.catalogName),
        supertableName: stringValue(configuration.supertableName),
        topicDefinitionFingerprint: fingerprint,
        outputTableName: stringValue(configuration.outputTableName),
        startingOffsets: configuration.startingOffsets === 'LATEST' ? 'LATEST' : 'EARLIEST',
        maxOffsetsPerVGroupPerTrigger: maximum,
        triggerIntervalSeconds: typeof configuration.triggerIntervalSeconds === 'number'
          ? configuration.triggerIntervalSeconds : 10,
        eventTimeColumn: configuration.eventTimeColumn === null
          || configuration.eventTimeColumn === undefined
          ? null : stringValue(configuration.eventTimeColumn),
        watermarkDelaySeconds,
      };
    })
  );
