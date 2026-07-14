import { describe, expect, it } from 'vitest';
import { groupSystemPermissions } from './systemPermissionGrouping';
import type { SystemPermission } from './systemAccess';

const permission = (id: string, module: string): SystemPermission => ({
  id,
  module,
  code: id,
  name: id,
  description: null,
  sortOrder: 0,
  active: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
});

describe('groupSystemPermissions', () => {
  it('keeps server ordering while collecting permissions under their module', () => {
    const groups = groupSystemPermissions([
      permission('system.user.view', '系统管理'),
      permission('system.role.view', '系统管理'),
      permission('datasource.view', '数据源管理'),
      permission('directory.view', '通用目录'),
      permission('datasource.create', '数据源管理'),
    ]);

    expect(groups.map((group) => group.module)).toEqual(['系统管理', '数据源管理', '通用目录']);
    expect(groups[1]?.permissions.map((item) => item.code)).toEqual(['datasource.view', 'datasource.create']);
  });
});
