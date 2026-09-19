export const escapeSearchText = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');
export const searchEquals = (field: string, value: string | undefined | null) => value ? `${field}:"${escapeSearchText(value)}"` : undefined;
export const searchContains = (field: string, value: string | undefined) => value?.trim() ? `${field}:*"${escapeSearchText(value.trim())}"*` : undefined;
export const searchComparison = (field: string, operator: '>' | '>=' | '<' | '<=', value?: string) => value ? `${field}${operator}"${escapeSearchText(value)}"` : undefined;
export const andSearch = (...conditions: (string | undefined)[]) => conditions.filter(Boolean).join(' AND ') || undefined;
export const orSearch = (...conditions: (string | undefined)[]) => { const values = conditions.filter(Boolean); return values.length ? `(${values.join(' OR ')})` : undefined; };
