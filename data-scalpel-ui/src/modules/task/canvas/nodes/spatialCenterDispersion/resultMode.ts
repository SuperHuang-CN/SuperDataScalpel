import type { SpatialCenterDispersionAnalysis, SpatialCenterDispersionConfiguration } from '../../canvasTypes';

export function parseCenterResultMode(raw: Record<string, unknown>, path: string, errors: string[]): Pick<SpatialCenterDispersionConfiguration, 'resultMode'> {
  if (!('resultMode' in raw)) return {};
  if (raw.resultMode == null) return { resultMode: null };
  if (raw.resultMode === 'ANALYSIS_TABLES' || raw.resultMode === 'LEGACY_WIDE') return { resultMode: raw.resultMode };
  errors.push(`${path}.resultMode 无效`); return {};
}
export function parseCenterOutputTable(raw: Record<string, unknown>, path: string, errors: string[]): Pick<SpatialCenterDispersionAnalysis, 'outputTableName'> {
  if (!('outputTableName' in raw)) return {};
  if (raw.outputTableName == null) return { outputTableName: null };
  if (typeof raw.outputTableName === 'string') return { outputTableName: raw.outputTableName };
  errors.push(`${path}.outputTableName 必须是字符串或 null`); return {};
}

export function parseCenterFeatureColumns(raw: Record<string, unknown>, path: string, errors: string[]): Pick<SpatialCenterDispersionAnalysis, 'centralFeatureColumns'> {
  if (!('centralFeatureColumns' in raw)) return {};
  if (raw.centralFeatureColumns == null) return { centralFeatureColumns: null };
  if (!Array.isArray(raw.centralFeatureColumns)) { errors.push(`${path}.centralFeatureColumns 必须是数组或 null`); return {}; }
  const fields: NonNullable<SpatialCenterDispersionAnalysis['centralFeatureColumns']> = [];
  raw.centralFeatureColumns.forEach((field: unknown, index) => {
    if (typeof field !== 'object' || field == null || Array.isArray(field)
        || !('sourceColumnName' in field) || typeof field.sourceColumnName !== 'string'
        || !('outputColumnName' in field) || typeof field.outputColumnName !== 'string'
        || !('included' in field) || typeof field.included !== 'boolean') {
      errors.push(`${path}.centralFeatureColumns[${index}] 必须包含字符串字段名和布尔 included`); return;
    }
    fields.push({ sourceColumnName: field.sourceColumnName, outputColumnName: field.outputColumnName, included: field.included });
  });
  return { centralFeatureColumns: fields };
}
