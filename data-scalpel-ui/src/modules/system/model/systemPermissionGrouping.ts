import type { SystemPermission } from './systemAccess';

export interface SystemPermissionGroup {
  module: string;
  permissions: SystemPermission[];
}

/** Groups the already server-sorted permission catalogue without changing its order. */
export const groupSystemPermissions = (permissions: SystemPermission[]): SystemPermissionGroup[] => {
  const groups = new Map<string, SystemPermission[]>();
  permissions.forEach((permission) => {
    const modulePermissions = groups.get(permission.module) ?? [];
    modulePermissions.push(permission);
    groups.set(permission.module, modulePermissions);
  });
  return [...groups.entries()].map(([module, modulePermissions]) => ({ module, permissions: modulePermissions }));
};
