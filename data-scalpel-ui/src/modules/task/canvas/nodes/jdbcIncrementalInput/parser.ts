import { stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'JDBC_INCREMENTAL_INPUT'>>(
      value,
      path,
      (configuration, errors) => {
        const startPosition = configuration.startPosition === 'EARLIEST'
          || configuration.startPosition === 'AT_TIME'
          ? configuration.startPosition
          : 'LATEST';
        const startTime = typeof configuration.startTime === 'string'
          && configuration.startTime.trim() ? configuration.startTime.trim() : null;
        if (startPosition === 'AT_TIME' && !startTime) {
          errors.push(`${path}.startTime 在 AT_TIME 模式下不能为空`);
        }
        const visibilityDelaySeconds = typeof configuration.visibilityDelaySeconds === 'number'
          ? configuration.visibilityDelaySeconds : 30;
        const triggerIntervalSeconds = typeof configuration.triggerIntervalSeconds === 'number'
          ? configuration.triggerIntervalSeconds : 60;
        if (!Number.isInteger(visibilityDelaySeconds)
          || visibilityDelaySeconds < 0 || visibilityDelaySeconds > 3600) {
          errors.push(`${path}.visibilityDelaySeconds 必须是 0 到 3600 的整数`);
        }
        if (!Number.isInteger(triggerIntervalSeconds)
          || triggerIntervalSeconds < 1 || triggerIntervalSeconds > 300) {
          errors.push(`${path}.triggerIntervalSeconds 必须是 1 到 300 的整数`);
        }
        return {
          dataSourceId: validateOptionalUuid(
            stringValue(configuration.dataSourceId), `${path}.dataSourceId`, errors,
          ),
          tableName: stringValue(configuration.tableName),
          outputTableName: stringValue(configuration.outputTableName),
          incrementalTimeColumn: stringValue(configuration.incrementalTimeColumn),
          startPosition,
          startTime: startPosition === 'AT_TIME' ? startTime : null,
          cursorTimeZone: stringValue(configuration.cursorTimeZone) || 'UTC',
          visibilityDelaySeconds,
          triggerIntervalSeconds,
        };
      },
    )
  );
