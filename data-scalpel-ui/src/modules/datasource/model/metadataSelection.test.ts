import { describe, expect, it } from 'vitest';
import { defaultNamespaceKey, namespaceKey } from './metadataSelection';

describe('metadata namespace selection', () => {
  it('selects the declared default namespace', () => {
    const namespaces = [
      { catalog: 'db', schema: 'archive', displayName: 'archive', defaultNamespace: false },
      { catalog: 'db', schema: 'public', displayName: 'public', defaultNamespace: true },
    ];

    expect(defaultNamespaceKey(namespaces)).toBe(namespaceKey(namespaces[1]));
  });

  it('falls back to the first namespace', () => {
    const namespace = { catalog: 'db', schema: null, displayName: 'db', defaultNamespace: false };
    expect(defaultNamespaceKey([namespace])).toBe(namespaceKey(namespace));
  });
});
