import { spatialDistanceUnits } from "../spatialUnits";
import { parseJoinConditions, parseJoinOutputColumns, parseSortFields, stringValue } from "../../canvasValueParsers";
import { CANVAS_SPATIAL_JOIN_MAX_SUMMARY_STATISTICS, type SpatialDistanceUnit, type SpatialJoinOneToOneOptions, type SpatialJoinSummaryStatistic, type SpatialJoinTemporalCondition, type SpatialJoinSpatialNearCondition, type SpatialJoinDistanceOutput } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration } from '../configurationParsing';

export const spatialJoinSummaryStatisticKinds = new Set(['SUM', 'MIN', 'MAX', 'MEAN', 'STDDEV']);

export const spatialJoinTemporalRelationships = new Set([
  'EQUALS', 'INTERSECTS', 'DURING', 'CONTAINS', 'FINISHES', 'FINISHED_BY',
  'MEETS', 'MET_BY', 'OVERLAPS', 'OVERLAPPED_BY', 'STARTS', 'STARTED_BY',
  'NEAR', 'NEAR_BEFORE', 'NEAR_AFTER',
]);

export const spatialDurationUnits = new Set([
  'MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS', 'DAYS', 'WEEKS',
]);

export const parseSpatialJoinOneToOne = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialJoinOneToOneOptions | null => {
  if (value === undefined || value === null) return null;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象或 null`);
    return null;
  }
  const mode = value.mode === 'SUMMARIZE_MATCHES' || value.mode === 'KEEP_ONE'
    ? value.mode
    : null;
  if (value.mode !== undefined && value.mode !== null && mode === null) {
    errors.push(`${path}.mode 仅支持 SUMMARIZE_MATCHES 或 KEEP_ONE`);
  }
  const rawStatistics = value.summaryStatistics;
  if (!Array.isArray(rawStatistics)) {
    errors.push(`${path}.summaryStatistics 必须是数组`);
  }
  const statistics = Array.isArray(rawStatistics) ? rawStatistics : [];
  if (statistics.length > CANVAS_SPATIAL_JOIN_MAX_SUMMARY_STATISTICS) {
    errors.push(`${path}.summaryStatistics 不能超过 ${CANVAS_SPATIAL_JOIN_MAX_SUMMARY_STATISTICS} 项`);
  }
  const summaryStatistics = statistics
    .slice(0, CANVAS_SPATIAL_JOIN_MAX_SUMMARY_STATISTICS)
    .flatMap((item, index): SpatialJoinSummaryStatistic[] => {
      const itemPath = `${path}.summaryStatistics[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const kind = stringValue(item.kind);
      if (!spatialJoinSummaryStatisticKinds.has(kind)) {
        errors.push(`${itemPath}.kind 仅支持 SUM、MIN、MAX、MEAN 或 STDDEV`);
      }
      return [{
        statisticId: stringValue(item.statisticId),
        kind: spatialJoinSummaryStatisticKinds.has(kind)
          ? kind as SpatialJoinSummaryStatistic['kind'] : null,
        sourceColumnName: stringValue(item.sourceColumnName),
        outputColumnName: stringValue(item.outputColumnName),
      }];
    });
  let keepRule: SpatialJoinOneToOneOptions['keepRule'] = null;
  if (value.keepRule !== undefined && value.keepRule !== null) {
    if (!isRecord(value.keepRule)) {
      errors.push(`${path}.keepRule 必须是对象或 null`);
    } else {
      const strategy = ['FIRST', 'LARGEST', 'SMALLEST', 'NEWEST', 'OLDEST']
        .includes(stringValue(value.keepRule.strategy))
        ? stringValue(value.keepRule.strategy) as NonNullable<SpatialJoinOneToOneOptions['keepRule']>['strategy']
        : null;
      if (value.keepRule.strategy !== undefined
        && value.keepRule.strategy !== null
        && strategy === null) {
        errors.push(`${path}.keepRule.strategy 不是受支持的保留策略`);
      }
      keepRule = {
        strategy,
        orderByColumnName: value.keepRule.orderByColumnName === undefined
          || value.keepRule.orderByColumnName === null
          ? null : stringValue(value.keepRule.orderByColumnName),
        stableOrder: parseSortFields(
          value.keepRule.stableOrder,
          `${path}.keepRule.stableOrder`,
          errors,
        ),
      };
    }
  }
  return {
    mode,
    joinCountColumnName: stringValue(value.joinCountColumnName),
    summaryStatistics,
    keepRule,
  };
};

export const parseSpatialJoinTemporalCondition = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialJoinTemporalCondition | null => {
  if (value === undefined || value === null) return null;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象或 null`);
    return null;
  }
  const rawRelationship = stringValue(value.relationship);
  if (!spatialJoinTemporalRelationships.has(rawRelationship)) {
    errors.push(`${path}.relationship 不是受支持的时间关系`);
  }
  const nullableColumnName = (raw: unknown, field: string): string | null => {
    if (raw === undefined || raw === null) return null;
    if (typeof raw !== 'string') {
      errors.push(`${path}.${field} 必须是字符串或 null`);
      return null;
    }
    return raw;
  };
  let nearDistance: number | null = null;
  if (value.nearDistance !== undefined && value.nearDistance !== null) {
    if (typeof value.nearDistance !== 'number' || !Number.isSafeInteger(value.nearDistance)) {
      errors.push(`${path}.nearDistance 必须是安全整数或 null`);
    } else {
      nearDistance = value.nearDistance;
    }
  }
  const rawUnit = stringValue(value.nearDistanceUnit);
  if (value.nearDistanceUnit !== undefined && value.nearDistanceUnit !== null
    && !spatialDurationUnits.has(rawUnit)) {
    errors.push(`${path}.nearDistanceUnit 不是受支持的固定时长单位`);
  }
  return {
    relationship: spatialJoinTemporalRelationships.has(rawRelationship)
      ? rawRelationship as SpatialJoinTemporalCondition['relationship'] : null,
    leftStartColumnName: stringValue(value.leftStartColumnName),
    leftEndColumnName: nullableColumnName(value.leftEndColumnName, 'leftEndColumnName'),
    rightStartColumnName: stringValue(value.rightStartColumnName),
    rightEndColumnName: nullableColumnName(value.rightEndColumnName, 'rightEndColumnName'),
    nearDistance,
    nearDistanceUnit: spatialDurationUnits.has(rawUnit)
      ? rawUnit as SpatialJoinTemporalCondition['nearDistanceUnit'] : null,
  };
};

export const parseSpatialJoinSpatialNearCondition = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialJoinSpatialNearCondition | null => {
  if (value === undefined || value === null) return null;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象或 null`);
    return null;
  }
  const rawMethod = stringValue(value.distanceMethod);
  if (rawMethod !== '' && rawMethod !== 'PLANAR' && rawMethod !== 'GEODESIC') {
    errors.push(`${path}.distanceMethod 仅支持 PLANAR 或 GEODESIC`);
  }
  let distance: number | null = null;
  if (value.distance !== undefined && value.distance !== null) {
    if (typeof value.distance !== 'number' || !Number.isFinite(value.distance)) {
      errors.push(`${path}.distance 必须是有限数值或 null`);
    } else {
      distance = value.distance;
    }
  }
  const rawUnit = stringValue(value.distanceUnit);
  if (value.distanceUnit !== undefined && value.distanceUnit !== null
    && !spatialDistanceUnits.has(rawUnit)) {
    errors.push(`${path}.distanceUnit 不是受支持的距离单位`);
  }
  return {
    leftGeometryColumnName: stringValue(value.leftGeometryColumnName),
    rightGeometryColumnName: stringValue(value.rightGeometryColumnName),
    distanceMethod: rawMethod === 'PLANAR' || rawMethod === 'GEODESIC' ? rawMethod : null,
    distance,
    distanceUnit: spatialDistanceUnits.has(rawUnit) ? rawUnit as SpatialDistanceUnit : null,
  };
};

export const parseSpatialJoinDistanceOutput = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialJoinDistanceOutput | null => {
  if (value === undefined || value === null) return null;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象或 null`);
    return null;
  }
  if (value.enabled !== undefined && typeof value.enabled !== 'boolean') {
    errors.push(`${path}.enabled 必须是 boolean`);
  }
  const spatialUnit = stringValue(value.spatialDistanceUnit);
  if (value.spatialDistanceUnit !== undefined && value.spatialDistanceUnit !== null
    && !spatialDistanceUnits.has(spatialUnit)) {
    errors.push(`${path}.spatialDistanceUnit 不是受支持的距离单位`);
  }
  const temporalUnit = stringValue(value.temporalDifferenceUnit);
  if (value.temporalDifferenceUnit !== undefined && value.temporalDifferenceUnit !== null
    && !spatialDurationUnits.has(temporalUnit)) {
    errors.push(`${path}.temporalDifferenceUnit 不是受支持的固定时长单位`);
  }
  return {
    enabled: value.enabled === true,
    spatialDistanceColumnName: stringValue(value.spatialDistanceColumnName),
    spatialDistanceUnit: spatialDistanceUnits.has(spatialUnit)
      ? spatialUnit as SpatialDistanceUnit : null,
    temporalDifferenceColumnName: stringValue(value.temporalDifferenceColumnName),
    temporalDifferenceUnit: spatialDurationUnits.has(temporalUnit)
      ? temporalUnit as SpatialJoinDistanceOutput['temporalDifferenceUnit'] : null,
  };
};

export const spatialPredicates = new Set([
  'INTERSECTS',
  'CONTAINS',
  'WITHIN',
  'COVERS',
  'COVERED_BY',
  'TOUCHES',
  'OVERLAPS',
  'CROSSES',
  'EQUALS',
]);

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_JOIN'>>(value, path, (configuration, errors) => {
      if (configuration.joinType !== 'INNER' && configuration.joinType !== 'LEFT') {
        errors.push(`${path}.joinType 仅支持 INNER 或 LEFT`);
      }
      if (!Array.isArray(configuration.conditions)) {
        errors.push(`${path}.conditions 必须是数组`);
      }
      const rawConditions = Array.isArray(configuration.conditions)
        ? configuration.conditions : [];
      if (rawConditions.length > 8) errors.push(`${path}.conditions 不能超过 8 项`);
      const conditions = rawConditions.slice(0, 8).flatMap((item, index) => {
        const conditionPath = `${path}.conditions[${index}]`;
        if (!isRecord(item)) {
          errors.push(`${conditionPath} 必须是对象`);
          return [];
        }
        const rawPredicate = stringValue(item.predicate);
        if (!spatialPredicates.has(rawPredicate)) {
          errors.push(`${conditionPath}.predicate 不是受支持的空间谓词`);
        }
        return [{
          leftGeometryColumnName: stringValue(item.leftGeometryColumnName),
          predicate: spatialPredicates.has(rawPredicate)
            ? rawPredicate as Configuration<'SPATIAL_JOIN'>['conditions'][number]['predicate']
            : null,
          rightGeometryColumnName: stringValue(item.rightGeometryColumnName),
        }];
      });
      const base: Configuration<'SPATIAL_JOIN'> = {
        leftTableName: stringValue(configuration.leftTableName),
        rightTableName: stringValue(configuration.rightTableName),
        outputTableName: stringValue(configuration.outputTableName),
        joinType: configuration.joinType === 'LEFT' ? 'LEFT' : 'INNER',
        conditions,
      };
      const withAttributes: Configuration<'SPATIAL_JOIN'> = configuration.attributeConditions === undefined
        ? base
        : configuration.attributeConditions === null
          ? { ...base, attributeConditions: null }
          : {
              ...base,
              attributeConditions: parseJoinConditions(
                configuration.attributeConditions,
                `${path}.attributeConditions`,
                errors,
              ),
            };
      let parsed: Configuration<'SPATIAL_JOIN'> = withAttributes;
      if (configuration.outputColumns === null) {
        parsed = { ...parsed, outputColumns: null };
      } else if (configuration.outputColumns !== undefined) {
        parsed = {
          ...parsed,
          outputColumns: parseJoinOutputColumns(
            configuration.outputColumns,
            `${path}.outputColumns`,
            errors,
          ),
        };
      }
      if (configuration.joinOperation === null) {
        parsed = { ...parsed, joinOperation: null };
      } else if (configuration.joinOperation !== undefined) {
        if (configuration.joinOperation !== 'JOIN_ONE_TO_MANY'
          && configuration.joinOperation !== 'JOIN_ONE_TO_ONE') {
          errors.push(`${path}.joinOperation 仅支持 JOIN_ONE_TO_MANY 或 JOIN_ONE_TO_ONE`);
        }
        parsed = {
          ...parsed,
          joinOperation: configuration.joinOperation === 'JOIN_ONE_TO_MANY'
            || configuration.joinOperation === 'JOIN_ONE_TO_ONE'
            ? configuration.joinOperation
            : null,
        };
      }
      if (configuration.oneToOne !== undefined) {
        parsed = {
          ...parsed,
          oneToOne: parseSpatialJoinOneToOne(
            configuration.oneToOne,
            `${path}.oneToOne`,
            errors,
          ),
        };
      }
      if (configuration.temporalCondition !== undefined) {
        parsed = {
          ...parsed,
          temporalCondition: parseSpatialJoinTemporalCondition(
          configuration.temporalCondition,
          `${path}.temporalCondition`,
          errors,
          ),
        };
      }
      if (configuration.spatialNear !== undefined) {
        parsed = {
          ...parsed,
          spatialNear: parseSpatialJoinSpatialNearCondition(
            configuration.spatialNear,
            `${path}.spatialNear`,
            errors,
          ),
        };
      }
      if (configuration.distanceOutput !== undefined) {
        parsed = {
          ...parsed,
          distanceOutput: parseSpatialJoinDistanceOutput(
            configuration.distanceOutput,
            `${path}.distanceOutput`,
            errors,
          ),
        };
      }
      return parsed;
    })
  );
