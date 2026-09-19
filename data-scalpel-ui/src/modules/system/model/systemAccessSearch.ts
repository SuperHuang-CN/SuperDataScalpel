import type { KeywordFilter } from './systemAccess';

const escapeDslText = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

const contains = (field: string, value: string) => `${field}:*"${escapeDslText(value)}"*`;

const buildKeywordSearch = (keyword: string | undefined, fields: string[]): string | undefined => {
  const value = keyword?.trim();
  if (!value) return undefined;
  const conditions = fields.map((field) => contains(field, value));
  return conditions.length === 1 ? conditions[0] : `(${conditions.join(' OR ')})`;
};

export const buildSystemUserSearch = (filters: KeywordFilter): string | undefined => (
  buildKeywordSearch(filters.keyword, ['username', 'displayName'])
);

export const buildSystemRoleSearch = (filters: KeywordFilter): string | undefined => (
  buildKeywordSearch(filters.keyword, ['code', 'name'])
);

export const buildSystemPermissionSearch = (filters: KeywordFilter): string | undefined => (
  buildKeywordSearch(filters.keyword, ['code', 'module', 'name', 'description'])
);
