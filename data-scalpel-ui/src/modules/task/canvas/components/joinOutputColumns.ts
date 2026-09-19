import type {
  CanvasNodeValidationResult,
  JoinOutputColumn,
} from '../canvasTypes';

type CanvasTable = CanvasNodeValidationResult['inputTables'][number];

export const suggestJoinOutputColumns = (
  left: CanvasTable,
  right: CanvasTable,
): JoinOutputColumn[] => {
  const leftNames = new Set(left.columns.map((column) => column.name.toLocaleLowerCase()));
  return [
    ...left.columns.map((column): JoinOutputColumn => ({
      sourceSide: 'LEFT',
      sourceColumnName: column.name,
      outputColumnName: column.name,
      included: true,
    })),
    ...right.columns.map((column): JoinOutputColumn => ({
      sourceSide: 'RIGHT',
      sourceColumnName: column.name,
      outputColumnName: leftNames.has(column.name.toLocaleLowerCase())
        ? `${right.name}_${column.name}`
        : column.name,
      included: true,
    })),
  ];
};
