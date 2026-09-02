import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import {
  ApiOutlined,
  CopyOutlined,
  DatabaseOutlined,
  HddOutlined,
  IdcardOutlined,
  SettingOutlined,
  ShareAltOutlined,
} from '@ant-design/icons';
import { Badge, Button, Card, Checkbox, Col, Collapse, Drawer, Form, Input, InputNumber, Row, Select, Space, Switch, Tag, Tooltip, TreeSelect, Typography, message } from 'antd';
import { type ReactNode, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { BusinessSecretInput } from '../../../shared/components/BusinessSecretInput';
import { directoryTreeSelectData, useDirectoryTree } from '../../directory';
import {
  useCreateDataSource,
  useDataSourceTypes,
  useTestDraftDataSourceConnection,
  useUpdateDataSource,
} from '../hooks/useDataSources';
import {
  dataSourcePurposeLabels,
  dataSourceTypeLabels,
  type CreateDataSourceRequest,
  type ConnectionTestResult,
  type DataSource,
  type DataSourceAssistantDraft,
  type DataSourceConnectionInput,
  type DataSourceConnectionKind,
  type DataSourcePurpose,
  type DataSourceType,
  type DataSourceTypeDefinition,
  type HttpApiAuthenticationInput,
  type HttpApiAuthenticationType,
  type HttpApiNamedValue,
  type UpdateDataSourceRequest,
} from '../model/dataSource';
import {
  defaultJdbcConnectionOptions,
  mergeJdbcConnectionOptions,
  splitJdbcConnectionOptions,
  type JdbcConnectionOptionFormRow,
} from '../model/jdbcConnectionOptions';
import { ConnectionTestResultModal } from './ConnectionTestResultModal';
import { JdbcConnectionOptionsFields } from './JdbcConnectionOptionsFields';
import { HttpApiConnectionFields } from './HttpApiConnectionFields';

interface DataSourceDrawerProps {
  dataSource: DataSource | null;
  open: boolean;
  initialDirectoryId?: string;
  initialDraft?: DataSourceAssistantDraft | null;
  canViewDirectories: boolean;
  canTest: boolean;
  onClose: () => void;
}

interface ConnectionTestFailure {
  result: ConnectionTestResult;
  targetLabel: string;
}

type DataSourceFormSection = 'basic' | 'connection' | 'advanced';

type KafkaSecurityProtocol = 'PLAINTEXT' | 'SSL' | 'SASL_PLAINTEXT' | 'SASL_SSL';

interface DataSourceConnectionFormValues {
  host?: string;
  port?: number;
  databaseName?: string;
  schemaName?: string;
  username?: string;
  password?: string;
  options?: Record<string, string>;
  customOptions?: JdbcConnectionOptionFormRow[];
  bootstrapServers?: string;
  securityProtocol?: KafkaSecurityProtocol;
  saslMechanism?: string;
  endpoint?: string;
  region?: string;
  bucket?: string;
  rootPrefix?: string;
  accessKey?: string;
  secretKey?: string;
  pathStyleAccess?: boolean;
  baseUrl?: string;
  defaultHeaders?: HttpApiNamedValue[];
  connectTimeoutMs?: number;
  requestTimeoutMs?: number;
  minimumRequestIntervalMs?: number;
  maxRetries?: number;
  authentication?: HttpApiAuthenticationFormValues;
  signingSecret?: string;
  signingPrivateKey?: string;
}

interface HttpApiAuthenticationFormValues {
  type?: HttpApiAuthenticationType;
  username?: string;
  password?: string;
  token?: string;
  location?: 'HEADER' | 'QUERY' | 'BODY';
  name?: string;
  valueTemplate?: string;
  apiKey?: string;
  tokenUrl?: string;
  clientId?: string;
  clientSecret?: string;
  scopesText?: string;
  audience?: string;
  method?: 'GET' | 'POST';
  headers?: HttpApiNamedValue[];
  bodyTemplate?: string;
  tokenPointer?: string;
  expiresInPointer?: string;
  fixedTtlSeconds?: number;
  tokenLocation?: 'HEADER' | 'QUERY' | 'BODY';
  tokenName?: string;
  tokenValueTemplate?: string;
}

interface DataSourceFormValues {
  code?: string;
  name: string;
  directoryId?: string;
  purposes: DataSourcePurpose[];
  type: DataSourceType;
  enabled: boolean;
  description?: string;
  connection: DataSourceConnectionFormValues;
}

const allPurposes: DataSourcePurpose[] = ['SOURCE', 'STORAGE', 'DISTRIBUTION'];
const purposeDescriptions: Record<DataSourcePurpose, string> = {
  SOURCE: '作为数据接入源',
  STORAGE: '用于数据存储',
  DISTRIBUTION: '用于数据分发',
};
const kafkaSecurityProtocolOptions: { value: KafkaSecurityProtocol; label: string }[] = [
  { value: 'PLAINTEXT', label: 'PLAINTEXT' },
  { value: 'SSL', label: 'SSL' },
  { value: 'SASL_PLAINTEXT', label: 'SASL_PLAINTEXT' },
  { value: 'SASL_SSL', label: 'SASL_SSL' },
];

const connectionKindForType = (type: DataSourceType): DataSourceConnectionKind => {
  if (type === 'KAFKA') return 'KAFKA';
  if (type === 'S3') return 'S3';
  if (type === 'HTTP_API' || type === 'ARCGIS_REST' || type === 'WFS') return 'HTTP_API';
  return 'JDBC';
};

const purposeIcon = (purpose: DataSourcePurpose) => {
  switch (purpose) {
    case 'SOURCE': return <DatabaseOutlined />;
    case 'STORAGE': return <HddOutlined />;
    case 'DISTRIBUTION': return <ShareAltOutlined />;
  }
};

const jdbcUrlPreview = (
  type: DataSourceType | undefined,
  connection: DataSourceConnectionFormValues | undefined,
): string => {
  const hostValue = connection?.host?.trim();
  const databaseName = connection?.databaseName?.trim();
  const port = connection?.port;
  if (!type || !hostValue || !port || !databaseName) return '';

  const host = hostValue.includes(':') && !hostValue.startsWith('[') ? `[${hostValue}]` : hostValue;
  const database = encodeURIComponent(databaseName);
  switch (type) {
    case 'MYSQL': return `jdbc:mysql://${host}:${port}/${database}`;
    case 'POSTGRESQL': return `jdbc:postgresql://${host}:${port}/${database}`;
    case 'ORACLE':
      return connection?.options?.connectionMode?.toUpperCase() === 'SID'
        ? `jdbc:oracle:thin:@${host}:${port}:${databaseName}`
        : `jdbc:oracle:thin:@//${host}:${port}/${database}`;
    case 'SQL_SERVER': return `jdbc:sqlserver://${host}:${port}`;
    case 'CLICKHOUSE': {
      const scheme = connection?.options?.ssl === 'true' ? 'https' : 'http';
      return `jdbc:clickhouse:${scheme}://${host}:${port}/${database}`;
    }
    case 'DAMENG': return `jdbc:dm://${host}:${port}/${database}`;
    case 'KINGBASE': return `jdbc:kingbase8://${host}:${port}/${database}`;
    case 'OPENGAUSS': return `jdbc:opengauss://${host}:${port}/${database}`;
    case 'TDENGINE_WEBSOCKET': return `jdbc:TAOS-WS://${host}:${port}/${database}`;
    case 'TDENGINE_RESTFUL': return `jdbc:TAOS-RS://${host}:${port}/${database}`;
    default: return '';
  }
};

const FormSectionTitle = ({
  title,
  description,
  icon,
}: {
  title: string;
  description: string;
  icon: ReactNode;
}) => (
  <div className="data-source-section-title">
    <span className="data-source-section-title-icon" aria-hidden="true">{icon}</span>
    <span className="data-source-section-title-copy">
      <span>{title}</span>
      <Typography.Text type="secondary">{description}</Typography.Text>
    </span>
  </div>
);

const requiredText = (value: string | undefined, label: string): string => {
  const normalized = value?.trim();
  if (!normalized) throw new Error(`请输入${label}`);
  return normalized;
};

const defaultConnection = (
  type: DataSourceType,
  definition?: DataSourceTypeDefinition,
): DataSourceConnectionFormValues => {
  switch (connectionKindForType(type)) {
    case 'KAFKA':
      return { securityProtocol: 'PLAINTEXT' };
    case 'S3':
      return { pathStyleAccess: true };
    case 'HTTP_API':
      return {
        defaultHeaders: [{ name: 'Accept', value: 'application/json' }],
        connectTimeoutMs: 5000,
        requestTimeoutMs: 30000,
        minimumRequestIntervalMs: 0,
        maxRetries: 2,
        authentication: { type: 'NONE' },
      };
    case 'JDBC':
      {
        const optionValues = defaultJdbcConnectionOptions(definition?.connectionOptions ?? []);
        return {
          port: definition?.defaultPort ?? (type === 'POSTGRESQL' ? 5432 : undefined),
          schemaName: definition?.defaultSchema ?? undefined,
          ...optionValues,
        };
      }
  }
};

const buildConnectionInput = (
  values: DataSourceFormValues,
  definition?: DataSourceTypeDefinition,
): DataSourceConnectionInput => {
  const authentication = (): HttpApiAuthenticationInput => {
    const value = values.connection.authentication ?? { type: 'NONE' };
    switch (value.type) {
      case 'BASIC':
        return { type: 'BASIC', username: requiredText(value.username, 'Basic 用户名'), password: value.password || undefined };
      case 'BEARER_TOKEN':
        return { type: 'BEARER_TOKEN', token: value.token || undefined };
      case 'API_KEY':
        return {
          type: 'API_KEY', location: value.location ?? 'HEADER', name: requiredText(value.name, 'API Key 参数名称'),
          valueTemplate: value.valueTemplate?.trim() || '${credential.apiKey}', apiKey: value.apiKey || undefined,
        };
      case 'OAUTH2_CLIENT_CREDENTIALS':
        return {
          type: 'OAUTH2_CLIENT_CREDENTIALS', tokenUrl: requiredText(value.tokenUrl, 'Token URL'),
          clientId: requiredText(value.clientId, 'Client ID'), clientSecret: value.clientSecret || undefined,
          scopes: value.scopesText?.split(/\s+/).map((item) => item.trim()).filter(Boolean),
          audience: value.audience?.trim() || undefined, tokenLocation: value.tokenLocation ?? 'HEADER',
          tokenName: value.tokenName?.trim() || 'Authorization',
          tokenValueTemplate: value.tokenValueTemplate?.trim() || 'Bearer ${token}',
        };
      case 'TOKEN_ENDPOINT':
        return {
          type: 'TOKEN_ENDPOINT', tokenUrl: requiredText(value.tokenUrl, 'Token URL'), method: value.method ?? 'POST',
          headers: value.headers ?? [], bodyTemplate: value.bodyTemplate || undefined,
          username: value.username?.trim() || undefined, password: value.password || undefined,
          tokenPointer: requiredText(value.tokenPointer, 'Token JSON Pointer'),
          expiresInPointer: value.expiresInPointer?.trim() || undefined, fixedTtlSeconds: value.fixedTtlSeconds,
          tokenLocation: value.tokenLocation ?? 'HEADER', tokenName: value.tokenName?.trim() || 'Authorization',
          tokenValueTemplate: value.tokenValueTemplate?.trim() || 'Bearer ${token}',
        };
      case 'NONE':
      case undefined:
        return { type: 'NONE' };
    }
  };
  switch (connectionKindForType(values.type)) {
    case 'KAFKA':
      return {
        kind: 'KAFKA',
        bootstrapServers: requiredText(values.connection.bootstrapServers, 'Bootstrap Servers'),
        securityProtocol: values.connection.securityProtocol,
        saslMechanism: values.connection.saslMechanism?.trim() || undefined,
        username: values.connection.username?.trim() || undefined,
        password: values.connection.password || undefined,
      };
    case 'S3':
      return {
        kind: 'S3',
        endpoint: requiredText(values.connection.endpoint, 'Endpoint 地址'),
        region: values.connection.region?.trim() || undefined,
        bucket: requiredText(values.connection.bucket, 'Bucket'),
        rootPrefix: values.connection.rootPrefix?.trim() || undefined,
        accessKey: requiredText(values.connection.accessKey, 'AccessKey'),
        secretKey: values.connection.secretKey || undefined,
        pathStyleAccess: values.connection.pathStyleAccess ?? true,
      };
    case 'HTTP_API':
      return {
        kind: 'HTTP_API',
        baseUrl: requiredText(values.connection.baseUrl, 'Base URL'),
        defaultHeaders: values.connection.defaultHeaders ?? [],
        connectTimeoutMs: values.connection.connectTimeoutMs ?? 5000,
        requestTimeoutMs: values.connection.requestTimeoutMs ?? 30000,
        minimumRequestIntervalMs: values.connection.minimumRequestIntervalMs ?? 0,
        maxRetries: values.connection.maxRetries ?? 2,
        authentication: authentication(),
        signingSecret: values.connection.signingSecret || undefined,
        signingPrivateKey: values.connection.signingPrivateKey || undefined,
      };
    case 'JDBC':
      return {
        kind: 'JDBC',
        host: requiredText(values.connection.host, '主机地址'),
        port: values.connection.port ?? 0,
        databaseName: requiredText(values.connection.databaseName, '数据库或服务名'),
        schemaName: values.connection.schemaName?.trim() || undefined,
        username: requiredText(values.connection.username, '用户名'),
        password: values.connection.password || undefined,
        options: mergeJdbcConnectionOptions(
          values.connection.options,
          values.connection.customOptions,
          definition?.connectionOptions ?? [],
        ),
      };
  }
};

const setEditingConnection = (
  dataSource: DataSource,
  definition?: DataSourceTypeDefinition,
): DataSourceConnectionFormValues => {
  switch (dataSource.connection.kind) {
    case 'JDBC':
      {
        const optionValues = splitJdbcConnectionOptions(
          dataSource.connection.options,
          definition?.connectionOptions ?? [],
        );
        return {
          host: dataSource.connection.host,
          port: dataSource.connection.port,
          databaseName: dataSource.connection.databaseName,
          schemaName: dataSource.connection.schemaName ?? undefined,
          username: dataSource.connection.username,
          ...optionValues,
        };
      }
    case 'KAFKA':
      return {
        bootstrapServers: dataSource.connection.bootstrapServers,
        securityProtocol: (dataSource.connection.securityProtocol ?? 'PLAINTEXT') as KafkaSecurityProtocol,
        saslMechanism: dataSource.connection.saslMechanism ?? undefined,
        username: dataSource.connection.username ?? undefined,
      };
    case 'S3':
      return {
        endpoint: dataSource.connection.endpoint,
        region: dataSource.connection.region ?? undefined,
        bucket: dataSource.connection.bucket,
        rootPrefix: dataSource.connection.rootPrefix ?? undefined,
        accessKey: dataSource.connection.accessKey,
        pathStyleAccess: dataSource.connection.pathStyleAccess,
      };
    case 'HTTP_API': {
      const { configuration } = dataSource.connection;
      const auth = configuration.authentication;
      const authentication: HttpApiAuthenticationFormValues = (() => {
        switch (auth.type) {
          case 'NONE': return { type: 'NONE' };
          case 'BASIC': return { type: auth.type, username: auth.username };
          case 'BEARER_TOKEN': return { type: auth.type };
          case 'API_KEY': return {
            type: auth.type, location: auth.location, name: auth.name, valueTemplate: auth.valueTemplate,
          };
          case 'OAUTH2_CLIENT_CREDENTIALS': return {
            type: auth.type, tokenUrl: auth.tokenUrl, clientId: auth.clientId,
            scopesText: auth.scopes.join(' '), audience: auth.audience ?? undefined,
            tokenLocation: auth.tokenLocation, tokenName: auth.tokenName, tokenValueTemplate: auth.tokenValueTemplate,
          };
          case 'TOKEN_ENDPOINT': return {
            type: auth.type, tokenUrl: auth.tokenUrl, method: auth.method, headers: auth.headers,
            bodyTemplate: auth.bodyTemplate ?? undefined, username: auth.username ?? undefined,
            tokenPointer: auth.tokenPointer, expiresInPointer: auth.expiresInPointer ?? undefined,
            fixedTtlSeconds: auth.fixedTtlSeconds ?? undefined, tokenLocation: auth.tokenLocation,
            tokenName: auth.tokenName, tokenValueTemplate: auth.tokenValueTemplate,
          };
        }
      })();
      return {
        baseUrl: configuration.baseUrl,
        defaultHeaders: configuration.defaultHeaders,
        connectTimeoutMs: configuration.connectTimeoutMs,
        requestTimeoutMs: configuration.requestTimeoutMs,
        minimumRequestIntervalMs: configuration.minimumRequestIntervalMs,
        maxRetries: configuration.maxRetries,
        authentication,
      };
    }
  }
};

const JdbcConnectionFields = ({ definition, editing }: { definition?: DataSourceTypeDefinition; editing: boolean }) => (
  <>
    <Col span={12}>
      <Form.Item label="主机" name={['connection', 'host']} rules={[{ required: true, whitespace: true, message: '请输入主机地址' }]}>
        <Input placeholder="如：192.168.1.10" />
      </Form.Item>
    </Col>
    <Col span={12}>
      <Form.Item label="端口" name={['connection', 'port']} rules={[{ required: true, message: '请输入端口' }]}>
        <InputNumber min={1} max={65535} precision={0} className="data-source-number-input" />
      </Form.Item>
    </Col>
    <Col span={12}>
      <Form.Item label={definition?.databaseNameLabel ?? '数据库/服务名'} name={['connection', 'databaseName']} rules={[{ required: true, whitespace: true, message: '请输入数据库或服务名' }]}>
        <Input />
      </Form.Item>
    </Col>
    {definition?.namespaceMode !== 'CATALOG' && (
      <Col span={12}>
        <Form.Item label={definition?.schemaNameLabel ?? 'Schema'} name={['connection', 'schemaName']} rules={[{ max: 128, message: 'Schema 不能超过 128 个字符' }]}>
          <Input placeholder={definition?.defaultSchema ? `可选，默认 ${definition.defaultSchema}` : '可选'} />
        </Form.Item>
      </Col>
    )}
    <Col span={12}>
      <Form.Item label="用户名" name={['connection', 'username']} rules={[{ required: true, whitespace: true, message: '请输入用户名' }]}>
        <Input name="jdbc-principal" autoComplete="off" />
      </Form.Item>
    </Col>
    <Col span={12}>
      <Form.Item label="密码" name={['connection', 'password']}>
        <BusinessSecretInput name="jdbc-secret" autoComplete="off" placeholder={editing ? '留空表示不修改' : '请输入数据库密码'} />
      </Form.Item>
    </Col>
  </>
);

const KafkaConnectionFields = () => (
  <>
    <Col span={24}>
      <Form.Item label="Bootstrap Servers" name={['connection', 'bootstrapServers']} rules={[{ required: true, whitespace: true, message: '请输入 Kafka 集群地址' }]}>
        <Input placeholder="如：192.168.1.10:9092,192.168.1.11:9092" />
      </Form.Item>
    </Col>
    <Col span={12}>
      <Form.Item label="安全协议" name={['connection', 'securityProtocol']}>
        <Select options={kafkaSecurityProtocolOptions} />
      </Form.Item>
    </Col>
    <Col span={12}>
      <Form.Item label="SASL 机制" name={['connection', 'saslMechanism']}>
        <Input placeholder="如：SCRAM-SHA-512" />
      </Form.Item>
    </Col>
    <Col span={12}>
      <Form.Item label="用户名" name={['connection', 'username']}>
        <Input name="kafka-principal" autoComplete="off" />
      </Form.Item>
    </Col>
    <Col span={12}>
      <Form.Item label="密码" name={['connection', 'password']} extra="修改时留空表示不修改已保存的密码。">
        <BusinessSecretInput name="kafka-secret" autoComplete="off" />
      </Form.Item>
    </Col>
  </>
);

const S3ConnectionFields = () => (
  <>
    <Col span={16}>
      <Form.Item label="Endpoint 地址" name={['connection', 'endpoint']} rules={[{ required: true, whitespace: true, message: '请输入 Endpoint 地址' }]}>
        <Input placeholder="如：http://minio.internal:9000" />
      </Form.Item>
    </Col>
    <Col span={8}>
      <Form.Item label="Region" name={['connection', 'region']}>
        <Input placeholder="可选" />
      </Form.Item>
    </Col>
    <Col span={12}>
      <Form.Item label="Bucket" name={['connection', 'bucket']} rules={[{ required: true, whitespace: true, message: '请输入 Bucket' }]}>
        <Input />
      </Form.Item>
    </Col>
    <Col span={12}>
      <Form.Item label="根目录" name={['connection', 'rootPrefix']} extra="可选；相对于 Bucket，不以 / 开头。">
        <Input placeholder="如：business-data/raw" />
      </Form.Item>
    </Col>
    <Col span={12}>
      <Form.Item label="AccessKey" name={['connection', 'accessKey']} rules={[{ required: true, whitespace: true, message: '请输入 AccessKey' }]}>
        <Input name="s3-access-key" autoComplete="off" />
      </Form.Item>
    </Col>
    <Col span={12}>
      <Form.Item label="SecretKey" name={['connection', 'secretKey']} extra="修改时留空表示不修改已保存的 SecretKey。">
        <BusinessSecretInput name="s3-secret-key" autoComplete="off" />
      </Form.Item>
    </Col>
    <Col span={12}>
      <Form.Item label="路径风格访问" name={['connection', 'pathStyleAccess']} valuePropName="checked">
        <Switch checkedChildren="是" unCheckedChildren="否" />
      </Form.Item>
    </Col>
  </>
);

export const DataSourceDrawer = ({
  dataSource,
  open,
  initialDirectoryId,
  initialDraft,
  canViewDirectories,
  canTest,
  onClose,
}: DataSourceDrawerProps) => {
  const [form] = Form.useForm<DataSourceFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const [testFailure, setTestFailure] = useState<ConnectionTestFailure | null>(null);
  const [lastTestResult, setLastTestResult] = useState<ConnectionTestResult | null>(null);
  const [activeSection, setActiveSection] = useState<DataSourceFormSection>('basic');
  const contentRef = useRef<HTMLDivElement>(null);
  const createMutation = useCreateDataSource();
  const updateMutation = useUpdateDataSource();
  const testMutation = useTestDraftDataSourceConnection();
  const dataSourceTypesQuery = useDataSourceTypes();
  const directoriesQuery = useDirectoryTree('DATA_SOURCE', open && canViewDirectories);
  const editing = Boolean(dataSource);
  const selectedType = Form.useWatch('type', form);
  const selectedPurposes = Form.useWatch('purposes', form) ?? [];
  const watchedConnection = Form.useWatch('connection', form);
  const authenticationType = Form.useWatch(['connection', 'authentication', 'type'], form) ?? 'NONE';
  const selectedDefinition = dataSourceTypesQuery.data?.find((definition) => definition.id === selectedType);
  const selectedKind = selectedDefinition?.connectionKind ?? (selectedType ? connectionKindForType(selectedType) : 'JDBC');
  const typeOptions = dataSourceTypesQuery.data?.map((definition) => ({
    value: definition.id,
    label: definition.connectionKind === 'JDBC' && !definition.driverAvailable
      ? `${definition.displayName}（驱动不可用）` : definition.displayName,
  })) ?? Object.entries(dataSourceTypeLabels).map(([value, label]) => ({ value: value as DataSourceType, label }));
  const supportedPurposes = selectedDefinition?.supportedPurposes ?? allPurposes;

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    if (dataSource) {
      const updateDraft = initialDraft?.mode === 'UPDATE' ? initialDraft : null;
      form.setFieldsValue({
        code: dataSource.code,
        name: updateDraft?.name ?? dataSource.name,
        directoryId: updateDraft ? (updateDraft.directoryId ?? undefined) : (dataSource.directoryId ?? undefined),
        purposes: updateDraft?.purposes ?? dataSource.purposes,
        type: dataSource.type,
        enabled: updateDraft?.enabled ?? dataSource.enabled,
        description: updateDraft ? (updateDraft.description ?? undefined) : (dataSource.description ?? undefined),
        connection: setEditingConnection(
          dataSource,
          dataSourceTypesQuery.data?.find((item) => item.id === dataSource.type),
        ),
      });
      return;
    }
    const createDraft = initialDraft?.mode === 'CREATE' ? initialDraft : null;
    const defaultType: DataSourceType = createDraft?.type ?? 'POSTGRESQL';
    form.setFieldsValue({
      code: createDraft?.code,
      name: createDraft?.name,
      directoryId: createDraft ? (createDraft.directoryId ?? undefined) : initialDirectoryId,
      purposes: createDraft?.purposes ?? ['SOURCE'],
      type: defaultType,
      enabled: createDraft?.enabled ?? true,
      description: createDraft?.description ?? undefined,
      connection: defaultConnection(defaultType, dataSourceTypesQuery.data?.find((item) => item.id === defaultType)),
    });
  }, [dataSource, dataSourceTypesQuery.data, form, initialDirectoryId, initialDraft, open]);

  const closeDrawer = () => {
    setTestFailure(null);
    setLastTestResult(null);
    setActiveSection('basic');
    onClose();
  };

  const changeType = (type: DataSourceType) => {
    setTestFailure(null);
    setLastTestResult(null);
    const definition = dataSourceTypesQuery.data?.find((item) => item.id === type);
    const allowed = definition?.supportedPurposes ?? allPurposes;
    const currentPurposes = form.getFieldValue('purposes') ?? [];
    const nextPurposes = currentPurposes.filter((purpose: DataSourcePurpose) => allowed.includes(purpose));
    form.setFieldsValue({
      purposes: nextPurposes.length ? nextPurposes : [allowed[0]],
      connection: defaultConnection(type, definition),
    });
  };

  const submit = async (values: DataSourceFormValues) => {
    try {
      const request: UpdateDataSourceRequest = {
        name: values.name,
        directoryId: values.directoryId,
        purposes: values.purposes,
        type: values.type,
        enabled: values.enabled,
        description: values.description,
        connection: buildConnectionInput(values, selectedDefinition),
      };
      if (dataSource) {
        await updateMutation.mutateAsync({ id: dataSource.id, request });
        messageApi.success('数据源已保存');
      } else if (values.code) {
        await createMutation.mutateAsync({ ...request, code: values.code } satisfies CreateDataSourceRequest);
        messageApi.success('数据源已创建');
      }
      closeDrawer();
    } catch (error) {
      messageApi.error(error instanceof ApiError || error instanceof Error ? error.message : '保存数据源失败');
    }
  };

  const testConnection = async () => {
    try {
      setTestFailure(null);
      setLastTestResult(null);
      const values = await form.validateFields([['type'], ['connection']], { recursive: true });
      const connection = buildConnectionInput(values, selectedDefinition);
      const result = await testMutation.mutateAsync({ type: values.type, connection });
      setLastTestResult(result);
      if (result.success) {
        const product = result.databaseProduct ? `（${result.databaseProduct} ${result.databaseVersion ?? ''}，${result.elapsedMs} ms）` : '';
        messageApi.success(`${result.message}${product}`);
      } else {
        const typeName = selectedDefinition?.displayName ?? dataSourceTypeLabels[values.type];
        setTestFailure({
          result,
          targetLabel: connection.kind === 'JDBC'
            ? `${typeName} · ${connection.host}:${connection.port}/${connection.databaseName}`
            : `${typeName} · ${connection.kind === 'HTTP_API' ? connection.baseUrl : (form.getFieldValue('name') || '当前配置')}`,
        });
      }
    } catch (error) {
      if (error instanceof ApiError || error instanceof Error) messageApi.error(error.message);
    }
  };

  const purposeOptions = allPurposes.map((purpose) => ({
    value: purpose,
    label: dataSourcePurposeLabels[purpose],
    disabled: !supportedPurposes.includes(purpose),
  }));
  const testAvailable = canTest && Boolean(selectedDefinition?.connectionTestAvailable);
  const jdbcPreview = selectedKind === 'JDBC' ? jdbcUrlPreview(selectedType, watchedConnection) : '';
  const sections: { key: DataSourceFormSection; label: string }[] = [
    { key: 'basic', label: '基本信息' },
    { key: 'connection', label: '连接配置' },
    ...(selectedKind === 'JDBC' ? [{ key: 'advanced' as const, label: '高级设置' }] : []),
  ];

  const scrollToSection = (section: DataSourceFormSection) => {
    const target = contentRef.current?.querySelector<HTMLElement>(`#data-source-${section}`);
    target?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    setActiveSection(section);
  };

  const updateActiveSection = () => {
    const container = contentRef.current;
    if (!container) return;
    if (container.scrollTop + container.clientHeight >= container.scrollHeight - 8) {
      setActiveSection(sections.at(-1)?.key ?? 'basic');
      return;
    }
    const containerTop = container.getBoundingClientRect().top;
    const visible = sections.filter(({ key }) => {
      const target = container.querySelector<HTMLElement>(`#data-source-${key}`);
      return target && target.getBoundingClientRect().top - containerTop <= 40;
    });
    setActiveSection(visible.at(-1)?.key ?? 'basic');
  };

  const copyJdbcPreview = async () => {
    if (!jdbcPreview) return;
    try {
      await navigator.clipboard.writeText(jdbcPreview);
      messageApi.success('JDBC 连接地址已复制');
    } catch {
      messageApi.error('复制失败，请手动选择连接地址');
    }
  };

  const testStatus = (() => {
    if (!testAvailable) return { status: 'default' as const, text: '该类型暂不支持连接测试' };
    if (!lastTestResult) return { status: 'default' as const, text: '连接尚未测试' };
    if (lastTestResult.success) return { status: 'success' as const, text: `连接成功 · ${lastTestResult.elapsedMs} ms` };
    return { status: 'error' as const, text: `连接失败 · ${lastTestResult.elapsedMs} ms` };
  })();

  const headerStatus = !testAvailable
    ? <Tag>暂不支持测试</Tag>
    : lastTestResult?.success
      ? <Tag color="success">连接成功</Tag>
      : lastTestResult
        ? <Tag color="error">连接失败</Tag>
        : <Tag>未测试</Tag>;

  return (
    <>
      {messageContext}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        title={(
          <div className="data-source-drawer-title">
            <span className="data-source-drawer-title-icon" aria-hidden="true"><DatabaseOutlined /></span>
            <span className="data-source-drawer-title-copy">
              <span>{editing ? '编辑数据源' : '新建数据源'}</span>
              <Typography.Text type="secondary">配置连接信息并验证可用性</Typography.Text>
            </span>
          </div>
        )}
        extra={<span className="data-source-drawer-header-status">{headerStatus}</span>}
        open={open}
        size="min(1180px, 100vw)"
        className="data-source-drawer"
        closable={{ placement: 'end' }}
        onClose={closeDrawer}
        destroyOnHidden
        footer={(
          <div className="data-source-drawer-footer">
            <Badge status={testStatus.status} text={lastTestResult?.success ? '连接配置已验证' : testStatus.text} />
            <Space>
              {testAvailable && <Button icon={<ApiOutlined />} loading={testMutation.isPending} onClick={() => void testConnection()}>测试连接</Button>}
              <Button onClick={closeDrawer}>取消</Button>
              <Button type="primary" loading={createMutation.isPending || updateMutation.isPending} onClick={() => form.submit()}>保存数据源</Button>
            </Space>
          </div>
        )}
      >
        <div className="data-source-drawer-layout">
          <nav className="data-source-section-nav" aria-label="数据源配置分区">
            {sections.map((section, index) => (
              <Button
                key={section.key}
                type="text"
                className={activeSection === section.key ? 'is-active' : undefined}
                onClick={() => scrollToSection(section.key)}
              >
                <span className="data-source-section-step" aria-hidden="true">{index + 1}</span>
                <span className="data-source-section-step-label">{section.label}</span>
              </Button>
            ))}
          </nav>
          <div className="data-source-form-scroll" ref={contentRef} onScroll={updateActiveSection}>
            <Form<DataSourceFormValues>
              autoComplete="off"
              form={form}
              layout="vertical"
              onFinish={(values) => void submit(values)}
              onValuesChange={(changedValues) => {
                if ('type' in changedValues || 'connection' in changedValues) setLastTestResult(null);
              }}
            >
              <Card
                id="data-source-basic"
                size="small"
                className="data-source-section-card"
                title={<FormSectionTitle title="基本信息" description="填写数据源的基本信息，便于识别与管理" icon={<IdcardOutlined />} />}
                extra={(
                  <span className="data-source-enabled-control">
                    <span>启用</span>
                    <Form.Item name="enabled" valuePropName="checked" noStyle>
                      <Switch aria-label="启用数据源" />
                    </Form.Item>
                  </span>
                )}
              >
                <Row gutter={12}>
                  <Col span={12}><Form.Item label="名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入名称' }, { max: 100, message: '名称不能超过 100 个字符' }]}><Input placeholder="如：业务系统 PostgreSQL" /></Form.Item></Col>
                  <Col span={12}><Form.Item label="编码" name="code" rules={editing ? [] : [{ required: true, whitespace: true, message: '请输入编码' }, { pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/, message: '编码以字母开头，只能包含字母、数字和下划线' }]}><Input disabled={editing} placeholder="如：business_postgresql" /></Form.Item></Col>
                  <Col span={canViewDirectories ? 12 : 24}><Form.Item label="数据源类型" name="type" rules={[{ required: true, message: '请选择数据源类型' }]}><Select options={typeOptions} onChange={changeType} /></Form.Item></Col>
                  {canViewDirectories && <Col span={12}><Form.Item label="目录" name="directoryId"><TreeSelect allowClear treeDefaultExpandAll treeData={directoryTreeSelectData(directoriesQuery.data ?? [])} placeholder="未分类" /></Form.Item></Col>}
                  <Col span={24}>
                    <Form.Item label="用途" name="purposes" rules={[{ required: true, type: 'array', min: 1, message: '至少选择一个用途' }]}>
                      <Checkbox.Group className="data-source-purpose-options">
                        {purposeOptions.map((option) => (
                          <Checkbox
                            key={option.value}
                            value={option.value}
                            disabled={option.disabled}
                            className={selectedPurposes.includes(option.value) ? 'is-selected' : undefined}
                          >
                            <span className="data-source-purpose-icon">{purposeIcon(option.value)}</span>
                            <span className="data-source-purpose-copy">
                              <span>{option.label}</span>
                              <Typography.Text type="secondary">{purposeDescriptions[option.value]}</Typography.Text>
                            </span>
                          </Checkbox>
                        ))}
                      </Checkbox.Group>
                    </Form.Item>
                  </Col>
                  <Col span={24}><Form.Item label="说明" name="description" rules={[{ max: 1000, message: '说明不能超过 1000 个字符' }]}><Input.TextArea placeholder="可选" maxLength={1000} autoSize={{ minRows: 1, maxRows: 3 }} /></Form.Item></Col>
                </Row>
              </Card>

              <Card
                id="data-source-connection"
                size="small"
                className="data-source-section-card"
                title={<FormSectionTitle title="连接配置" description="填写连接信息并验证可用性" icon={<ApiOutlined />} />}
                extra={<span className="data-source-section-status"><Badge status={testStatus.status} text={testStatus.text} /></span>}
              >
                <Row gutter={12}>
                  {selectedType === 'TDENGINE_WEBSOCKET' && (
                    <Col span={24}>
                      <Alert
                        type="info"
                        showIcon
                        title="推荐的 TDengine 连接方式"
                        description="第一阶段仅发现和读取超级表，不列出子表，也不开放写入与自定义 SQL 输入。"
                      />
                    </Col>
                  )}
                  {selectedType === 'TDENGINE_RESTFUL' && (
                    <Col span={24}>
                      <Alert
                        type="warning"
                        showIcon
                        title="RESTful JDBC 仅用于旧环境兼容"
                        description="TDengine 官方已弃用 RestfulDriver；新连接请优先选择 WebSocket JDBC。batchfetch/batchLoad=true 不受支持。"
                      />
                    </Col>
                  )}
                  {selectedKind === 'JDBC' && <JdbcConnectionFields definition={selectedDefinition} editing={editing} />}
                  {selectedKind === 'KAFKA' && <KafkaConnectionFields />}
                  {selectedKind === 'S3' && <S3ConnectionFields />}
                  {selectedKind === 'HTTP_API' && <HttpApiConnectionFields authenticationType={authenticationType} />}
                  {selectedKind === 'JDBC' && (
                    <Col span={24}>
                      <Form.Item label="JDBC 连接地址预览" className="data-source-jdbc-preview">
                        <Input
                          readOnly
                          value={jdbcPreview}
                          placeholder="填写主机、端口和数据库后自动生成"
                          suffix={(
                            <Tooltip title={jdbcPreview ? '复制连接地址' : undefined}>
                              <Button
                                type="text"
                                size="small"
                                icon={<CopyOutlined />}
                                aria-label="复制 JDBC 连接地址"
                                className={jdbcPreview ? undefined : 'data-source-jdbc-preview-copy-hidden'}
                                disabled={!jdbcPreview}
                                onClick={() => void copyJdbcPreview()}
                              />
                            </Tooltip>
                          )}
                        />
                      </Form.Item>
                      <Typography.Text type="secondary" className="data-source-credential-tip">
                        密码将加密保存；编辑时留空表示不修改。高级参数由 JDBC 驱动以连接属性方式应用。
                      </Typography.Text>
                    </Col>
                  )}
                </Row>
                {(selectedKind === 'KAFKA' || selectedKind === 'S3') && (
                  <Typography.Text type="secondary">Kafka、S3 的真实测试与资源读取将在下一阶段开放。</Typography.Text>
                )}
              </Card>

              {selectedKind === 'JDBC' && (
                <div id="data-source-advanced" className="data-source-advanced-section">
                  <Collapse
                    defaultActiveKey={['advanced']}
                    items={[{
                      key: 'advanced',
                      label: <FormSectionTitle title="高级设置" description="SSL、驱动参数与连接选项" icon={<SettingOutlined />} />,
                      children: <JdbcConnectionOptionsFields definitions={selectedDefinition?.connectionOptions ?? []} />,
                    }]}
                  />
                </div>
              )}
            </Form>
          </div>
        </div>
      </Drawer>
      {testFailure && (
        <ConnectionTestResultModal
          open
          result={testFailure.result}
          targetLabel={testFailure.targetLabel}
          onClose={() => setTestFailure(null)}
        />
      )}
    </>
  );
};
