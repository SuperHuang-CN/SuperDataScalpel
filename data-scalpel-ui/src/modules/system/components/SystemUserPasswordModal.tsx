import { KeyOutlined, LockOutlined } from '@ant-design/icons';
import { Button, Form, Modal, Space, Tag, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { BusinessSecretInput } from '../../../shared/components/BusinessSecretInput';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { useResetSystemUserPassword } from '../hooks/useSystemAccess';
import type { ResetSystemUserPasswordRequest, SystemUser } from '../model/systemAccess';

interface SystemUserPasswordModalProps {
  user: SystemUser | null;
  onClose: () => void;
}

export const SystemUserPasswordModal = ({ user, onClose }: SystemUserPasswordModalProps) => {
  const [form] = Form.useForm<ResetSystemUserPasswordRequest>();
  const [messageApi, messageContext] = message.useMessage();
  const [operationError, setOperationError] = useState<string | null>(null);
  const resetMutation = useResetSystemUserPassword();

  useEffect(() => {
    form.resetFields();
  }, [form, user]);

  const submit = async (values: ResetSystemUserPasswordRequest) => {
    if (!user) return;
    setOperationError(null);
    try {
      await resetMutation.mutateAsync({ id: user.id, request: values });
      messageApi.success('密码已重置');
      onClose();
    } catch (error) {
      const errorMessage = error instanceof ApiError ? error.message : '重置密码失败';
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
    }
  };

  const close = () => {
    if (resetMutation.isPending) return;
    form.resetFields();
    setOperationError(null);
    onClose();
  };

  return (
    <>
      {messageContext}
      <Modal
        rootClassName="business-overlay business-modal-overlay system-user-password-modal"
        title={(
          <div className="system-user-password-title">
            <span className="system-user-password-title-icon" aria-hidden="true"><KeyOutlined /></span>
            <span className="system-user-password-title-copy">
              <span>重置用户密码</span>
              <Typography.Text type="secondary">为指定账号设置新的登录凭据</Typography.Text>
            </span>
          </div>
        )}
        open={Boolean(user)}
        onCancel={close}
        closable={!resetMutation.isPending}
        maskClosable={!resetMutation.isPending}
        footer={(
          <div className="system-user-password-footer">
            {operationError ? (
              <InlineFeedback tone="error" label="密码重置失败" detail={operationError} />
            ) : (
              <InlineFeedback tone="info" label={user ? `将替换 ${user.displayName} 的当前密码` : '等待选择用户'} />
            )}
            <Space>
              <Button disabled={resetMutation.isPending} onClick={close}>取消</Button>
              <Button type="primary" loading={resetMutation.isPending} onClick={() => form.submit()}>确认重置</Button>
            </Space>
          </div>
        )}
        destroyOnHidden
      >
        <div className="system-user-password-context">
          <span className="system-user-password-context-icon" aria-hidden="true"><LockOutlined /></span>
          <span>
            <strong>{user?.displayName ?? '—'}</strong>
            <Typography.Text type="secondary">{user?.username ?? '—'}</Typography.Text>
          </span>
          <Tag>{user?.enabled ? '启用账号' : '停用账号'}</Tag>
        </div>
        <Form autoComplete="off" form={form} layout="vertical" requiredMark={false} className="system-user-password-form" onFinish={submit}>
          <Form.Item
            name="password"
            label={(
              <span className="system-user-password-field-label">
                新密码
                <ContextHelp ariaLabel="查看密码要求" content="密码至少 8 位。保存后旧密码立即失效，用户下次登录必须使用新密码。" />
              </span>
            )}
            rules={[{ required: true, min: 8, message: '密码至少 8 位' }]}
          >
            <BusinessSecretInput name="managed-user-reset-secret" autoComplete="off" revealLabel="显示新密码" hideLabel="隐藏新密码" placeholder="输入至少 8 位的新密码" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};
