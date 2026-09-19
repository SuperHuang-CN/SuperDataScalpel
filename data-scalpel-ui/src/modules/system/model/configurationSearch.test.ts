import { describe, expect, it } from 'vitest';
import { buildSystemConfigurationSearch } from './configurationSearch';

describe('buildSystemConfigurationSearch', () => {
  it('combines supplied filters with the common Search DSL', () => {
    expect(buildSystemConfigurationSearch({ name: '平台', configKey: 'platform.' }))
      .toBe('name:*"平台"* AND configKey:*"platform."*');
  });

  it('escapes values and omits blank filters', () => {
    expect(buildSystemConfigurationSearch({ name: '名称"A\\B', configKey: '  ' }))
      .toBe('name:*"名称\\"A\\\\B"*');
    expect(buildSystemConfigurationSearch({})).toBeUndefined();
  });
});
