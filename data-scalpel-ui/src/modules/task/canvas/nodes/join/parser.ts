import { parseJoinConditions, parseJoinOutputColumns, parseJoinType, stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'JOIN'>>(value, path, (configuration, errors) => ({
      leftTableName: stringValue(configuration.leftTableName),
      rightTableName: stringValue(configuration.rightTableName),
      outputTableName: stringValue(configuration.outputTableName),
      joinType: parseJoinType(configuration.joinType, `${path}.joinType`, errors),
      conditions: parseJoinConditions(configuration.conditions, `${path}.conditions`, errors),
      outputColumns: parseJoinOutputColumns(
        configuration.outputColumns,
        `${path}.outputColumns`,
        errors,
      ),
    }))
  );
