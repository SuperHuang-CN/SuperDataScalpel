import { SafetyCertificateOutlined, TeamOutlined } from '@ant-design/icons';
import { Badge, Button, Col, Drawer, Form, Input, Row, Space, Tag, Typography, message } from 'antd';
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
        rootClassName="business-overlay business-drawer-overlay"
        className="data-model-drawer system-role-drawer"
        title={(
          <div className="data-model-drawer-title">
            <span className="data-model-drawer-title-icon" aria-hidden="true"><TeamOutlined /></span>
            <span className="data-model-drawer-title-copy">
              <span>{isEditing ? '修改系统角色' : '新建系统角色'}</span>
              <Typography.Text type="secondary">定义角色身份与职责边界，权限在保存后单独配置</Typography.Text>
            </span>
          </div>
        )}
        extra={<Tag className="data-model-drawer-header-tag">{role?.builtIn ? '内置角色' : isEditing ? '自定义角色' : '新角色'}</Tag>}
        open={open}
        size={680}
        onClose={onClose}
        forceRender
        destroyOnHidden
        footer={(
          <div className="data-model-drawer-footer">
            <Badge status="processing" text={isEditing ? '权限配置保持不变' : '创建后继续配置权限'} />
            <Space>
              <Button onClick={onClose}>取消</Button>
              <Button type="primary" loading={createMutation.isPending || updateMutation.isPending} onClick={() => form.submit()}>
                {isEditing ? '保存修改' : '创建角色'}
              </Button>
            </Space>
          </div>
        )}
      >
        <Form
          name="system-role-editor-form"
          className="data-model-form system-role-form"
          autoComplete="off"
          form={form}
          layout="vertical"
          onFinish={(values) => void submit(values)}
        >
          <section className="data-model-form-section">
            <header className="data-model-form-section-header">
              <span className="data-model-form-section-icon" aria-hidden="true"><SafetyCertificateOutlined /></span>
              <span className="data-model-form-section-copy">
                <span className="data-model-form-section-title">角色身份</span>
                <Typography.Text type="secondary">角色编码创建后锁定，名称和职责说明可持续维护</Typography.Text>
              </span>
            </header>
            <div className="data-model-form-section-body">
              <Row gutter={14}>
                <Col xs={24} sm={12}>
                  <Form.Item
                    name="code"
                    label="角色编码"
                    rules={[
                      { required: true, whitespace: true, message: '请输入角色编码' },
                      { pattern: /^[A-Za-z][A-Za-z0-9_.-]{0,63}$/, message: '以字母开头，可使用字母、数字、点、下划线和连字符' },
                    ]}
                  >
                    <Input name="system-role-code" autoComplete="off" disabled={isEditing} placeholder="如 data_operator" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item name="name" label="角色名称" rules={[{ required: true, whitespace: true, message: '请输入角色名称' }]}>
                    <Input name="system-role-name" autoComplete="off" maxLength={100} placeholder="输入角色显示名称" />
                  </Form.Item>
                </Col>
                <Col span={24}>
                  <Form.Item name="description" label="职责说明">
                    <Input.TextArea name="system-role-description" autoComplete="off" rows={4} maxLength={500} showCount placeholder="描述适用岗位、职责范围或使用场景" />
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
