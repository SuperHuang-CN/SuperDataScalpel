import { Button, Drawer, Form, Input, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useCreateSystemRole, useUpdateSystemRole } from '../hooks/useSystemAccess';
import type { CreateSystemRoleRequest, SystemRole, UpdateSystemRoleRequest } from '../model/systemAccess';

interface SystemRoleDrawerProps {
  open: boolean;
  role: SystemRole | null;
  onClose: () => void;
}

interface RoleFormValues {
  code: string;
  name: string;
  description?: string;
}

export const SystemRoleDrawer = ({ open, role, onClose }: SystemRoleDrawerProps) => {
  const [form] = Form.useForm<RoleFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const createMutation = useCreateSystemRole();
  const updateMutation = useUpdateSystemRole();
  const isEditing = Boolean(role);

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue(role ? { code: role.code, name: role.name, description: role.description ?? undefined } : {
      code: '', name: '', description: undefined,
    });
    form.setFields([{ name: 'code', errors: [] }]);
  }, [form, open, role]);

  const submit = async (values: RoleFormValues) => {
    try {
      if (role) {
        const request: UpdateSystemRoleRequest = { name: values.name.trim(), description: values.description?.trim() || undefined };
        await updateMutation.mutateAsync({ id: role.id, request });
        messageApi.success('角色已保存');
      } else {
        const request: CreateSystemRoleRequest = {
          code: values.code.trim(),
          name: values.name.trim(),
          description: values.description?.trim() || undefined,
        };
        await createMutation.mutateAsync(request);
        messageApi.success('角色已创建，请继续配置权限');
      }
      onClose();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存角色失败');
    }
  };

  return (
    <>
      {messageContext}
      <Drawer
        title={isEditing ? '修改角色' : '新建角色'}
        open={open}
        onClose={onClose}
        destroyOnHidden
        footer={<Button type="primary" loading={createMutation.isPending || updateMutation.isPending} onClick={() => form.submit()}>保存</Button>}
      >
        <Form form={form} layout="vertical" requiredMark={false} onFinish={submit}>
          <Form.Item
            name="code"
            label="角色编码"
            rules={[
              { required: true, whitespace: true, message: '请输入角色编码' },
              { pattern: /^[A-Za-z][A-Za-z0-9_.-]{0,63}$/, message: '以字母开头，可使用字母、数字、点、下划线和连字符' },
            ]}
          >
            <Input disabled={isEditing} placeholder="如 data_operator" />
          </Form.Item>
          <Form.Item name="name" label="角色名称" rules={[{ required: true, whitespace: true, message: '请输入角色名称' }]}>
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input.TextArea rows={3} maxLength={500} showCount />
          </Form.Item>
        </Form>
      </Drawer>
    </>
  );
};
