import { SafetyCertificateOutlined } from '@ant-design/icons';
import { Button, Checkbox, Drawer, Empty, Space, Spin, Tag, Typography, message } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
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
  const [operationError, setOperationError] = useState<string | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const updateMutation = useUpdateSystemRolePermissions();
  const permissionsByModule = useMemo(() => permissions.reduce<Map<string, SystemPermission[]>>((result, permission) => {
    const group = result.get(permission.module) ?? [];
    group.push(permission);
    result.set(permission.module, group);
    return result;
  }, new Map()), [permissions]);

  const update = async () => {
    setOperationError(null);
    try {
      await updateMutation.mutateAsync({ id: role.id, request: { permissionIds: selectedPermissionIds } });
      messageApi.success('角色权限已保存');
      onClose();
    } catch (error) {
      const errorMessage = error instanceof ApiError ? error.message : '保存角色权限失败';
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
    }
  };

  const close = () => {
    if (updateMutation.isPending) return;
    setOperationError(null);
    onClose();
  };

  return (
    <>
      {messageContext}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        className="system-role-permissions-drawer"
        title={(
          <div className="system-role-permissions-title">
            <span className="system-role-permissions-title-icon" aria-hidden="true"><SafetyCertificateOutlined /></span>
            <span className="system-role-permissions-title-copy">
              <span>配置角色权限</span>
              <Typography.Text type="secondary">{role.name} · 按业务模块分配可访问功能</Typography.Text>
            </span>
          </div>
        )}
        extra={<Tag className="system-role-permissions-header-tag">已选 {selectedPermissionIds.length} 项</Tag>}
        open={open}
        onClose={close}
        closable={!updateMutation.isPending}
        maskClosable={!updateMutation.isPending}
        destroyOnHidden
        width={560}
        footer={(
          <div className="system-role-permissions-footer">
            {operationError ? (
              <InlineFeedback tone="error" label="权限保存失败" detail={operationError} />
            ) : (
              <InlineFeedback tone="info" label={`将为 ${role.name} 保存 ${selectedPermissionIds.length} 项权限`} />
            )}
            <Space>
              <Button disabled={updateMutation.isPending} onClick={close}>取消</Button>
              <Button type="primary" loading={updateMutation.isPending} onClick={() => void update()}>保存权限</Button>
            </Space>
          </div>
        )}
      >
        <Spin spinning={loading}>
          {permissionsByModule.size === 0 && !loading ? <Empty description="暂无可配置权限" /> : (
            <div className="role-permission-groups system-role-permission-groups">
              {[...permissionsByModule.entries()].map(([module, modulePermissions]) => (
                <section key={module} className="role-permission-group">
                  <header className="role-permission-group-title">
                    <span>{module}</span>
                    <Typography.Text type="secondary">{modulePermissions.filter((permission) => selectedPermissionIds.includes(permission.id)).length} / {modulePermissions.length}</Typography.Text>
                  </header>
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
