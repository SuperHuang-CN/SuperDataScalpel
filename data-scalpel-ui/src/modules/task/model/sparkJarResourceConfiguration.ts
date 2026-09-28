import type { TableIdentifier } from '../../datasource';
import type { SparkJarDevelopmentKit, SparkJarResourceBinding } from './task';

export type SparkJarBindingDraft = Omit<SparkJarResourceBinding, 'resourceName'>;
export interface SparkJarResourceSelection {
  binding: SparkJarBindingDraft;
  table: TableIdentifier | null;
}

/** Shared by task configuration and the editor. Does not mutate the caller's draft. */
export const replaceSparkJarResource = (
  bindings: SparkJarBindingDraft[],
  configuration: SparkJarDevelopmentKit['configuration'],
  index: number,
  selection: SparkJarResourceSelection,
) => {
  if (index < 0 || index > bindings.length) throw new Error('资源位置已失效，请重新打开编辑');
  const previous = bindings[index];
  const { binding, table } = selection;
  const nextBindings = [...bindings];
  nextBindings[index] = binding;
  const oldName = previous?.bindingName ?? binding.bindingName;
  const unaffected = (item: { bindingName: string }) => item.bindingName !== oldName && item.bindingName !== binding.bindingName;
  const oldSamples = configuration.samples.filter((item) => item.bindingName === oldName);
  const oldTables = configuration.jdbcTables.filter((item) => item.bindingName === oldName);
  const samples = configuration.samples.filter(unaffected);
  const jdbcTables = configuration.jdbcTables.filter(unaffected);
  const canRead = binding.accessMode !== 'WRITE';
  const defaultSample = { mode: 'ROW_COUNT' as const, rowCount: 1_000 };
  if (canRead && binding.resourceType === 'MODEL') {
    samples.push({ ...(oldSamples[0] ?? defaultSample), bindingName: binding.bindingName });
  }
  const sameJdbcSource = previous?.resourceType === 'JDBC_DATA_SOURCE'
    && binding.resourceType === 'JDBC_DATA_SOURCE' && previous.resourceId === binding.resourceId;
  if (canRead && binding.resourceType === 'JDBC_DATA_SOURCE' && table) {
    jdbcTables.push({ ...(oldTables[0] ?? defaultSample), ...table, bindingName: binding.bindingName });
    // Existing multi-table bindings remain supported; replacing the first table does not drop the rest.
    if (sameJdbcSource) jdbcTables.push(...oldTables.slice(1)
      .filter((item) => !(item.table === table.table && (item.catalog ?? null) === (table.catalog ?? null)
        && (item.schema ?? null) === (table.schema ?? null)))
      .map((item) => ({ ...item, bindingName: binding.bindingName })));
  }
  return {
    bindings: nextBindings,
    configuration: { samples, jdbcTables },
    discardedTableCount: !sameJdbcSource || !canRead ? oldTables.length : 0,
  };
};
