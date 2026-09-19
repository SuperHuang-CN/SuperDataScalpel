import { spatialDistanceUnits, spatialAreaUnits } from "../spatialUnits";
import { parseWithinStatisticOptions } from "./statisticOptions";
import { parseWithinGroupResult } from "./groupResult";
import { parseWithinRegions } from "./regions";
import { parseJoinOutputColumns, stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { CANVAS_SPATIAL_WITHIN_MAX_STATISTICS, type SpatialWithinStatistic } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration, parseSpatialGroupSummary, parseSpatialTemporalSlicing } from '../configurationParsing';

export const spatialWithinKinds = new Set([
  'COUNT_FIELD', 'ANY',
  'COUNT', 'SUM', 'MEAN', 'MIN', 'MAX', 'RANGE', 'STDDEV', 'VARIANCE',
  'LENGTH_WITHIN', 'AREA_WITHIN',
]);

export const parseSpatialWithinStatistics = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialWithinStatistic[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_SPATIAL_WITHIN_MAX_STATISTICS) {
    errors.push(`${path} 不能超过 ${CANVAS_SPATIAL_WITHIN_MAX_STATISTICS} 项`);
  }
  return value.slice(0, CANVAS_SPATIAL_WITHIN_MAX_STATISTICS)
    .flatMap((item, index): SpatialWithinStatistic[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const kind = stringValue(item.kind);
      if (!spatialWithinKinds.has(kind)) {
        errors.push(`${itemPath}.kind 不是受支持的区域统计类型`);
      }
      if (item.sourceColumnName !== null
        && item.sourceColumnName !== undefined
        && typeof item.sourceColumnName !== 'string') {
        errors.push(`${itemPath}.sourceColumnName 必须是字符串或 null`);
      }
      return [{
        statisticId: validateOptionalUuid(
          stringValue(item.statisticId), `${itemPath}.statisticId`, errors,
        ),
        kind: spatialWithinKinds.has(kind)
          ? kind as SpatialWithinStatistic['kind'] : 'COUNT',
        sourceColumnName: typeof item.sourceColumnName === 'string'
          ? item.sourceColumnName : null,
        outputColumnName: stringValue(item.outputColumnName),
        ...parseWithinStatisticOptions(item, itemPath, errors),
      }];
    });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_SUMMARIZE_WITHIN'>>(
      value,
      path,
      (configuration, errors) => {
        const distanceMethod = stringValue(configuration.distanceMethod);
        const lengthUnit = stringValue(configuration.lengthUnit);
        const areaUnit = stringValue(configuration.areaUnit);
        const lengthUnits = spatialDistanceUnits;
        const areaUnits = spatialAreaUnits;
        if (distanceMethod !== 'PLANAR' && distanceMethod !== 'GEODESIC') {
          errors.push(`${path}.distanceMethod 仅支持 PLANAR 或 GEODESIC`);
        }
        if (!lengthUnits.has(lengthUnit)) errors.push(`${path}.lengthUnit 不是受支持的长度单位`);
        if (!areaUnits.has(areaUnit)) errors.push(`${path}.areaUnit 不是受支持的面积单位`);
        return {
          areaTableName: stringValue(configuration.areaTableName),
          areaGeometryColumnName: stringValue(configuration.areaGeometryColumnName),
          summaryTableName: stringValue(configuration.summaryTableName),
          summaryGeometryColumnName: stringValue(configuration.summaryGeometryColumnName),
          includeEmptyAreas: configuration.includeEmptyAreas === true,
          distanceMethod: distanceMethod === 'GEODESIC' ? 'GEODESIC'
            : distanceMethod === 'PLANAR' ? 'PLANAR' : null,
          lengthUnit: lengthUnits.has(lengthUnit)
            ? lengthUnit as Configuration<'SPATIAL_SUMMARIZE_WITHIN'>['lengthUnit']
            : 'SOURCE_CRS_UNIT',
          areaUnit: areaUnits.has(areaUnit)
            ? areaUnit as Configuration<'SPATIAL_SUMMARIZE_WITHIN'>['areaUnit']
            : 'SQUARE_METERS',
          areaOutputColumns: parseJoinOutputColumns(
            configuration.areaOutputColumns,
            `${path}.areaOutputColumns`,
            errors,
          ),
          statistics: parseSpatialWithinStatistics(
            configuration.statistics,
            `${path}.statistics`,
            errors,
          ),
          groupSummary: parseSpatialGroupSummary(
            configuration.groupSummary,
            `${path}.groupSummary`,
            errors,
          ),
          ...parseWithinGroupResult(configuration.groupResult, `${path}.groupResult`, errors),
          ...parseWithinRegions(configuration, path, errors),
          temporalSlicing: parseSpatialTemporalSlicing(
            configuration.temporalSlicing,
            `${path}.temporalSlicing`,
            errors,
          ),
          outputTableName: stringValue(configuration.outputTableName),
        };
      },
    )
  );
