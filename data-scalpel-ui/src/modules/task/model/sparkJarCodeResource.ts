export interface SparkJarCodeResource {
  bindingName: string;
  label: string;
  kind: 'MODEL' | 'JDBC_TABLE' | 'JDBC_CONNECTION' | 'KAFKA_TOPIC';
  accessMode: 'READ' | 'WRITE' | 'READ_WRITE';
  table?: string;
  catalog?: string | null;
  schema?: string | null;
  fields: Array<{ name: string; type: string; nullable: boolean }>;
  fieldsLoading?: boolean;
}

/** Inserts a complete statement. Reading is deferred until a Spark action is requested. */
export const sparkJarReadSnippet = (resource: SparkJarCodeResource, source: string, print = false): string => {
  if (resource.accessMode === 'WRITE' || resource.kind === 'JDBC_CONNECTION' || print && resource.kind === 'KAFKA_TOPIC') return '';
  const quote = (value: string | null | undefined) => value == null ? 'null' : JSON.stringify(value);
  const base = `${resource.bindingName.replace(/[^A-Za-z0-9_]/g, '_')}_rows`;
  const safeBase = /^[A-Za-z_]/.test(base) ? base : `data_${base}`;
  let variable = safeBase;
  for (let index = 2; new RegExp(`\\b${variable}\\b`).test(source); index++) variable = `${safeBase}_${index}`;
  const binding = quote(resource.bindingName);
  const expression = resource.kind === 'MODEL'
    ? `context.models().read(${binding})`
    : resource.kind === 'KAFKA_TOPIC'
      ? `context.kafka().readStream(${binding}, cn.superhuang.datascalpel.sdk.KafkaStartingOffsets.EARLIEST)`
      : `context.jdbc().readTable(${binding}, cn.superhuang.datascalpel.sdk.JdbcTableIdentifier.of(${quote(resource.catalog)}, ${quote(resource.schema)}, ${quote(resource.table)}))`;
  return `var ${variable} = ${expression};\n${print ? `${variable}.show(20, false);\n` : ''}`;
};
