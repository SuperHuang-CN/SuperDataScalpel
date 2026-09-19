import { describe, expect, it } from 'vitest';
import { buildSystemPermissionSearch, buildSystemRoleSearch, buildSystemUserSearch } from './systemAccessSearch';

describe('system access search', () => {
  it('builds user and role keyword queries with stable fields', () => {
    expect(buildSystemUserSearch({ keyword: 'data.operator' }))
      .toBe('(username:*"data.operator"* OR displayName:*"data.operator"*)');
    expect(buildSystemRoleSearch({ keyword: '数据管理员' }))
      .toBe('(code:*"数据管理员"* OR name:*"数据管理员"*)');
  });

  it('searches permission catalogue text and escapes DSL syntax', () => {
    expect(buildSystemPermissionSearch({ keyword: '管理"A\\B' }))
      .toBe('(code:*"管理\\"A\\\\B"* OR module:*"管理\\"A\\\\B"* OR name:*"管理\\"A\\\\B"* OR description:*"管理\\"A\\\\B"*)');
    expect(buildSystemPermissionSearch({ keyword: '  ' })).toBeUndefined();
  });
});
