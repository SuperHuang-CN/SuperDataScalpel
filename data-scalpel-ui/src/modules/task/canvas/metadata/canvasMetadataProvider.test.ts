import { describe, expect, it } from 'vitest';
import { canvasMetadataProviderRegistry } from './canvasMetadataProvider';

describe('canvasMetadataProviderRegistry', () => {
  it('deduplicates resources without losing resource-kind isolation', () => {
    const references = canvasMetadataProviderRegistry.deduplicate([
      {
        kind: 'JDBC_TABLE',
        nodeId: 'input-a',
        role: 'SOURCE',
        dataSourceId: 'source-1',
        tableName: 'orders',
      },
      {
        kind: 'JDBC_TABLE',
        nodeId: 'input-b',
        role: 'SOURCE',
        dataSourceId: 'source-1',
        tableName: 'orders',
      },
      {
        kind: 'KAFKA_TOPIC',
        nodeId: 'stream-a',
        role: 'SOURCE',
        dataSourceId: 'source-1',
        topic: 'orders',
      },
    ]);

    expect(references).toHaveLength(2);
    expect(references.map((reference) => reference.kind)).toEqual([
      'JDBC_TABLE',
      'KAFKA_TOPIC',
    ]);
  });
});
