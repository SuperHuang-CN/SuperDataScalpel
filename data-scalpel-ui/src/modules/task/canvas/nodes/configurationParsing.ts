import { spatialDistanceUnits } from "./spatialUnits";
import { parseTrackFixedTimeBoundary } from "./trackTimeBoundary";
import { parseCalendarWindow } from "./spatialCalendarWindow";
import { stringValue, validateOptionalUuid } from "../canvasValueParsers";
import { type SpatialGroupSummary, type SpatialTemporalSlicing, type TrackBoundaryConfiguration, type TrackSummaryStatistic, type CanvasNodeConfigurationByType, type CanvasNodeType as CanvasNodeTypeValue } from "../canvasTypes";
import type { CanvasParseResult } from "./nodeSpec";

export type Configuration<T extends CanvasNodeTypeValue> = CanvasNodeConfigurationByType<T>;

export const isRecord = (value: unknown): value is Record<string, unknown> => (
  typeof value === 'object' && value !== null && !Array.isArray(value)
);

export const parseConfiguration = <T>(
  value: unknown,
  _path: string,
  parser: (configuration: Record<string, unknown>, errors: string[]) => unknown,
): CanvasParseResult<T> => {
  const errors: string[] = [];
  const configuration = isRecord(value) ? value : {};
  const parsed = parser(configuration, errors) as T;
  return errors.length > 0
    ? { success: false, errors }
    : { success: true, value: parsed };
};

export const parseProcessorOutput = (
  value: unknown,
  path: string,
  errors: string[],
): { mode: 'REPLACE_SOURCE'; outputTableName: string | null } | { mode: 'CREATE_NEW_TABLE'; outputTableName: string } => {
  if (!isRecord(value)) {
    errors.push(`${path} 必须是输出方式对象`);
    return { mode: 'REPLACE_SOURCE', outputTableName: null };
  }
  if (value.mode === 'REPLACE_SOURCE') {
    if (value.outputTableName !== null && value.outputTableName !== undefined
      && typeof value.outputTableName !== 'string') {
      errors.push(`${path}.outputTableName 必须是字符串或 null`);
    }
    return { mode: 'REPLACE_SOURCE', outputTableName: value.outputTableName == null ? null : stringValue(value.outputTableName) };
  }
  if (value.mode === 'CREATE_NEW_TABLE') {
    if (typeof value.outputTableName !== 'string') {
      errors.push(`${path}.outputTableName 必须是字符串`);
    }
    return { mode: 'CREATE_NEW_TABLE', outputTableName: stringValue(value.outputTableName) };
  }
  errors.push(`${path}.mode 仅支持 REPLACE_SOURCE 或 CREATE_NEW_TABLE`);
  return { mode: 'REPLACE_SOURCE', outputTableName: null };
};

export const parseProcessorOperations = <T extends object>(
  value: unknown,
  path: string,
  errors: string[],
  parsePayload: (operation: Record<string, unknown>, operationPath: string) => T,
): Array<T & { operationId: string; sourceTableName: string; output: ReturnType<typeof parseProcessorOutput> }> => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index) => {
    const operationPath = `${path}[${index}]`;
    if (!isRecord(item)) {
      errors.push(`${operationPath} 必须是对象`);
      return [];
    }
    const operationId = stringValue(item.operationId);
    if (!operationId) errors.push(`${operationPath}.operationId 必须是 UUID`);
    else validateOptionalUuid(operationId, `${operationPath}.operationId`, errors);
    return [{
      operationId,
      sourceTableName: stringValue(item.sourceTableName),
      output: parseProcessorOutput(item.output, `${operationPath}.output`, errors),
      ...parsePayload(item, operationPath),
    }];
  });
};

export const parseOutputWrites = <T>(
  value: unknown,
  path: string,
  errors: string[],
  parseWrite: (item: Record<string, unknown>, itemPath: string) => T,
): T[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index) => {
    if (!isRecord(item)) {
      errors.push(`${path}[${index}] 必须是对象`);
      return [];
    }
    return [parseWrite(item, `${path}[${index}]`)];
  });
};

export const parseSnapshotDeletePolicy = (
  value: unknown,
  path: string,
  errors: string[],
): Configuration<'JDBC_SNAPSHOT_SYNC_OUTPUT'>['deletePolicy'] => {
  if (!isRecord(value)) {
    errors.push(`${path} 必须是删除策略对象`);
    return { action: 'KEEP', maxDeleteRows: null, maxDeleteRatio: null };
  }
  if (value.action !== 'KEEP' && value.action !== 'DELETE') {
    errors.push(`${path}.action 仅支持 KEEP 或 DELETE`);
  }
  const parseNullableNumber = (raw: unknown, fieldPath: string): number | null => {
    if (raw === null || raw === undefined) return null;
    if (typeof raw !== 'number' || !Number.isFinite(raw)) {
      errors.push(`${fieldPath} 必须是有限数值或 null`);
      return null;
    }
    return raw;
  };
  return {
    action: value.action === 'DELETE' ? 'DELETE' : 'KEEP',
    maxDeleteRows: parseNullableNumber(value.maxDeleteRows, `${path}.maxDeleteRows`),
    maxDeleteRatio: parseNullableNumber(value.maxDeleteRatio, `${path}.maxDeleteRatio`),
  };
};

export const parseInteger = (
  value: unknown,
  path: string,
  errors: string[],
  fallback: number,
) => {
  if (typeof value !== 'number' || !Number.isInteger(value)) {
    errors.push(`${path} 必须是整数`);
    return fallback;
  }
  return value;
};

export const parseFiniteNumber = (
  value: unknown,
  path: string,
  errors: string[],
  fallback: number,
) => {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    errors.push(`${path} 必须是有限数值`);
    return fallback;
  }
  return value;
};

export const parseSpatialGroupSummary = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialGroupSummary | null => {
  if (value === null || value === undefined) return null;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象或 null`);
    return null;
  }
  const nullableString = (field: string): string | null => {
    const candidate = value[field];
    if (candidate !== null && candidate !== undefined && typeof candidate !== 'string') {
      errors.push(`${path}.${field} 必须是字符串或 null`);
    }
    return typeof candidate === 'string' ? candidate : null;
  };
  return {
    groupByColumnName: stringValue(value.groupByColumnName),
    includeMinorityMajority: value.includeMinorityMajority === true,
    includeGroupPercentage: value.includeGroupPercentage === true,
    minorityFlagColumnName: nullableString('minorityFlagColumnName'),
    majorityFlagColumnName: nullableString('majorityFlagColumnName'),
    groupPercentageColumnName: nullableString('groupPercentageColumnName'),
  };
};

export const durationUnits = new Set(['MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS', 'DAYS', 'WEEKS']);

export const parseSpatialTemporalSlicing = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialTemporalSlicing | null => {
  if (value === null || value === undefined) return null;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象或 null`);
    return null;
  }
  const intervalUnit = stringValue(value.intervalUnit);
  const repeatIntervalUnit = value.repeatIntervalUnit == null
    ? null : stringValue(value.repeatIntervalUnit);
  if (!durationUnits.has(intervalUnit)) errors.push(`${path}.intervalUnit 不是受支持的时长单位`);
  if (repeatIntervalUnit !== null && !durationUnits.has(repeatIntervalUnit)) {
    errors.push(`${path}.repeatIntervalUnit 不是受支持的时长单位`);
  }
  if (typeof value.interval !== 'number' || !Number.isSafeInteger(value.interval)) {
    errors.push(`${path}.interval 必须是安全整数`);
  }
  if (value.repeatInterval !== null && value.repeatInterval !== undefined
    && (typeof value.repeatInterval !== 'number' || !Number.isSafeInteger(value.repeatInterval))) {
    errors.push(`${path}.repeatInterval 必须是安全整数或 null`);
  }
  if (value.referenceTime !== null && value.referenceTime !== undefined
    && typeof value.referenceTime !== 'string') {
    errors.push(`${path}.referenceTime 必须是字符串或 null`);
  }
  return {
    timeColumnName: stringValue(value.timeColumnName),
    ...parseCalendarWindow(value, path, errors),
    interval: typeof value.interval === 'number' ? value.interval : 0,
    intervalUnit: durationUnits.has(intervalUnit)
      ? intervalUnit as SpatialTemporalSlicing['intervalUnit'] : 'HOURS',
    repeatInterval: typeof value.repeatInterval === 'number' ? value.repeatInterval : null,
    repeatIntervalUnit: repeatIntervalUnit !== null && durationUnits.has(repeatIntervalUnit)
      ? repeatIntervalUnit as SpatialTemporalSlicing['repeatIntervalUnit'] : null,
    referenceTime: typeof value.referenceTime === 'string' ? value.referenceTime : null,
    timeZone: stringValue(value.timeZone),
    windowStartColumnName: stringValue(value.windowStartColumnName),
    windowEndColumnName: stringValue(value.windowEndColumnName),
  };
};

export const trackDistanceUnits = spatialDistanceUnits;

export const trackSummaryKinds = new Set([
  'COUNT', 'COUNT_FIELD', 'ANY', 'SUM', 'MEAN', 'MIN', 'MAX', 'RANGE', 'STDDEV', 'VARIANCE', 'FIRST', 'LAST',
]);

export const parseNullableFiniteNumber = (value: unknown, path: string, errors: string[]) => {
  if (value === null || value === undefined) return null;
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    errors.push(`${path} 必须是有限数值或 null`);
    return null;
  }
  return value;
};

export const parseTrackBoundaries = (
  value: unknown,
  path: string,
  errors: string[],
): TrackBoundaryConfiguration => {
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象`);
    return {
      maximumTimeGap: null,
      maximumTimeGapUnit: null,
      maximumDistanceGap: null,
      maximumDistanceGapUnit: null,
    };
  }
  const timeUnit = value.maximumTimeGapUnit == null ? null : stringValue(value.maximumTimeGapUnit);
  const distanceUnit = value.maximumDistanceGapUnit == null
    ? null : stringValue(value.maximumDistanceGapUnit);
  if (timeUnit !== null && !durationUnits.has(timeUnit)) {
    errors.push(`${path}.maximumTimeGapUnit 不是受支持的时长单位`);
  }
  if (distanceUnit !== null && !trackDistanceUnits.has(distanceUnit)) {
    errors.push(`${path}.maximumDistanceGapUnit 不是受支持的距离单位`);
  }
  return {
    maximumTimeGap: parseNullableFiniteNumber(value.maximumTimeGap, `${path}.maximumTimeGap`, errors),
    ...(value.fixedTimeBoundary === undefined ? {} : {
      fixedTimeBoundary: parseTrackFixedTimeBoundary(value.fixedTimeBoundary, `${path}.fixedTimeBoundary`, errors),
    }),
    maximumTimeGapUnit: timeUnit !== null && durationUnits.has(timeUnit)
      ? timeUnit as TrackBoundaryConfiguration['maximumTimeGapUnit'] : null,
    maximumDistanceGap: parseNullableFiniteNumber(
      value.maximumDistanceGap, `${path}.maximumDistanceGap`, errors,
    ),
    maximumDistanceGapUnit: distanceUnit !== null && trackDistanceUnits.has(distanceUnit)
      ? distanceUnit as TrackBoundaryConfiguration['maximumDistanceGapUnit'] : null,
  };
};

export const parseTrackSummaries = (
  value: unknown,
  path: string,
  errors: string[],
): TrackSummaryStatistic[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > 32) errors.push(`${path} 不能超过 32 项`);
  return value.slice(0, 32).flatMap((item, index): TrackSummaryStatistic[] => {
    const itemPath = `${path}[${index}]`;
    if (!isRecord(item)) {
      errors.push(`${itemPath} 必须是对象`);
      return [];
    }
    const kind = stringValue(item.kind);
    if (!trackSummaryKinds.has(kind)) errors.push(`${itemPath}.kind 不是受支持的轨迹汇总类型`);
    if (item.sourceColumnName !== null && item.sourceColumnName !== undefined
      && typeof item.sourceColumnName !== 'string') {
      errors.push(`${itemPath}.sourceColumnName 必须是字符串或 null`);
    }
    return [{
      statisticId: validateOptionalUuid(stringValue(item.statisticId), `${itemPath}.statisticId`, errors),
      kind: trackSummaryKinds.has(kind) ? kind as TrackSummaryStatistic['kind'] : 'COUNT',
      sourceColumnName: typeof item.sourceColumnName === 'string' ? item.sourceColumnName : null,
      outputColumnName: stringValue(item.outputColumnName),
    }];
  });
};

export const groupByProximityTemporalUnits = new Set([
  'MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS', 'DAYS', 'WEEKS', 'MONTHS', 'YEARS',
]);
