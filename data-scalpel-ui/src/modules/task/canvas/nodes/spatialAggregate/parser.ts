import { parseStringArray, stringValue } from "../../canvasValueParsers";
import { CANVAS_SPATIAL_AGGREGATE_MAX_AGGREGATIONS, CANVAS_SPATIAL_AGGREGATE_MAX_SUMMARY_STATISTICS, type SpatialAggregation, type SpatialAggregateDissolveOptions, type SpatialAggregateStatistic } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration } from '../configurationParsing';

export const spatialAggregationKinds = new Set(['UNION', 'INTERSECTION', 'COLLECT', 'ENVELOPE']);

export const parseSpatialAggregations = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialAggregation[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_SPATIAL_AGGREGATE_MAX_AGGREGATIONS) {
    errors.push(`${path} 不能超过 ${CANVAS_SPATIAL_AGGREGATE_MAX_AGGREGATIONS} 项`);
  }
  return value.slice(0, CANVAS_SPATIAL_AGGREGATE_MAX_AGGREGATIONS)
    .flatMap((item, index): SpatialAggregation[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const kind = stringValue(item.kind);
      if (!spatialAggregationKinds.has(kind)) {
        errors.push(`${itemPath}.kind 仅支持 UNION、INTERSECTION、COLLECT 或 ENVELOPE`);
      }
      return [{
        kind: spatialAggregationKinds.has(kind)
          ? kind as SpatialAggregation['kind'] : 'UNION',
        geometryColumnName: stringValue(item.geometryColumnName),
        outputColumnName: stringValue(item.outputColumnName),
      }];
    });
};

export const spatialAggregateStatisticKinds = new Set([
  'COUNT_FIELD', 'SUM', 'MEAN', 'MIN', 'MAX', 'RANGE', 'STDDEV', 'VARIANCE', 'ANY',
]);

export const spatialAggregateDissolveGroupingModes = new Set([
  'ALL_OR_FIELDS', 'CONNECTED_COMPONENTS',
]);

export const parseSpatialAggregateDissolve = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialAggregateDissolveOptions | null => {
  if (value === undefined || value === null) return null;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象或 null`);
    return null;
  }
  if (typeof value.enabled !== 'boolean') {
    errors.push(`${path}.enabled 必须是布尔值`);
  }
  if (typeof value.multipart !== 'boolean') {
    errors.push(`${path}.multipart 必须是布尔值`);
  }
  const groupingMode = stringValue(value.groupingMode);
  if (value.groupingMode !== undefined && value.groupingMode !== null
      && !spatialAggregateDissolveGroupingModes.has(groupingMode)) {
    errors.push(`${path}.groupingMode 不是受支持的 Dissolve 分组方式`);
  }
  const rawStatistics = value.summaryStatistics;
  if (!Array.isArray(rawStatistics)) {
    errors.push(`${path}.summaryStatistics 必须是数组`);
  }
  const source = Array.isArray(rawStatistics) ? rawStatistics : [];
  if (source.length > CANVAS_SPATIAL_AGGREGATE_MAX_SUMMARY_STATISTICS) {
    errors.push(
      `${path}.summaryStatistics 不能超过 ${CANVAS_SPATIAL_AGGREGATE_MAX_SUMMARY_STATISTICS} 项`,
    );
  }
  const summaryStatistics = source
    .slice(0, CANVAS_SPATIAL_AGGREGATE_MAX_SUMMARY_STATISTICS)
    .flatMap((item, index): SpatialAggregateStatistic[] => {
      const itemPath = `${path}.summaryStatistics[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const kind = stringValue(item.kind);
      if (!spatialAggregateStatisticKinds.has(kind)) {
        errors.push(`${itemPath}.kind 不是受支持的 Dissolve 统计类型`);
      }
      return [{
        statisticId: stringValue(item.statisticId),
        kind: spatialAggregateStatisticKinds.has(kind)
          ? kind as SpatialAggregateStatistic['kind'] : 'COUNT_FIELD',
        sourceColumnName: stringValue(item.sourceColumnName),
        outputColumnName: stringValue(item.outputColumnName),
      }];
    });
  return {
    enabled: value.enabled === true,
    multipart: value.multipart === true,
    countOutputColumnName: stringValue(value.countOutputColumnName),
    summaryStatistics,
    groupingMode: spatialAggregateDissolveGroupingModes.has(groupingMode)
      ? groupingMode as SpatialAggregateDissolveOptions['groupingMode'] : null,
  };
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_AGGREGATE'>>(
      value,
      path,
      (configuration, errors) => ({
        sourceTableName: stringValue(configuration.sourceTableName),
        outputTableName: stringValue(configuration.outputTableName),
        groupByColumns: parseStringArray(
          configuration.groupByColumns,
          `${path}.groupByColumns`,
          errors,
        ),
        aggregations: parseSpatialAggregations(
          configuration.aggregations,
          `${path}.aggregations`,
          errors,
        ),
        dissolve: parseSpatialAggregateDissolve(
          configuration.dissolve,
          `${path}.dissolve`,
          errors,
        ),
      }),
    )
  );
