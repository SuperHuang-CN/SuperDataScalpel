import { stringValue } from "../../canvasValueParsers";
import { parseConfiguration, isRecord, type Configuration, parseInteger } from '../configurationParsing';

export const geometryKinds = new Set([
  'GEOMETRY',
  'POINT',
  'LINESTRING',
  'POLYGON',
  'MULTIPOINT',
  'MULTILINESTRING',
  'MULTIPOLYGON',
  'GEOMETRYCOLLECTION',
]);

export const coordinateDimensions = new Set(['XY', 'XYZ', 'XYM', 'XYZM']);

export const parseGeometryTypeDefinition = (
  value: unknown,
  path: string,
  errors: string[],
): Configuration<'GEOMETRY_CONSTRUCT'>['targetGeometry'] => {
  if (value === null || value === undefined) return null;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是 Geometry 类型对象`);
    return null;
  }
  const kind = stringValue(value.kind);
  const dimension = stringValue(value.dimension);
  if (!geometryKinds.has(kind)) errors.push(`${path}.kind 不是受支持的 GeometryKind`);
  if (!coordinateDimensions.has(dimension)) {
    errors.push(`${path}.dimension 不是受支持的坐标维度`);
  }
  if (!isRecord(value.crs)) {
    errors.push(`${path}.crs 必须是 CRS 对象`);
    return null;
  }
  const authority = stringValue(value.crs.authority);
  const code = parseInteger(value.crs.code, `${path}.crs.code`, errors, 4326);
  return {
    kind: geometryKinds.has(kind) ? kind as NonNullable<Configuration<'GEOMETRY_CONSTRUCT'>['targetGeometry']>['kind'] : 'POINT',
    crs: { authority, code },
    dimension: coordinateDimensions.has(dimension)
      ? dimension as NonNullable<Configuration<'GEOMETRY_CONSTRUCT'>['targetGeometry']>['dimension']
      : 'XY',
  };
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'GEOMETRY_CONSTRUCT'>>(
      value,
      path,
      (configuration, errors) => {
        let source: Configuration<'GEOMETRY_CONSTRUCT'>['source'];
        if (!isRecord(configuration.source)) {
          errors.push(`${path}.source 必须是来源对象`);
          source = { kind: 'WKT', columnName: '' };
        } else if (configuration.source.kind === 'WKT'
          || configuration.source.kind === 'WKB'
          || configuration.source.kind === 'GEOJSON') {
          source = {
            kind: configuration.source.kind,
            columnName: stringValue(configuration.source.columnName),
          };
        } else if (configuration.source.kind === 'POINT_FROM_XY') {
          source = {
            kind: 'POINT_FROM_XY',
            xColumnName: stringValue(configuration.source.xColumnName),
            yColumnName: stringValue(configuration.source.yColumnName),
          };
        } else {
          errors.push(`${path}.source.kind 不是受支持的 Geometry 构造来源`);
          source = { kind: 'WKT', columnName: '' };
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          outputTableName: stringValue(configuration.outputTableName),
          outputColumnName: stringValue(configuration.outputColumnName),
          source,
          targetGeometry: parseGeometryTypeDefinition(
            configuration.targetGeometry,
            `${path}.targetGeometry`,
            errors,
          ),
        };
      },
    )
  );
