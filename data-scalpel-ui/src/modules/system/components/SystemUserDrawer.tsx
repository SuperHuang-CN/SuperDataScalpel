import { Button, Drawer, Form, Input, Select, Switch, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useCreateSystemUser, useUpdateSystemUser } from '../hooks/useSystemAccess';
import type { CreateSystemUserRequest, SystemRole, SystemUser, UpdateSystemUserRequest } from '../model/systemAccess';

interface SystemUserDrawerProps {
  open: boolean;
  user: SystemUser | null;
  roles: SystemRole[];
  onClose: () => void;
}

interface UserFormValues {
  username: string;
  displayName: string;
  password?: string;
  roleId: string;
  enabled: boolean;
}

export const SystemUserDrawer = ({ open, user, roles, onClose }: SystemUserDrawerProps) => {
  const [form] = Form.useForm<UserFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const createMutation = useCreateSystemUser();
  const updateMutation = useUpdateSystemUser();
  const isEditing = Boolean(user);

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue(user ? {
      username: user.username,
      displayName: user.displayName,
      roleId: user.roleId,
      enabled: user.enabled,
      password: undefined,
    } : {
      username: '',
      displayName: '',
      roleId: undefined,
      enabled: true,
      password: undefined,
    });
    form.setFields([{ name: 'username', errors: [] }, { name: 'password', errors: [] }]);
  }, [form, open, user]);

  const submit = async (values: UserFormValues) => {
    try {
      if (user) {
        const request: UpdateSystemUserRequest = {
          displayName: values.displayName.trim(),
          roleId: values.roleId,
          enabled: values.enabled,
        };
        await updateMutation.mutateAsync({ id: user.id, request });
        messageApi.success('用户已保存');
      } else {
        const request: CreateSystemUserRequest = {
          username: values.username.trim(),
          displayName: values.displayName.trim(),
          password: values.password ?? '',
          roleId: values.roleId,
          enabled: values.enabled,
        };
        await createMutation.mutateAsync(request);
        messageApi.success('用户已创建');
      }
      onClose();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存用户失败');
    }
  };

  return (
    <>
      {messageContext}
      <Drawer
        title={isEditing ? '修改用户' : '新建用户'}
        open={open}
        onClose={onClose}
        destroyOnHidden
        footer={<Button type="primary" loading={createMutation.isPending || updateMutation.isPending} onClick={() => form.submit()}>保存</Button>}
      >
        <Form form={form} layout="vertical" requiredMark={false} onFinish={submit}>
          <Form.Item
            name="username"
            label="用户名"
            rules={[
              { required: true, whitespace: true, message: '请输入用户名' },
              { pattern: /^[A-Za-z][A-Za-z0-9_.-]{2,63}$/, message: '以字母开头，可使用字母、数字、点、下划线和连字符' },
            ]}
          >
            <Input disabled={isEditing} placeholder="如 data.operator" />
          </Form.Item>
          <Form.Item name="displayName" label="显示名称" rules={[{ required: true, whitespace: true, message: '请输入显示名称' }]}>
            <Input maxLength={100} />
          </Form.Item>
          {!isEditing && (
            <Form.Item name="password" label="初始密码" rules={[{ required: true, min: 8, message: '密码至少 8 位' }]}>
              <Input.Password autoComplete="new-password" />
            </Form.Item>
          )}
          <Form.Item name="roleId" label="角色" rules={[{ required: true, message: '请选择角色' }]}>
            <Select
              placeholder="请选择角色"
              options={roles.map((role) => ({ value: role.id, label: `${role.name}（${role.code}）` }))}
            />
          </Form.Item>
          <Form.Item name="enabled" label="状态" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="停用" />
          </Form.Item>
        </Form>
      </Drawer>
    </>
  );
};
