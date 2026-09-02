import { IdcardOutlined, SafetyCertificateOutlined, UserAddOutlined } from '@ant-design/icons';
import { Badge, Button, Col, Drawer, Form, Input, Row, Select, Space, Switch, Tag, Typography, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
import { BusinessSecretInput } from '../../../shared/components/BusinessSecretInput';
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
  const enabled = Form.useWatch('enabled', form) ?? true;
  const selectedRoleId = Form.useWatch('roleId', form);
  const selectedRole = roles.find((role) => role.id === selectedRoleId);

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
        rootClassName="business-overlay business-drawer-overlay"
        className="data-model-drawer system-user-drawer"
        title={(
          <div className="data-model-drawer-title">
            <span className="data-model-drawer-title-icon" aria-hidden="true"><UserAddOutlined /></span>
            <span className="data-model-drawer-title-copy">
              <span>{isEditing ? '修改系统用户' : '新建系统用户'}</span>
              <Typography.Text type="secondary">维护登录身份、显示名称与系统访问角色</Typography.Text>
            </span>
          </div>
        )}
        extra={<Tag className="data-model-drawer-header-tag" color={enabled ? 'success' : undefined}>{enabled ? '启用' : '停用'}</Tag>}
        open={open}
        size={720}
        onClose={onClose}
        forceRender
        destroyOnHidden
        footer={(
          <div className="data-model-drawer-footer">
            <Badge
              status={selectedRole ? 'processing' : 'default'}
              text={selectedRole ? `角色 · ${selectedRole.name}` : '尚未分配角色'}
            />
            <Space>
              <Button onClick={onClose}>取消</Button>
              <Button type="primary" loading={createMutation.isPending || updateMutation.isPending} onClick={() => form.submit()}>
                {isEditing ? '保存修改' : '创建用户'}
              </Button>
            </Space>
          </div>
        )}
      >
        <Form
          name="system-user-editor-form"
          className="data-model-form system-user-form"
          autoComplete="off"
          form={form}
          layout="vertical"
          onFinish={(values) => void submit(values)}
        >
          <section className="data-model-form-section">
            <header className="data-model-form-section-header">
              <span className="data-model-form-section-icon" aria-hidden="true"><IdcardOutlined /></span>
              <span className="data-model-form-section-copy">
                <span className="data-model-form-section-title">账号身份</span>
                <Typography.Text type="secondary">用户名用于登录且创建后不可修改</Typography.Text>
              </span>
            </header>
            <div className="data-model-form-section-body">
              <Row gutter={14}>
                <Col xs={24} sm={12}>
                  <Form.Item
                    name="username"
                    label="用户名"
                    rules={[
                      { required: true, whitespace: true, message: '请输入用户名' },
                      { pattern: /^[A-Za-z][A-Za-z0-9_.-]{2,63}$/, message: '以字母开头，可使用字母、数字、点、下划线和连字符' },
                    ]}
                  >
                    <Input name="managed-user-code" autoComplete="off" disabled={isEditing} placeholder="如 data.operator" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item name="displayName" label="显示名称" rules={[{ required: true, whitespace: true, message: '请输入显示名称' }]}>
                    <Input name="managed-user-display-name" autoComplete="off" maxLength={100} placeholder="输入界面展示名称" />
                  </Form.Item>
                </Col>
                {!isEditing && (
                  <Col span={24}>
                    <Form.Item name="password" label="初始密码" rules={[{ required: true, min: 8, message: '密码至少 8 位' }]}>
                      <BusinessSecretInput name="managed-user-initial-secret" autoComplete="off" revealLabel="显示初始密码" hideLabel="隐藏初始密码" placeholder="至少 8 位，创建后可单独重置" />
                    </Form.Item>
                  </Col>
                )}
              </Row>
            </div>
          </section>

          <section className="data-model-form-section">
            <header className="data-model-form-section-header">
              <span className="data-model-form-section-icon" aria-hidden="true"><SafetyCertificateOutlined /></span>
              <span className="data-model-form-section-copy">
                <span className="data-model-form-section-title">访问角色</span>
                <Typography.Text type="secondary">角色决定用户可以访问的系统功能</Typography.Text>
              </span>
              <span className="data-source-enabled-control">
                <span>启用</span>
                <Form.Item name="enabled" valuePropName="checked" noStyle><Switch aria-label="启用系统用户" /></Form.Item>
              </span>
            </header>
            <div className="data-model-form-section-body">
              <Row gutter={14}>
                <Col span={24}>
                  <Form.Item name="roleId" label="角色" rules={[{ required: true, message: '请选择角色' }]}>
                    <Select
                      placeholder="请选择角色"
                      options={roles.map((role) => ({ value: role.id, label: `${role.name}（${role.code}）` }))}
                    />
                  </Form.Item>
                </Col>
              </Row>
            </div>
          </section>
        </Form>
      </Drawer>
    </>
  );
};
