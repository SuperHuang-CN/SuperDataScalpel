import { describe, expect, it } from 'vitest';
import type { CanvasNodeValidationResult } from '../canvasTypes';
import { suggestJoinOutputColumns } from './joinOutputColumns';

type CanvasTable = CanvasNodeValidationResult['inputTables'][number];

const table = (name: string, columns: string[]): CanvasTable => ({
  name,
  origin: null,
  columns: columns.map((columnName) => ({
    name: columnName,
    fieldType: 'STRING',
    length: null,
    precision: null,
    scale: null,
    nullable: true,
    defaultValue: null,
    autoIncrement: false,
    generated: false,
    comment: null,
    geometry: null,
  })),
  datasetKind: 'BOUNDED',
  eventTimeColumn: null,
  watermarkDelay: null,
});

describe('join output column suggestions', () => {
  it('keeps left names and prefixes colliding right fields with the full logical table name', () => {
    const result = suggestJoinOutputColumns(
      table('orders', ['id', 'name', 'amount']),
      table('sys_dept', ['id', 'name', 'code']),
    );

    expect(result.map((column) => [
      column.sourceSide,
      column.sourceColumnName,
      column.outputColumnName,
    ])).toEqual([
      ['LEFT', 'id', 'id'],
      ['LEFT', 'name', 'name'],
      ['LEFT', 'amount', 'amount'],
      ['RIGHT', 'id', 'sys_dept_id'],
      ['RIGHT', 'name', 'sys_dept_name'],
      ['RIGHT', 'code', 'code'],
    ]);
  });

  it('does not append a hidden numeric suffix when a suggested name still collides', () => {
    const result = suggestJoinOutputColumns(
      table('orders', ['id', 'sys_dept_id']),
      table('sys_dept', ['id']),
    );

    expect(result.at(-1)?.outputColumnName).toBe('sys_dept_id');
  });
});
