import { parseNearestMatching } from './spatialNearest/matching';
import { spatialDistanceUnits, spatialAreaUnits } from './spatialUnits';
import { parseCenterFeatureColumns, parseCenterOutputTable, parseCenterResultMode } from './spatialCenterDispersion/resultMode';
import { parseUnaryPolicy } from './unaryGeometryPolicy';
import { parseIncidentLifecycleOptions } from './trackDetectIncidents/readConfiguration';
import { parseTrackFixedTimeBoundary } from './trackTimeBoundary';
import { parseBinSizeSemantics } from './spatialBinAggregate/binSizeSemantics';
import { parseH3 } from './spatialBinAggregate/h3';
import { parsePlanarGrid } from './spatialBinAggregate/planarGrid';
import { parseCalendarWindow } from './spatialCalendarWindow';
import { parseDbscanOptions } from './spatialPointCluster/dbscanOptions';
import { parseHdbscanOptions } from './spatialPointCluster/hdbscanOptions';
import { parseDwellOptions } from './trackFindDwell/rangeOptions';
import { parseMotionWindowOptions } from './trackMotionStatistics/windowOptions';
import { parseWithinStatisticOptions } from './spatialSummarizeWithin/statisticOptions';
import { parseWithinGroupResult } from './spatialSummarizeWithin/groupResult';
import { parseWithinRegions } from './spatialSummarizeWithin/regions';
import { parseReconstruction } from './trackReconstruct/reconstruction';
import {
  legacyTableName,
  parseAggregations,
  parseCanvasLiteral,
  parseDeduplicateKeepStrategy,
  parseDerivations,
  parseFileOutputConflictPolicy,
  parseFileOutputFormatOptions,
  parseFilterCondition,
  parseJoinConditions,
  parseJoinOutputColumns,
  parseJoinType,
  parseKafkaValueSchema,
  parseMappings,
  parsePlatformTypeDefinition,
  parseRuntimeParameters,
  parseSortFields,
  parseStreamJoinType,
  parseStringArray,
  parseTypeCasts,
  parseUnionMode,
  parseWriteMode,
  stringValue,
  validateOptionalUuid,
} from '../canvasDefinitionIO';
import {
  CANVAS_NULL_HANDLING_MAX_RULES,
  CANVAS_JSON_EXTRACT_MAX_EXTRACTIONS,
  CANVAS_JSON_EXTRACT_MAX_PATH_LENGTH,
  CANVAS_GEOMETRY_DERIVE_MAX_DERIVATIONS,
  CANVAS_SPATIAL_WITHIN_MAX_STATISTICS,
  CANVAS_SPATIAL_BIN_MAX_STATISTICS,
  CANVAS_SPATIAL_CENTER_MAX_ANALYSES,
  CANVAS_SPATIAL_CENTER_MAX_GROUP_COLUMNS,
  CANVAS_SPATIAL_DENSITY_MAX_FIELDS,
  CANVAS_SPATIAL_MULTI_VARIABLE_GRID_MAX_VARIABLES,
  CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_ANALYSIS_FIELDS,
  CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_APPEND_FIELDS,
  CANVAS_TRACE_PROXIMITY_MAX_INTERESTS,
  CANVAS_SNAP_TRACKS_MAX_LINE_FIELDS,
  CANVAS_MASKING_MAX_FIELD_RULES,
  CANVAS_TOP_N_MAX_LIMIT,
  CANVAS_SPATIAL_MEASURE_MAX_MEASUREMENTS,
  CANVAS_SPATIAL_AGGREGATE_MAX_AGGREGATIONS,
  CANVAS_SPATIAL_AGGREGATE_MAX_SUMMARY_STATISTICS,
  CANVAS_SPATIAL_JOIN_MAX_SUMMARY_STATISTICS,
  CANVAS_VALUE_MAPPING_MAX_ENTRIES_PER_RULE,
  CANVAS_VALUE_MAPPING_MAX_RULES,
  CANVAS_VALUE_MAPPING_MAX_TOTAL_ENTRIES,
  CANVAS_WINDOW_MAX_FUNCTIONS,
  CanvasNodeType,
  normalizeFileOutputPath,
  type NullHandlingRule,
  type MaskFieldRule,
  type MaskingRuleDefinition,
  type JsonExtraction,
  type GeometryDerivation,
  type SpatialWithinStatistic,
  type SpatialGroupSummary,
  type SpatialTemporalSlicing,
  type TrackBoundaryConfiguration,
  type TrackSummaryStatistic,
  type TrackMotionMetric,
  type SpatialBinStatistic,
  type SpatialPointClusterParameters,
  type SpatialCenterDispersionAnalysis,
  type SpatialDensityField,
  type SpatialMultiVariableGridVariable,
  type SpatialSimilarLocationsAnalysisField,
  type SpatialSimilarLocationsAppendField,
  type SpatialEnrichFromGridField,
  type SpatialGroupByProximityAttributeCondition,
  type SpatialGroupByProximityAttributeRelationship,
  type SpatialGroupByProximitySpatialRelationship,
  type SpatialGroupByProximityTemporalCondition,
  type SpatialGroupByProximityTemporalRelationship,
  type SpatialGroupByProximityTemporalUnit,
  type TraceProximityEntityOfInterest,
  type TraceProximityInterestSource,
  type SnapTracksDirectionMatching,
  type SnapTracksLineField,
  type RowsFrameBoundary,
  type RowsWindowFrame,
  type SpatialAreaUnit,
  type SpatialDistanceUnit,
  type SpatialMeasurement,
  type SpatialAggregation,
  type SpatialAggregateDissolveOptions,
  type SpatialAggregateStatistic,
  type SpatialJoinOneToOneOptions,
  type SpatialJoinSummaryStatistic,
  type SpatialJoinTemporalCondition,
  type SpatialJoinSpatialNearCondition,
  type SpatialJoinDistanceOutput,
  type UnionMergeTable,
  type CanvasColumnSchema,
  type ValueMappingRule,
  type WindowFunctionItem,
  type CanvasNodeConfigurationByType,
  type CanvasNodeType as CanvasNodeTypeValue,
} from '../canvasTypes';
import { createMaskingRuleDefinition } from '../../model/maskingRule';
import { findFilterSqlExpressionViolation } from './filter/filterSqlExpression';
import type { CanvasParseResult } from './nodeSpec';

type Configuration<T extends CanvasNodeTypeValue> = CanvasNodeConfigurationByType<T>;

type ConfigurationParserMap = {
  [T in CanvasNodeTypeValue]: (
    value: unknown,
    path: string,
  ) => CanvasParseResult<Configuration<T>>;
};

const isRecord = (value: unknown): value is Record<string, unknown> => (
  typeof value === 'object' && value !== null && !Array.isArray(value)
);

const parseConfiguration = <T>(
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

const parseProcessorOutput = (
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

const parseProcessorOperations = <T extends object>(
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

const parseJdbcInputTables = (
  configuration: Record<string, unknown>,
  path: string,
  errors: string[],
): Configuration<'JDBC_INPUT'>['tables'] => {
  if (configuration.tables === undefined || configuration.tables === null) {
    return [];
  }
  if (!Array.isArray(configuration.tables)) {
    errors.push(`${path}.tables 必须是数组`);
    return [];
  }
  return configuration.tables.flatMap((item, index) => {
    if (!isRecord(item)) {
      errors.push(`${path}.tables[${index}] 必须是对象`);
      return [];
    }
    const readOptionsValue = item.readOptions;
    if (readOptionsValue !== undefined && readOptionsValue !== null && !Array.isArray(readOptionsValue)) {
      errors.push(`${path}.tables[${index}].readOptions 必须是数组`);
    }
    const readOptions = Array.isArray(readOptionsValue)
      ? readOptionsValue.flatMap((option, optionIndex) => {
        if (!isRecord(option)) {
          errors.push(`${path}.tables[${index}].readOptions[${optionIndex}] 必须是对象`);
          return [];
        }
        return [{ name: stringValue(option.name), value: stringValue(option.value) }];
      })
      : [];
    return [{ tableName: stringValue(item.tableName), readOptions }];
  });
};

const parseModelInputSelections = (
  value: unknown,
  path: string,
  errors: string[],
): Configuration<'MODEL_INPUT'>['models'] => {
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
    return [{ modelId: validateOptionalUuid(stringValue(item.modelId), `${path}[${index}].modelId`, errors) }];
  });
};

const parseOutputWrites = <T>(
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

const parseFileDatasetInputTables = (
  value: unknown,
  path: string,
  errors: string[],
): Configuration<'FILE_DATASET_INPUT'>['tables'] => {
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
    return [{
      fileDatasetTableId: validateOptionalUuid(
        stringValue(item.fileDatasetTableId), `${path}[${index}].fileDatasetTableId`, errors,
      ),
    }];
  });
};

const parseHttpApiInputResources = (
  value: unknown,
  path: string,
  errors: string[],
): Configuration<'HTTP_API_INPUT'>['resources'] => {
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
    return [{
      resourceId: validateOptionalUuid(stringValue(item.resourceId), `${path}[${index}].resourceId`, errors),
      outputTableName: stringValue(item.outputTableName),
      runtimeParameters: parseRuntimeParameters(item.runtimeParameters, `${path}[${index}].runtimeParameters`, errors),
    }];
  });
};

const parseSpatialServiceInputResources = (
  value: unknown,
  path: string,
  errors: string[],
): Configuration<'SPATIAL_SERVICE_INPUT'>['resources'] => {
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
    return [{
      resourceId: validateOptionalUuid(stringValue(item.resourceId), `${path}[${index}].resourceId`, errors),
      outputTableName: stringValue(item.outputTableName),
    }];
  });
};

const parseSnapshotDeletePolicy = (
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

const parseJdbcQueryOutputColumns = (
  value: unknown,
  path: string,
  errors: string[],
): CanvasColumnSchema[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): CanvasColumnSchema[] => {
    const columnPath = `${path}[${index}]`;
    if (!isRecord(item)) {
      errors.push(`${columnPath} 必须是对象`);
      return [];
    }
    const type = parsePlatformTypeDefinition({
      type: item.fieldType,
      length: item.length,
      precision: item.precision,
      scale: item.scale,
      geometry: item.geometry,
    }, columnPath, errors);
    if (typeof item.nullable !== 'boolean') errors.push(`${columnPath}.nullable 必须是 Boolean`);
    if (item.defaultValue !== null && item.defaultValue !== undefined
      && typeof item.defaultValue !== 'string') {
      errors.push(`${columnPath}.defaultValue 必须是字符串或 null`);
    }
    if (typeof item.autoIncrement !== 'boolean') {
      errors.push(`${columnPath}.autoIncrement 必须是 Boolean`);
    }
    if (typeof item.generated !== 'boolean') {
      errors.push(`${columnPath}.generated 必须是 Boolean`);
    }
    if (item.comment !== null && item.comment !== undefined && typeof item.comment !== 'string') {
      errors.push(`${columnPath}.comment 必须是字符串或 null`);
    }
    return [{
      name: stringValue(item.name),
      fieldType: type.type,
      length: type.length,
      precision: type.precision,
      scale: type.scale,
      nullable: typeof item.nullable === 'boolean' ? item.nullable : true,
      defaultValue: typeof item.defaultValue === 'string' ? item.defaultValue : null,
      autoIncrement: typeof item.autoIncrement === 'boolean' ? item.autoIncrement : false,
      generated: typeof item.generated === 'boolean' ? item.generated : false,
      comment: typeof item.comment === 'string' ? item.comment : null,
      geometry: null,
    }];
  });
};

const parseNullHandlingRules = (
  value: unknown,
  path: string,
  errors: string[],
): NullHandlingRule[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_NULL_HANDLING_MAX_RULES) {
    errors.push(`${path} 不能超过 ${CANVAS_NULL_HANDLING_MAX_RULES} 项`);
  }
  return value.slice(0, CANVAS_NULL_HANDLING_MAX_RULES)
    .flatMap((item, index): NullHandlingRule[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      if (item.kind === 'DROP_ROW') {
        if (item.matchMode !== 'ANY_NULL' && item.matchMode !== 'ALL_NULL') {
          errors.push(`${itemPath}.matchMode 仅支持 ANY_NULL 或 ALL_NULL`);
        }
        return [{
          kind: 'DROP_ROW',
          columnNames: parseStringArray(item.columnNames, `${itemPath}.columnNames`, errors),
          matchMode: item.matchMode === 'ALL_NULL' ? 'ALL_NULL' : 'ANY_NULL',
        }];
      }
      if (item.kind === 'FILL_LITERAL') {
        return [{
          kind: 'FILL_LITERAL',
          columnName: stringValue(item.columnName),
          value: parseCanvasLiteral(item.value, `${itemPath}.value`, errors),
        }];
      }
      errors.push(`${itemPath}.kind 仅支持 DROP_ROW 或 FILL_LITERAL`);
      return [];
    });
};

const parseValueMappingRules = (
  value: unknown,
  path: string,
  errors: string[],
): ValueMappingRule[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_VALUE_MAPPING_MAX_RULES) {
    errors.push(`${path} 不能超过 ${CANVAS_VALUE_MAPPING_MAX_RULES} 项`);
  }
  let totalEntries = 0;
  const rules = value.slice(0, CANVAS_VALUE_MAPPING_MAX_RULES)
    .flatMap((item, index): ValueMappingRule[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const rawEntries = item.entries;
      if (rawEntries !== undefined && rawEntries !== null && !Array.isArray(rawEntries)) {
        errors.push(`${itemPath}.entries 必须是数组`);
      }
      const entries = Array.isArray(rawEntries)
        ? rawEntries.slice(0, CANVAS_VALUE_MAPPING_MAX_ENTRIES_PER_RULE)
          .flatMap((entry, entryIndex) => {
            const entryPath = `${itemPath}.entries[${entryIndex}]`;
            if (!isRecord(entry)) {
              errors.push(`${entryPath} 必须是对象`);
              return [];
            }
            return [{
              sourceValue: parseCanvasLiteral(
                entry.sourceValue,
                `${entryPath}.sourceValue`,
                errors,
              ),
              targetValue: entry.targetValue === null || entry.targetValue === undefined
                ? null
                : parseCanvasLiteral(
                  entry.targetValue,
                  `${entryPath}.targetValue`,
                  errors,
                ),
            }];
          })
        : [];
      if (Array.isArray(rawEntries)
        && rawEntries.length > CANVAS_VALUE_MAPPING_MAX_ENTRIES_PER_RULE) {
        errors.push(
          `${itemPath}.entries 不能超过 ${CANVAS_VALUE_MAPPING_MAX_ENTRIES_PER_RULE} 项`,
        );
      }
      totalEntries += entries.length;
      const unmatchedStrategies = new Set(['KEEP', 'SET_NULL', 'SET_LITERAL', 'ERROR']);
      if (!unmatchedStrategies.has(stringValue(item.unmatchedStrategy))) {
        errors.push(`${itemPath}.unmatchedStrategy 不是受支持的策略`);
      }
      const unmatchedStrategy = unmatchedStrategies.has(stringValue(item.unmatchedStrategy))
        ? stringValue(item.unmatchedStrategy) as ValueMappingRule['unmatchedStrategy']
        : 'KEEP';
      return [{
        columnName: stringValue(item.columnName),
        entries,
        unmatchedStrategy,
        unmatchedValue: item.unmatchedValue === null || item.unmatchedValue === undefined
          ? null
          : parseCanvasLiteral(
            item.unmatchedValue,
            `${itemPath}.unmatchedValue`,
            errors,
          ),
      }];
    });
  if (totalEntries > CANVAS_VALUE_MAPPING_MAX_TOTAL_ENTRIES) {
    errors.push(`${path} 映射项总数不能超过 ${CANVAS_VALUE_MAPPING_MAX_TOTAL_ENTRIES}`);
  }
  return rules;
};

const spatialAggregationKinds = new Set(['UNION', 'INTERSECTION', 'COLLECT', 'ENVELOPE']);

const parseSpatialAggregations = (
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

const unionMergeFieldActions = new Set(['MATCH', 'RENAME', 'REMOVE']);

const parseUnionMergingTables = (
  value: unknown,
  path: string,
  errors: string[],
): UnionMergeTable[] | null => {
  if (value === undefined || value === null) return null;
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组或 null`);
    return [];
  }
  return value.flatMap((item, tableIndex): UnionMergeTable[] => {
    const tablePath = `${path}[${tableIndex}]`;
    if (!isRecord(item)) {
      errors.push(`${tablePath} 必须是对象`);
      return [];
    }
    if (!Array.isArray(item.fieldRules)) {
      errors.push(`${tablePath}.fieldRules 必须是数组`);
    }
    const fieldRules = Array.isArray(item.fieldRules)
      ? item.fieldRules.flatMap((rule, ruleIndex): UnionMergeTable['fieldRules'] => {
        const rulePath = `${tablePath}.fieldRules[${ruleIndex}]`;
        if (!isRecord(rule)) {
          errors.push(`${rulePath} 必须是对象`);
          return [];
        }
        const action = stringValue(rule.action);
        if (!unionMergeFieldActions.has(action)) {
          errors.push(`${rulePath}.action 仅支持 MATCH、RENAME 或 REMOVE`);
        }
        if (rule.targetColumnName !== undefined && rule.targetColumnName !== null
            && typeof rule.targetColumnName !== 'string') {
          errors.push(`${rulePath}.targetColumnName 必须是字符串或 null`);
        }
        return [{
          sourceColumnName: stringValue(rule.sourceColumnName),
          action: unionMergeFieldActions.has(action)
            ? action as UnionMergeTable['fieldRules'][number]['action']
            : null,
          targetColumnName: typeof rule.targetColumnName === 'string'
            ? rule.targetColumnName
            : null,
        }];
      })
      : [];
    return [{ tableName: stringValue(item.tableName), fieldRules }];
  });
};

const spatialAggregateStatisticKinds = new Set([
  'COUNT_FIELD', 'SUM', 'MEAN', 'MIN', 'MAX', 'RANGE', 'STDDEV', 'VARIANCE', 'ANY',
]);
const spatialAggregateDissolveGroupingModes = new Set([
  'ALL_OR_FIELDS', 'CONNECTED_COMPONENTS',
]);

const parseSpatialAggregateDissolve = (
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

const spatialJoinSummaryStatisticKinds = new Set(['SUM', 'MIN', 'MAX', 'MEAN', 'STDDEV']);
const spatialJoinTemporalRelationships = new Set([
  'EQUALS', 'INTERSECTS', 'DURING', 'CONTAINS', 'FINISHES', 'FINISHED_BY',
  'MEETS', 'MET_BY', 'OVERLAPS', 'OVERLAPPED_BY', 'STARTS', 'STARTED_BY',
  'NEAR', 'NEAR_BEFORE', 'NEAR_AFTER',
]);
const spatialDurationUnits = new Set([
  'MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS', 'DAYS', 'WEEKS',
]);

const parseSpatialJoinOneToOne = (
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

const parseSpatialJoinTemporalCondition = (
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

const parseSpatialJoinSpatialNearCondition = (
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

const parseSpatialJoinDistanceOutput = (
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

const parseMaskingDefinition = (
  value: unknown,
  path: string,
  errors: string[],
): MaskingRuleDefinition => {
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象`);
    return createMaskingRuleDefinition();
  }
  const supportedStrategies = new Set([
    'PARTIAL_MASK',
    'POSITION_MASK',
    'KEEP_LENGTH_MASK',
    'FIXED_VALUE',
    'NULLIFY',
  ]);
  const rawStrategy = stringValue(value.strategy);
  if (!supportedStrategies.has(rawStrategy)) {
    errors.push(`${path}.strategy 不是受支持的脱敏策略`);
  }
  const strategy = supportedStrategies.has(rawStrategy)
    ? rawStrategy as MaskingRuleDefinition['strategy']
    : 'PARTIAL_MASK';
  const defaults = createMaskingRuleDefinition(strategy);
  const parseKeepLength = (raw: unknown, fieldPath: string) => {
    if (typeof raw !== 'number' || !Number.isInteger(raw) || raw < 0 || raw > 1024) {
      errors.push(`${fieldPath} 必须是 0..1024 的整数`);
      return 0;
    }
    return raw;
  };
  const parseMaskPosition = (raw: unknown, fieldPath: string) => {
    if (raw === null || raw === undefined) return 2;
    if (typeof raw !== 'number' || !Number.isInteger(raw) || raw < 1 || raw > 1024) {
      errors.push(`${fieldPath} 必须是 1..1024 的整数`);
      return 2;
    }
    return raw;
  };
  if (strategy === 'PARTIAL_MASK') {
    const maskCharacter = value.maskCharacter === null
      || value.maskCharacter === undefined
      ? '*' : stringValue(value.maskCharacter);
    if (Array.from(maskCharacter).length !== 1) {
      errors.push(`${path}.maskCharacter 必须是一个 Unicode 字符`);
    }
    if (value.maskPosition !== null && value.maskPosition !== undefined
      || value.fixedValue !== null && value.fixedValue !== undefined) {
      errors.push(`${path} 包含不适用于 PARTIAL_MASK 的参数`);
    }
    return {
      ...defaults,
      keepPrefixLength: parseKeepLength(
        value.keepPrefixLength,
        `${path}.keepPrefixLength`,
      ),
      keepSuffixLength: parseKeepLength(
        value.keepSuffixLength,
        `${path}.keepSuffixLength`,
      ),
      maskCharacter,
    };
  }
  if (strategy === 'POSITION_MASK') {
    const maskCharacter = value.maskCharacter === null
      || value.maskCharacter === undefined
      ? '*' : stringValue(value.maskCharacter);
    if (Array.from(maskCharacter).length !== 1) {
      errors.push(`${path}.maskCharacter 必须是一个 Unicode 字符`);
    }
    if (value.keepPrefixLength !== null && value.keepPrefixLength !== undefined
      || value.keepSuffixLength !== null && value.keepSuffixLength !== undefined
      || value.fixedValue !== null && value.fixedValue !== undefined) {
      errors.push(`${path} 包含不适用于 POSITION_MASK 的参数`);
    }
    return {
      ...defaults,
      maskPosition: parseMaskPosition(value.maskPosition, `${path}.maskPosition`),
      maskCharacter,
    };
  }
  if (strategy === 'KEEP_LENGTH_MASK') {
    const maskCharacter = value.maskCharacter === null
      || value.maskCharacter === undefined
      ? '*' : stringValue(value.maskCharacter);
    if (Array.from(maskCharacter).length !== 1) {
      errors.push(`${path}.maskCharacter 必须是一个 Unicode 字符`);
    }
    if (value.keepPrefixLength !== null && value.keepPrefixLength !== undefined
      || value.keepSuffixLength !== null && value.keepSuffixLength !== undefined
      || value.maskPosition !== null && value.maskPosition !== undefined
      || value.fixedValue !== null && value.fixedValue !== undefined) {
      errors.push(`${path} 包含不适用于 KEEP_LENGTH_MASK 的参数`);
    }
    return { ...defaults, maskCharacter };
  }
  if (strategy === 'FIXED_VALUE') {
    if (typeof value.fixedValue !== 'string') {
      errors.push(`${path}.fixedValue 必须是字符串`);
    } else if (value.fixedValue.length > 1024) {
      errors.push(`${path}.fixedValue 不能超过 1024 个字符`);
    }
    if (value.keepPrefixLength !== null && value.keepPrefixLength !== undefined
      || value.keepSuffixLength !== null && value.keepSuffixLength !== undefined
      || value.maskPosition !== null && value.maskPosition !== undefined
      || value.maskCharacter !== null && value.maskCharacter !== undefined) {
      errors.push(`${path} 包含不适用于 FIXED_VALUE 的参数`);
    }
    return {
      ...defaults,
      fixedValue: typeof value.fixedValue === 'string' ? value.fixedValue : '',
    };
  }
  if (value.keepPrefixLength !== null && value.keepPrefixLength !== undefined
    || value.keepSuffixLength !== null && value.keepSuffixLength !== undefined
    || value.maskPosition !== null && value.maskPosition !== undefined
    || value.maskCharacter !== null && value.maskCharacter !== undefined
    || value.fixedValue !== null && value.fixedValue !== undefined) {
    errors.push(`${path} 包含不适用于 NULLIFY 的参数`);
  }
  return defaults;
};

const parseMaskFieldRules = (
  value: unknown,
  path: string,
  errors: string[],
): MaskFieldRule[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_MASKING_MAX_FIELD_RULES) {
    errors.push(`${path} 不能超过 ${CANVAS_MASKING_MAX_FIELD_RULES} 项`);
  }
  return value.slice(0, CANVAS_MASKING_MAX_FIELD_RULES)
    .flatMap((item, index): MaskFieldRule[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const ruleSource = item.ruleSource === 'GLOBAL' ? 'GLOBAL' : 'INLINE';
      if (item.ruleSource !== 'GLOBAL' && item.ruleSource !== 'INLINE') {
        errors.push(`${itemPath}.ruleSource 仅支持 GLOBAL 或 INLINE`);
      }
      let sourceRuleRef: MaskFieldRule['sourceRuleRef'] = null;
      if (ruleSource === 'GLOBAL') {
        if (!isRecord(item.sourceRuleRef)) {
          errors.push(`${itemPath}.sourceRuleRef 必须是对象`);
        } else {
          sourceRuleRef = {
            ruleId: validateOptionalUuid(
              stringValue(item.sourceRuleRef.ruleId),
              `${itemPath}.sourceRuleRef.ruleId`,
              errors,
            ),
            ruleCode: stringValue(item.sourceRuleRef.ruleCode),
            ruleName: stringValue(item.sourceRuleRef.ruleName),
          };
        }
      } else if (item.sourceRuleRef !== null && item.sourceRuleRef !== undefined) {
        errors.push(`${itemPath}.sourceRuleRef 仅允许 GLOBAL 规则配置`);
      }
      return [{
        fieldName: stringValue(item.fieldName),
        ruleSource,
        sourceRuleRef,
        definition: parseMaskingDefinition(
          item.definition,
          `${itemPath}.definition`,
          errors,
        ),
      }];
    });
};

const parseJsonExtractions = (
  value: unknown,
  path: string,
  errors: string[],
): JsonExtraction[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_JSON_EXTRACT_MAX_EXTRACTIONS) {
    errors.push(`${path} 不能超过 ${CANVAS_JSON_EXTRACT_MAX_EXTRACTIONS} 项`);
  }
  return value.slice(0, CANVAS_JSON_EXTRACT_MAX_EXTRACTIONS)
    .flatMap((item, index): JsonExtraction[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      if (typeof item.jsonPath !== 'string') {
        errors.push(`${itemPath}.jsonPath 必须是字符串`);
      }
      if (typeof item.outputColumnName !== 'string') {
        errors.push(`${itemPath}.outputColumnName 必须是字符串`);
      }
      const jsonPath = stringValue(item.jsonPath);
      if (jsonPath.length > CANVAS_JSON_EXTRACT_MAX_PATH_LENGTH) {
        errors.push(
          `${itemPath}.jsonPath 不能超过 ${CANVAS_JSON_EXTRACT_MAX_PATH_LENGTH} 个字符`,
        );
      }
      return [{
        jsonPath,
        outputColumnName: stringValue(item.outputColumnName),
        targetType: parsePlatformTypeDefinition(
          item.targetType,
          `${itemPath}.targetType`,
          errors,
        ),
      }];
    });
};

const parseRowsFrameBoundary = (
  value: unknown,
  path: string,
  errors: string[],
): RowsFrameBoundary => {
  if (!isRecord(value)) {
    errors.push(`${path} 必须是 Frame 边界对象`);
    return { kind: 'CURRENT_ROW' };
  }
  if (value.kind === 'UNBOUNDED_PRECEDING'
    || value.kind === 'CURRENT_ROW'
    || value.kind === 'UNBOUNDED_FOLLOWING') {
    return { kind: value.kind };
  }
  if (value.kind === 'PRECEDING' || value.kind === 'FOLLOWING') {
    if (typeof value.offset !== 'number' || !Number.isInteger(value.offset)) {
      errors.push(`${path}.offset 必须是整数`);
    }
    return {
      kind: value.kind,
      offset: typeof value.offset === 'number' && Number.isInteger(value.offset)
        ? value.offset : 0,
    };
  }
  errors.push(`${path}.kind 不是受支持的 Frame 边界`);
  return { kind: 'CURRENT_ROW' };
};

const parseRowsWindowFrame = (
  value: unknown,
  path: string,
  errors: string[],
): RowsWindowFrame => {
  if (!isRecord(value)) {
    errors.push(`${path} 必须是 ROWS Frame 对象`);
    return {
      type: 'ROWS',
      start: { kind: 'UNBOUNDED_PRECEDING' },
      end: { kind: 'CURRENT_ROW' },
    };
  }
  if (value.type !== 'ROWS') errors.push(`${path}.type 仅支持 ROWS`);
  return {
    type: 'ROWS',
    start: parseRowsFrameBoundary(value.start, `${path}.start`, errors),
    end: parseRowsFrameBoundary(value.end, `${path}.end`, errors),
  };
};

const parseWindowFunctions = (
  value: unknown,
  path: string,
  errors: string[],
): WindowFunctionItem[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_WINDOW_MAX_FUNCTIONS) {
    errors.push(`${path} 不能超过 ${CANVAS_WINDOW_MAX_FUNCTIONS} 项`);
  }
  return value.slice(0, CANVAS_WINDOW_MAX_FUNCTIONS)
    .flatMap((item, index): WindowFunctionItem[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const outputColumnName = stringValue(item.outputColumnName);
      if (item.kind === 'ROW_NUMBER' || item.kind === 'RANK' || item.kind === 'DENSE_RANK') {
        return [{ kind: item.kind, outputColumnName }];
      }
      if (item.kind === 'LAG' || item.kind === 'LEAD') {
        if (typeof item.offset !== 'number' || !Number.isInteger(item.offset)) {
          errors.push(`${itemPath}.offset 必须是整数`);
        }
        return [{
          kind: item.kind,
          sourceColumnName: stringValue(item.sourceColumnName),
          offset: typeof item.offset === 'number' && Number.isInteger(item.offset)
            ? item.offset : 0,
          defaultValue: item.defaultValue === null || item.defaultValue === undefined
            ? null
            : parseCanvasLiteral(
              item.defaultValue,
              `${itemPath}.defaultValue`,
              errors,
            ),
          outputColumnName,
        }];
      }
      if (item.kind === 'COUNT'
        || item.kind === 'SUM'
        || item.kind === 'AVG'
        || item.kind === 'MIN'
        || item.kind === 'MAX') {
        if (item.sourceColumnName !== null
          && item.sourceColumnName !== undefined
          && typeof item.sourceColumnName !== 'string') {
          errors.push(`${itemPath}.sourceColumnName 必须是字符串或 null`);
        }
        return [{
          kind: item.kind,
          sourceColumnName: typeof item.sourceColumnName === 'string'
            ? item.sourceColumnName : null,
          outputColumnName,
          frame: parseRowsWindowFrame(item.frame, `${itemPath}.frame`, errors),
        }];
      }
      if (item.kind === 'FIRST_VALUE' || item.kind === 'LAST_VALUE') {
        if (typeof item.ignoreNulls !== 'boolean') {
          errors.push(`${itemPath}.ignoreNulls 必须是布尔值`);
        }
        return [{
          kind: item.kind,
          sourceColumnName: stringValue(item.sourceColumnName),
          ignoreNulls: item.ignoreNulls === true,
          outputColumnName,
          frame: parseRowsWindowFrame(item.frame, `${itemPath}.frame`, errors),
        }];
      }
      errors.push(`${itemPath}.kind 不是受支持的窗口函数`);
      return [];
    });
};

const parseInteger = (
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

const parseFiniteNumber = (
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

const spatialPredicates = new Set([
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

const geometryKinds = new Set([
  'GEOMETRY',
  'POINT',
  'LINESTRING',
  'POLYGON',
  'MULTIPOINT',
  'MULTILINESTRING',
  'MULTIPOLYGON',
  'GEOMETRYCOLLECTION',
]);

const coordinateDimensions = new Set(['XY', 'XYZ', 'XYM', 'XYZM']);

const parseGeometryTypeDefinition = (
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

const parseSpatialMeasurements = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialMeasurement[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_SPATIAL_MEASURE_MAX_MEASUREMENTS) {
    errors.push(`${path} 不能超过 ${CANVAS_SPATIAL_MEASURE_MAX_MEASUREMENTS} 项`);
  }
  return value.slice(0, CANVAS_SPATIAL_MEASURE_MAX_MEASUREMENTS)
    .flatMap((item, index): SpatialMeasurement[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const outputColumnName = stringValue(item.outputColumnName);
      if (item.kind === 'X' || item.kind === 'Y') {
        return [{
          kind: item.kind,
          geometryColumnName: stringValue(item.geometryColumnName),
          outputColumnName,
        }];
      }
      if (item.kind === 'AREA') {
        if (item.mode !== 'PLANAR' && item.mode !== 'SPHEROID') {
          errors.push(`${itemPath}.mode 仅支持 PLANAR 或 SPHEROID`);
        }
        const outputUnit = item.outputUnit == null ? null : stringValue(item.outputUnit);
        if (outputUnit !== null && !spatialAreaUnits.has(outputUnit)) {
          errors.push(`${itemPath}.outputUnit 不是受支持的面积单位`);
        }
        return [{
          kind: 'AREA',
          geometryColumnName: stringValue(item.geometryColumnName),
          mode: item.mode === 'SPHEROID' ? 'SPHEROID' : 'PLANAR',
          outputColumnName,
          outputUnit: outputUnit !== null && spatialAreaUnits.has(outputUnit)
            ? outputUnit as SpatialAreaUnit : null,
        }];
      }
      if (item.kind === 'LENGTH' || item.kind === 'PERIMETER') {
        if (item.mode !== 'PLANAR' && item.mode !== 'SPHEROID') {
          errors.push(`${itemPath}.mode 仅支持 PLANAR 或 SPHEROID`);
        }
        const outputUnit = item.outputUnit == null ? null : stringValue(item.outputUnit);
        if (outputUnit !== null && !spatialDistanceUnits.has(outputUnit)) {
          errors.push(`${itemPath}.outputUnit 不是受支持的距离单位`);
        }
        return [{
          kind: item.kind,
          geometryColumnName: stringValue(item.geometryColumnName),
          mode: item.mode === 'SPHEROID' ? 'SPHEROID' : 'PLANAR',
          outputColumnName,
          outputUnit: outputUnit !== null && spatialDistanceUnits.has(outputUnit)
            ? outputUnit as SpatialDistanceUnit : null,
        }];
      }
      if (item.kind === 'DISTANCE') {
        if (item.mode !== 'PLANAR' && item.mode !== 'SPHEROID') {
          errors.push(`${itemPath}.mode 仅支持 PLANAR 或 SPHEROID`);
        }
        const outputUnit = item.outputUnit == null ? null : stringValue(item.outputUnit);
        if (outputUnit !== null && !spatialDistanceUnits.has(outputUnit)) {
          errors.push(`${itemPath}.outputUnit 不是受支持的距离单位`);
        }
        return [{
          kind: 'DISTANCE',
          leftGeometryColumnName: stringValue(item.leftGeometryColumnName),
          rightGeometryColumnName: stringValue(item.rightGeometryColumnName),
          mode: item.mode === 'SPHEROID' ? 'SPHEROID' : 'PLANAR',
          outputColumnName,
          outputUnit: outputUnit !== null && spatialDistanceUnits.has(outputUnit)
            ? outputUnit as SpatialDistanceUnit : null,
        }];
      }
      errors.push(`${itemPath}.kind 不是受支持的空间测量类型`);
      return [];
    });
};

const geometryDeriveKinds = new Set([
  'CENTROID',
  'POINT_ON_SURFACE',
  'ENVELOPE',
  'CONVEX_HULL',
  'BOUNDARY',
]);

const parseGeometryDerivations = (
  value: unknown,
  path: string,
  errors: string[],
): GeometryDerivation[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_GEOMETRY_DERIVE_MAX_DERIVATIONS) {
    errors.push(`${path} 不能超过 ${CANVAS_GEOMETRY_DERIVE_MAX_DERIVATIONS} 项`);
  }
  return value.slice(0, CANVAS_GEOMETRY_DERIVE_MAX_DERIVATIONS)
    .flatMap((item, index): GeometryDerivation[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const kind = stringValue(item.kind);
      if (item.kind != null && !geometryDeriveKinds.has(kind)) {
        errors.push(`${itemPath}.kind 不是受支持的 Geometry 派生类型`);
      }
      return [{
        derivationId: validateOptionalUuid(
          stringValue(item.derivationId),
          `${itemPath}.derivationId`,
          errors,
        ),
        kind: geometryDeriveKinds.has(kind)
          ? kind as GeometryDerivation['kind'] : null,
        sourceColumnName: stringValue(item.sourceColumnName),
        outputColumnName: stringValue(item.outputColumnName),
        ...parseUnaryPolicy(item, itemPath, errors),
      }];
    });
};

const spatialWithinKinds = new Set([
  'COUNT_FIELD', 'ANY',
  'COUNT', 'SUM', 'MEAN', 'MIN', 'MAX', 'RANGE', 'STDDEV', 'VARIANCE',
  'LENGTH_WITHIN', 'AREA_WITHIN',
]);

const parseSpatialWithinStatistics = (
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

const parseSpatialGroupSummary = (
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

const durationUnits = new Set(['MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS', 'DAYS', 'WEEKS']);

const parseSpatialTemporalSlicing = (
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

const trackDistanceUnits = spatialDistanceUnits;
const trackSpeedUnits = new Set([
  'METERS_PER_SECOND', 'KILOMETERS_PER_HOUR', 'FEET_PER_SECOND', 'MILES_PER_HOUR', 'KNOTS',
]);
const trackAccelerationUnits = new Set([
  'METERS_PER_SECOND_SQUARED', 'FEET_PER_SECOND_SQUARED',
]);
const trackSummaryKinds = new Set([
  'COUNT', 'COUNT_FIELD', 'ANY', 'SUM', 'MEAN', 'MIN', 'MAX', 'RANGE', 'STDDEV', 'VARIANCE', 'FIRST', 'LAST',
]);

const parseNullableFiniteNumber = (value: unknown, path: string, errors: string[]) => {
  if (value === null || value === undefined) return null;
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    errors.push(`${path} 必须是有限数值或 null`);
    return null;
  }
  return value;
};

const parseTrackBoundaries = (
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

const parseTrackSummaries = (
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

const parseTrackMotionMetrics = (
  value: unknown,
  path: string,
  errors: string[],
): TrackMotionMetric[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > 16) errors.push(`${path} 不能超过 16 项`);
  return value.slice(0, 16).flatMap((item, index): TrackMotionMetric[] => {
    const itemPath = `${path}[${index}]`;
    if (!isRecord(item)) {
      errors.push(`${itemPath} 必须是对象`);
      return [];
    }
    const metricId = validateOptionalUuid(stringValue(item.metricId), `${itemPath}.metricId`, errors);
    const outputColumnName = stringValue(item.outputColumnName);
    const outputUnit = item.outputUnit == null ? null : stringValue(item.outputUnit);
    if (item.kind === 'DISTANCE' || item.kind === 'ELEVATION_CHANGE') {
      if (outputUnit === null || !trackDistanceUnits.has(outputUnit)) {
        errors.push(`${itemPath}.outputUnit 不是受支持的距离单位`);
      }
      return [{ kind: item.kind, metricId, outputColumnName,
        outputUnit: trackDistanceUnits.has(outputUnit ?? '')
          ? outputUnit as Extract<TrackMotionMetric, { kind: 'DISTANCE' }>['outputUnit']
          : 'SOURCE_CRS_UNIT' }];
    }
    if (item.kind === 'DURATION') {
      if (outputUnit === null || !durationUnits.has(outputUnit)) {
        errors.push(`${itemPath}.outputUnit 不是受支持的时长单位`);
      }
      return [{ kind: 'DURATION', metricId, outputColumnName,
        outputUnit: durationUnits.has(outputUnit ?? '')
          ? outputUnit as Extract<TrackMotionMetric, { kind: 'DURATION' }>['outputUnit'] : 'SECONDS' }];
    }
    if (item.kind === 'SPEED') {
      if (outputUnit === null || !trackSpeedUnits.has(outputUnit)) {
        errors.push(`${itemPath}.outputUnit 不是受支持的速度单位`);
      }
      return [{ kind: 'SPEED', metricId, outputColumnName,
        outputUnit: trackSpeedUnits.has(outputUnit ?? '')
          ? outputUnit as Extract<TrackMotionMetric, { kind: 'SPEED' }>['outputUnit'] : 'METERS_PER_SECOND' }];
    }
    if (item.kind === 'ACCELERATION') {
      if (outputUnit === null || !trackAccelerationUnits.has(outputUnit)) {
        errors.push(`${itemPath}.outputUnit 不是受支持的加速度单位`);
      }
      return [{ kind: 'ACCELERATION', metricId, outputColumnName,
        outputUnit: trackAccelerationUnits.has(outputUnit ?? '')
          ? outputUnit as Extract<TrackMotionMetric, { kind: 'ACCELERATION' }>['outputUnit']
          : 'METERS_PER_SECOND_SQUARED' }];
    }
    if (item.kind === 'BEARING') {
      if (outputUnit !== 'DEGREES') errors.push(`${itemPath}.outputUnit 必须是 DEGREES`);
      return [{ kind: 'BEARING', metricId, outputColumnName, outputUnit: 'DEGREES' }];
    }
    if (item.kind === 'SLOPE') {
      if (outputUnit !== 'PERCENT') errors.push(`${itemPath}.outputUnit 必须是 PERCENT`);
      return [{ kind: 'SLOPE', metricId, outputColumnName, outputUnit: 'PERCENT' }];
    }
    if (item.kind === 'IDLE') {
      if (item.outputUnit !== null) errors.push(`${itemPath}.outputUnit 必须是 null`);
      return [{ kind: 'IDLE', metricId, outputColumnName, outputUnit: null }];
    }
    errors.push(`${itemPath}.kind 不是受支持的运动指标`);
    return [];
  });
};

const spatialBinStatisticKinds = new Set([
  'COUNT', 'COUNT_FIELD', 'ANY', 'SUM', 'MEAN', 'MIN', 'MAX', 'RANGE', 'STDDEV', 'VARIANCE',
]);

const parseSpatialBinStatistics = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialBinStatistic[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_SPATIAL_BIN_MAX_STATISTICS) {
    errors.push(`${path} 不能超过 ${CANVAS_SPATIAL_BIN_MAX_STATISTICS} 项`);
  }
  return value.slice(0, CANVAS_SPATIAL_BIN_MAX_STATISTICS)
    .flatMap((item, index): SpatialBinStatistic[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const kind = stringValue(item.kind);
      if (!spatialBinStatisticKinds.has(kind)) {
        errors.push(`${itemPath}.kind 不是受支持的格网统计类型`);
      }
      if (item.sourceColumnName !== null && item.sourceColumnName !== undefined
        && typeof item.sourceColumnName !== 'string') {
        errors.push(`${itemPath}.sourceColumnName 必须是字符串或 null`);
      }
      return [{
        statisticId: validateOptionalUuid(
          stringValue(item.statisticId), `${itemPath}.statisticId`, errors,
        ),
        kind: spatialBinStatisticKinds.has(kind)
          ? kind as SpatialBinStatistic['kind'] : 'COUNT',
        sourceColumnName: typeof item.sourceColumnName === 'string'
          ? item.sourceColumnName : null,
        outputColumnName: stringValue(item.outputColumnName),
      }];
    });
};

const parseSpatialDensityFields = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialDensityField[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_SPATIAL_DENSITY_MAX_FIELDS) {
    errors.push(`${path} 不能超过 ${CANVAS_SPATIAL_DENSITY_MAX_FIELDS} 项`);
  }
  return value.slice(0, CANVAS_SPATIAL_DENSITY_MAX_FIELDS)
    .flatMap((item, index): SpatialDensityField[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      return [{
        fieldId: validateOptionalUuid(stringValue(item.fieldId), `${itemPath}.fieldId`, errors),
        sourceColumnName: stringValue(item.sourceColumnName),
        outputColumnName: stringValue(item.outputColumnName),
      }];
    });
};

const multiVariableGridKinds = new Set([
  'DISTANCE_TO_NEAREST', 'ATTRIBUTE_OF_NEAREST', 'ATTRIBUTE_SUMMARY_OF_RELATED',
]);
const multiVariableGridStatisticKinds = new Set([
  'COUNT', 'SUM', 'MEAN', 'MIN', 'MAX', 'RANGE', 'STDDEV', 'VARIANCE', 'ANY',
]);

const parseSpatialMultiVariableGridVariables = (
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

const parseSpatialSimilarLocationsAnalysisFields = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialSimilarLocationsAnalysisField[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_ANALYSIS_FIELDS) {
    errors.push(`${path} 不能超过 ${CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_ANALYSIS_FIELDS} 项`);
  }
  return value.slice(0, CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_ANALYSIS_FIELDS)
    .flatMap((item, index): SpatialSimilarLocationsAnalysisField[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      if (typeof item.columnName !== 'string') {
        errors.push(`${itemPath}.columnName 必须是字符串`);
      }
      if (typeof item.outputColumnName !== 'string') {
        errors.push(`${itemPath}.outputColumnName 必须是字符串`);
      }
      return [{
        columnName: stringValue(item.columnName),
        outputColumnName: stringValue(item.outputColumnName),
      }];
    });
};

const parseSpatialSimilarLocationsAppendFields = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialSimilarLocationsAppendField[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_APPEND_FIELDS) {
    errors.push(`${path} 不能超过 ${CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_APPEND_FIELDS} 项`);
  }
  return value.slice(0, CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_APPEND_FIELDS)
    .flatMap((item, index): SpatialSimilarLocationsAppendField[] => {
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

const parseSpatialEnrichFromGridFields = (
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

const groupByProximitySpatialRelationships = new Set([
  'INTERSECTS', 'TOUCHES', 'NEAR_PLANAR', 'NEAR_GEODESIC',
]);
const groupByProximityTemporalRelationships = new Set(['INTERSECTS', 'NEAR']);
const groupByProximityTemporalUnits = new Set([
  'MILLISECONDS', 'SECONDS', 'MINUTES', 'HOURS', 'DAYS', 'WEEKS', 'MONTHS', 'YEARS',
]);
const groupByProximityAttributeRelationships = new Set([
  'EQUALS', 'ABSOLUTE_DIFFERENCE_AT_MOST',
]);

const parseGroupByProximityTemporalCondition = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialGroupByProximityTemporalCondition | null => {
  if (value == null) return null;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象或 null`);
    return null;
  }
  const relationship = value.relationship == null ? null : stringValue(value.relationship);
  if (relationship !== null && !groupByProximityTemporalRelationships.has(relationship)) {
    errors.push(`${path}.relationship 不是受支持的时间关系`);
  }
  const unit = value.nearDistanceUnit == null ? null : stringValue(value.nearDistanceUnit);
  if (unit !== null && !groupByProximityTemporalUnits.has(unit)) {
    errors.push(`${path}.nearDistanceUnit 不是受支持的时间单位`);
  }
  if (value.endColumnName != null && typeof value.endColumnName !== 'string') {
    errors.push(`${path}.endColumnName 必须是字符串或 null`);
  }
  if (value.nearDistance != null
    && (typeof value.nearDistance !== 'number' || !Number.isInteger(value.nearDistance))) {
    errors.push(`${path}.nearDistance 必须是整数或 null`);
  }
  return {
    relationship: relationship !== null && groupByProximityTemporalRelationships.has(relationship)
      ? relationship as SpatialGroupByProximityTemporalRelationship : null,
    startColumnName: stringValue(value.startColumnName),
    endColumnName: typeof value.endColumnName === 'string' ? value.endColumnName : null,
    nearDistance: typeof value.nearDistance === 'number' && Number.isInteger(value.nearDistance)
      ? value.nearDistance : null,
    nearDistanceUnit: unit !== null && groupByProximityTemporalUnits.has(unit)
      ? unit as SpatialGroupByProximityTemporalUnit : null,
  };
};

const parseGroupByProximityAttributeConditions = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialGroupByProximityAttributeCondition[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): SpatialGroupByProximityAttributeCondition[] => {
    const itemPath = `${path}[${index}]`;
    if (!isRecord(item)) {
      errors.push(`${itemPath} 必须是对象`);
      return [];
    }
    const relationship = item.relationship == null ? null : stringValue(item.relationship);
    if (relationship !== null && !groupByProximityAttributeRelationships.has(relationship)) {
      errors.push(`${itemPath}.relationship 不是受支持的属性关系`);
    }
    if (item.maximumDifference != null
      && (typeof item.maximumDifference !== 'number'
        || !Number.isFinite(item.maximumDifference))) {
      errors.push(`${itemPath}.maximumDifference 必须是有限数值或 null`);
    }
    return [{
      columnName: stringValue(item.columnName),
      relationship: relationship !== null
        && groupByProximityAttributeRelationships.has(relationship)
        ? relationship as SpatialGroupByProximityAttributeRelationship : null,
      maximumDifference: typeof item.maximumDifference === 'number'
        && Number.isFinite(item.maximumDifference) ? item.maximumDifference : null,
    }];
  });
};

const traceProximityInterestSources = new Set(['ENTITY_IDS', 'TABLE']);

const parseTraceProximityInterests = (
  value: unknown,
  path: string,
  errors: string[],
): TraceProximityEntityOfInterest[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_TRACE_PROXIMITY_MAX_INTERESTS) {
    errors.push(`${path} 不能超过 ${CANVAS_TRACE_PROXIMITY_MAX_INTERESTS} 项`);
  }
  return value.flatMap((item, index): TraceProximityEntityOfInterest[] => {
    const itemPath = `${path}[${index}]`;
    if (!isRecord(item)) {
      errors.push(`${itemPath} 必须是对象`);
      return [];
    }
    if (typeof item.entityId !== 'string') {
      errors.push(`${itemPath}.entityId 必须是字符串`);
    }
    if (item.startEpochMillis != null
      && (typeof item.startEpochMillis !== 'number'
        || !Number.isSafeInteger(item.startEpochMillis))) {
      errors.push(`${itemPath}.startEpochMillis 必须是安全整数或 null`);
    }
    return [{
      entityId: stringValue(item.entityId),
      startEpochMillis: typeof item.startEpochMillis === 'number'
        && Number.isSafeInteger(item.startEpochMillis) ? item.startEpochMillis : null,
    }];
  });
};

const parseSnapTracksDirection = (
  value: unknown,
  path: string,
  errors: string[],
): SnapTracksDirectionMatching | null => {
  if (value == null) return null;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象或 null`);
    return null;
  }
  const fields = [
    'directionColumnName', 'forwardValue', 'backwardValue', 'bothValue', 'noneValue',
  ] as const;
  fields.forEach((field) => {
    if (typeof value[field] !== 'string') errors.push(`${path}.${field} 必须是字符串`);
  });
  return {
    directionColumnName: stringValue(value.directionColumnName),
    forwardValue: stringValue(value.forwardValue),
    backwardValue: stringValue(value.backwardValue),
    bothValue: stringValue(value.bothValue),
    noneValue: stringValue(value.noneValue),
  };
};

const parseSnapTracksLineFields = (
  value: unknown,
  path: string,
  errors: string[],
): SnapTracksLineField[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_SNAP_TRACKS_MAX_LINE_FIELDS) {
    errors.push(`${path} 不能超过 ${CANVAS_SNAP_TRACKS_MAX_LINE_FIELDS} 项`);
  }
  return value.slice(0, CANVAS_SNAP_TRACKS_MAX_LINE_FIELDS)
    .flatMap((item, index): SnapTracksLineField[] => {
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

const parseSpatialPointClusterParameters = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialPointClusterParameters => {
  if (!isRecord(value)) {
    errors.push(`${path} 必须是算法参数对象`);
    return {
      algorithm: 'DBSCAN', searchDistance: 0,
      searchDistanceUnit: 'METERS', minimumFeatures: 0,
    };
  }
  const minimumFeatures = typeof value.minimumFeatures === 'number'
    && Number.isSafeInteger(value.minimumFeatures) ? value.minimumFeatures : 0;
  if (typeof value.minimumFeatures !== 'number' || !Number.isSafeInteger(value.minimumFeatures)) {
    errors.push(`${path}.minimumFeatures 必须是安全整数`);
  }
  if (value.algorithm === 'DBSCAN') {
    const unit = stringValue(value.searchDistanceUnit);
    if (!trackDistanceUnits.has(unit)) errors.push(`${path}.searchDistanceUnit 不是受支持的距离单位`);
    return {
      algorithm: 'DBSCAN',
      searchDistance: parseFiniteNumber(value.searchDistance, `${path}.searchDistance`, errors, 0),
      searchDistanceUnit: trackDistanceUnits.has(unit)
        ? unit as Extract<SpatialPointClusterParameters, { algorithm: 'DBSCAN' }>['searchDistanceUnit']
        : 'METERS',
      minimumFeatures,
    };
  }
  if (value.algorithm === 'HDBSCAN') return { algorithm: 'HDBSCAN', minimumFeatures };
  if (value.algorithm === 'MULTI_SCALE') {
    return {
      algorithm: 'MULTI_SCALE',
      minimumFeatures,
      sensitivity: parseFiniteNumber(value.sensitivity, `${path}.sensitivity`, errors, 0),
    };
  }
  errors.push(`${path}.algorithm 仅支持 DBSCAN、HDBSCAN 或 MULTI_SCALE`);
  return {
    algorithm: 'DBSCAN', searchDistance: 0,
    searchDistanceUnit: 'METERS', minimumFeatures,
  };
};

const spatialCenterKinds = new Set([
  'MEAN_CENTER', 'MEDIAN_CENTER', 'CENTRAL_FEATURE',
  'STANDARD_DISTANCE', 'DIRECTIONAL_ELLIPSE',
]);

const parseSpatialCenterAnalyses = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialCenterDispersionAnalysis[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_SPATIAL_CENTER_MAX_ANALYSES) {
    errors.push(`${path} 不能超过 ${CANVAS_SPATIAL_CENTER_MAX_ANALYSES} 项`);
  }
  return value.slice(0, CANVAS_SPATIAL_CENTER_MAX_ANALYSES)
    .flatMap((item, index): SpatialCenterDispersionAnalysis[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const kind = stringValue(item.kind);
      if (!spatialCenterKinds.has(kind)) errors.push(`${itemPath}.kind 不是受支持的分析类型`);
      if (item.standardDeviations !== null && item.standardDeviations !== undefined
        && (typeof item.standardDeviations !== 'number'
          || !Number.isSafeInteger(item.standardDeviations))) {
        errors.push(`${itemPath}.standardDeviations 必须是安全整数或 null`);
      }
      return [{
        analysisId: validateOptionalUuid(
          stringValue(item.analysisId), `${itemPath}.analysisId`, errors,
        ),
        kind: spatialCenterKinds.has(kind)
          ? kind as SpatialCenterDispersionAnalysis['kind'] : 'MEAN_CENTER',
        outputColumnName: stringValue(item.outputColumnName),
        standardDeviations: typeof item.standardDeviations === 'number'
          ? item.standardDeviations : null,
        ...parseCenterOutputTable(item, itemPath, errors),
        ...parseCenterFeatureColumns(item, itemPath, errors),
      }];
    });
};

const configurationParsers = {
  [CanvasNodeType.ModelInput]: (value, path) => (
    parseConfiguration<Configuration<'MODEL_INPUT'>>(value, path, (configuration, errors) => ({
      models: parseModelInputSelections(configuration.models, `${path}.models`, errors),
    }))
  ),
  [CanvasNodeType.JdbcInput]: (value, path) => (
    parseConfiguration<Configuration<'JDBC_INPUT'>>(value, path, (configuration, errors) => ({
      dataSourceId: stringValue(configuration.dataSourceId),
      tables: parseJdbcInputTables(configuration, path, errors),
    }))
  ),
  [CanvasNodeType.JdbcIncrementalInput]: (value, path) => (
    parseConfiguration<Configuration<'JDBC_INCREMENTAL_INPUT'>>(
      value,
      path,
      (configuration, errors) => {
        const startPosition = configuration.startPosition === 'EARLIEST'
          || configuration.startPosition === 'AT_TIME'
          ? configuration.startPosition
          : 'LATEST';
        const startTime = typeof configuration.startTime === 'string'
          && configuration.startTime.trim() ? configuration.startTime.trim() : null;
        if (startPosition === 'AT_TIME' && !startTime) {
          errors.push(`${path}.startTime 在 AT_TIME 模式下不能为空`);
        }
        const visibilityDelaySeconds = typeof configuration.visibilityDelaySeconds === 'number'
          ? configuration.visibilityDelaySeconds : 30;
        const triggerIntervalSeconds = typeof configuration.triggerIntervalSeconds === 'number'
          ? configuration.triggerIntervalSeconds : 60;
        if (!Number.isInteger(visibilityDelaySeconds)
          || visibilityDelaySeconds < 0 || visibilityDelaySeconds > 3600) {
          errors.push(`${path}.visibilityDelaySeconds 必须是 0 到 3600 的整数`);
        }
        if (!Number.isInteger(triggerIntervalSeconds)
          || triggerIntervalSeconds < 1 || triggerIntervalSeconds > 300) {
          errors.push(`${path}.triggerIntervalSeconds 必须是 1 到 300 的整数`);
        }
        return {
          dataSourceId: validateOptionalUuid(
            stringValue(configuration.dataSourceId), `${path}.dataSourceId`, errors,
          ),
          tableName: stringValue(configuration.tableName),
          outputTableName: stringValue(configuration.outputTableName),
          incrementalTimeColumn: stringValue(configuration.incrementalTimeColumn),
          startPosition,
          startTime: startPosition === 'AT_TIME' ? startTime : null,
          cursorTimeZone: stringValue(configuration.cursorTimeZone) || 'UTC',
          visibilityDelaySeconds,
          triggerIntervalSeconds,
        };
      },
    )
  ),
  [CanvasNodeType.JdbcQueryInput]: (value, path) => (
    parseConfiguration<Configuration<'JDBC_QUERY_INPUT'>>(value, path, (configuration, errors) => {
      const sql = stringValue(configuration.sql);
      const analyzedSqlSha256 = stringValue(configuration.analyzedSqlSha256);
      if (sql.length > 100_000) errors.push(`${path}.sql 不能超过 100000 个字符`);
      if (analyzedSqlSha256 && !/^[0-9a-f]{64}$/.test(analyzedSqlSha256)) {
        errors.push(`${path}.analyzedSqlSha256 必须是 64 位小写 SHA-256`);
      }
      return {
        dataSourceId: validateOptionalUuid(
          stringValue(configuration.dataSourceId),
          `${path}.dataSourceId`,
          errors,
        ),
        sql,
        outputTableName: stringValue(configuration.outputTableName),
        analyzedSqlSha256,
        outputColumns: parseJdbcQueryOutputColumns(
          configuration.outputColumns,
          `${path}.outputColumns`,
          errors,
        ),
      };
    })
  ),
  [CanvasNodeType.FileDatasetInput]: (value, path) => (
    parseConfiguration<Configuration<'FILE_DATASET_INPUT'>>(
      value,
      path,
      (configuration, errors) => ({
        fileDatasetId: validateOptionalUuid(
          stringValue(configuration.fileDatasetId), `${path}.fileDatasetId`, errors,
        ),
        tables: parseFileDatasetInputTables(configuration.tables, `${path}.tables`, errors),
      }),
    )
  ),
  [CanvasNodeType.HttpApiInput]: (value, path) => (
    parseConfiguration<Configuration<'HTTP_API_INPUT'>>(value, path, (configuration, errors) => ({
      dataSourceId: validateOptionalUuid(
        stringValue(configuration.dataSourceId),
        `${path}.dataSourceId`,
        errors,
      ),
      resources: parseHttpApiInputResources(configuration.resources, `${path}.resources`, errors),
    }))
  ),
  [CanvasNodeType.SpatialServiceInput]: (value, path) => (
    parseConfiguration<Configuration<'SPATIAL_SERVICE_INPUT'>>(value, path, (configuration, errors) => ({
      dataSourceId: validateOptionalUuid(stringValue(configuration.dataSourceId), `${path}.dataSourceId`, errors),
      resources: parseSpatialServiceInputResources(configuration.resources, `${path}.resources`, errors),
    }))
  ),
  [CanvasNodeType.KafkaInput]: (value, path) => (
    parseConfiguration<Configuration<'KAFKA_INPUT'>>(value, path, (configuration, errors) => {
      if (configuration.startingOffsets !== undefined
        && configuration.startingOffsets !== null
        && configuration.startingOffsets !== ''
        && configuration.startingOffsets !== 'EARLIEST'
      && configuration.startingOffsets !== 'LATEST') {
        errors.push(`${path}.startingOffsets 仅支持 EARLIEST 或 LATEST`);
      }
      const valueFormat = configuration.valueFormat === undefined || configuration.valueFormat === null
        ? 'JSON'
        : configuration.valueFormat === 'JSON'
          || configuration.valueFormat === 'TEXT'
          || configuration.valueFormat === 'BINARY'
          ? configuration.valueFormat
          : null;
      if (valueFormat === null) {
        errors.push(`${path}.valueFormat 仅支持 JSON、TEXT 或 BINARY`);
      }
      const metadataFields = configuration.metadataFields === undefined
        || configuration.metadataFields === null
        ? []
        : Array.isArray(configuration.metadataFields)
          ? configuration.metadataFields.flatMap((item, index) => {
            if (item === 'KEY' || item === 'TOPIC' || item === 'PARTITION'
              || item === 'OFFSET' || item === 'TIMESTAMP') return [item];
            errors.push(`${path}.metadataFields[${index}] 不是受支持的 Kafka 元数据字段`);
            return [];
          })
          : [];
      if (configuration.metadataFields !== undefined
        && configuration.metadataFields !== null
        && !Array.isArray(configuration.metadataFields)) {
        errors.push(`${path}.metadataFields 必须是数组`);
      }
      const duplicateMetadataFields = metadataFields.filter(
        (field, index) => metadataFields.indexOf(field) !== index,
      );
      if (duplicateMetadataFields.length > 0) {
        errors.push(`${path}.metadataFields 不能重复配置：${duplicateMetadataFields[0]}`);
      }
      const valueSchema = parseKafkaValueSchema(
        configuration.valueSchema,
        `${path}.valueSchema`,
        errors,
      );
      if (valueFormat !== null && valueFormat !== 'JSON' && valueSchema.columns.length > 0) {
        errors.push(`${path}.valueSchema.columns 在 TEXT/BINARY 格式下必须为空`);
      }
      const metadataColumnNames: Record<string, string> = {
        KEY: '_kafka_key',
        TOPIC: '_kafka_topic',
        PARTITION: '_kafka_partition',
        OFFSET: '_kafka_offset',
        TIMESTAMP: '_kafka_timestamp',
      };
      if (valueFormat === 'JSON') {
        valueSchema.columns.forEach((column, index) => {
          if (metadataFields.some((field) => metadataColumnNames[field] === column.name)) {
            errors.push(`${path}.valueSchema.columns[${index}].name 与 Kafka 元数据字段重名`);
          }
        });
      }
      return {
        dataSourceId: validateOptionalUuid(
          stringValue(configuration.dataSourceId),
          `${path}.dataSourceId`,
          errors,
        ),
        topic: stringValue(configuration.topic),
        valueSchema,
        outputTableName: stringValue(configuration.outputTableName),
        startingOffsets: configuration.startingOffsets === 'EARLIEST'
          || configuration.startingOffsets === 'LATEST'
          ? configuration.startingOffsets
          : null,
        triggerIntervalSeconds: typeof configuration.triggerIntervalSeconds === 'number'
          ? configuration.triggerIntervalSeconds : 10,
        valueFormat: valueFormat ?? 'JSON',
        metadataFields,
      };
    })
  ),
  [CanvasNodeType.TdEngineTmqInput]: (value, path) => (
    parseConfiguration<Configuration<'TDENGINE_TMQ_INPUT'>>(value, path, (configuration, errors) => {
      const fingerprint = stringValue(configuration.topicDefinitionFingerprint);
      if (fingerprint && !/^(?:[0-9a-f]{64}|v2:[0-9a-f]{64})$/.test(fingerprint)) {
        errors.push(`${path}.topicDefinitionFingerprint 必须是旧版 SHA-256 或 v2 指纹`);
      }
      const maximum = typeof configuration.maxOffsetsPerVGroupPerTrigger === 'number'
        ? configuration.maxOffsetsPerVGroupPerTrigger
        : 10_000;
      if (!Number.isInteger(maximum) || maximum < 1 || maximum > 1_000_000) {
        errors.push(`${path}.maxOffsetsPerVGroupPerTrigger 必须是 1 到 1000000 的整数`);
      }
      const watermarkDelaySeconds = configuration.watermarkDelaySeconds === null
        || configuration.watermarkDelaySeconds === undefined
        ? null : Number(configuration.watermarkDelaySeconds);
      if (watermarkDelaySeconds !== null
        && (!Number.isInteger(watermarkDelaySeconds)
          || watermarkDelaySeconds < 1 || watermarkDelaySeconds > 2_592_000)) {
        errors.push(`${path}.watermarkDelaySeconds 必须是 1 到 2592000 的整数`);
      }
      return {
        dataSourceId: validateOptionalUuid(stringValue(configuration.dataSourceId), `${path}.dataSourceId`, errors),
        topicName: stringValue(configuration.topicName),
        catalogName: stringValue(configuration.catalogName),
        supertableName: stringValue(configuration.supertableName),
        topicDefinitionFingerprint: fingerprint,
        outputTableName: stringValue(configuration.outputTableName),
        startingOffsets: configuration.startingOffsets === 'LATEST' ? 'LATEST' : 'EARLIEST',
        maxOffsetsPerVGroupPerTrigger: maximum,
        triggerIntervalSeconds: typeof configuration.triggerIntervalSeconds === 'number'
          ? configuration.triggerIntervalSeconds : 10,
        eventTimeColumn: configuration.eventTimeColumn === null
          || configuration.eventTimeColumn === undefined
          ? null : stringValue(configuration.eventTimeColumn),
        watermarkDelaySeconds,
      };
    })
  ),
  [CanvasNodeType.Join]: (value, path) => (
    parseConfiguration<Configuration<'JOIN'>>(value, path, (configuration, errors) => ({
      leftTableName: stringValue(configuration.leftTableName),
      rightTableName: stringValue(configuration.rightTableName),
      outputTableName: stringValue(configuration.outputTableName),
      joinType: parseJoinType(configuration.joinType, `${path}.joinType`, errors),
      conditions: parseJoinConditions(configuration.conditions, `${path}.conditions`, errors),
      outputColumns: parseJoinOutputColumns(
        configuration.outputColumns,
        `${path}.outputColumns`,
        errors,
      ),
    }))
  ),
  [CanvasNodeType.GeometryConstruct]: (value, path) => (
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
  ),
  [CanvasNodeType.SpatialTransform]: (value, path) => (
    parseConfiguration<Configuration<'SPATIAL_TRANSFORM'>>(
      value,
      path,
      (configuration, errors) => {
        let targetCrs: Configuration<'SPATIAL_TRANSFORM'>['targetCrs'] = null;
        if (configuration.targetCrs !== null && configuration.targetCrs !== undefined) {
          if (!isRecord(configuration.targetCrs)) {
            errors.push(`${path}.targetCrs 必须是 CRS 对象`);
          } else {
            const authority = stringValue(configuration.targetCrs.authority).toUpperCase();
            const code = parseInteger(
              configuration.targetCrs.code,
              `${path}.targetCrs.code`,
              errors,
              4326,
            );
            if (authority !== 'EPSG') {
              errors.push(`${path}.targetCrs.authority 第一阶段仅支持 EPSG`);
            }
            if (code < 1) errors.push(`${path}.targetCrs.code 必须为正整数`);
            targetCrs = { authority: 'EPSG', code };
          }
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          outputTableName: stringValue(configuration.outputTableName),
          geometryColumnName: stringValue(configuration.geometryColumnName),
          targetCrs,
        };
      },
    )
  ),
  [CanvasNodeType.GeometryValidate]: (value, path) => (
    parseConfiguration<Configuration<'GEOMETRY_VALIDATE'>>(
      value,
      path,
      (configuration, errors) => {
        if (configuration.reasonColumnName !== null
          && configuration.reasonColumnName !== undefined
          && typeof configuration.reasonColumnName !== 'string') {
          errors.push(`${path}.reasonColumnName 必须是字符串或 null`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          outputTableName: stringValue(configuration.outputTableName),
          geometryColumnName: stringValue(configuration.geometryColumnName),
          validColumnName: stringValue(configuration.validColumnName),
          reasonColumnName: typeof configuration.reasonColumnName === 'string'
            ? configuration.reasonColumnName : null,
        };
      },
    )
  ),
  [CanvasNodeType.GeometryRepair]: (value, path) => (
    parseConfiguration<Configuration<'GEOMETRY_REPAIR'>>(
      value,
      path,
      (configuration) => ({
        sourceTableName: stringValue(configuration.sourceTableName),
        outputTableName: stringValue(configuration.outputTableName),
        geometryColumnName: stringValue(configuration.geometryColumnName),
        outputColumnName: stringValue(configuration.outputColumnName),
      }),
    )
  ),
  [CanvasNodeType.GeometryDerive]: (value, path) => (
    parseConfiguration<Configuration<'GEOMETRY_DERIVE'>>(
      value,
      path,
      (configuration, errors) => ({
        sourceTableName: stringValue(configuration.sourceTableName),
        outputTableName: stringValue(configuration.outputTableName),
        derivations: parseGeometryDerivations(
          configuration.derivations,
          `${path}.derivations`,
          errors,
        ),
      }),
    )
  ),
  [CanvasNodeType.GeometrySimplify]: (value, path) => (
    parseConfiguration<Configuration<'GEOMETRY_SIMPLIFY'>>(
      value,
      path,
      (configuration, errors) => {
        const algorithm = stringValue(configuration.algorithm);
        const toleranceUnit = stringValue(configuration.toleranceUnit);
        const allowedAlgorithms = new Set(['DOUGLAS_PEUCKER', 'TOPOLOGY_PRESERVING']);
        const allowedUnits = spatialDistanceUnits;
        if (configuration.algorithm != null && !allowedAlgorithms.has(algorithm)) {
          errors.push(`${path}.algorithm 不是受支持的简化算法`);
        }
        if (configuration.toleranceUnit != null && !allowedUnits.has(toleranceUnit)) {
          errors.push(`${path}.toleranceUnit 不是受支持的距离单位`);
        }
        if (configuration.tolerance != null && (typeof configuration.tolerance !== 'number'
          || !Number.isFinite(configuration.tolerance))) {
          errors.push(`${path}.tolerance 必须是有限数值或 null`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          geometryColumnName: stringValue(configuration.geometryColumnName),
          outputTableName: stringValue(configuration.outputTableName),
          outputColumnName: stringValue(configuration.outputColumnName),
          algorithm: allowedAlgorithms.has(algorithm)
            ? algorithm as Configuration<'GEOMETRY_SIMPLIFY'>['algorithm'] : null,
          tolerance: typeof configuration.tolerance === 'number'
            ? configuration.tolerance : null,
          toleranceUnit: allowedUnits.has(toleranceUnit)
            ? toleranceUnit as Configuration<'GEOMETRY_SIMPLIFY'>['toleranceUnit']
            : null,
          ...parseUnaryPolicy(configuration, path, errors),
        };
      },
    )
  ),
  [CanvasNodeType.SpatialNearest]: (value, path) => (
    parseConfiguration<Configuration<'SPATIAL_NEAREST'>>(
      value,
      path,
      (configuration, errors) => {
        const distanceMethod = stringValue(configuration.distanceMethod);
        const maximumDistanceUnit = configuration.maximumDistanceUnit == null
          ? null : stringValue(configuration.maximumDistanceUnit);
        const distanceOutputUnit = stringValue(configuration.distanceOutputUnit);
        const distanceUnits = spatialDistanceUnits;
        if (configuration.distanceMethod != null && distanceMethod !== 'PLANAR' && distanceMethod !== 'GEODESIC') {
          errors.push(`${path}.distanceMethod 仅支持 PLANAR 或 GEODESIC`);
        }
        if (typeof configuration.nearestCount !== 'number'
          || !Number.isInteger(configuration.nearestCount)) {
          errors.push(`${path}.nearestCount 必须是整数`);
        }
        if (configuration.maximumDistance !== null
          && configuration.maximumDistance !== undefined
          && (typeof configuration.maximumDistance !== 'number'
            || !Number.isFinite(configuration.maximumDistance))) {
          errors.push(`${path}.maximumDistance 必须是有限数值或 null`);
        }
        if (maximumDistanceUnit !== null && !distanceUnits.has(maximumDistanceUnit)) {
          errors.push(`${path}.maximumDistanceUnit 不是受支持的距离单位`);
        }
        if (!distanceUnits.has(distanceOutputUnit)) {
          errors.push(`${path}.distanceOutputUnit 不是受支持的距离单位`);
        }
        if (configuration.rankColumnName !== null
          && configuration.rankColumnName !== undefined
          && typeof configuration.rankColumnName !== 'string') {
          errors.push(`${path}.rankColumnName 必须是字符串或 null`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          sourceGeometryColumnName: stringValue(configuration.sourceGeometryColumnName),
          candidateTableName: stringValue(configuration.candidateTableName),
          candidateGeometryColumnName: stringValue(configuration.candidateGeometryColumnName),
          candidateIdColumnName: stringValue(configuration.candidateIdColumnName),
          distanceMethod: distanceMethod === 'GEODESIC' ? 'GEODESIC'
            : distanceMethod === 'PLANAR' ? 'PLANAR' : null,
          nearestCount: typeof configuration.nearestCount === 'number'
            ? configuration.nearestCount : 0,
          maximumDistance: typeof configuration.maximumDistance === 'number'
            ? configuration.maximumDistance : null,
          maximumDistanceUnit: maximumDistanceUnit !== null
            && distanceUnits.has(maximumDistanceUnit)
            ? maximumDistanceUnit as Configuration<'SPATIAL_NEAREST'>['maximumDistanceUnit']
            : null,
          includeUnmatched: configuration.includeUnmatched === true,
          outputTableName: stringValue(configuration.outputTableName),
          distanceColumnName: stringValue(configuration.distanceColumnName),
          distanceOutputUnit: distanceUnits.has(distanceOutputUnit)
            ? distanceOutputUnit as Configuration<'SPATIAL_NEAREST'>['distanceOutputUnit']
            : 'SOURCE_CRS_UNIT',
          rankColumnName: typeof configuration.rankColumnName === 'string'
            ? configuration.rankColumnName : null,
          outputColumns: parseJoinOutputColumns(
            configuration.outputColumns,
            `${path}.outputColumns`,
            errors,
          ),
          ...parseNearestMatching(configuration, path, errors),
        };
      },
    )
  ),
  [CanvasNodeType.SpatialSummarizeWithin]: (value, path) => (
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
  ),
  [CanvasNodeType.SpatialOverlay]: (value, path) => (
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
  ),
  [CanvasNodeType.TrackReconstruct]: (value, path) => (
    parseConfiguration<Configuration<'TRACK_RECONSTRUCT'>>(value, path, (configuration, errors) => {
      const distanceMethod = stringValue(configuration.distanceMethod);
      if (!['PLANAR', 'GEODESIC'].includes(distanceMethod)) {
        errors.push(`${path}.distanceMethod 仅支持 PLANAR 或 GEODESIC`);
      }
      return {
        ...parseReconstruction(configuration, path, errors),
        sourceTableName: stringValue(configuration.sourceTableName),
        pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
        trackIdColumns: parseStringArray(configuration.trackIdColumns, `${path}.trackIdColumns`, errors),
        timeColumnName: stringValue(configuration.timeColumnName),
        distanceMethod: ['PLANAR', 'GEODESIC'].includes(distanceMethod)
          ? distanceMethod as Configuration<'TRACK_RECONSTRUCT'>['distanceMethod'] : null,
        boundaries: parseTrackBoundaries(configuration.boundaries, `${path}.boundaries`, errors),
        summaryStatistics: parseTrackSummaries(
          configuration.summaryStatistics, `${path}.summaryStatistics`, errors,
        ),
        outputTableName: stringValue(configuration.outputTableName),
        outputGeometryColumnName: stringValue(configuration.outputGeometryColumnName),
        startTimeColumnName: stringValue(configuration.startTimeColumnName),
        endTimeColumnName: stringValue(configuration.endTimeColumnName),
        pointCountColumnName: stringValue(configuration.pointCountColumnName),
      };
    })
  ),
  [CanvasNodeType.TrackMotionStatistics]: (value, path) => (
    parseConfiguration<Configuration<'TRACK_MOTION_STATISTICS'>>(value, path, (configuration, errors) => {
      const distanceMethod = stringValue(configuration.distanceMethod);
      if (configuration.distanceMethod != null && !['PLANAR', 'GEODESIC'].includes(distanceMethod)) {
        errors.push(`${path}.distanceMethod 仅支持 PLANAR 或 GEODESIC`);
      }
      return {
        ...parseMotionWindowOptions(configuration, path, errors),
        sourceTableName: stringValue(configuration.sourceTableName),
        pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
        trackIdColumns: parseStringArray(configuration.trackIdColumns, `${path}.trackIdColumns`, errors),
        timeColumnName: stringValue(configuration.timeColumnName),
        distanceMethod: ['PLANAR', 'GEODESIC'].includes(distanceMethod)
          ? distanceMethod as Configuration<'TRACK_MOTION_STATISTICS'>['distanceMethod'] : null,
        boundaries: parseTrackBoundaries(configuration.boundaries, `${path}.boundaries`, errors),
        historyPoints: typeof configuration.historyPoints === 'number'
          && Number.isInteger(configuration.historyPoints) ? configuration.historyPoints : 0,
        idleDistanceThreshold: parseNullableFiniteNumber(
          configuration.idleDistanceThreshold, `${path}.idleDistanceThreshold`, errors,
        ),
        idleDistanceThresholdUnit: trackDistanceUnits.has(
          stringValue(configuration.idleDistanceThresholdUnit),
        ) ? stringValue(configuration.idleDistanceThresholdUnit) as Configuration<'TRACK_MOTION_STATISTICS'>['idleDistanceThresholdUnit'] : null,
        metrics: parseTrackMotionMetrics(configuration.metrics, `${path}.metrics`, errors),
        outputTableName: stringValue(configuration.outputTableName),
      };
    })
  ),
  [CanvasNodeType.TrackFindDwell]: (value, path) => (
    parseConfiguration<Configuration<'TRACK_FIND_DWELL'>>(value, path, (configuration, errors) => {
      const distanceMethod = stringValue(configuration.distanceMethod);
      const distanceUnit = stringValue(configuration.distanceThresholdUnit);
      const durationUnit = stringValue(configuration.minimumDurationUnit);
      const geometryKind = stringValue(configuration.outputGeometryKind);
      if (configuration.distanceMethod != null && !['PLANAR', 'GEODESIC'].includes(distanceMethod)) errors.push(`${path}.distanceMethod 无效`);
      if (!trackDistanceUnits.has(distanceUnit)) errors.push(`${path}.distanceThresholdUnit 无效`);
      if (!durationUnits.has(durationUnit)) errors.push(`${path}.minimumDurationUnit 无效`);
      if (configuration.outputGeometryKind != null && !['CENTROID', 'CONVEX_HULL'].includes(geometryKind)) errors.push(`${path}.outputGeometryKind 无效`);
      return {
        ...parseDwellOptions(configuration, path, errors),
        sourceTableName: stringValue(configuration.sourceTableName),
        pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
        trackIdColumns: parseStringArray(configuration.trackIdColumns, `${path}.trackIdColumns`, errors),
        timeColumnName: stringValue(configuration.timeColumnName),
        distanceMethod: ['PLANAR', 'GEODESIC'].includes(distanceMethod)
          ? distanceMethod as Configuration<'TRACK_FIND_DWELL'>['distanceMethod'] : null,
        distanceThreshold: parseFiniteNumber(configuration.distanceThreshold, `${path}.distanceThreshold`, errors, 0),
        distanceThresholdUnit: trackDistanceUnits.has(distanceUnit)
          ? distanceUnit as Configuration<'TRACK_FIND_DWELL'>['distanceThresholdUnit'] : 'METERS',
        minimumDuration: parseFiniteNumber(configuration.minimumDuration, `${path}.minimumDuration`, errors, 0),
        minimumDurationUnit: durationUnits.has(durationUnit)
          ? durationUnit as Configuration<'TRACK_FIND_DWELL'>['minimumDurationUnit'] : 'MINUTES',
        boundaries: parseTrackBoundaries(configuration.boundaries, `${path}.boundaries`, errors),
        summaryStatistics: parseTrackSummaries(
          configuration.summaryStatistics, `${path}.summaryStatistics`, errors,
        ),
        outputGeometryKind: ['CENTROID', 'CONVEX_HULL'].includes(geometryKind)
          ? geometryKind as Configuration<'TRACK_FIND_DWELL'>['outputGeometryKind'] : null,
        outputTableName: stringValue(configuration.outputTableName),
        dwellIdColumnName: stringValue(configuration.dwellIdColumnName),
        startTimeColumnName: stringValue(configuration.startTimeColumnName),
        endTimeColumnName: stringValue(configuration.endTimeColumnName),
        durationColumnName: stringValue(configuration.durationColumnName),
        pointCountColumnName: stringValue(configuration.pointCountColumnName),
        outputGeometryColumnName: stringValue(configuration.outputGeometryColumnName),
      };
    })
  ),
  [CanvasNodeType.TrackDetectIncidents]: (value, path) => (
    parseConfiguration<Configuration<'TRACK_DETECT_INCIDENTS'>>(value, path, (configuration, errors) => {
      const distanceMethod = stringValue(configuration.distanceMethod);
      const resultMode = stringValue(configuration.resultMode);
      const durationUnit = stringValue(configuration.incidentDurationUnit);
      if (configuration.distanceMethod != null && !['PLANAR', 'GEODESIC'].includes(distanceMethod)) {
        errors.push(`${path}.distanceMethod 无效`);
      }
      if (!['INCIDENTS_ONLY', 'ALL_EVENTS'].includes(resultMode)) errors.push(`${path}.resultMode 无效`);
      if (!durationUnits.has(durationUnit)) errors.push(`${path}.incidentDurationUnit 无效`);
      if (configuration.pointGeometryColumnName !== null
        && configuration.pointGeometryColumnName !== undefined
        && typeof configuration.pointGeometryColumnName !== 'string') {
        errors.push(`${path}.pointGeometryColumnName 必须是字符串或 null`);
      }
      return {
        ...parseIncidentLifecycleOptions(configuration, path, errors),
        sourceTableName: stringValue(configuration.sourceTableName),
        pointGeometryColumnName: typeof configuration.pointGeometryColumnName === 'string'
          ? configuration.pointGeometryColumnName : null,
        trackIdColumns: parseStringArray(configuration.trackIdColumns, `${path}.trackIdColumns`, errors),
        timeColumnName: stringValue(configuration.timeColumnName),
        distanceMethod: ['PLANAR', 'GEODESIC'].includes(distanceMethod)
          ? distanceMethod as Configuration<'TRACK_DETECT_INCIDENTS'>['distanceMethod'] : null,
        boundaries: parseTrackBoundaries(configuration.boundaries, `${path}.boundaries`, errors),
        startCondition: parseFilterCondition(
          configuration.startCondition, `${path}.startCondition`, errors,
        ),
        endCondition: configuration.endCondition == null ? null : parseFilterCondition(
          configuration.endCondition, `${path}.endCondition`, errors,
        ),
        resultMode: ['INCIDENTS_ONLY', 'ALL_EVENTS'].includes(resultMode)
          ? resultMode as Configuration<'TRACK_DETECT_INCIDENTS'>['resultMode'] : null,
        outputTableName: stringValue(configuration.outputTableName),
        incidentIdColumnName: stringValue(configuration.incidentIdColumnName),
        incidentFlagColumnName: stringValue(configuration.incidentFlagColumnName),
        incidentStartTimeColumnName: stringValue(configuration.incidentStartTimeColumnName),
        incidentEndTimeColumnName: stringValue(configuration.incidentEndTimeColumnName),
        incidentDurationColumnName: stringValue(configuration.incidentDurationColumnName),
        incidentDurationUnit: durationUnits.has(durationUnit)
          ? durationUnit as Configuration<'TRACK_DETECT_INCIDENTS'>['incidentDurationUnit'] : 'MINUTES',
      };
    })
  ),
  [CanvasNodeType.SpatialBinAggregate]: (value, path) => (
    parseConfiguration<Configuration<'SPATIAL_BIN_AGGREGATE'>>(
      value,
      path,
      (configuration, errors) => {
        const binShape = stringValue(configuration.binShape);
        const binSizeUnit = stringValue(configuration.binSizeUnit);
        if (configuration.binShape != null && !['SQUARE', 'HEXAGON', 'H3'].includes(binShape)) {
          errors.push(`${path}.binShape 仅支持 SQUARE、HEXAGON 或 H3`);
        }
        if (!trackDistanceUnits.has(binSizeUnit)) {
          errors.push(`${path}.binSizeUnit 不是受支持的距离单位`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
          binShape: ['SQUARE', 'HEXAGON', 'H3'].includes(binShape)
            ? binShape as Configuration<'SPATIAL_BIN_AGGREGATE'>['binShape'] : null,
          binSize: parseFiniteNumber(configuration.binSize, `${path}.binSize`, errors, 0),
          ...parseH3(configuration, path, errors),
          ...parsePlanarGrid(configuration, path, errors),
          ...(configuration.binSizeSemantics === undefined ? {} : {
            binSizeSemantics: parseBinSizeSemantics(configuration.binSizeSemantics, `${path}.binSizeSemantics`, errors),
          }),
          binSizeUnit: trackDistanceUnits.has(binSizeUnit)
            ? binSizeUnit as Configuration<'SPATIAL_BIN_AGGREGATE'>['binSizeUnit'] : 'METERS',
          includeEmptyBins: configuration.includeEmptyBins === true,
          statistics: parseSpatialBinStatistics(
            configuration.statistics, `${path}.statistics`, errors,
          ),
          groupSummary: parseSpatialGroupSummary(
            configuration.groupSummary, `${path}.groupSummary`, errors,
          ),
          temporalSlicing: parseSpatialTemporalSlicing(
            configuration.temporalSlicing, `${path}.temporalSlicing`, errors,
          ),
          outputTableName: stringValue(configuration.outputTableName),
          binIdColumnName: stringValue(configuration.binIdColumnName),
          binGeometryColumnName: stringValue(configuration.binGeometryColumnName),
        };
      },
    )
  ),
  [CanvasNodeType.SpatialPointCluster]: (value, path) => (
    parseConfiguration<Configuration<'SPATIAL_POINT_CLUSTER'>>(
      value,
      path,
      (configuration, errors) => {
        const distanceMethod = stringValue(configuration.distanceMethod);
        if (!['PLANAR', 'GEODESIC'].includes(distanceMethod)) {
          errors.push(`${path}.distanceMethod 仅支持 PLANAR 或 GEODESIC`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
          featureIdColumnName: stringValue(configuration.featureIdColumnName),
          distanceMethod: ['PLANAR', 'GEODESIC'].includes(distanceMethod)
            ? distanceMethod as Configuration<'SPATIAL_POINT_CLUSTER'>['distanceMethod'] : null,
          parameters: parseSpatialPointClusterParameters(
            configuration.parameters, `${path}.parameters`, errors,
          ),
          ...parseDbscanOptions(configuration, path, errors),
          ...parseHdbscanOptions(configuration, path, errors),
          outputTableName: stringValue(configuration.outputTableName),
          clusterIdColumnName: stringValue(configuration.clusterIdColumnName),
          noiseColumnName: stringValue(configuration.noiseColumnName),
        };
      },
    )
  ),
  [CanvasNodeType.SpatialCenterDispersion]: (value, path) => (
    parseConfiguration<Configuration<'SPATIAL_CENTER_DISPERSION'>>(
      value,
      path,
      (configuration, errors) => {
        if (configuration.featureIdColumnName !== null
          && configuration.featureIdColumnName !== undefined
          && typeof configuration.featureIdColumnName !== 'string') {
          errors.push(`${path}.featureIdColumnName 必须是字符串或 null`);
        }
        if (configuration.weightColumnName !== null
          && configuration.weightColumnName !== undefined
          && typeof configuration.weightColumnName !== 'string') {
          errors.push(`${path}.weightColumnName 必须是字符串或 null`);
        }
        const groups = parseStringArray(
          configuration.groupByColumns, `${path}.groupByColumns`, errors,
        );
        if (groups.length > CANVAS_SPATIAL_CENTER_MAX_GROUP_COLUMNS) {
          errors.push(`${path}.groupByColumns 不能超过 ${CANVAS_SPATIAL_CENTER_MAX_GROUP_COLUMNS} 项`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
          featureIdColumnName: typeof configuration.featureIdColumnName === 'string'
            ? configuration.featureIdColumnName : null,
          groupByColumns: groups,
          weightColumnName: typeof configuration.weightColumnName === 'string'
            ? configuration.weightColumnName : null,
          analyses: parseSpatialCenterAnalyses(
            configuration.analyses, `${path}.analyses`, errors,
          ),
          outputTableName: stringValue(configuration.outputTableName),
          ...parseCenterResultMode(configuration, path, errors),
        };
      },
    )
  ),
  [CanvasNodeType.SpatialDensity]: (value, path) => (
    parseConfiguration<Configuration<'SPATIAL_DENSITY'>>(
      value,
      path,
      (configuration, errors) => {
        const weighting = stringValue(configuration.weighting);
        const binShape = stringValue(configuration.binShape);
        const binSizeUnit = stringValue(configuration.binSizeUnit);
        const radiusUnit = stringValue(configuration.radiusUnit);
        const areaUnit = stringValue(configuration.areaUnit);
        if (configuration.weighting != null && !['UNIFORM', 'KERNEL'].includes(weighting)) {
          errors.push(`${path}.weighting 仅支持 UNIFORM 或 KERNEL`);
        }
        if (configuration.binShape != null && !['SQUARE', 'HEXAGON'].includes(binShape)) {
          errors.push(`${path}.binShape 仅支持 SQUARE 或 HEXAGON`);
        }
        if (!spatialDistanceUnits.has(binSizeUnit)) errors.push(`${path}.binSizeUnit 不是受支持的距离单位`);
        if (!spatialDistanceUnits.has(radiusUnit)) errors.push(`${path}.radiusUnit 不是受支持的距离单位`);
        if (!spatialAreaUnits.has(areaUnit)) errors.push(`${path}.areaUnit 不是受支持的面积单位`);
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
          fields: parseSpatialDensityFields(configuration.fields, `${path}.fields`, errors),
          weighting: ['UNIFORM', 'KERNEL'].includes(weighting)
            ? weighting as Configuration<'SPATIAL_DENSITY'>['weighting'] : null,
          binShape: ['SQUARE', 'HEXAGON'].includes(binShape)
            ? binShape as Configuration<'SPATIAL_DENSITY'>['binShape'] : null,
          binSize: parseFiniteNumber(configuration.binSize, `${path}.binSize`, errors, 0),
          binSizeUnit: spatialDistanceUnits.has(binSizeUnit)
            ? binSizeUnit as Configuration<'SPATIAL_DENSITY'>['binSizeUnit'] : 'METERS',
          radius: parseFiniteNumber(configuration.radius, `${path}.radius`, errors, 0),
          radiusUnit: spatialDistanceUnits.has(radiusUnit)
            ? radiusUnit as Configuration<'SPATIAL_DENSITY'>['radiusUnit'] : 'METERS',
          areaUnit: spatialAreaUnits.has(areaUnit)
            ? areaUnit as Configuration<'SPATIAL_DENSITY'>['areaUnit'] : 'SQUARE_KILOMETERS',
          temporalSlicing: parseSpatialTemporalSlicing(
            configuration.temporalSlicing, `${path}.temporalSlicing`, errors,
          ),
          outputTableName: stringValue(configuration.outputTableName),
          binIdColumnName: stringValue(configuration.binIdColumnName),
          binGeometryColumnName: stringValue(configuration.binGeometryColumnName),
          countDensityColumnName: stringValue(configuration.countDensityColumnName),
        };
      },
    )
  ),
  [CanvasNodeType.SpatialHotSpots]: (value, path) => (
    parseConfiguration<Configuration<'SPATIAL_HOT_SPOTS'>>(
      value,
      path,
      (configuration, errors) => {
        const analysisSource = stringValue(configuration.analysisSource);
        const multipleTesting = stringValue(configuration.multipleTesting);
        const binSizeUnit = stringValue(configuration.binSizeUnit);
        const neighborhoodDistanceUnit = stringValue(configuration.neighborhoodDistanceUnit);
        if (configuration.analysisSource != null
          && !['POINT_COUNT', 'FIELD_SUM'].includes(analysisSource)) {
          errors.push(`${path}.analysisSource 仅支持 POINT_COUNT 或 FIELD_SUM`);
        }
        if (configuration.analysisColumnName !== null
          && configuration.analysisColumnName !== undefined
          && typeof configuration.analysisColumnName !== 'string') {
          errors.push(`${path}.analysisColumnName 必须是字符串或 null`);
        }
        if (configuration.multipleTesting != null
          && !['NONE', 'FDR_BH'].includes(multipleTesting)) {
          errors.push(`${path}.multipleTesting 仅支持 NONE 或 FDR_BH`);
        }
        if (!spatialDistanceUnits.has(binSizeUnit)) {
          errors.push(`${path}.binSizeUnit 不是受支持的距离单位`);
        }
        if (!spatialDistanceUnits.has(neighborhoodDistanceUnit)) {
          errors.push(`${path}.neighborhoodDistanceUnit 不是受支持的距离单位`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
          analysisSource: ['POINT_COUNT', 'FIELD_SUM'].includes(analysisSource)
            ? analysisSource as Configuration<'SPATIAL_HOT_SPOTS'>['analysisSource'] : null,
          analysisColumnName: typeof configuration.analysisColumnName === 'string'
            ? configuration.analysisColumnName : null,
          binSize: parseFiniteNumber(configuration.binSize, `${path}.binSize`, errors, 0),
          binSizeUnit: spatialDistanceUnits.has(binSizeUnit)
            ? binSizeUnit as Configuration<'SPATIAL_HOT_SPOTS'>['binSizeUnit'] : 'METERS',
          neighborhoodDistance: parseFiniteNumber(
            configuration.neighborhoodDistance, `${path}.neighborhoodDistance`, errors, 0,
          ),
          neighborhoodDistanceUnit: spatialDistanceUnits.has(neighborhoodDistanceUnit)
            ? neighborhoodDistanceUnit as Configuration<'SPATIAL_HOT_SPOTS'>['neighborhoodDistanceUnit'] : 'METERS',
          temporalSlicing: parseSpatialTemporalSlicing(
            configuration.temporalSlicing, `${path}.temporalSlicing`, errors,
          ),
          multipleTesting: ['NONE', 'FDR_BH'].includes(multipleTesting)
            ? multipleTesting as Configuration<'SPATIAL_HOT_SPOTS'>['multipleTesting'] : null,
          outputTableName: stringValue(configuration.outputTableName),
          binIdColumnName: stringValue(configuration.binIdColumnName),
          binGeometryColumnName: stringValue(configuration.binGeometryColumnName),
          pointCountColumnName: stringValue(configuration.pointCountColumnName),
          analysisValueColumnName: stringValue(configuration.analysisValueColumnName),
          zScoreColumnName: stringValue(configuration.zScoreColumnName),
          pValueColumnName: stringValue(configuration.pValueColumnName),
          adjustedPValueColumnName: stringValue(configuration.adjustedPValueColumnName),
          confidenceBinColumnName: stringValue(configuration.confidenceBinColumnName),
        };
      },
    )
  ),
  [CanvasNodeType.SpatialMultiVariableGrid]: (value, path) => (
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
  ),
  [CanvasNodeType.SpatialSimilarLocations]: (value, path) => (
    parseConfiguration<Configuration<'SPATIAL_SIMILAR_LOCATIONS'>>(
      value,
      path,
      (configuration, errors) => {
        const matchMethod = configuration.matchMethod == null
          ? null : stringValue(configuration.matchMethod);
        const resultMode = configuration.resultMode == null
          ? null : stringValue(configuration.resultMode);
        if (matchMethod !== null
          && !['ATTRIBUTE_VALUES', 'ATTRIBUTE_PROFILES'].includes(matchMethod)) {
          errors.push(`${path}.matchMethod 不是受支持的匹配方法`);
        }
        if (resultMode !== null
          && !['MOST_SIMILAR', 'LEAST_SIMILAR', 'BOTH'].includes(resultMode)) {
          errors.push(`${path}.resultMode 不是受支持的返回范围`);
        }
        return {
          referenceTableName: stringValue(configuration.referenceTableName),
          referenceIdColumnName: stringValue(configuration.referenceIdColumnName),
          referenceGeometryColumnName: stringValue(configuration.referenceGeometryColumnName),
          referenceFilter: configuration.referenceFilter == null ? null
            : parseFilterCondition(configuration.referenceFilter, `${path}.referenceFilter`, errors),
          candidateTableName: stringValue(configuration.candidateTableName),
          candidateIdColumnName: stringValue(configuration.candidateIdColumnName),
          candidateGeometryColumnName: stringValue(configuration.candidateGeometryColumnName),
          candidateFilter: configuration.candidateFilter == null ? null
            : parseFilterCondition(configuration.candidateFilter, `${path}.candidateFilter`, errors),
          analysisFields: parseSpatialSimilarLocationsAnalysisFields(
            configuration.analysisFields, `${path}.analysisFields`, errors,
          ),
          appendFields: parseSpatialSimilarLocationsAppendFields(
            configuration.appendFields, `${path}.appendFields`, errors,
          ),
          matchMethod: ['ATTRIBUTE_VALUES', 'ATTRIBUTE_PROFILES'].includes(matchMethod ?? '')
            ? matchMethod as Configuration<'SPATIAL_SIMILAR_LOCATIONS'>['matchMethod'] : null,
          resultMode: ['MOST_SIMILAR', 'LEAST_SIMILAR', 'BOTH'].includes(resultMode ?? '')
            ? resultMode as Configuration<'SPATIAL_SIMILAR_LOCATIONS'>['resultMode'] : null,
          numberOfResults: parseInteger(
            configuration.numberOfResults, `${path}.numberOfResults`, errors, 0,
          ),
          outputTableName: stringValue(configuration.outputTableName),
          outputGeometryColumnName: stringValue(configuration.outputGeometryColumnName),
          locationTypeColumnName: stringValue(configuration.locationTypeColumnName),
          similarityRankColumnName: stringValue(configuration.similarityRankColumnName),
          dissimilarityRankColumnName: stringValue(configuration.dissimilarityRankColumnName),
          similarityIndexColumnName: stringValue(configuration.similarityIndexColumnName),
          cosineIndexColumnName: stringValue(configuration.cosineIndexColumnName),
          labelRankColumnName: stringValue(configuration.labelRankColumnName),
          referenceIdOutputColumnName: stringValue(configuration.referenceIdOutputColumnName),
          searchIdOutputColumnName: stringValue(configuration.searchIdOutputColumnName),
        };
      },
    )
  ),
  [CanvasNodeType.SpatialDescribeDataset]: (value, path) => (
    parseConfiguration<Configuration<'SPATIAL_DESCRIBE_DATASET'>>(
      value,
      path,
      (configuration, errors) => {
        if (typeof configuration.extentOutput !== 'boolean') {
          errors.push(`${path}.extentOutput 必须是布尔值`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          geometryColumnName: stringValue(configuration.geometryColumnName),
          statisticsTableName: stringValue(configuration.statisticsTableName),
          descriptionTableName: stringValue(configuration.descriptionTableName),
          sampleSize: parseInteger(configuration.sampleSize, `${path}.sampleSize`, errors, 0),
          sampleTableName: stringValue(configuration.sampleTableName),
          extentOutput: configuration.extentOutput === true,
          extentTableName: stringValue(configuration.extentTableName),
        };
      },
    )
  ),
  [CanvasNodeType.SpatialEnrichFromGrid]: (value, path) => (
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
  ),
  [CanvasNodeType.SpatialGroupByProximity]: (value, path) => (
    parseConfiguration<Configuration<'SPATIAL_GROUP_BY_PROXIMITY'>>(
      value,
      path,
      (configuration, errors) => {
        const spatialRelationship = configuration.spatialRelationship == null
          ? null : stringValue(configuration.spatialRelationship);
        const distanceUnit = configuration.spatialNearDistanceUnit == null
          ? null : stringValue(configuration.spatialNearDistanceUnit);
        if (spatialRelationship !== null
          && !groupByProximitySpatialRelationships.has(spatialRelationship)) {
          errors.push(`${path}.spatialRelationship 不是受支持的空间关系`);
        }
        if (distanceUnit !== null && !spatialDistanceUnits.has(distanceUnit)) {
          errors.push(`${path}.spatialNearDistanceUnit 不是受支持的距离单位`);
        }
        if (configuration.spatialNearDistance != null
          && (typeof configuration.spatialNearDistance !== 'number'
            || !Number.isFinite(configuration.spatialNearDistance))) {
          errors.push(`${path}.spatialNearDistance 必须是有限数值或 null`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          geometryColumnName: stringValue(configuration.geometryColumnName),
          spatialRelationship: spatialRelationship !== null
            && groupByProximitySpatialRelationships.has(spatialRelationship)
            ? spatialRelationship as SpatialGroupByProximitySpatialRelationship : null,
          spatialNearDistance: typeof configuration.spatialNearDistance === 'number'
            && Number.isFinite(configuration.spatialNearDistance)
            ? configuration.spatialNearDistance : null,
          spatialNearDistanceUnit: distanceUnit !== null && spatialDistanceUnits.has(distanceUnit)
            ? distanceUnit as SpatialDistanceUnit : null,
          temporalCondition: parseGroupByProximityTemporalCondition(
            configuration.temporalCondition, `${path}.temporalCondition`, errors,
          ),
          attributeConditions: parseGroupByProximityAttributeConditions(
            configuration.attributeConditions, `${path}.attributeConditions`, errors,
          ),
          groupIdColumnName: stringValue(configuration.groupIdColumnName),
          outputTableName: stringValue(configuration.outputTableName),
        };
      },
    )
  ),
  [CanvasNodeType.TraceProximityEvents]: (value, path) => (
    parseConfiguration<Configuration<'TRACE_PROXIMITY_EVENTS'>>(
      value,
      path,
      (configuration, errors) => {
        const distanceMethod = configuration.distanceMethod == null
          ? null : stringValue(configuration.distanceMethod);
        const spatialUnit = configuration.spatialSearchDistanceUnit == null
          ? null : stringValue(configuration.spatialSearchDistanceUnit);
        const temporalUnit = configuration.temporalSearchDistanceUnit == null
          ? null : stringValue(configuration.temporalSearchDistanceUnit);
        const interestSource = configuration.interestSource == null
          ? null : stringValue(configuration.interestSource);
        if (distanceMethod !== null
          && distanceMethod !== 'PLANAR' && distanceMethod !== 'GEODESIC') {
          errors.push(`${path}.distanceMethod 仅支持 PLANAR 或 GEODESIC`);
        }
        if (spatialUnit !== null && !spatialDistanceUnits.has(spatialUnit)) {
          errors.push(`${path}.spatialSearchDistanceUnit 不是受支持的距离单位`);
        }
        if (temporalUnit !== null && !groupByProximityTemporalUnits.has(temporalUnit)) {
          errors.push(`${path}.temporalSearchDistanceUnit 不是受支持的时间单位`);
        }
        if (interestSource !== null && !traceProximityInterestSources.has(interestSource)) {
          errors.push(`${path}.interestSource 不是受支持的起始实体来源`);
        }
        if (configuration.spatialSearchDistance != null
          && (typeof configuration.spatialSearchDistance !== 'number'
            || !Number.isFinite(configuration.spatialSearchDistance))) {
          errors.push(`${path}.spatialSearchDistance 必须是有限数值或 null`);
        }
        for (const [key, field] of [
          ['temporalSearchDistance', configuration.temporalSearchDistance],
          ['maxTraceDepth', configuration.maxTraceDepth],
        ] as const) {
          if (field != null && (typeof field !== 'number' || !Number.isSafeInteger(field))) {
            errors.push(`${path}.${key} 必须是安全整数或 null`);
          }
        }
        if (configuration.interestStartTimeColumnName != null
          && typeof configuration.interestStartTimeColumnName !== 'string') {
          errors.push(`${path}.interestStartTimeColumnName 必须是字符串或 null`);
        }
        if (typeof configuration.includeTracks !== 'boolean') {
          errors.push(`${path}.includeTracks 必须是布尔值`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
          entityIdColumnName: stringValue(configuration.entityIdColumnName),
          timeColumnName: stringValue(configuration.timeColumnName),
          distanceMethod: distanceMethod === 'PLANAR' || distanceMethod === 'GEODESIC'
            ? distanceMethod : null,
          spatialSearchDistance: typeof configuration.spatialSearchDistance === 'number'
            && Number.isFinite(configuration.spatialSearchDistance)
            ? configuration.spatialSearchDistance : null,
          spatialSearchDistanceUnit: spatialUnit !== null && spatialDistanceUnits.has(spatialUnit)
            ? spatialUnit as SpatialDistanceUnit : null,
          temporalSearchDistance: typeof configuration.temporalSearchDistance === 'number'
            && Number.isSafeInteger(configuration.temporalSearchDistance)
            ? configuration.temporalSearchDistance : null,
          temporalSearchDistanceUnit: temporalUnit !== null
            && groupByProximityTemporalUnits.has(temporalUnit)
            ? temporalUnit as SpatialGroupByProximityTemporalUnit : null,
          interestSource: interestSource !== null && traceProximityInterestSources.has(interestSource)
            ? interestSource as TraceProximityInterestSource : null,
          entitiesOfInterest: parseTraceProximityInterests(
            configuration.entitiesOfInterest, `${path}.entitiesOfInterest`, errors,
          ),
          entitiesOfInterestTableName: stringValue(configuration.entitiesOfInterestTableName),
          interestEntityIdColumnName: stringValue(configuration.interestEntityIdColumnName),
          interestStartTimeColumnName: typeof configuration.interestStartTimeColumnName === 'string'
            ? configuration.interestStartTimeColumnName : null,
          maxTraceDepth: typeof configuration.maxTraceDepth === 'number'
            && Number.isSafeInteger(configuration.maxTraceDepth)
            ? configuration.maxTraceDepth : null,
          attributeMatchColumns: parseStringArray(
            configuration.attributeMatchColumns, `${path}.attributeMatchColumns`, errors,
          ),
          includeTracks: configuration.includeTracks === true,
          outputTableName: stringValue(configuration.outputTableName),
          tracksOutputTableName: stringValue(configuration.tracksOutputTableName),
          fromEntityIdColumnName: stringValue(configuration.fromEntityIdColumnName),
          toEntityIdColumnName: stringValue(configuration.toEntityIdColumnName),
          depthColumnName: stringValue(configuration.depthColumnName),
          durationMinutesColumnName: stringValue(configuration.durationMinutesColumnName),
          eventTimeColumnName: stringValue(configuration.eventTimeColumnName),
        };
      },
    )
  ),
  [CanvasNodeType.SnapTracks]: (value, path) => (
    parseConfiguration<Configuration<'SNAP_TRACKS'>>(value, path, (configuration, errors) => {
      const distanceMethod = configuration.distanceMethod == null
        ? null : stringValue(configuration.distanceMethod);
      const distanceUnit = configuration.searchDistanceUnit == null
        ? null : stringValue(configuration.searchDistanceUnit);
      const outputMode = configuration.outputMode == null
        ? null : stringValue(configuration.outputMode);
      if (distanceMethod !== null
        && distanceMethod !== 'PLANAR' && distanceMethod !== 'GEODESIC') {
        errors.push(`${path}.distanceMethod 仅支持 PLANAR 或 GEODESIC`);
      }
      if (distanceUnit !== null && !spatialDistanceUnits.has(distanceUnit)) {
        errors.push(`${path}.searchDistanceUnit 不是受支持的距离单位`);
      }
      if (outputMode !== null
        && outputMode !== 'ALL_FEATURES' && outputMode !== 'MATCHED_FEATURES') {
        errors.push(`${path}.outputMode 仅支持 ALL_FEATURES 或 MATCHED_FEATURES`);
      }
      return {
        pointTableName: stringValue(configuration.pointTableName),
        pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
        trackIdColumns: parseStringArray(
          configuration.trackIdColumns, `${path}.trackIdColumns`, errors,
        ),
        timeColumnName: stringValue(configuration.timeColumnName),
        orderByColumns: parseStringArray(
          configuration.orderByColumns, `${path}.orderByColumns`, errors,
        ),
        lineTableName: stringValue(configuration.lineTableName),
        lineGeometryColumnName: stringValue(configuration.lineGeometryColumnName),
        lineIdColumnName: stringValue(configuration.lineIdColumnName),
        fromNodeColumnName: stringValue(configuration.fromNodeColumnName),
        toNodeColumnName: stringValue(configuration.toNodeColumnName),
        searchDistance: parseNullableFiniteNumber(
          configuration.searchDistance, `${path}.searchDistance`, errors,
        ),
        searchDistanceUnit: distanceUnit !== null && spatialDistanceUnits.has(distanceUnit)
          ? distanceUnit as SpatialDistanceUnit : null,
        distanceMethod: distanceMethod === 'PLANAR' || distanceMethod === 'GEODESIC'
          ? distanceMethod : null,
        boundaries: parseTrackBoundaries(configuration.boundaries, `${path}.boundaries`, errors),
        directionMatching: parseSnapTracksDirection(
          configuration.directionMatching, `${path}.directionMatching`, errors,
        ),
        lineFields: parseSnapTracksLineFields(
          configuration.lineFields, `${path}.lineFields`, errors,
        ),
        outputMode: outputMode === 'ALL_FEATURES' || outputMode === 'MATCHED_FEATURES'
          ? outputMode : null,
        outputTableName: stringValue(configuration.outputTableName),
        snappedGeometryColumnName: stringValue(configuration.snappedGeometryColumnName),
        matchedLineIdColumnName: stringValue(configuration.matchedLineIdColumnName),
        matchStatusColumnName: stringValue(configuration.matchStatusColumnName),
        originalXColumnName: stringValue(configuration.originalXColumnName),
        originalYColumnName: stringValue(configuration.originalYColumnName),
        matchXColumnName: stringValue(configuration.matchXColumnName),
        matchYColumnName: stringValue(configuration.matchYColumnName),
        matchDistanceColumnName: stringValue(configuration.matchDistanceColumnName),
      };
    })
  ),
  [CanvasNodeType.GeometryBuffer]: (value, path) => (
    parseConfiguration<Configuration<'GEOMETRY_BUFFER'>>(
      value,
      path,
      (configuration, errors) => {
        const distanceUnit = configuration.distanceUnit == null
          ? null : stringValue(configuration.distanceUnit);
        const distanceSource = configuration.distanceSource == null
          ? null : stringValue(configuration.distanceSource);
        if (configuration.mode !== 'PLANAR' && configuration.mode !== 'SPHEROID') {
          errors.push(`${path}.mode 仅支持 PLANAR 或 SPHEROID`);
        }
        if (distanceUnit !== null && !spatialDistanceUnits.has(distanceUnit)) {
          errors.push(`${path}.distanceUnit 不是受支持的距离单位`);
        }
        if (distanceSource !== null
          && distanceSource !== 'CONSTANT'
          && distanceSource !== 'FIELD'
          && distanceSource !== 'EXPRESSION') {
          errors.push(`${path}.distanceSource 仅支持 CONSTANT、FIELD 或 EXPRESSION`);
        }
        if (configuration.distanceFieldName !== null
          && configuration.distanceFieldName !== undefined
          && typeof configuration.distanceFieldName !== 'string') {
          errors.push(`${path}.distanceFieldName 必须是字符串或 null`);
        }
        if (configuration.distanceExpression !== null
          && configuration.distanceExpression !== undefined
          && typeof configuration.distanceExpression !== 'string') {
          errors.push(`${path}.distanceExpression 必须是字符串或 null`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          outputTableName: stringValue(configuration.outputTableName),
          geometryColumnName: stringValue(configuration.geometryColumnName),
          outputColumnName: stringValue(configuration.outputColumnName),
          distance: parseFiniteNumber(
            configuration.distance,
            `${path}.distance`,
            errors,
            100,
          ),
          mode: configuration.mode === 'SPHEROID' ? 'SPHEROID' : 'PLANAR',
          distanceUnit: distanceUnit !== null && spatialDistanceUnits.has(distanceUnit)
            ? distanceUnit as Configuration<'GEOMETRY_BUFFER'>['distanceUnit'] : null,
          distanceSource: distanceSource === 'CONSTANT'
            || distanceSource === 'FIELD'
            || distanceSource === 'EXPRESSION'
            ? distanceSource : null,
          distanceFieldName: typeof configuration.distanceFieldName === 'string'
            ? configuration.distanceFieldName : null,
          distanceExpression: typeof configuration.distanceExpression === 'string'
            ? configuration.distanceExpression : null,
        };
      },
    )
  ),
  [CanvasNodeType.GeometryExplode]: (value, path) => (
    parseConfiguration<Configuration<'GEOMETRY_EXPLODE'>>(
      value,
      path,
      (configuration, errors) => {
        if (configuration.partIndexColumnName !== null
          && configuration.partIndexColumnName !== undefined
          && typeof configuration.partIndexColumnName !== 'string') {
          errors.push(`${path}.partIndexColumnName 必须是字符串或 null`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          outputTableName: stringValue(configuration.outputTableName),
          geometryColumnName: stringValue(configuration.geometryColumnName),
          outputColumnName: stringValue(configuration.outputColumnName),
          partIndexColumnName: typeof configuration.partIndexColumnName === 'string'
            ? configuration.partIndexColumnName : null,
        };
      },
    )
  ),
  [CanvasNodeType.SpatialMeasure]: (value, path) => (
    parseConfiguration<Configuration<'SPATIAL_MEASURE'>>(
      value,
      path,
      (configuration, errors) => ({
        sourceTableName: stringValue(configuration.sourceTableName),
        outputTableName: stringValue(configuration.outputTableName),
        measurements: parseSpatialMeasurements(
          configuration.measurements,
          `${path}.measurements`,
          errors,
        ),
      }),
    )
  ),
  [CanvasNodeType.GeometrySerialize]: (value, path) => (
    parseConfiguration<Configuration<'GEOMETRY_SERIALIZE'>>(
      value,
      path,
      (configuration, errors) => {
        if (configuration.format !== 'WKT'
          && configuration.format !== 'WKB'
          && configuration.format !== 'GEOJSON') {
          errors.push(`${path}.format 仅支持 WKT、WKB 或 GEOJSON`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          outputTableName: stringValue(configuration.outputTableName),
          geometryColumnName: stringValue(configuration.geometryColumnName),
          outputColumnName: stringValue(configuration.outputColumnName),
          format: configuration.format === 'WKB'
            ? 'WKB' : configuration.format === 'GEOJSON' ? 'GEOJSON' : 'WKT',
        };
      },
    )
  ),
  [CanvasNodeType.SpatialClip]: (value, path) => (
    parseConfiguration<Configuration<'SPATIAL_CLIP'>>(
      value,
      path,
      (configuration, errors) => {
        const geometryPolicy = configuration.geometryPolicy;
        if (geometryPolicy != null
          && geometryPolicy !== 'SOURCE_FAMILY_2D'
          && geometryPolicy !== 'LEGACY_ANY_DIMENSION') {
          errors.push(`${path}.geometryPolicy 仅支持 SOURCE_FAMILY_2D 或 LEGACY_ANY_DIMENSION`);
        }
        const maskCombination = configuration.maskCombination;
        if (maskCombination != null
          && maskCombination !== 'DISSOLVE_ALL'
          && maskCombination !== 'PAIRWISE') {
          errors.push(`${path}.maskCombination 仅支持 DISSOLVE_ALL 或 PAIRWISE`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          maskTableName: stringValue(configuration.maskTableName),
          outputTableName: stringValue(configuration.outputTableName),
          sourceGeometryColumnName: stringValue(configuration.sourceGeometryColumnName),
          maskGeometryColumnName: stringValue(configuration.maskGeometryColumnName),
          outputColumnName: stringValue(configuration.outputColumnName),
          geometryPolicy: geometryPolicy === 'SOURCE_FAMILY_2D'
            || geometryPolicy === 'LEGACY_ANY_DIMENSION' ? geometryPolicy : null,
          maskCombination: maskCombination === 'DISSOLVE_ALL'
            || maskCombination === 'PAIRWISE' ? maskCombination : null,
        };
      },
    )
  ),
  [CanvasNodeType.SpatialAggregate]: (value, path) => (
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
  ),
  [CanvasNodeType.SpatialJoin]: (value, path) => (
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
  ),
  [CanvasNodeType.StreamJoin]: (value, path) => (
    parseConfiguration<Configuration<'STREAM_JOIN'>>(value, path, (configuration, errors) => ({
      leftTableName: stringValue(configuration.leftTableName),
      rightTableName: stringValue(configuration.rightTableName),
      outputTableName: stringValue(configuration.outputTableName),
      joinType: parseStreamJoinType(configuration.joinType, `${path}.joinType`, errors),
      conditions: parseJoinConditions(configuration.conditions, `${path}.conditions`, errors),
      outputColumns: parseJoinOutputColumns(
        configuration.outputColumns,
        `${path}.outputColumns`,
        errors,
      ),
    }))
  ),
  [CanvasNodeType.Rename]: (value, path) => (
    parseConfiguration<Configuration<'RENAME'>>(value, path, (configuration, errors) => ({
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => ({ columnMappings: parseMappings(operation.columnMappings, `${operationPath}.columnMappings`, errors) })),
    }))
  ),
  [CanvasNodeType.Filter]: (value, path) => (
    parseConfiguration<Configuration<'FILTER'>>(value, path, (configuration, errors) => ({
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => {
          const rawMode = operation.mode ?? 'STRUCTURED';
          if (rawMode !== 'STRUCTURED' && rawMode !== 'SQL_EXPRESSION') {
            errors.push(`${operationPath}.mode 仅支持 STRUCTURED 或 SQL_EXPRESSION`);
          }
          if (operation.sqlExpression !== undefined
            && operation.sqlExpression !== null
            && typeof operation.sqlExpression !== 'string') {
            errors.push(`${operationPath}.sqlExpression 必须是字符串`);
          }
          const mode = rawMode === 'SQL_EXPRESSION' ? 'SQL_EXPRESSION' : 'STRUCTURED';
          const sqlExpression = typeof operation.sqlExpression === 'string'
            ? operation.sqlExpression
            : '';
          const sqlViolation = findFilterSqlExpressionViolation(sqlExpression);
          if (mode === 'SQL_EXPRESSION' && sqlViolation !== null && sqlViolation !== 'REQUIRED') {
            errors.push(`${operationPath}.sqlExpression 只能包含单个布尔谓词，不能包含 WHERE、完整 SQL、注释或分号`);
          }
          return {
            mode,
            condition: parseFilterCondition(operation.condition, `${operationPath}.condition`, errors),
            sqlExpression,
          };
        }),
    }))
  ),
  [CanvasNodeType.SqlTransform]: (value, path) => (
    parseConfiguration<Configuration<'SQL_TRANSFORM'>>(value, path, (configuration, errors) => {
      const sql = stringValue(configuration.sql);
      if (sql.length > 100_000) errors.push(`${path}.sql 不能超过 100000 个字符`);
      return {
        outputTableName: stringValue(configuration.outputTableName),
        sql,
      };
    })
  ),
  [CanvasNodeType.SelectColumns]: (value, path) => (
    parseConfiguration<Configuration<'SELECT_COLUMNS'>>(value, path, (configuration, errors) => ({
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => ({ columns: parseStringArray(operation.columns, `${operationPath}.columns`, errors) })),
    }))
  ),
  [CanvasNodeType.DeriveColumns]: (value, path) => (
    parseConfiguration<Configuration<'DERIVE_COLUMNS'>>(value, path, (configuration, errors) => ({
      globalDerivations: parseDerivations(configuration.globalDerivations ?? [], `${path}.globalDerivations`, errors),
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => ({ derivations: parseDerivations(operation.derivations, `${operationPath}.derivations`, errors) })),
    }))
  ),
  [CanvasNodeType.TypeCast]: (value, path) => (
    parseConfiguration<Configuration<'TYPE_CAST'>>(value, path, (configuration, errors) => ({
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => ({ casts: parseTypeCasts(operation.casts, `${operationPath}.casts`, errors) })),
    }))
  ),
  [CanvasNodeType.Aggregate]: (value, path) => (
    parseConfiguration<Configuration<'AGGREGATE'>>(value, path, (configuration, errors) => ({
      sourceTableName: stringValue(configuration.sourceTableName),
      outputTableName: stringValue(configuration.outputTableName),
      groupByColumns: parseStringArray(
        configuration.groupByColumns,
        `${path}.groupByColumns`,
        errors,
      ),
      aggregations: parseAggregations(
        configuration.aggregations,
        `${path}.aggregations`,
        errors,
      ),
    }))
  ),
  [CanvasNodeType.Union]: (value, path) => (
    parseConfiguration<Configuration<'UNION'>>(value, path, (configuration, errors) => ({
      inputTableNames: parseStringArray(
        configuration.inputTableNames,
        `${path}.inputTableNames`,
        errors,
      ),
      outputTableName: stringValue(configuration.outputTableName),
      mode: parseUnionMode(configuration.mode, `${path}.mode`, errors),
      mergingTables: parseUnionMergingTables(
        configuration.mergingTables,
        `${path}.mergingTables`,
        errors,
      ),
    }))
  ),
  [CanvasNodeType.Deduplicate]: (value, path) => (
    parseConfiguration<Configuration<'DEDUPLICATE'>>(value, path, (configuration, errors) => ({
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => ({
          keyColumns: parseStringArray(operation.keyColumns, `${operationPath}.keyColumns`, errors),
          keepStrategy: parseDeduplicateKeepStrategy(operation.keepStrategy, `${operationPath}.keepStrategy`, errors),
          orderBy: parseSortFields(operation.orderBy, `${operationPath}.orderBy`, errors),
        })),
    }))
  ),
  [CanvasNodeType.NullHandling]: (value, path) => (
    parseConfiguration<Configuration<'NULL_HANDLING'>>(value, path, (configuration, errors) => ({
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => ({ rules: parseNullHandlingRules(operation.rules, `${operationPath}.rules`, errors) })),
    }))
  ),
  [CanvasNodeType.ValueMapping]: (value, path) => (
    parseConfiguration<Configuration<'VALUE_MAPPING'>>(value, path, (configuration, errors) => ({
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => ({ rules: parseValueMappingRules(operation.rules, `${operationPath}.rules`, errors) })),
    }))
  ),
  [CanvasNodeType.MaskFields]: (value, path) => (
    parseConfiguration<Configuration<'MASK_FIELDS'>>(value, path, (configuration, errors) => ({
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => ({ fieldRules: parseMaskFieldRules(operation.fieldRules, `${operationPath}.fieldRules`, errors) })),
    }))
  ),
  [CanvasNodeType.JsonExtract]: (value, path) => (
    parseConfiguration<Configuration<'JSON_EXTRACT'>>(value, path, (configuration, errors) => {
      return {
        operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
          (operation, operationPath) => {
            if (operation.failureStrategy !== 'ERROR' && operation.failureStrategy !== 'SET_NULL') {
              errors.push(`${operationPath}.failureStrategy 仅支持 ERROR 或 SET_NULL`);
            }
            return {
              sourceColumnName: stringValue(operation.sourceColumnName),
              extractions: parseJsonExtractions(operation.extractions, `${operationPath}.extractions`, errors),
              failureStrategy: operation.failureStrategy === 'SET_NULL' ? 'SET_NULL' : 'ERROR',
            };
          }),
      };
    })
  ),
  [CanvasNodeType.Window]: (value, path) => (
    parseConfiguration<Configuration<'WINDOW'>>(value, path, (configuration, errors) => ({
      sourceTableName: stringValue(configuration.sourceTableName),
      outputTableName: stringValue(configuration.outputTableName),
      partitionByColumns: parseStringArray(
        configuration.partitionByColumns,
        `${path}.partitionByColumns`,
        errors,
      ),
      orderBy: parseSortFields(configuration.orderBy, `${path}.orderBy`, errors),
      functions: parseWindowFunctions(configuration.functions, `${path}.functions`, errors),
    }))
  ),
  [CanvasNodeType.TopN]: (value, path) => (
    parseConfiguration<Configuration<'TOP_N'>>(value, path, (configuration, errors) => {
      return {
        operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
          (operation, operationPath) => {
            const limit = parseInteger(operation.limit, `${operationPath}.limit`, errors, 10);
            if (limit < 1 || limit > CANVAS_TOP_N_MAX_LIMIT) {
              errors.push(`${operationPath}.limit 必须在 1..${CANVAS_TOP_N_MAX_LIMIT}`);
            }
            if (operation.tieStrategy !== 'EXACT' && operation.tieStrategy !== 'WITH_TIES') {
              errors.push(`${operationPath}.tieStrategy 仅支持 EXACT 或 WITH_TIES`);
            }
            return {
              partitionByColumns: parseStringArray(operation.partitionByColumns, `${operationPath}.partitionByColumns`, errors),
              orderBy: parseSortFields(operation.orderBy, `${operationPath}.orderBy`, errors),
              limit,
              tieStrategy: operation.tieStrategy === 'WITH_TIES' ? 'WITH_TIES' : 'EXACT',
            };
          }),
      };
    })
  ),
  [CanvasNodeType.ModelOutput]: (value, path) => (
    parseConfiguration<Configuration<'MODEL_OUTPUT'>>(value, path, (configuration, errors) => {
      const writes = parseOutputWrites(configuration.writes, `${path}.writes`, errors, (write, writePath) => ({
        writeId: validateOptionalUuid(stringValue(write.writeId), `${writePath}.writeId`, errors),
        sourceTableName: stringValue(write.sourceTableName),
        targetModelId: validateOptionalUuid(stringValue(write.targetModelId), `${writePath}.targetModelId`, errors),
        writeMode: parseWriteMode(write.writeMode, `${writePath}.writeMode`, errors),
        columnMappings: parseMappings(write.columnMappings, `${writePath}.columnMappings`, errors),
      }));
      return { writes };
    })
  ),
  [CanvasNodeType.JdbcOutput]: (value, path) => (
    parseConfiguration<Configuration<'JDBC_OUTPUT'>>(value, path, (configuration, errors) => {
      const writes = parseOutputWrites(configuration.writes, `${path}.writes`, errors, (write, writePath) => ({
        writeId: validateOptionalUuid(stringValue(write.writeId), `${writePath}.writeId`, errors),
        sourceTableName: stringValue(write.sourceTableName),
        targetTableName: stringValue(write.targetTableName) || legacyTableName(write.targetTable),
        writeMode: parseWriteMode(write.writeMode, `${writePath}.writeMode`, errors),
        columnMappings: parseMappings(write.columnMappings, `${writePath}.columnMappings`, errors),
        upsertKeyColumns: parseStringArray(write.upsertKeyColumns, `${writePath}.upsertKeyColumns`, errors),
      }));
      return { dataSourceId: stringValue(configuration.dataSourceId), writes };
    })
  ),
  [CanvasNodeType.JdbcSnapshotSyncOutput]: (value, path) => (
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
  ),
  [CanvasNodeType.ModelSnapshotSyncOutput]: (value, path) => (
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
  ),
  [CanvasNodeType.KafkaOutput]: (value, path) => (
    parseConfiguration<Configuration<'KAFKA_OUTPUT'>>(value, path, (configuration, errors) => {
      const writes = parseOutputWrites(configuration.writes, `${path}.writes`, errors, (write, writePath) => {
        const rawFormat = write.valueFormat;
        const valueFormat = rawFormat == null
          ? null
          : rawFormat === 'JSON' || rawFormat === 'TEXT' || rawFormat === 'BINARY'
            ? rawFormat
            : null;
        if (rawFormat != null && valueFormat === null) {
          errors.push(`${writePath}.valueFormat 不是支持的 Kafka Value 格式`);
        }
        const valueColumnNames = parseStringArray(
          write.valueColumnNames, `${writePath}.valueColumnNames`, errors,
        );
        const valueSchema = write.valueSchema == null
          ? null
          : parseKafkaValueSchema(write.valueSchema, `${writePath}.valueSchema`, errors);
        const columnMappings = parseMappings(
          write.columnMappings, `${writePath}.columnMappings`, errors,
        );
        valueColumnNames.forEach((columnName, index) => {
          if (!columnName.trim()) {
            errors.push(`${writePath}.valueColumnNames[${index}] 不能为空`);
          }
        });
        if (valueFormat === null) {
          if (valueSchema === null) errors.push(`${writePath}.valueSchema 不能为空`);
          if (valueColumnNames.length > 0) {
            errors.push(`${writePath}.valueColumnNames 在旧版 JSON 映射模式下必须为空`);
          }
        } else {
          if (valueSchema && valueSchema.columns.length > 0) {
            errors.push(`${writePath}.valueSchema.columns 在新 Kafka Value 模式下必须为空`);
          }
          if (columnMappings.length > 0) {
            errors.push(`${writePath}.columnMappings 在新 Kafka Value 模式下必须为空`);
          }
          if (valueColumnNames.length === 0) {
            errors.push(`${writePath}.valueColumnNames 至少需要一个字段`);
          }
          if (new Set(valueColumnNames).size !== valueColumnNames.length) {
            errors.push(`${writePath}.valueColumnNames 不能包含重复字段`);
          }
          if (valueFormat !== 'JSON' && valueColumnNames.length !== 1) {
            errors.push(`${writePath}.valueColumnNames 在 ${valueFormat} 格式下必须且只能有一个字段`);
          }
        }
        return {
          writeId: validateOptionalUuid(stringValue(write.writeId), `${writePath}.writeId`, errors),
          sourceTableName: stringValue(write.sourceTableName),
          topic: stringValue(write.topic),
          valueFormat,
          valueColumnNames,
          keyColumnName: stringValue(write.keyColumnName),
          valueSchema,
          columnMappings,
        };
      });
      return {
        dataSourceId: validateOptionalUuid(
          stringValue(configuration.dataSourceId), `${path}.dataSourceId`, errors,
        ),
        writes,
      };
    })
  ),
  [CanvasNodeType.FileOutput]: (value, path) => (
    parseConfiguration<Configuration<'FILE_OUTPUT'>>(value, path, (configuration, errors) => {
      return {
        dataSourceId: validateOptionalUuid(
          stringValue(configuration.dataSourceId),
          `${path}.dataSourceId`,
          errors,
        ),
        writes: parseOutputWrites(configuration.writes, `${path}.writes`, errors, (write, writePath) => {
          const rawTargetPath = stringValue(write.targetPath);
          const targetPath = normalizeFileOutputPath(rawTargetPath);
          const invalidSegment = targetPath.split('/').some(
            (segment) => !segment || segment === '.' || segment === '..'
              || segment.toLowerCase() === '_temporary',
          );
          if (!targetPath || targetPath.length > 1024 || rawTargetPath.trim().startsWith('/')
            || targetPath.includes('\\') || targetPath.includes('://') || targetPath.includes('?')
            || targetPath.includes('#') || invalidSegment) {
            errors.push(`${writePath}.targetPath 必须是合法的 S3 相对路径`);
          }
          return {
            writeId: validateOptionalUuid(stringValue(write.writeId), `${writePath}.writeId`, errors),
            sourceTableName: stringValue(write.sourceTableName),
            targetPath,
            conflictPolicy: parseFileOutputConflictPolicy(write.conflictPolicy, `${writePath}.conflictPolicy`, errors),
            formatOptions: parseFileOutputFormatOptions(write.formatOptions, `${writePath}.formatOptions`, errors),
          };
        }),
      };
    })
  ),
} satisfies ConfigurationParserMap;

export const parseCanvasNodeConfiguration = <T extends CanvasNodeTypeValue>(
  type: T,
  value: unknown,
  path: string,
): CanvasParseResult<CanvasNodeConfigurationByType<T>> => {
  const parser = configurationParsers[type];
  return parser(value, path) as unknown as CanvasParseResult<CanvasNodeConfigurationByType<T>>;
};
