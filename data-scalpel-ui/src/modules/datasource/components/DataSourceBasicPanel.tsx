import { DatabaseOutlined, ProfileOutlined } from '@ant-design/icons';
import { Space, Table, Tag } from 'antd';
import { BusinessDetailDescriptions } from '../../../shared/components/BusinessDetailDescriptions';
import { BusinessDetailSection } from '../../../shared/components/BusinessDetailSection';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';
import type { DataSource, HttpApiAuthentication, HttpApiNamedValue } from '../model/dataSource';
import { dataSourcePurposeLabels, dataSourceTypeLabels } from '../model/dataSource';

interface DataSourceBasicPanelProps {
  dataSource: DataSource;
  directoryName?: string;
}

const configured = (value: boolean) => (
  <Tag color={value ? 'success' : 'default'}>{value ? '已配置' : '未配置'}</Tag>
);

const authenticationLabel = (authentication: HttpApiAuthentication) => {
  switch (authentication.type) {
    case 'NONE': return '无鉴权';
    case 'BASIC': return `Basic · ${authentication.username} · 密码${authentication.passwordConfigured ? '已配置' : '未配置'}`;
    case 'BEARER_TOKEN': return `Bearer Token · ${authentication.tokenConfigured ? '已配置' : '未配置'}`;
    case 'API_KEY': return `API Key · ${authentication.location} · ${authentication.name} · ${authentication.apiKeyConfigured ? '已配置' : '未配置'}`;
    case 'OAUTH2_CLIENT_CREDENTIALS': return `OAuth2 Client Credentials · ${authentication.clientId} · 密钥${authentication.clientSecretConfigured ? '已配置' : '未配置'}`;
    case 'TOKEN_ENDPOINT': return `Token Endpoint · ${authentication.method} ${authentication.tokenUrl} · 密码${authentication.passwordConfigured ? '已配置' : '未配置'}`;
  }
};

const sensitiveHeader = (name: string) => /(authorization|token|secret|api[-_]?key|credential)/i.test(name);

const namedValues = (values: HttpApiNamedValue[]) => values.length ? (
  <Space size={[4, 4]} wrap>
    {values.map((item) => (
      <Tag key={item.name}>
        <code>{item.name}</code>
        {' = '}
        {sensitiveHeader(item.name) ? '••••••' : item.value}
      </Tag>
    ))}
  </Space>
) : '—';

const connectionItems = (dataSource: DataSource) => {
  const connection = dataSource.connection;
  switch (connection.kind) {
    case 'JDBC': return [
      { key: 'endpoint', label: '主机 / 端口', children: <code>{connection.host}:{connection.port}</code> },
      { key: 'database', label: '数据库', children: connection.databaseName },
      { key: 'schema', label: '默认 Schema', children: connection.schemaName || '—' },
      { key: 'username', label: '用户名', children: connection.username },
      { key: 'password', label: '密码', children: configured(connection.passwordConfigured) },
      {
        key: 'options', label: 'JDBC 定制参数', span: 2,
        children: Object.keys(connection.options).length ? (
          <Table<{ name: string; value: string }>
            size="small"
            rowKey="name"
            pagination={false}
            dataSource={Object.entries(connection.options).map(([name, value]) => ({ name, value }))}
            columns={[
              { title: '参数', dataIndex: 'name', width: 240, render: (value: string) => <code>{value}</code> },
              { title: '值', dataIndex: 'value', render: (value: string) => <code>{value}</code> },
            ]}
          />
        ) : '—',
      },
    ];
    case 'KAFKA': return [
      { key: 'bootstrap', label: 'Bootstrap Servers', span: 2, children: <code>{connection.bootstrapServers}</code> },
      { key: 'protocol', label: '安全协议', children: connection.securityProtocol || '—' },
      { key: 'sasl', label: 'SASL 机制', children: connection.saslMechanism || '—' },
      { key: 'username', label: '用户名', children: connection.username || '—' },
      { key: 'password', label: '密码', children: configured(connection.passwordConfigured) },
    ];
    case 'S3': return [
      { key: 'endpoint', label: 'Endpoint', span: 2, children: <code>{connection.endpoint}</code> },
      { key: 'region', label: 'Region', children: connection.region || '—' },
      { key: 'bucket', label: 'Bucket', children: <code>{connection.bucket}</code> },
      { key: 'prefix', label: '根路径', children: connection.rootPrefix ? <code>{connection.rootPrefix}</code> : '—' },
      { key: 'pathStyle', label: '寻址模式', children: connection.pathStyleAccess ? 'Path-style' : 'Virtual-hosted-style' },
      { key: 'accessKey', label: 'Access Key', children: connection.accessKey },
      { key: 'secretKey', label: 'Secret Key', children: configured(connection.secretKeyConfigured) },
    ];
    case 'HTTP_API': {
      const configuration = connection.configuration;
      return [
        { key: 'baseUrl', label: 'Base URL', span: 2, children: <code>{configuration.baseUrl}</code> },
        { key: 'auth', label: '鉴权方式', span: 2, children: authenticationLabel(configuration.authentication) },
        { key: 'connectTimeout', label: '连接超时', children: `${configuration.connectTimeoutMs} ms` },
        { key: 'requestTimeout', label: '请求超时', children: `${configuration.requestTimeoutMs} ms` },
        { key: 'interval', label: '最小请求间隔', children: `${configuration.minimumRequestIntervalMs} ms` },
        { key: 'retries', label: '最大重试次数', children: configuration.maxRetries },
        { key: 'headers', label: '默认 Header', span: 2, children: namedValues(configuration.defaultHeaders) },
        { key: 'signingSecret', label: '签名密钥', children: configured(configuration.signingSecretConfigured) },
        { key: 'signingPrivateKey', label: '签名私钥', children: configured(configuration.signingPrivateKeyConfigured) },
      ];
    }
  }
};

export const DataSourceBasicPanel = ({ dataSource, directoryName }: DataSourceBasicPanelProps) => (
  <div className="data-source-detail-tab-panel data-source-basic-panel">
    <BusinessDetailSection
      title="管理信息"
      description="数据源的基础属性与归属信息"
      icon={<ProfileOutlined />}
    >
      <BusinessDetailDescriptions
        column={{ xs: 1, md: 2, xl: 4 }}
        items={[
          { key: 'name', label: '名称', children: dataSource.name },
          { key: 'code', label: '编码', children: <code>{dataSource.code}</code> },
          { key: 'type', label: '类型', children: dataSourceTypeLabels[dataSource.type] },
          { key: 'status', label: '状态', children: <Tag color={dataSource.enabled ? 'success' : 'default'}>{dataSource.enabled ? '启用' : '停用'}</Tag> },
          { key: 'purposes', label: '用途', children: <Space size={4}>{dataSource.purposes.map((purpose) => <Tag key={purpose}>{dataSourcePurposeLabels[purpose]}</Tag>)}</Space> },
          { key: 'directory', label: '目录', children: directoryName || '未分类' },
          { key: 'description', label: '说明', span: 2, children: dataSource.description || '—' },
          { key: 'createdAt', label: '创建时间', children: formatManagementDateTime(dataSource.createdAt) },
          { key: 'updatedAt', label: '更新时间', children: formatManagementDateTime(dataSource.updatedAt) },
        ]}
      />
    </BusinessDetailSection>
    <BusinessDetailSection
      title="连接配置"
      description="访问外部资源所需的连接参数"
      icon={<DatabaseOutlined />}
    >
      <BusinessDetailDescriptions
        column={{ xs: 1, md: 2, xl: 3 }}
        items={connectionItems(dataSource)}
      />
    </BusinessDetailSection>
  </div>
);
