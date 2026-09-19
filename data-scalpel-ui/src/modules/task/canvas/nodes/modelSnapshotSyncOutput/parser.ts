import { parseMappings, parseStringArray, stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration, parseSnapshotDeletePolicy } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'MODEL_SNAPSHOT_SYNC_OUTPUT'>>(
      value,
      path,
      (configuration, errors) => ({
        sourceTableName: stringValue(configuration.sourceTableName),
        targetModelId: validateOptionalUuid(
          stringValue(configuration.targetModelId),
          `${path}.targetModelId`,
          errors,
        ),
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
