import type { DataSourceNamespace } from './dataSource';

export const namespaceKey = (namespace: Pick<DataSourceNamespace, 'catalog' | 'schema'>): string => (
  JSON.stringify([namespace.catalog ?? '', namespace.schema ?? ''])
);

export const defaultNamespaceKey = (namespaces: DataSourceNamespace[]): string | undefined => {
  const selected = namespaces.find((namespace) => namespace.defaultNamespace) ?? namespaces[0];
  return selected ? namespaceKey(selected) : undefined;
};
