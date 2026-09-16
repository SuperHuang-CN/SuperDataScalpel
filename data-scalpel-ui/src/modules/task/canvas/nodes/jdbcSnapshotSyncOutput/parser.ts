import { parseMappings, parseStringArray, stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration, parseSnapshotDeletePolicy } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'JDBC_SNAPSHOT_SYNC_OUTPUT'>>(
      value,
      path,
      (configuration, errors) => ({
        sourceTableName: stringValue(configuration.sourceTableName),
        dataSourceId: stringValue(configuration.dataSourceId),
        targetTableName: stringValue(configuration.targetTableName),
        keyColumns: parseStringArray(
          configuration.keyColumns,
          `${path}.keyColumns`,
          errors,
        ),
        columnMappings: parseMappings(
          configuration.columnMappings,
          `${path}.columnMappings`,
          errors,
        ),
        deletePolicy: parseSnapshotDeletePolicy(
          configuration.deletePolicy,
          `${path}.deletePolicy`,
          errors,
        ),
      }),
    )
  );
