import { describe, expect, it } from 'vitest';
import { replaceSparkJarResource, type SparkJarBindingDraft } from './sparkJarResourceConfiguration';
import type { SparkJarDevelopmentKit } from './task';

const jdbc: SparkJarBindingDraft = {
  bindingName: 'source', resourceType: 'JDBC_DATA_SOURCE', resourceId: 'pg', accessMode: 'READ', topicName: null,
};
const configuration: SparkJarDevelopmentKit['configuration'] = {
  samples: [{ bindingName: 'other_model', mode: 'NONE' }],
  jdbcTables: [
    { bindingName: 'source', catalog: null, schema: 'public', table: 'a', mode: 'ROW_COUNT', rowCount: 27 },
    { bindingName: 'source', catalog: null, schema: 'public', table: 'b', mode: 'PERCENTAGE', percentage: 12 },
  ],
};

describe('replaceSparkJarResource', () => {
  it('renames the binding without dropping other JDBC tables or changing sample limits', () => {
    const snapshot = structuredClone(configuration);
    const result = replaceSparkJarResource([jdbc], configuration, 0, {
      binding: { ...jdbc, bindingName: 'renamed', accessMode: 'READ_WRITE' },
      table: { catalog: null, schema: 'public', table: 'c' },
    });
    expect(result.configuration.jdbcTables).toEqual([
      { ...configuration.jdbcTables[0], bindingName: 'renamed', table: 'c' },
      { ...configuration.jdbcTables[1], bindingName: 'renamed' },
    ]);
    expect(result.configuration.samples).toEqual(configuration.samples);
    expect(result.discardedTableCount).toBe(0);
    expect(configuration).toEqual(snapshot);
    expect(jdbc.bindingName).toBe('source');
  });

  it('reports removed local table choices when switching connections or removing input', () => {
    const output = replaceSparkJarResource([jdbc], configuration, 0, {
      binding: { ...jdbc, accessMode: 'WRITE' }, table: null,
    });
    expect(output.discardedTableCount).toBe(2);
    expect(output.configuration.jdbcTables).toEqual([]);
    const moved = replaceSparkJarResource([jdbc], configuration, 0, {
      binding: { ...jdbc, resourceId: 'mysql' }, table: { catalog: 'business', schema: null, table: 'new' },
    });
    expect(moved.discardedTableCount).toBe(2);
    expect(moved.configuration.jdbcTables).toHaveLength(1);
    expect(moved.configuration.jdbcTables[0].rowCount).toBe(27);
  });

  it('preserves model samples on rename and adds defaults only for readable models', () => {
    const model: SparkJarBindingDraft = { ...jdbc, resourceType: 'MODEL' };
    const config: SparkJarDevelopmentKit['configuration'] = {
      samples: [{ bindingName: 'source', mode: 'PERCENTAGE', percentage: 2 }], jdbcTables: [],
    };
    const renamed = replaceSparkJarResource([model], config, 0, { binding: { ...model, bindingName: 'renamed' }, table: null });
    expect(renamed.configuration.samples).toEqual([{ bindingName: 'renamed', mode: 'PERCENTAGE', percentage: 2 }]);
    const added = replaceSparkJarResource([], { samples: [], jdbcTables: [] }, 0, { binding: model, table: null });
    expect(added.configuration.samples).toEqual([{ bindingName: 'source', mode: 'ROW_COUNT', rowCount: 1_000 }]);
    const output = replaceSparkJarResource([model], config, 0, { binding: { ...model, accessMode: 'WRITE' }, table: null });
    expect(output.configuration.samples).toEqual([]);
  });

  it('does not duplicate a historical table selected as the first table', () => {
    const result = replaceSparkJarResource([jdbc], configuration, 0, {
      binding: jdbc, table: { catalog: null, schema: 'public', table: 'b' },
    });
    expect(result.configuration.jdbcTables.map((item) => item.table)).toEqual(['b']);
    expect(() => replaceSparkJarResource([], configuration, -1, { binding: jdbc, table: null })).toThrow('资源位置已失效');
  });
});
