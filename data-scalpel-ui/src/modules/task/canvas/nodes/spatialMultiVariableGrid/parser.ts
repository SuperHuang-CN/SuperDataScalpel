import { spatialDistanceUnits } from "../spatialUnits";
import { parseFilterCondition, stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { CANVAS_SPATIAL_MULTI_VARIABLE_GRID_MAX_VARIABLES, type SpatialMultiVariableGridVariable } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration, parseFiniteNumber } from '../configurationParsing';

export const multiVariableGridKinds = new Set([
  'DISTANCE_TO_NEAREST', 'ATTRIBUTE_OF_NEAREST', 'ATTRIBUTE_SUMMARY_OF_RELATED',
]);

export const multiVariableGridStatisticKinds = new Set([
  'COUNT', 'SUM', 'MEAN', 'MIN', 'MAX', 'RANGE', 'STDDEV', 'VARIANCE', 'ANY',
]);

export const parseSpatialMultiVariableGridVariables = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialMultiVariableGridVariable[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_SPATIAL_MULTI_VARIABLE_GRID_MAX_VARIABLES) {
    errors.push(`${path} 不能超过 ${CANVAS_SPATIAL_MULTI_VARIABLE_GRID_MAX_VARIABLES} 项`);
  }
  return value.slice(0, CANVAS_SPATIAL_MULTI_VARIABLE_GRID_MAX_VARIABLES)
    .flatMap((item, index): SpatialMultiVariableGridVariable[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const kind = item.kind == null ? null : stringValue(item.kind);
      const statisticKind = item.statisticKind == null ? null : stringValue(item.statisticKind);
      const searchDistanceUnit = item.searchDistanceUnit == null
        ? null : stringValue(item.searchDistanceUnit);
      if (kind !== null && !multiVariableGridKinds.has(kind)) {
        errors.push(`${itemPath}.kind 不是受支持的格网变量类型`);
      }
      if (statisticKind !== null && !multiVariableGridStatisticKinds.has(statisticKind)) {
        errors.push(`${itemPath}.statisticKind 不是受支持的关联统计类型`);
      }
      if (searchDistanceUnit !== null && !spatialDistanceUnits.has(searchDistanceUnit)) {
        errors.push(`${itemPath}.searchDistanceUnit 不是受支持的距离单位`);
      }
      for (const name of ['attributeColumnName', 'statisticColumnName'] as const) {
        if (item[name] !== null && item[name] !== undefined && typeof item[name] !== 'string') {
          errors.push(`${itemPath}.${name} 必须是字符串或 null`);
        }
      }
      return [{
        variableId: validateOptionalUuid(
          stringValue(item.variableId), `${itemPath}.variableId`, errors,
        ),
        sourceTableName: stringValue(item.sourceTableName),
        geometryColumnName: stringValue(item.geometryColumnName),
        kind: kind !== null && multiVariableGridKinds.has(kind)
          ? kind as SpatialMultiVariableGridVariable['kind'] : null,
        attributeColumnName: typeof item.attributeColumnName === 'string'
          ? item.attributeColumnName : null,
        statisticKind: statisticKind !== null && multiVariableGridStatisticKinds.has(statisticKind)
          ? statisticKind as SpatialMultiVariableGridVariable['statisticKind'] : null,
        statisticColumnName: typeof item.statisticColumnName === 'string'
          ? item.statisticColumnName : null,
        searchDistance: item.searchDistance == null ? null
          : parseFiniteNumber(item.searchDistance, `${itemPath}.searchDistance`, errors, 0),
        searchDistanceUnit: searchDistanceUnit !== null && spatialDistanceUnits.has(searchDistanceUnit)
          ? searchDistanceUnit as SpatialMultiVariableGridVariable['searchDistanceUnit'] : null,
        filter: item.filter == null ? null
          : parseFilterCondition(item.filter, `${itemPath}.filter`, errors),
        outputColumnName: stringValue(item.outputColumnName),
      }];
    });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_MULTI_VARIABLE_GRID'>>(
      value,
      path,
      (configuration, errors) => {
        const binShape = stringValue(configuration.binShape);
        const binSizeUnit = stringValue(configuration.binSizeUnit);
        if (configuration.binShape != null && !['SQUARE', 'HEXAGON'].includes(binShape)) {
          errors.push(`${path}.binShape 仅支持 SQUARE 或 HEXAGON`);
        }
        if (!spatialDistanceUnits.has(binSizeUnit)) {
          errors.push(`${path}.binSizeUnit 不是受支持的距离单位`);
        }
        return {
          variables: parseSpatialMultiVariableGridVariables(
            configuration.variables, `${path}.variables`, errors,
          ),
          binShape: ['SQUARE', 'HEXAGON'].includes(binShape)
            ? binShape as Configuration<'SPATIAL_MULTI_VARIABLE_GRID'>['binShape'] : null,
          binSize: parseFiniteNumber(configuration.binSize, `${path}.binSize`, errors, 0),
          binSizeUnit: spatialDistanceUnits.has(binSizeUnit)
            ? binSizeUnit as Configuration<'SPATIAL_MULTI_VARIABLE_GRID'>['binSizeUnit'] : 'METERS',
          outputTableName: stringValue(configuration.outputTableName),
          binIdColumnName: stringValue(configuration.binIdColumnName),
          binGeometryColumnName: stringValue(configuration.binGeometryColumnName),
        };
      },
    )
  );
