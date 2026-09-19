import type { SpatialSummarizeWithinConfiguration, SpatialWithinGroupResult } from '../../canvasTypes';

export const usesLinkedWithinGroups = (configuration: SpatialSummarizeWithinConfiguration) => Boolean(
  configuration.groupSummary && configuration.groupResult && configuration.groupResult.mode !== 'LEGACY_FLAT',
);

export const createWithinGroupResult = (): SpatialWithinGroupResult => ({
  mode: 'LINKED_TABLES',
  areaKeyColumnName: '', areaKeyOutputColumnName: 'summary_area_id', outputTableName: '',
  groupValueColumnName: 'group_value', minorityValueColumnName: 'minority_group',
  majorityValueColumnName: 'majority_group', minorityPercentageColumnName: 'minority_percentage',
  majorityPercentageColumnName: 'majority_percentage',
});

export const parseWithinGroupResult = (value: unknown, path: string, errors: string[]): {
  groupResult?: SpatialWithinGroupResult | null;
} => {
  if (value === undefined) return {};
  if (value === null) return { groupResult: null };
  if (typeof value !== 'object' || Array.isArray(value)) {
    errors.push(`${path} 必须是对象或 null`); return {};
  }
  const object = value as Record<string, unknown>;
  if (object.mode != null && object.mode !== 'LINKED_TABLES' && object.mode !== 'LEGACY_FLAT') errors.push(`${path}.mode 不是受支持的分组模式`);
  const required = (field: string) => {
    if (object[field] !== undefined && typeof object[field] !== 'string') errors.push(`${path}.${field} 必须是字符串`);
    return typeof object[field] === 'string' ? object[field] : '';
  };
  const optional = (field: string) => {
    if (object[field] != null && typeof object[field] !== 'string') errors.push(`${path}.${field} 必须是字符串或 null`);
    return typeof object[field] === 'string' ? object[field] : null;
  };
  return { groupResult: {
    ...(object.mode !== undefined ? { mode: object.mode === 'LINKED_TABLES' || object.mode === 'LEGACY_FLAT' ? object.mode : null } : {}),
    areaKeyColumnName: required('areaKeyColumnName'), areaKeyOutputColumnName: required('areaKeyOutputColumnName'),
    outputTableName: required('outputTableName'), groupValueColumnName: required('groupValueColumnName'),
    minorityValueColumnName: optional('minorityValueColumnName'), majorityValueColumnName: optional('majorityValueColumnName'),
    minorityPercentageColumnName: optional('minorityPercentageColumnName'), majorityPercentageColumnName: optional('majorityPercentageColumnName'),
  } };
};
