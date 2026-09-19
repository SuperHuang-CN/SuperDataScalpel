import { parseAggregations, parseStringArray, stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'AGGREGATE'>>(value, path, (configuration, errors) => ({
      sourceTableName: stringValue(configuration.sourceTableName),
      outputTableName: stringValue(configuration.outputTableName),
      groupByColumns: parseStringArray(
        configuration.groupByColumns,
        `${path}.groupByColumns`,
        errors,
      ),
      aggregations: parseAggregations(
        configuration.aggregations,
        `${path}.aggregations`,
        errors,
      ),
    }))
  );
