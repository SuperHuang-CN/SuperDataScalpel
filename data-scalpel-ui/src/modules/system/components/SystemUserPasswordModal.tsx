import { Form, Input, Modal, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useResetSystemUserPassword } from '../hooks/useSystemAccess';
import type { ResetSystemUserPasswordRequest, SystemUser } from '../model/systemAccess';

interface SystemUserPasswordModalProps {
  user: SystemUser | null;
  onClose: () => void;
}

export const SystemUserPasswordModal = ({ user, onClose }: SystemUserPasswordModalProps) => {
  const [form] = Form.useForm<ResetSystemUserPasswordRequest>();
  const [messageApi, messageContext] = message.useMessage();
  const resetMutation = useResetSystemUserPassword();

  useEffect(() => {
    form.resetFields();
  }, [form, user]);

  const submit = async (values: ResetSystemUserPasswordRequest) => {
    if (!user) return;
    try {
      await resetMutation.mutateAsync({ id: user.id, request: values });
      messageApi.success('密码已重置');
      onClose();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '重置密码失败');
    }
  };

  return (
    <>
      {messageContext}
      <Modal
        rootClassName="business-overlay business-modal-overlay"
        title={`重置密码：${user?.displayName ?? ''}`}
        open={Boolean(user)}
        onCancel={onClose}
        onOk={() => form.submit()}
        okText="保存"
        confirmLoading={resetMutation.isPending}
        destroyOnHidden
      >
        <Form autoComplete="off" form={form} layout="vertical" requiredMark={false} onFinish={submit}>
          <Form.Item name="password" label="新密码" rules={[{ required: true, min: 8, message: '密码至少 8 位' }]}>
            <Input.Password name="managed-user-reset-secret" autoComplete="off" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};
