import { describe, expect, it } from 'vitest';
import { activeBatchWrite, batchWriteIssue, parseBatchWrite } from './batchWriteOptions';
import type { CanvasColumnSchema } from '../canvasTypes';

describe('atomic batch write options', () => {
  const column = { name: 'region', fieldType: 'INTEGER', nullable: true } as CanvasColumnSchema;
  const options = { allowEmptyOverwrite: false, overwriteCondition: {
    kind: 'PREDICATE' as const, columnName: 'region', operator: 'EQUALS' as const,
    values: [{ dataType: 'INTEGER' as const, value: '1' }],
  } };
  it('preserves the exact deletion scope through JSON round trip', () => {
    const errors: string[] = [];
    expect(parseBatchWrite(JSON.parse(JSON.stringify(options)), 'batchWrite', errors)).toEqual(options);
    expect(errors).toEqual([]);
    expect(parseBatchWrite(undefined, 'batchWrite', errors)).toBeNull();
  });
  it('rejects empty conditions and unmapped deletion fields', () => {
    expect(batchWriteIssue(options, 'OVERWRITE', [column], [], 'POSTGRESQL')).toContain('未完成映射');
    expect(batchWriteIssue(options, 'OVERWRITE', [column], ['region'], 'POSTGRESQL')).toBeNull();
    expect(batchWriteIssue({ ...options, overwriteCondition: { kind: 'GROUP', operator: 'AND', children: [] } },
      'OVERWRITE', [column], ['region'], 'POSTGRESQL')).not.toBeNull();
  });
  it('never enables unsupported databases and drops inactive overwrite settings explicitly', () => {
    expect(batchWriteIssue(options, 'OVERWRITE', [column], ['region'], 'CLICKHOUSE')).toContain('未开放');
    expect(activeBatchWrite(options, 'APPEND')).toEqual({ allowEmptyOverwrite: false, overwriteCondition: null });
  });
});
