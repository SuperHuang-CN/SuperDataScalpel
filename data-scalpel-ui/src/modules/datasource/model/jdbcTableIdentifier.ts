import type { TableIdentifier } from './dataSource';

export const jdbcTableIdentifierKey = (identifier: TableIdentifier): string => JSON.stringify({
  catalog: identifier.catalog ?? null,
  schema: identifier.schema ?? null,
  table: identifier.table,
});

/** UI labels intentionally hide the data source's fixed catalog and schema. */
export const jdbcTableIdentifierDisplayName = (identifier: TableIdentifier): string => {
  const table = identifier.table.trim();
  const qualifier = [identifier.catalog, identifier.schema]
    .filter((part): part is string => Boolean(part))
    .join('.');

  if (qualifier && table.startsWith(`${qualifier}.`)) return table.slice(qualifier.length + 1);
  return table.includes('.') ? table.slice(table.lastIndexOf('.') + 1) : table;
};
