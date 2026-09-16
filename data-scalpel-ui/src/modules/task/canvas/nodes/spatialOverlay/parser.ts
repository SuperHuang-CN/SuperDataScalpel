import { parseJoinOutputColumns, stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_OVERLAY'>>(
      value,
      path,
      (configuration, errors) => {
        const operation = stringValue(configuration.operation);
        if (configuration.operation != null && !['INTERSECTION', 'ERASE', 'UNION', 'IDENTITY', 'SYMMETRICAL_DIFFERENCE'].includes(operation)) {
          errors.push(`${path}.operation 不是有效的叠加方式`);
        }
        const geometryPolicy = configuration.geometryPolicy;
        if (geometryPolicy != null && geometryPolicy !== 'FAMILY_2D' && geometryPolicy !== 'LEGACY_GEOMETRY') {
          errors.push(`${path}.geometryPolicy 仅支持 FAMILY_2D 或 LEGACY_GEOMETRY`);
        }
        return {
          leftTableName: stringValue(configuration.leftTableName),
          leftGeometryColumnName: stringValue(configuration.leftGeometryColumnName),
          rightTableName: stringValue(configuration.rightTableName),
          rightGeometryColumnName: stringValue(configuration.rightGeometryColumnName),
          operation: ['INTERSECTION', 'ERASE', 'UNION', 'IDENTITY', 'SYMMETRICAL_DIFFERENCE'].includes(operation)
            ? operation as Configuration<'SPATIAL_OVERLAY'>['operation'] : null,
          ...('geometryPolicy' in configuration ? {
            geometryPolicy: geometryPolicy === 'FAMILY_2D' || geometryPolicy === 'LEGACY_GEOMETRY' ? geometryPolicy : null,
          } : {}),
          outputTableName: stringValue(configuration.outputTableName),
          outputGeometryColumnName: stringValue(configuration.outputGeometryColumnName),
          outputColumns: parseJoinOutputColumns(
            configuration.outputColumns,
            `${path}.outputColumns`,
            errors,
          ),
        };
      },
    )
  );
