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
  parseJoinType,
  parseKafkaValueSchema,
  parseMappingMode,
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
  CANVAS_MASKING_MAX_FIELD_RULES,
  CANVAS_TOP_N_MAX_LIMIT,
  CANVAS_SPATIAL_MEASURE_MAX_MEASUREMENTS,
  CANVAS_SPATIAL_AGGREGATE_MAX_AGGREGATIONS,
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
  type RowsFrameBoundary,
  type RowsWindowFrame,
  type SpatialMeasurement,
  type SpatialAggregation,
  type CanvasColumnSchema,
  type ValueMappingRule,
  type WindowFunctionItem,
  type CanvasNodeConfigurationByType,
  type CanvasNodeType as CanvasNodeTypeValue,
} from '../canvasTypes';
import { createMaskingRuleDefinition } from '../../model/maskingRule';
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
  parser: (configuration: Record<string, unknown>, errors: string[]) => T,
): CanvasParseResult<T> => {
  const errors: string[] = [];
  const configuration = isRecord(value) ? value : {};
  const parsed = parser(configuration, errors);
  return errors.length > 0
    ? { success: false, errors }
    : { success: true, value: parsed };
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
  if (strategy === 'PARTIAL_MASK') {
    const maskCharacter = value.maskCharacter === null
      || value.maskCharacter === undefined
      ? '*' : stringValue(value.maskCharacter);
    if (Array.from(maskCharacter).length !== 1) {
      errors.push(`${path}.maskCharacter 必须是一个 Unicode 字符`);
    }
    if (value.fixedValue !== null && value.fixedValue !== undefined) {
      errors.push(`${path}.fixedValue 不适用于 PARTIAL_MASK`);
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
  if (strategy === 'KEEP_LENGTH_MASK') {
    const maskCharacter = value.maskCharacter === null
      || value.maskCharacter === undefined
      ? '*' : stringValue(value.maskCharacter);
    if (Array.from(maskCharacter).length !== 1) {
      errors.push(`${path}.maskCharacter 必须是一个 Unicode 字符`);
    }
    if (value.keepPrefixLength !== null && value.keepPrefixLength !== undefined
      || value.keepSuffixLength !== null && value.keepSuffixLength !== undefined
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
      if (item.kind === 'AREA' || item.kind === 'LENGTH' || item.kind === 'PERIMETER') {
        if (item.mode !== 'PLANAR' && item.mode !== 'SPHEROID') {
          errors.push(`${itemPath}.mode 仅支持 PLANAR 或 SPHEROID`);
        }
        return [{
          kind: item.kind,
          geometryColumnName: stringValue(item.geometryColumnName),
          mode: item.mode === 'SPHEROID' ? 'SPHEROID' : 'PLANAR',
          outputColumnName,
        }];
      }
      if (item.kind === 'DISTANCE') {
        if (item.mode !== 'PLANAR' && item.mode !== 'SPHEROID') {
          errors.push(`${itemPath}.mode 仅支持 PLANAR 或 SPHEROID`);
        }
        return [{
          kind: 'DISTANCE',
          leftGeometryColumnName: stringValue(item.leftGeometryColumnName),
          rightGeometryColumnName: stringValue(item.rightGeometryColumnName),
          mode: item.mode === 'SPHEROID' ? 'SPHEROID' : 'PLANAR',
          outputColumnName,
        }];
      }
      errors.push(`${itemPath}.kind 不是受支持的空间测量类型`);
      return [];
    });
};

const configurationParsers = {
  [CanvasNodeType.ModelInput]: (value, path) => (
    parseConfiguration<Configuration<'MODEL_INPUT'>>(value, path, (configuration, errors) => ({
      modelId: validateOptionalUuid(
        stringValue(configuration.modelId),
        `${path}.modelId`,
        errors,
      ),
    }))
  ),
  [CanvasNodeType.JdbcInput]: (value, path) => (
    parseConfiguration<Configuration<'JDBC_INPUT'>>(value, path, (configuration) => ({
      dataSourceId: stringValue(configuration.dataSourceId),
      tableName: stringValue(configuration.tableName) || legacyTableName(configuration.table),
    }))
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
        fileDatasetTableId: validateOptionalUuid(
          stringValue(configuration.fileDatasetTableId),
          `${path}.fileDatasetTableId`,
          errors,
        ),
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
      resourceId: validateOptionalUuid(
        stringValue(configuration.resourceId),
        `${path}.resourceId`,
        errors,
      ),
      outputTableName: stringValue(configuration.outputTableName),
      runtimeParameters: parseRuntimeParameters(
        configuration.runtimeParameters,
        `${path}.runtimeParameters`,
        errors,
      ),
    }))
  ),
  [CanvasNodeType.SpatialServiceInput]: (value, path) => (
    parseConfiguration<Configuration<'SPATIAL_SERVICE_INPUT'>>(value, path, (configuration, errors) => ({
      dataSourceId: validateOptionalUuid(stringValue(configuration.dataSourceId), `${path}.dataSourceId`, errors),
      resourceId: validateOptionalUuid(stringValue(configuration.resourceId), `${path}.resourceId`, errors),
      outputTableName: stringValue(configuration.outputTableName),
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
      return {
        dataSourceId: validateOptionalUuid(
          stringValue(configuration.dataSourceId),
          `${path}.dataSourceId`,
          errors,
        ),
        topic: stringValue(configuration.topic),
        valueSchema: parseKafkaValueSchema(
          configuration.valueSchema,
          `${path}.valueSchema`,
          errors,
        ),
        outputTableName: stringValue(configuration.outputTableName),
        startingOffsets: configuration.startingOffsets === 'EARLIEST'
          || configuration.startingOffsets === 'LATEST'
          ? configuration.startingOffsets
          : null,
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
  [CanvasNodeType.GeometryBuffer]: (value, path) => (
    parseConfiguration<Configuration<'GEOMETRY_BUFFER'>>(
      value,
      path,
      (configuration, errors) => {
        if (configuration.mode !== 'PLANAR' && configuration.mode !== 'SPHEROID') {
          errors.push(`${path}.mode 仅支持 PLANAR 或 SPHEROID`);
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
      (configuration) => ({
        sourceTableName: stringValue(configuration.sourceTableName),
        maskTableName: stringValue(configuration.maskTableName),
        outputTableName: stringValue(configuration.outputTableName),
        sourceGeometryColumnName: stringValue(configuration.sourceGeometryColumnName),
        maskGeometryColumnName: stringValue(configuration.maskGeometryColumnName),
        outputColumnName: stringValue(configuration.outputColumnName),
      }),
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
      }),
    )
  ),
  [CanvasNodeType.SpatialJoin]: (value, path) => (
    parseConfiguration<Configuration<'SPATIAL_JOIN'>>(value, path, (configuration, errors) => {
      if (configuration.joinType !== 'INNER') {
        errors.push(`${path}.joinType 第一阶段仅支持 INNER`);
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
      return {
        leftTableName: stringValue(configuration.leftTableName),
        rightTableName: stringValue(configuration.rightTableName),
        outputTableName: stringValue(configuration.outputTableName),
        joinType: 'INNER',
        conditions,
      };
    })
  ),
  [CanvasNodeType.StreamJoin]: (value, path) => (
    parseConfiguration<Configuration<'STREAM_JOIN'>>(value, path, (configuration, errors) => ({
      leftTableName: stringValue(configuration.leftTableName),
      rightTableName: stringValue(configuration.rightTableName),
      outputTableName: stringValue(configuration.outputTableName),
      joinType: parseStreamJoinType(configuration.joinType, `${path}.joinType`, errors),
      conditions: parseJoinConditions(configuration.conditions, `${path}.conditions`, errors),
    }))
  ),
  [CanvasNodeType.Rename]: (value, path) => (
    parseConfiguration<Configuration<'RENAME'>>(value, path, (configuration, errors) => ({
      sourceTableName: stringValue(configuration.sourceTableName),
      outputTableName: stringValue(configuration.outputTableName),
      columnMappings: parseMappings(
        configuration.columnMappings,
        `${path}.columnMappings`,
        errors,
      ),
    }))
  ),
  [CanvasNodeType.Filter]: (value, path) => (
    parseConfiguration<Configuration<'FILTER'>>(value, path, (configuration, errors) => ({
      sourceTableName: stringValue(configuration.sourceTableName),
      outputTableName: stringValue(configuration.outputTableName),
      condition: parseFilterCondition(configuration.condition, `${path}.condition`, errors),
    }))
  ),
  [CanvasNodeType.SelectColumns]: (value, path) => (
    parseConfiguration<Configuration<'SELECT_COLUMNS'>>(value, path, (configuration, errors) => ({
      sourceTableName: stringValue(configuration.sourceTableName),
      outputTableName: stringValue(configuration.outputTableName),
      columns: parseStringArray(configuration.columns, `${path}.columns`, errors),
    }))
  ),
  [CanvasNodeType.DeriveColumns]: (value, path) => (
    parseConfiguration<Configuration<'DERIVE_COLUMNS'>>(value, path, (configuration, errors) => ({
      sourceTableName: stringValue(configuration.sourceTableName),
      outputTableName: stringValue(configuration.outputTableName),
      derivations: parseDerivations(configuration.derivations, `${path}.derivations`, errors),
    }))
  ),
  [CanvasNodeType.TypeCast]: (value, path) => (
    parseConfiguration<Configuration<'TYPE_CAST'>>(value, path, (configuration, errors) => ({
      sourceTableName: stringValue(configuration.sourceTableName),
      outputTableName: stringValue(configuration.outputTableName),
      casts: parseTypeCasts(configuration.casts, `${path}.casts`, errors),
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
    }))
  ),
  [CanvasNodeType.Deduplicate]: (value, path) => (
    parseConfiguration<Configuration<'DEDUPLICATE'>>(value, path, (configuration, errors) => ({
      sourceTableName: stringValue(configuration.sourceTableName),
      outputTableName: stringValue(configuration.outputTableName),
      keyColumns: parseStringArray(configuration.keyColumns, `${path}.keyColumns`, errors),
      keepStrategy: parseDeduplicateKeepStrategy(
        configuration.keepStrategy,
        `${path}.keepStrategy`,
        errors,
      ),
      orderBy: parseSortFields(configuration.orderBy, `${path}.orderBy`, errors),
    }))
  ),
  [CanvasNodeType.NullHandling]: (value, path) => (
    parseConfiguration<Configuration<'NULL_HANDLING'>>(value, path, (configuration, errors) => ({
      sourceTableName: stringValue(configuration.sourceTableName),
      outputTableName: stringValue(configuration.outputTableName),
      rules: parseNullHandlingRules(configuration.rules, `${path}.rules`, errors),
    }))
  ),
  [CanvasNodeType.ValueMapping]: (value, path) => (
    parseConfiguration<Configuration<'VALUE_MAPPING'>>(value, path, (configuration, errors) => ({
      sourceTableName: stringValue(configuration.sourceTableName),
      outputTableName: stringValue(configuration.outputTableName),
      rules: parseValueMappingRules(configuration.rules, `${path}.rules`, errors),
    }))
  ),
  [CanvasNodeType.MaskFields]: (value, path) => (
    parseConfiguration<Configuration<'MASK_FIELDS'>>(value, path, (configuration, errors) => ({
      sourceTableName: stringValue(configuration.sourceTableName),
      outputTableName: stringValue(configuration.outputTableName),
      fieldRules: parseMaskFieldRules(
        configuration.fieldRules,
        `${path}.fieldRules`,
        errors,
      ),
    }))
  ),
  [CanvasNodeType.JsonExtract]: (value, path) => (
    parseConfiguration<Configuration<'JSON_EXTRACT'>>(value, path, (configuration, errors) => {
      if (configuration.failureStrategy !== 'ERROR'
        && configuration.failureStrategy !== 'SET_NULL') {
        errors.push(`${path}.failureStrategy 仅支持 ERROR 或 SET_NULL`);
      }
      return {
        sourceTableName: stringValue(configuration.sourceTableName),
        outputTableName: stringValue(configuration.outputTableName),
        sourceColumnName: stringValue(configuration.sourceColumnName),
        extractions: parseJsonExtractions(
          configuration.extractions,
          `${path}.extractions`,
          errors,
        ),
        failureStrategy: configuration.failureStrategy === 'SET_NULL'
          ? 'SET_NULL' : 'ERROR',
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
      const limit = parseInteger(configuration.limit, `${path}.limit`, errors, 10);
      if (limit < 1 || limit > CANVAS_TOP_N_MAX_LIMIT) {
        errors.push(`${path}.limit 必须在 1..${CANVAS_TOP_N_MAX_LIMIT}`);
      }
      if (configuration.tieStrategy !== 'EXACT'
        && configuration.tieStrategy !== 'WITH_TIES') {
        errors.push(`${path}.tieStrategy 仅支持 EXACT 或 WITH_TIES`);
      }
      return {
        sourceTableName: stringValue(configuration.sourceTableName),
        outputTableName: stringValue(configuration.outputTableName),
        partitionByColumns: parseStringArray(
          configuration.partitionByColumns,
          `${path}.partitionByColumns`,
          errors,
        ),
        orderBy: parseSortFields(configuration.orderBy, `${path}.orderBy`, errors),
        limit,
        tieStrategy: configuration.tieStrategy === 'WITH_TIES'
          ? 'WITH_TIES' : 'EXACT',
      };
    })
  ),
  [CanvasNodeType.ModelOutput]: (value, path) => (
    parseConfiguration<Configuration<'MODEL_OUTPUT'>>(value, path, (configuration, errors) => ({
      sourceTableName: stringValue(configuration.sourceTableName),
      targetModelId: validateOptionalUuid(
        stringValue(configuration.targetModelId),
        `${path}.targetModelId`,
        errors,
      ),
      writeMode: parseWriteMode(configuration.writeMode, `${path}.writeMode`, errors),
      columnMappingMode: parseMappingMode(
        configuration.columnMappingMode,
        `${path}.columnMappingMode`,
        errors,
      ),
      columnMappings: parseMappings(
        configuration.columnMappings,
        `${path}.columnMappings`,
        errors,
      ),
    }))
  ),
  [CanvasNodeType.JdbcOutput]: (value, path) => (
    parseConfiguration<Configuration<'JDBC_OUTPUT'>>(value, path, (configuration, errors) => ({
      sourceTableName: stringValue(configuration.sourceTableName),
      dataSourceId: stringValue(configuration.dataSourceId),
      targetTableName: stringValue(configuration.targetTableName)
        || legacyTableName(configuration.targetTable),
      writeMode: parseWriteMode(configuration.writeMode, `${path}.writeMode`, errors),
      columnMappingMode: parseMappingMode(
        configuration.columnMappingMode,
        `${path}.columnMappingMode`,
        errors,
      ),
      columnMappings: parseMappings(
        configuration.columnMappings,
        `${path}.columnMappings`,
        errors,
      ),
      upsertKeyColumns: parseStringArray(
        configuration.upsertKeyColumns,
        `${path}.upsertKeyColumns`,
        errors,
      ),
    }))
  ),
  [CanvasNodeType.KafkaOutput]: (value, path) => (
    parseConfiguration<Configuration<'KAFKA_OUTPUT'>>(value, path, (configuration, errors) => ({
      sourceTableName: stringValue(configuration.sourceTableName),
      dataSourceId: validateOptionalUuid(
        stringValue(configuration.dataSourceId),
        `${path}.dataSourceId`,
        errors,
      ),
      topic: stringValue(configuration.topic),
      valueSchema: parseKafkaValueSchema(
        configuration.valueSchema,
        `${path}.valueSchema`,
        errors,
      ),
      keyColumnName: stringValue(configuration.keyColumnName),
      columnMappingMode: parseMappingMode(
        configuration.columnMappingMode,
        `${path}.columnMappingMode`,
        errors,
      ),
      columnMappings: parseMappings(
        configuration.columnMappings,
        `${path}.columnMappings`,
        errors,
      ),
    }))
  ),
  [CanvasNodeType.FileOutput]: (value, path) => (
    parseConfiguration<Configuration<'FILE_OUTPUT'>>(value, path, (configuration, errors) => {
      const rawTargetPath = stringValue(configuration.targetPath);
      const targetPath = normalizeFileOutputPath(rawTargetPath);
      const invalidSegment = targetPath.split('/').some(
        (segment) => !segment || segment === '.' || segment === '..'
          || segment.toLowerCase() === '_temporary',
      );
      if (!targetPath
        || targetPath.length > 1024
        || rawTargetPath.trim().startsWith('/')
        || targetPath.includes('\\')
        || targetPath.includes('://')
        || targetPath.includes('?')
        || targetPath.includes('#')
        || invalidSegment) {
        errors.push(`${path}.targetPath 必须是合法的 S3 相对路径`);
      }
      return {
        sourceTableName: stringValue(configuration.sourceTableName),
        dataSourceId: validateOptionalUuid(
          stringValue(configuration.dataSourceId),
          `${path}.dataSourceId`,
          errors,
        ),
        targetPath,
        conflictPolicy: parseFileOutputConflictPolicy(
          configuration.conflictPolicy,
          `${path}.conflictPolicy`,
          errors,
        ),
        formatOptions: parseFileOutputFormatOptions(
          configuration.formatOptions,
          `${path}.formatOptions`,
          errors,
        ),
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
