import { ApiOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { Button, Card, Form, Input, Typography, message } from 'antd';
import { Navigate, useNavigate } from 'react-router-dom';
import { api, authToken } from '../api';

interface LoginResponse {
  accessToken: string;
}

export const LoginPage = () => {
  const navigate = useNavigate();
  const login = useMutation({
    mutationFn: (values: { username: string; password: string }) =>
      api<LoginResponse>('/auth/login', { method: 'POST', body: JSON.stringify(values), headers: { 'Content-Type': 'application/json' } }),
    onSuccess: (response) => {
      authToken.set(response.accessToken);
      navigate('/', { replace: true });
    },
    onError: (error: Error) => message.error(error.message),
  });

  if (authToken.get()) return <Navigate to="/" replace />;
  return (
    <div className="login-page">
      <Card className="login-card">
        <Typography.Title level={3}><ApiOutlined /> Super API Gateway</Typography.Title>
        <Typography.Paragraph type="secondary">独立网关管理控制台</Typography.Paragraph>
        <Form
          layout="vertical"
          autoComplete="on"
          initialValues={{ username: 'admin' }}
          onFinish={(values) => login.mutate(values)}
        >
          <Form.Item label="用户名" name="username" rules={[{ required: true }]}>
            <Input autoComplete="username" />
          </Form.Item>
          <Form.Item label="密码" name="password" rules={[{ required: true }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Button block type="primary" htmlType="submit" loading={login.isPending}>登录</Button>
        </Form>
      </Card>
    </div>
  );
};
