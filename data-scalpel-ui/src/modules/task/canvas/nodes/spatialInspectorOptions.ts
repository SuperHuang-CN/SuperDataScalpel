import type { CanvasColumnSchema, CanvasTableSchema } from '../canvasTypes';

interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export const spatialTableOptions = (
  tables: CanvasTableSchema[],
  current: string,
): SelectOption[] => [
  ...(current && !tables.some((table) => table.name === current)
    ? [{ value: current, label: `${current}（已失效）`, disabled: true }]
    : []),
  ...tables.map((table) => ({ value: table.name, label: table.name })),
];

export const spatialColumnOptions = (
  columns: CanvasColumnSchema[],
  current: string,
  predicate: (column: CanvasColumnSchema) => boolean,
): SelectOption[] => {
  const candidates = columns.filter(predicate);
  return [
    ...(current && !candidates.some((column) => column.name === current)
      ? [{ value: current, label: `${current}（已失效）`, disabled: true }]
      : []),
    ...candidates.map((column) => ({
      value: column.name,
      label: column.fieldType === 'GEOMETRY'
        ? `${column.name} · ${column.geometry?.kind ?? 'GEOMETRY'} · EPSG:${column.geometry?.crs.code ?? '-'}`
        : `${column.name} · ${column.fieldType}`,
    })),
  ];
};

export const spatialGeometryColumns = (table: CanvasTableSchema | undefined) => (
  table?.columns.filter((column) => column.fieldType === 'GEOMETRY') ?? []
);
