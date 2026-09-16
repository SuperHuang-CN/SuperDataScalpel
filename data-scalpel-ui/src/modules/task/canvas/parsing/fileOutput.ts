import { type JdbcWriteMode, type FileOutputConflictPolicy, type FileOutputFormatOptions } from "../canvasTypes";
import { isRecord, stringValue } from './scalars';

export const parseWriteMode = (value: unknown, path: string, errors: string[]): JdbcWriteMode | null => {
  if (value === null || value === undefined || value === '') return null;
  if (value === 'APPEND' || value === 'OVERWRITE' || value === 'UPSERT') return value;
  errors.push(`${path} 不是受支持的写入模式`);
  return null;
};

export const parseFileOutputConflictPolicy = (
  value: unknown,
  path: string,
  errors: string[],
): FileOutputConflictPolicy => {
  if (value === 'FAIL_IF_EXISTS' || value === 'OVERWRITE') return value;
  errors.push(`${path} 仅支持 FAIL_IF_EXISTS 或 OVERWRITE`);
  return 'FAIL_IF_EXISTS';
};

export const parseFileOutputFormatOptions = (
  value: unknown,
  path: string,
  errors: string[],
): FileOutputFormatOptions => {
  const fallback: FileOutputFormatOptions = {
    type: 'CSV',
    header: true,
    delimiter: ',',
    quote: '"',
    escape: '\\',
    nullValue: '',
  };
  if (!isRecord(value)) {
    errors.push(`${path} 必须是文件格式配置对象`);
    return fallback;
  }
  if (value.type === 'CSV') {
    if (typeof value.header !== 'boolean') errors.push(`${path}.header 必须是布尔值`);
    for (const field of ['delimiter', 'quote', 'escape'] as const) {
      if (typeof value[field] !== 'string' || [...value[field]].length !== 1
        || value[field].includes('\r') || value[field].includes('\n')) {
        errors.push(`${path}.${field} 必须是一个非换行字符`);
      }
    }
    if (typeof value.nullValue !== 'string') errors.push(`${path}.nullValue 必须是字符串`);
    return {
      type: 'CSV',
      header: typeof value.header === 'boolean' ? value.header : true,
      delimiter: typeof value.delimiter === 'string' ? value.delimiter : ',',
      quote: typeof value.quote === 'string' ? value.quote : '"',
      escape: typeof value.escape === 'string' ? value.escape : '\\',
      nullValue: typeof value.nullValue === 'string' ? value.nullValue : '',
    };
  }
  if (value.type === 'JSON_LINES') {
    if (typeof value.ignoreNullFields !== 'boolean') {
      errors.push(`${path}.ignoreNullFields 必须是布尔值`);
    }
    return {
      type: 'JSON_LINES',
      ignoreNullFields: typeof value.ignoreNullFields === 'boolean'
        ? value.ignoreNullFields : false,
    };
  }
  if (value.type === 'PARQUET') return { type: 'PARQUET' };
  if (value.type === 'SHAPEFILE') {
    const packageMode = value.packageMode === 'ZIP' || value.packageMode === 'COMPONENT_DIRECTORY'
      ? value.packageMode : 'ZIP';
    if (value.packageMode !== 'ZIP' && value.packageMode !== 'COMPONENT_DIRECTORY') {
      errors.push(`${path}.packageMode 仅支持 ZIP 或 COMPONENT_DIRECTORY`);
    }
    const targetShapeType = value.targetShapeType === 'POINT'
      || value.targetShapeType === 'MULTIPOINT'
      || value.targetShapeType === 'POLYLINE'
      || value.targetShapeType === 'POLYGON'
      ? value.targetShapeType : 'POINT';
    if (!['POINT', 'MULTIPOINT', 'POLYLINE', 'POLYGON'].includes(String(value.targetShapeType))) {
      errors.push(`${path}.targetShapeType 不是受支持的 Shape 类型`);
    }
    if (typeof value.baseName !== 'string') errors.push(`${path}.baseName 必须是字符串`);
    if (typeof value.geometryColumnName !== 'string') {
      errors.push(`${path}.geometryColumnName 必须是字符串`);
    }
    const attributeMappings = Array.isArray(value.attributeMappings)
      ? value.attributeMappings.flatMap((item, index) => {
        if (!isRecord(item)) {
          errors.push(`${path}.attributeMappings[${index}] 必须是对象`);
          return [];
        }
        if (typeof item.sourceColumnName !== 'string') {
          errors.push(`${path}.attributeMappings[${index}].sourceColumnName 必须是字符串`);
        }
        if (typeof item.targetFieldName !== 'string') {
          errors.push(`${path}.attributeMappings[${index}].targetFieldName 必须是字符串`);
        }
        if (item.targetStringByteLength !== null
            && !Number.isInteger(item.targetStringByteLength)) {
          errors.push(
            `${path}.attributeMappings[${index}].targetStringByteLength 必须是 null 或整数`,
          );
        }
        return [{
          sourceColumnName: stringValue(item.sourceColumnName),
          targetFieldName: stringValue(item.targetFieldName),
          targetStringByteLength: Number.isInteger(item.targetStringByteLength)
            ? Number(item.targetStringByteLength) : null,
        }];
      })
      : [];
    if (!Array.isArray(value.attributeMappings)) {
      errors.push(`${path}.attributeMappings 必须是数组`);
    }
    return {
      type: 'SHAPEFILE',
      baseName: stringValue(value.baseName),
      packageMode,
      geometryColumnName: stringValue(value.geometryColumnName),
      targetShapeType,
      attributeMappings,
    };
  }
  if (value.type === 'GEOPARQUET') {
    const compression = value.compression === 'SNAPPY' || value.compression === 'ZSTD'
      ? value.compression : 'SNAPPY';
    const coveringMode = value.coveringMode === 'NONE' || value.coveringMode === 'ROW_BBOX'
      ? value.coveringMode : 'ROW_BBOX';
    if (typeof value.geometryColumnName !== 'string') {
      errors.push(`${path}.geometryColumnName 必须是字符串`);
    }
    if (value.compression !== 'SNAPPY' && value.compression !== 'ZSTD') {
      errors.push(`${path}.compression 仅支持 SNAPPY 或 ZSTD`);
    }
    if (value.coveringMode !== 'NONE' && value.coveringMode !== 'ROW_BBOX') {
      errors.push(`${path}.coveringMode 仅支持 NONE 或 ROW_BBOX`);
    }
    return {
      type: 'GEOPARQUET',
      geometryColumnName: stringValue(value.geometryColumnName),
      compression,
      coveringMode,
    };
  }
  if (value.type === 'GEOJSON') {
    if (typeof value.baseName !== 'string') errors.push(`${path}.baseName 必须是字符串`);
    if (typeof value.geometryColumnName !== 'string') {
      errors.push(`${path}.geometryColumnName 必须是字符串`);
    }
    if (value.idColumnName !== null && typeof value.idColumnName !== 'string') {
      errors.push(`${path}.idColumnName 必须是 null 或字符串`);
    }
    if (typeof value.ignoreNullProperties !== 'boolean') {
      errors.push(`${path}.ignoreNullProperties 必须是布尔值`);
    }
    return {
      type: 'GEOJSON',
      baseName: stringValue(value.baseName),
      geometryColumnName: stringValue(value.geometryColumnName),
      idColumnName: value.idColumnName === null ? null : stringValue(value.idColumnName),
      ignoreNullProperties: typeof value.ignoreNullProperties === 'boolean'
        ? value.ignoreNullProperties : false,
    };
  }
  errors.push(
    `${path}.type 仅支持 CSV、JSON_LINES、PARQUET、SHAPEFILE、GEOPARQUET 或 GEOJSON`,
  );
  return fallback;
};
