import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Col, Form, Input, InputNumber, Select, Space } from 'antd';
import { BusinessSecretInput } from '../../../shared/components/BusinessSecretInput';
import type { HttpApiAuthenticationType } from '../model/dataSource';

const authenticationOptions: { value: HttpApiAuthenticationType; label: string }[] = [
  { value: 'NONE', label: '无鉴权' },
  { value: 'BASIC', label: 'Basic Authentication' },
  { value: 'BEARER_TOKEN', label: '固定 Bearer Token' },
  { value: 'API_KEY', label: 'API Key' },
  { value: 'OAUTH2_CLIENT_CREDENTIALS', label: 'OAuth2 Client Credentials' },
  { value: 'TOKEN_ENDPOINT', label: '自定义 Token 接口' },
];

const locationOptions = [
  { value: 'HEADER', label: 'Header' },
  { value: 'QUERY', label: 'Query' },
  { value: 'BODY', label: 'Body' },
];

const NamedValues = ({ name, addLabel }: { name: (string | number)[]; addLabel: string }) => (
  <Form.List name={name}>
    {(fields, { add, remove }) => (
      <Space orientation="vertical" size={6} className="http-api-named-values">
        {fields.map((field) => (
          <Space key={field.key} align="baseline" className="http-api-named-value-row">
            <Form.Item name={[field.name, 'name']} rules={[{ required: true, whitespace: true, message: '请输入名称' }]}>
              <Input placeholder="名称" />
            </Form.Item>
            <Form.Item name={[field.name, 'value']} rules={[{ required: true, message: '请输入值或模板' }]}>
              <Input placeholder="值，可使用 ${runtime.xxx}" />
            </Form.Item>
            <Button
              type="text"
              danger
              icon={<DeleteOutlined />}
              aria-label="删除参数"
              onClick={() => remove(field.name)}
            />
          </Space>
        ))}
        <Button type="dashed" icon={<PlusOutlined />} onClick={() => add({ name: '', value: '' })}>
          {addLabel}
        </Button>
      </Space>
    )}
  </Form.List>
);

const AuthenticationFields = ({ type }: { type: HttpApiAuthenticationType }) => {
  switch (type) {
    case 'NONE':
      return <Alert showIcon type="info" title="该数据源不附加鉴权信息" />;
    case 'BASIC':
      return <>
        <Col span={12}>
          <Form.Item label="用户名" name={['connection', 'authentication', 'username']} rules={[{ required: true, whitespace: true }]}>
            <Input name="http-basic-principal" autoComplete="off" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item label="密码" name={['connection', 'authentication', 'password']} extra="修改时留空表示保留原密码。">
            <BusinessSecretInput name="http-basic-secret" autoComplete="off" />
          </Form.Item>
        </Col>
      </>;
    case 'BEARER_TOKEN':
      return <Col span={24}>
        <Form.Item label="Bearer Token" name={['connection', 'authentication', 'token']} extra="修改时留空表示保留原 Token。">
          <BusinessSecretInput name="http-bearer-token" autoComplete="off" />
        </Form.Item>
      </Col>;
    case 'API_KEY':
      return <>
        <Col span={8}>
          <Form.Item label="放置位置" name={['connection', 'authentication', 'location']} rules={[{ required: true }]}>
            <Select options={locationOptions} />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item label="参数名称" name={['connection', 'authentication', 'name']} rules={[{ required: true, whitespace: true }]}>
            <Input placeholder="X-API-Key" />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item label="值模板" name={['connection', 'authentication', 'valueTemplate']}>
            <Input placeholder="${credential.apiKey}" />
          </Form.Item>
        </Col>
        <Col span={24}>
          <Form.Item label="API Key" name={['connection', 'authentication', 'apiKey']} extra="修改时留空表示保留原 API Key。">
            <BusinessSecretInput name="http-api-key" autoComplete="off" />
          </Form.Item>
        </Col>
      </>;
    case 'OAUTH2_CLIENT_CREDENTIALS':
      return <>
        <Col span={24}>
          <Form.Item label="Token URL" name={['connection', 'authentication', 'tokenUrl']} rules={[{ required: true, type: 'url' }]}>
            <Input placeholder="https://auth.example.com/oauth/token" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item label="Client ID" name={['connection', 'authentication', 'clientId']} rules={[{ required: true, whitespace: true }]}>
            <Input name="http-oauth-client-id" autoComplete="off" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item label="Client Secret" name={['connection', 'authentication', 'clientSecret']} extra="修改时留空表示保留原密钥。">
            <BusinessSecretInput name="http-oauth-client-secret" autoComplete="off" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item label="Scopes" name={['connection', 'authentication', 'scopesText']} extra="多个 Scope 用空格分隔。">
            <Input placeholder="read profile" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item label="Audience" name={['connection', 'authentication', 'audience']}>
            <Input />
          </Form.Item>
        </Col>
        <TokenPlacementFields />
      </>;
    case 'TOKEN_ENDPOINT':
      return <>
        <Col span={16}>
          <Form.Item label="Token URL" name={['connection', 'authentication', 'tokenUrl']} rules={[{ required: true, type: 'url' }]}>
            <Input />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item label="请求方法" name={['connection', 'authentication', 'method']} rules={[{ required: true }]}>
            <Select options={[{ value: 'GET', label: 'GET' }, { value: 'POST', label: 'POST' }]} />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item label="用户名" name={['connection', 'authentication', 'username']}>
            <Input name="http-token-principal" autoComplete="off" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item label="密码" name={['connection', 'authentication', 'password']} extra="模板中使用 ${credential.password}。">
            <BusinessSecretInput name="http-token-secret" autoComplete="off" />
          </Form.Item>
        </Col>
        <Col span={24}>
          <Form.Item label="Token 请求 Header">
            <NamedValues name={['connection', 'authentication', 'headers']} addLabel="添加 Token Header" />
          </Form.Item>
        </Col>
        <Col span={24}>
          <Form.Item label="Token 请求 Body 模板" name={['connection', 'authentication', 'bodyTemplate']}>
            <Input.TextArea autoSize={{ minRows: 3, maxRows: 8 }} placeholder={'{"username":"${credential.username}","password":"${credential.password}"}'} />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item label="Token JSON Pointer" name={['connection', 'authentication', 'tokenPointer']} rules={[{ required: true, whitespace: true }]}>
            <Input placeholder="/data/accessToken" />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item label="过期秒数 Pointer" name={['connection', 'authentication', 'expiresInPointer']}>
            <Input placeholder="/data/expiresIn" />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item label="固定 TTL（秒）" name={['connection', 'authentication', 'fixedTtlSeconds']}>
            <InputNumber min={30} max={86400} precision={0} className="data-source-number-input" />
          </Form.Item>
        </Col>
        <TokenPlacementFields />
      </>;
  }
};

const TokenPlacementFields = () => <>
  <Col span={8}>
    <Form.Item label="Token 放置位置" name={['connection', 'authentication', 'tokenLocation']} initialValue="HEADER" rules={[{ required: true }]}>
      <Select options={locationOptions} />
    </Form.Item>
  </Col>
  <Col span={8}>
    <Form.Item label="Token 参数名称" name={['connection', 'authentication', 'tokenName']} initialValue="Authorization" rules={[{ required: true, whitespace: true }]}>
      <Input placeholder="Authorization" />
    </Form.Item>
  </Col>
  <Col span={8}>
    <Form.Item label="Token 值模板" name={['connection', 'authentication', 'tokenValueTemplate']} initialValue="Bearer ${token}" rules={[{ required: true }]}>
      <Input placeholder="Bearer ${token}" />
    </Form.Item>
  </Col>
</>;

export const HttpApiConnectionFields = ({ authenticationType }: {
  authenticationType: HttpApiAuthenticationType;
}) => (
  <>
    <Col span={24}>
      <Alert
        showIcon
        type="info"
        title="连接负责地址、凭据与运行时 Token；具体路径、签名、分页和返回 Schema 在保存后通过“API 资源”配置。"
      />
    </Col>
    <Col span={24}>
      <Form.Item label="Base URL" name={['connection', 'baseUrl']} rules={[{ required: true, type: 'url', message: '请输入完整的 HTTP/HTTPS 地址' }]}>
        <Input placeholder="https://api.example.com" />
      </Form.Item>
    </Col>
    <Col span={24}>
      <Form.Item label="默认 Header">
        <NamedValues name={['connection', 'defaultHeaders']} addLabel="添加默认 Header" />
      </Form.Item>
    </Col>
    <Col span={6}>
      <Form.Item label="连接超时（ms）" name={['connection', 'connectTimeoutMs']} rules={[{ required: true }]}>
        <InputNumber min={100} max={120000} precision={0} className="data-source-number-input" />
      </Form.Item>
    </Col>
    <Col span={6}>
      <Form.Item label="请求超时（ms）" name={['connection', 'requestTimeoutMs']} rules={[{ required: true }]}>
        <InputNumber min={100} max={600000} precision={0} className="data-source-number-input" />
      </Form.Item>
    </Col>
    <Col span={6}>
      <Form.Item label="最小请求间隔（ms）" name={['connection', 'minimumRequestIntervalMs']} rules={[{ required: true }]}>
        <InputNumber min={0} max={60000} precision={0} className="data-source-number-input" />
      </Form.Item>
    </Col>
    <Col span={6}>
      <Form.Item label="最大重试次数" name={['connection', 'maxRetries']} rules={[{ required: true }]}>
        <InputNumber min={0} max={5} precision={0} className="data-source-number-input" />
      </Form.Item>
    </Col>
    <Col span={24}>
      <Form.Item label="鉴权方式" name={['connection', 'authentication', 'type']} rules={[{ required: true }]}>
        <Select options={authenticationOptions} />
      </Form.Item>
    </Col>
    <AuthenticationFields type={authenticationType} />
    <Col span={12}>
      <Form.Item label="签名密钥" name={['connection', 'signingSecret']} extra="HMAC/MD5 签名使用；修改时留空表示保留。">
        <BusinessSecretInput name="http-signing-secret" autoComplete="off" />
      </Form.Item>
    </Col>
    <Col span={12}>
      <Form.Item label="RSA PKCS#8 私钥" name={['connection', 'signingPrivateKey']} extra="RSA-SHA256 使用；修改时留空表示保留。">
        <Input.TextArea name="http-signing-private-key" autoComplete="off" autoSize={{ minRows: 2, maxRows: 5 }} />
      </Form.Item>
    </Col>
  </>
);
