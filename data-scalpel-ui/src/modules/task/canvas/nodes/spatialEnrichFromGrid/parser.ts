import { stringValue } from "../../canvasValueParsers";
import { type SpatialEnrichFromGridField } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration } from '../configurationParsing';

export const parseSpatialEnrichFromGridFields = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialEnrichFromGridField[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): SpatialEnrichFromGridField[] => {
    const itemPath = `${path}[${index}]`;
    if (!isRecord(item)) {
      errors.push(`${itemPath} 必须是对象`);
      return [];
    }
    if (typeof item.sourceColumnName !== 'string') {
      errors.push(`${itemPath}.sourceColumnName 必须是字符串`);
    }
    if (typeof item.outputColumnName !== 'string') {
      errors.push(`${itemPath}.outputColumnName 必须是字符串`);
    }
    return [{
      sourceColumnName: stringValue(item.sourceColumnName),
      outputColumnName: stringValue(item.outputColumnName),
    }];
  });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_ENRICH_FROM_GRID'>>(
      value,
      path,
      (configuration, errors) => ({
        pointTableName: stringValue(configuration.pointTableName),
        pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
        gridTableName: stringValue(configuration.gridTableName),
        gridGeometryColumnName: stringValue(configuration.gridGeometryColumnName),
        gridIdColumnName: stringValue(configuration.gridIdColumnName),
        enrichFields: parseSpatialEnrichFromGridFields(
          configuration.enrichFields, `${path}.enrichFields`, errors,
        ),
        outputTableName: stringValue(configuration.outputTableName),
      }),
    )
  );
