import { describe, expect, it } from 'vitest';
import type { CanvasColumnSchema } from '../canvasTypes';
import { autoMatchedSourceColumn, orderOutputFieldMappings } from './outputFieldMappings';

const column = (name: string): CanvasColumnSchema => ({
  name,
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
});

describe('OutputFieldMappingFields helpers', () => {
  it('matches exact, case-insensitive and camel-snake-equivalent names in priority order', () => {
    expect(autoMatchedSourceColumn('order_id', [column('ORDER_ID'), column('order_id')]))
      .toBe('order_id');
    expect(autoMatchedSourceColumn('ORDER_ID', [column('order_id')])).toBe('order_id');
    expect(autoMatchedSourceColumn('orderId', [column('order_id')])).toBe('order_id');
  });

  it('does not choose when the active matching level is ambiguous', () => {
    expect(autoMatchedSourceColumn('order__id', [column('order_id'), column('orderId')]))
      .toBeNull();
  });

  it('orders valid mappings by target schema and retains missing targets at the end', () => {
    expect(orderOutputFieldMappings(
      [column('id'), column('name')],
      [
        { sourceColumnName: 'legacy', targetColumnName: 'removed' },
        { sourceColumnName: 'source_name', targetColumnName: 'name' },
        { sourceColumnName: 'source_id', targetColumnName: 'id' },
      ],
    )).toEqual([
      { sourceColumnName: 'source_id', targetColumnName: 'id' },
      { sourceColumnName: 'source_name', targetColumnName: 'name' },
      { sourceColumnName: 'legacy', targetColumnName: 'removed' },
    ]);
  });
});
