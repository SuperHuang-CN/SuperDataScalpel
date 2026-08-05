import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { Button, Card, Form, Input, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { useLogin } from '../hooks/useSystemAccess';
import type { LoginRequest } from '../model/systemAccess';

export const LoginPage = () => {
  const navigate = useNavigate();
  const [messageApi, messageContext] = message.useMessage();
  const loginMutation = useLogin();

  const submit = async (values: LoginRequest) => {
    try {
      await loginMutation.mutateAsync(values);
      navigate('/', { replace: true });
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '登录失败，请稍后重试');
    }
  };

  return (
    <main className="login-page">
      {messageContext}
      <Card className="login-card" bordered>
        <Typography.Title level={3} className="login-title">DataScalpel</Typography.Title>
        <Typography.Paragraph type="secondary" className="login-subtitle">
          数据中台管理控制台
        </Typography.Paragraph>
        <Typography.Paragraph type="secondary" className="login-test-account">
          测试账号：admin / admin123456
        </Typography.Paragraph>
        <Form<LoginRequest> layout="vertical" requiredMark={false} onFinish={submit} autoComplete="on">
          <Form.Item name="username" label="用户名" rules={[{ required: true, whitespace: true, message: '请输入用户名' }]}>
            <Input name="username" prefix={<UserOutlined />} autoFocus autoComplete="username" placeholder="请输入用户名" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password name="password" prefix={<LockOutlined />} autoComplete="current-password" placeholder="请输入密码" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loginMutation.isPending}>
            登录
          </Button>
        </Form>
      </Card>
    </main>
  );
};
