import { ApiOutlined, ApartmentOutlined, ArrowRightOutlined, DatabaseOutlined, DeploymentUnitOutlined, LockOutlined, SafetyCertificateOutlined, UserOutlined } from '@ant-design/icons';
import { Button, ConfigProvider, Form, Input, message } from 'antd';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { useCurrentUser, useLogin } from '../hooks/useSystemAccess';
import type { LoginRequest } from '../model/systemAccess';
import loginIllustration from '../../../shared/assets/login-spatiotemporal-cutout.png';
import './LoginPage.css';

const loginTheme = {
  token: {
    fontFamily: '"Segoe UI", "Microsoft YaHei UI", sans-serif',
    colorPrimary: '#347f9f',
    colorText: '#273949',
    colorTextPlaceholder: '#6d7e8c',
    colorBorder: '#cadbe6',
    fontSize: 16,
    fontSizeLG: 16,
    controlHeightLG: 56,
    borderRadius: 4,
  },
  components: { Button: { primaryShadow: 'none', fontWeight: 500 } },
};

export const LoginPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [messageApi, messageContext] = message.useMessage();
  const loginMutation = useLogin();
  const currentUserQuery = useCurrentUser();
  const requestedPath = (location.state as { from?: { pathname?: string; search?: string; hash?: string } } | null)?.from;
  const destination = requestedPath?.pathname?.startsWith('/') && !requestedPath.pathname.startsWith('//') && requestedPath.pathname !== '/login'
    ? `${requestedPath.pathname}${requestedPath.search ?? ''}${requestedPath.hash ?? ''}`
    : '/';

  const submit = async (values: LoginRequest) => {
    try {
      await loginMutation.mutateAsync(values);
      navigate(destination, { replace: true });
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '登录失败，请稍后重试');
    }
  };

  if (currentUserQuery.data) return <Navigate to={destination} replace />;

  return (
    <ConfigProvider componentSize="large" theme={loginTheme}>
      <main className="login-screen">
        {messageContext}
        <div className="login-shell">
          <header className="login-wordmark">
            <span className="login-brand-symbol" aria-hidden="true" />
            <span>DataScalpel</span>
          </header>
          <div className="login-layout">
            <section className="login-introduction" aria-labelledby="login-platform-title">
              <h1 id="login-platform-title">时空数据治理平台</h1>
              <p className="login-platform-description">连接多源数据，构建空间模型，编排任务并发布数据服务。</p>
              <img className="login-map" src={loginIllustration} alt="" aria-hidden="true" decoding="async" draggable={false} />
              <ol className="login-workflow" aria-label="平台工作流程">
                {[
                  { label: '资源接入', icon: DatabaseOutlined },
                  { label: '数据建模', icon: ApartmentOutlined },
                  { label: '任务编排', icon: DeploymentUnitOutlined },
                  { label: '服务开放', icon: ApiOutlined },
                ].map(({ label, icon: Icon }, index) => (
                  <li key={label}>
                    <Icon className="login-workflow-icon" aria-hidden="true" />
                    <span>{label}</span>
                    {index < 3 && <ArrowRightOutlined className="login-workflow-arrow" aria-hidden="true" />}
                  </li>
                ))}
              </ol>
            </section>
            <section className="login-panel" aria-labelledby="login-form-title">
              <h2 id="login-form-title">登录工作台</h2>
              <p className="login-form-description">使用您的账号，进入数据治理工作空间</p>
              <Form<LoginRequest> name="login" layout="vertical" requiredMark={false} onFinish={submit} autoComplete="on">
                <Form.Item name="username" label="用户名" rules={[{ required: true, whitespace: true, message: '请输入用户名' }]}>
                  <Input name="username" prefix={<UserOutlined />} autoFocus autoComplete="username" placeholder="请输入用户名" />
                </Form.Item>
                <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
                  <Input.Password name="password" prefix={<LockOutlined />} autoComplete="current-password" placeholder="请输入密码" />
                </Form.Item>
                <Button className="login-submit" type="primary" htmlType="submit" block loading={loginMutation.isPending}>
                  <span>登录</span><ArrowRightOutlined aria-hidden="true" />
                </Button>
              </Form>
              <p className="login-demo-account">
                测试账号：admin / admin123456
              </p>
              <div className="login-security-note"><SafetyCertificateOutlined aria-hidden="true" /><span>面向内网环境部署<br />访问由本地系统统一保护</span></div>
            </section>
          </div>
        </div>
      </main>
    </ConfigProvider>
  );
};
