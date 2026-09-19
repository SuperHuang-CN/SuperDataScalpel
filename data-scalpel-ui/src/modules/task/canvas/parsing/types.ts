import { type ColumnTypeCast, type CastFailureStrategy, type EpochTimestampUnit, type StringTemporalParseOptions, type StringTimestampZoneMode, type TemporalStringFormatOptions, type PlatformTypeDefinition } from "../canvasTypes";
import { isValidIanaZoneId } from "../../model/taskScheduleValidation";
import { isRecord, stringValue, platformDataTypes, optionalInteger } from './scalars';

export const parsePlatformTypeDefinition = (
  value: unknown,
  path: string,
  errors: string[],
): PlatformTypeDefinition => {
  if (!isRecord(value)) {
    errors.push(`${path} 必须是平台类型对象`);
    return {
      type: 'STRING',
      length: null,
      precision: null,
      scale: null,
      geometry: null,
    };
  }
  const rawType = stringValue(value.type);
  if (!platformDataTypes.has(rawType)) {
    errors.push(`${path}.type 不是 Canvas 支持的平台标量类型`);
  }
  const type = platformDataTypes.has(rawType)
    ? rawType as PlatformTypeDefinition['type']
    : 'STRING';
  const length = optionalInteger(value.length, `${path}.length`, errors);
  const precision = optionalInteger(value.precision, `${path}.precision`, errors);
  const scale = optionalInteger(value.scale, `${path}.scale`, errors);
  if (type === 'STRING') {
    if (length !== null && length < 1) errors.push(`${path}.length 必须为正整数`);
    if (precision !== null || scale !== null) {
      errors.push(`${path} 的 STRING 不能配置 precision/scale`);
    }
  } else if (type === 'DECIMAL') {
    if (length !== null) errors.push(`${path} 的 DECIMAL 不能配置 length`);
    if (precision === null || precision < 1 || precision > 38) {
      errors.push(`${path}.precision 必须在 1..38`);
    }
    if (scale === null || scale < 0 || (precision !== null && scale > precision)) {
      errors.push(`${path}.scale 必须在 0..precision`);
    }
  } else if (length !== null || precision !== null || scale !== null) {
    errors.push(`${path} 的 ${type} 不能配置 length/precision/scale`);
  }
  if (value.geometry !== undefined && value.geometry !== null) {
    errors.push(`${path}.geometry 在 Canvas 标量处理器中不受支持`);
  }
  return {
    type,
    length: type === 'STRING' ? length : null,
    precision: type === 'DECIMAL' ? precision : null,
    scale: type === 'DECIMAL' ? scale : null,
    geometry: null,
  };
};

export const parseTypeCasts = (
  value: unknown,
  path: string,
  errors: string[],
): ColumnTypeCast[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): ColumnTypeCast[] => {
    const itemPath = `${path}[${index}]`;
    if (!isRecord(item)) {
      errors.push(`${itemPath} 必须是对象`);
      return [];
    }
    const failureStrategy = item.failureStrategy === 'FAIL'
      || item.failureStrategy === 'SET_NULL'
      ? item.failureStrategy as CastFailureStrategy
      : null;
    if (item.failureStrategy !== undefined
      && item.failureStrategy !== null
      && failureStrategy === null) {
      errors.push(`${itemPath}.failureStrategy 仅支持 FAIL 或 SET_NULL`);
    }
    const epochTimestampUnit = item.epochTimestampUnit === 'SECONDS'
      || item.epochTimestampUnit === 'MILLISECONDS'
      || item.epochTimestampUnit === 'MICROSECONDS'
      ? item.epochTimestampUnit as EpochTimestampUnit
      : item.epochTimestampUnit === null ? null : undefined;
    if (item.epochTimestampUnit !== undefined && epochTimestampUnit === undefined) {
      errors.push(`${itemPath}.epochTimestampUnit 仅支持 SECONDS、MILLISECONDS 或 MICROSECONDS`);
    }
    const targetType = parsePlatformTypeDefinition(
      item.targetType,
      `${itemPath}.targetType`,
      errors,
    );
    const stringTemporalParseOptions = parseStringTemporalParseOptions(
      item.stringTemporalParseOptions,
      `${itemPath}.stringTemporalParseOptions`,
      errors,
    );
    validateStringTemporalParseOptions(
      stringTemporalParseOptions,
      targetType,
      epochTimestampUnit,
      `${itemPath}.stringTemporalParseOptions`,
      errors,
    );
    const temporalStringFormatOptions = parseTemporalStringFormatOptions(
      item.temporalStringFormatOptions,
      `${itemPath}.temporalStringFormatOptions`,
      errors,
    );
    validateTemporalStringFormatOptions(
      temporalStringFormatOptions,
      targetType,
      epochTimestampUnit,
      stringTemporalParseOptions,
      `${itemPath}.temporalStringFormatOptions`,
      errors,
    );
    return [{
      columnName: stringValue(item.columnName),
      targetType,
      failureStrategy,
      ...(item.epochTimestampUnit === undefined ? {} : { epochTimestampUnit }),
      ...(item.stringTemporalParseOptions === undefined ? {} : { stringTemporalParseOptions }),
      ...(item.temporalStringFormatOptions === undefined ? {} : { temporalStringFormatOptions }),
    }];
  });
};

export const parseTemporalStringFormatOptions = (
  value: unknown,
  path: string,
  errors: string[],
): TemporalStringFormatOptions | null | undefined => {
  if (value === undefined) return undefined;
  if (value === null) return null;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象或 null`);
    return undefined;
  }
  if (typeof value.pattern !== 'string') errors.push(`${path}.pattern 必须是字符串`);
  const targetTimeZone = value.targetTimeZone === null || value.targetTimeZone === undefined
    ? null
    : typeof value.targetTimeZone === 'string' ? value.targetTimeZone : undefined;
  if (targetTimeZone === undefined) errors.push(`${path}.targetTimeZone 必须是字符串或 null`);
  return {
    pattern: stringValue(value.pattern),
    targetTimeZone: targetTimeZone ?? null,
  };
};

export const validateTemporalStringFormatOptions = (
  options: TemporalStringFormatOptions | null | undefined,
  targetType: PlatformTypeDefinition,
  epochTimestampUnit: EpochTimestampUnit | null | undefined,
  parseOptions: StringTemporalParseOptions | null | undefined,
  path: string,
  errors: string[],
) => {
  if (options == null) return;
  if (epochTimestampUnit != null || parseOptions != null) {
    errors.push(`${path} 不能与其他特殊时间转换配置同时使用`);
  }
  if (targetType.type !== 'STRING') {
    errors.push(`${path} 仅支持 DATE、TIMESTAMP 或 TIMESTAMP_NTZ 转换为 STRING`);
  }
  if (!options.pattern.trim()) {
    errors.push(`${path}.pattern 不能为空`);
  } else if (options.pattern.length > 128) {
    errors.push(`${path}.pattern 不能超过 128 个字符`);
  } else if (containsUnquotedZonePatternSymbol(options.pattern)) {
    errors.push(`${path}.pattern 不能包含时区或偏移符号`);
  }
  if (options.targetTimeZone !== null) {
    if (!options.targetTimeZone.trim()) {
      errors.push(`${path}.targetTimeZone 不能为空字符串`);
    } else if (options.targetTimeZone.length > 64) {
      errors.push(`${path}.targetTimeZone 不能超过 64 个字符`);
    } else if (!isValidIanaZoneId(options.targetTimeZone)) {
      errors.push(`${path}.targetTimeZone 必须是有效的 IANA Zone ID`);
    }
  }
};

export const parseStringTemporalParseOptions = (
  value: unknown,
  path: string,
  errors: string[],
): StringTemporalParseOptions | null | undefined => {
  if (value === undefined) return undefined;
  if (value === null) return null;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象或 null`);
    return undefined;
  }
  if (typeof value.pattern !== 'string') errors.push(`${path}.pattern 必须是字符串`);
  const zoneMode = value.zoneMode === 'SOURCE_TIME_ZONE' || value.zoneMode === 'EMBEDDED_OFFSET'
    ? value.zoneMode as StringTimestampZoneMode
    : value.zoneMode === null || value.zoneMode === undefined ? null : undefined;
  if (zoneMode === undefined) {
    errors.push(`${path}.zoneMode 仅支持 SOURCE_TIME_ZONE 或 EMBEDDED_OFFSET`);
  }
  const sourceTimeZone = value.sourceTimeZone === null || value.sourceTimeZone === undefined
    ? null
    : typeof value.sourceTimeZone === 'string' ? value.sourceTimeZone : undefined;
  if (sourceTimeZone === undefined) errors.push(`${path}.sourceTimeZone 必须是字符串或 null`);
  return {
    pattern: stringValue(value.pattern),
    zoneMode: zoneMode ?? null,
    sourceTimeZone: sourceTimeZone ?? null,
  };
};

export const validateStringTemporalParseOptions = (
  options: StringTemporalParseOptions | null | undefined,
  targetType: PlatformTypeDefinition,
  epochTimestampUnit: EpochTimestampUnit | null | undefined,
  path: string,
  errors: string[],
) => {
  if (options == null) return;
  if (epochTimestampUnit != null) {
    errors.push(`${path} 不能与 epochTimestampUnit 同时配置`);
  }
  if (targetType.type !== 'DATE' && targetType.type !== 'TIMESTAMP') {
    errors.push(`${path} 仅支持 STRING 转换为 DATE 或 TIMESTAMP`);
    return;
  }
  if (!options.pattern.trim()) {
    errors.push(`${path}.pattern 不能为空`);
  } else if (options.pattern.length > 128) {
    errors.push(`${path}.pattern 不能超过 128 个字符`);
  }
  if (targetType.type === 'DATE') {
    if (options.zoneMode !== null || options.sourceTimeZone !== null) {
      errors.push(`${path} 的 DATE 解析不能配置时区`);
    }
    return;
  }
  if (options.zoneMode === null) {
    errors.push(`${path}.zoneMode 不能为空`);
    return;
  }
  const patternContainsZone = containsUnquotedZonePatternSymbol(options.pattern);
  if (options.zoneMode === 'SOURCE_TIME_ZONE') {
    if ((options.sourceTimeZone?.length ?? 0) > 64) {
      errors.push(`${path}.sourceTimeZone 不能超过 64 个字符`);
    } else if (!isValidIanaZoneId(options.sourceTimeZone ?? '')) {
      errors.push(`${path}.sourceTimeZone 必须是有效的 IANA Zone ID`);
    }
    if (patternContainsZone) {
      errors.push(`${path}.pattern 在指定来源时区模式下不能包含时区或偏移符号`);
    }
    return;
  }
  if (options.sourceTimeZone !== null) {
    errors.push(`${path}.sourceTimeZone 在字符串自带偏移模式下必须为空`);
  }
  if (!patternContainsZone) {
    errors.push(`${path}.pattern 在字符串自带偏移模式下必须包含时区或偏移符号`);
  }
};

export const containsUnquotedZonePatternSymbol = (pattern: string) => {
  let quoted = false;
  for (let index = 0; index < pattern.length; index += 1) {
    const symbol = pattern[index];
    if (symbol === "'") {
      if (quoted && pattern[index + 1] === "'") {
        index += 1;
      } else {
        quoted = !quoted;
      }
      continue;
    }
    if (!quoted && 'XxZOVz'.includes(symbol)) return true;
  }
  return false;
};
