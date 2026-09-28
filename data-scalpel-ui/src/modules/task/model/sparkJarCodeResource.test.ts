import { describe, expect, it } from 'vitest';
import { sparkJarReadSnippet, type SparkJarCodeResource } from './sparkJarCodeResource';

const resource: SparkJarCodeResource = { bindingName: 'source_assets', label: '资产', kind: 'MODEL', accessMode: 'READ', fields: [] };

describe('sparkJarReadSnippet', () => {
  it('generates complete read and print statements without requiring imports', () => {
    expect(sparkJarReadSnippet(resource, '', true)).toBe('var source_assets_rows = context.models().read("source_assets");\nsource_assets_rows.show(20, false);\n');
  });
  it('does not redeclare an existing variable', () => {
    expect(sparkJarReadSnippet(resource, 'var source_assets_rows = null; var source_assets_rows_2 = null;'))
      .toContain('var source_assets_rows_3 =');
  });
  it('preserves the complete JDBC identifier and escapes Java strings', () => {
    expect(sparkJarReadSnippet({ ...resource, kind: 'JDBC_TABLE', bindingName: 'a"b', catalog: 'catalog', schema: 'ods', table: 'asset\\table' }, ''))
      .toContain('context.jdbc().readTable("a\\"b", cn.superhuang.datascalpel.sdk.JdbcTableIdentifier.of("catalog", "ods", "asset\\\\table"));');
  });
  it('does not suggest synchronous show for unbounded Kafka input or read a write-only resource', () => {
    expect(sparkJarReadSnippet({ ...resource, kind: 'KAFKA_TOPIC' }, '', true)).toBe('');
    expect(sparkJarReadSnippet({ ...resource, accessMode: 'WRITE' }, '')).toBe('');
    expect(sparkJarReadSnippet({ ...resource, kind: 'JDBC_CONNECTION' }, '')).toBe('');
    expect(sparkJarReadSnippet({ ...resource, kind: 'KAFKA_TOPIC' }, '')).toContain('KafkaStartingOffsets.EARLIEST');
  });
});
