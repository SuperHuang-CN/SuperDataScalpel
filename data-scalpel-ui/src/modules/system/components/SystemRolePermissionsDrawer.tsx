import { Button, Checkbox, Drawer, Empty, Space, Spin, message } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useUpdateSystemRolePermissions } from '../hooks/useSystemAccess';
import type { SystemPermission, SystemRole } from '../model/systemAccess';

interface SystemRolePermissionsDrawerProps {
  open: boolean;
  role: SystemRole | null;
  permissions: SystemPermission[];
  loading: boolean;
  onClose: () => void;
}

export const SystemRolePermissionsDrawer = ({
  open, role, permissions, loading, onClose,
}: SystemRolePermissionsDrawerProps) => (
  role ? (
    <RolePermissionsEditor
      key={role.id}
      open={open}
      role={role}
      permissions={permissions}
      loading={loading}
      onClose={onClose}
    />
  ) : null
);

interface RolePermissionsEditorProps extends Omit<SystemRolePermissionsDrawerProps, 'role'> {
  role: SystemRole;
}

const RolePermissionsEditor = ({
  open, role, permissions, loading, onClose,
}: RolePermissionsEditorProps) => {
  const [selectedPermissionIds, setSelectedPermissionIds] = useState<string[]>(role.permissionIds);
  const [messageApi, messageContext] = message.useMessage();
  const updateMutation = useUpdateSystemRolePermissions();
  const permissionsByModule = useMemo(() => permissions.reduce<Map<string, SystemPermission[]>>((result, permission) => {
    const group = result.get(permission.module) ?? [];
    group.push(permission);
    result.set(permission.module, group);
    return result;
  }, new Map()), [permissions]);

  const update = async () => {
    try {
      await updateMutation.mutateAsync({ id: role.id, request: { permissionIds: selectedPermissionIds } });
      messageApi.success('角色权限已保存');
      onClose();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存角色权限失败');
    }
  };

  return (
    <>
      {messageContext}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        title={`配置权限：${role.name}`}
        open={open}
        onClose={onClose}
        destroyOnHidden
        width={560}
        footer={(
          <Space>
            <Button onClick={onClose}>取消</Button>
            <Button type="primary" loading={updateMutation.isPending} onClick={() => void update()}>保存权限</Button>
          </Space>
        )}
      >
        <Spin spinning={loading}>
          {permissionsByModule.size === 0 && !loading ? <Empty description="暂无可配置权限" /> : (
            <div className="role-permission-groups">
              {[...permissionsByModule.entries()].map(([module, modulePermissions]) => (
                <section key={module} className="role-permission-group">
                  <div className="role-permission-group-title">{module}</div>
                  <Checkbox.Group
                    value={selectedPermissionIds}
                    onChange={(values) => setSelectedPermissionIds(values as string[])}
                    className="role-permission-options"
                  >
                    {modulePermissions.map((permission) => (
                      <Checkbox key={permission.id} value={permission.id}>
                        <span>{permission.name}</span>
                        <span className="role-permission-description">{permission.description}</span>
                      </Checkbox>
                    ))}
                  </Checkbox.Group>
                </section>
              ))}
            </div>
          )}
        </Spin>
      </Drawer>
    </>
  );
};
