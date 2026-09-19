import type { CanvasColumnMapping, CanvasColumnSchema } from '../canvasTypes';

const canonicalFieldName = (name: string) => name.toLocaleLowerCase('en-US').replaceAll('_', '');

const uniqueMatch = (
  candidates: readonly CanvasColumnSchema[],
  predicate: (column: CanvasColumnSchema) => boolean,
) => {
  const matches = candidates.filter(predicate);
  return matches.length === 1 ? matches[0] : null;
};

export const autoMatchedSourceColumn = (
  targetName: string,
  sourceColumns: readonly CanvasColumnSchema[],
): string | null => {
  const exact = uniqueMatch(sourceColumns, (column) => column.name === targetName);
  if (exact) return exact.name;
  const caseInsensitive = uniqueMatch(
    sourceColumns,
    (column) => column.name.toLocaleLowerCase('en-US') === targetName.toLocaleLowerCase('en-US'),
  );
  if (caseInsensitive) return caseInsensitive.name;
  const canonicalTarget = canonicalFieldName(targetName);
  const canonical = uniqueMatch(
    sourceColumns,
    (column) => canonicalFieldName(column.name) === canonicalTarget,
  );
  return canonical?.name ?? null;
};

export const mappingByTarget = (mappings: readonly CanvasColumnMapping[]) => {
  const indexed = new Map<string, CanvasColumnMapping>();
  mappings.forEach((mapping) => indexed.set(mapping.targetColumnName, mapping));
  return indexed;
};

export const orderedMappings = (
  targetColumns: readonly CanvasColumnSchema[],
  mappings: ReadonlyMap<string, CanvasColumnMapping>,
) => {
  const targetNames = new Set(targetColumns.map((column) => column.name));
  const current = targetColumns.flatMap((column) => {
    const mapping = mappings.get(column.name);
    return mapping ? [mapping] : [];
  });
  const missingTargets = [...mappings.values()]
    .filter((mapping) => !targetNames.has(mapping.targetColumnName));
  return [...current, ...missingTargets];
};

export const orderOutputFieldMappings = (
  targetColumns: readonly CanvasColumnSchema[],
  mappings: readonly CanvasColumnMapping[] | undefined,
) => orderedMappings(targetColumns, mappingByTarget(mappings ?? []));
