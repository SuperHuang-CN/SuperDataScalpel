import { Button, Checkbox, Col, Drawer, Form, Input, InputNumber, Row, Select, Space, Switch, TreeSelect, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
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
  type DataSource,
  type DataSourceConnectionInput,
  type DataSourceConnectionKind,
  type DataSourcePurpose,
  type DataSourceType,
  type DataSourceTypeDefinition,
  type UpdateDataSourceRequest,
} from '../model/dataSource';

interface DataSourceDrawerProps {
  dataSource: DataSource | null;
  open: boolean;
  canViewDirectories: boolean;
  canTest: boolean;
  onClose: () => void;
}

type KafkaSecurityProtocol = 'PLAINTEXT' | 'SSL' | 'SASL_PLAINTEXT' | 'SASL_SSL';

interface DataSourceConnectionFormValues {
  host?: string;
  port?: number;
  databaseName?: string;
  schemaName?: string;
  username?: string;
  password?: string;
  options?: Record<string, string>;
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
const kafkaSecurityProtocolOptions: { value: KafkaSecurityProtocol; label: string }[] = [
  { value: 'PLAINTEXT', label: 'PLAINTEXT' },
  { value: 'SSL', label: 'SSL' },
  { value: 'SASL_PLAINTEXT', label: 'SASL_PLAINTEXT' },
  { value: 'SASL_SSL', label: 'SASL_SSL' },
];

const connectionKindForType = (type: DataSourceType): DataSourceConnectionKind => {
  if (type === 'KAFKA') return 'KAFKA';
  if (type === 'S3') return 'S3';
  return 'JDBC';
};

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
    case 'JDBC':
      return {
        port: definition?.defaultPort ?? (type === 'POSTGRESQL' ? 5432 : undefined),
        schemaName: definition?.defaultSchema ?? undefined,
        options: Object.fromEntries(
          (definition?.connectionOptions ?? [])
            .filter((option) => option.defaultValue !== null)
            .map((option) => [option.key, option.defaultValue as string]),
        ),
      };
  }
};

const buildConnectionInput = (values: DataSourceFormValues): DataSourceConnectionInput => {
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
    case 'JDBC':
      return {
        kind: 'JDBC',
        host: requiredText(values.connection.host, '主机地址'),
        port: values.connection.port ?? 0,
        databaseName: requiredText(values.connection.databaseName, '数据库或服务名'),
        schemaName: values.connection.schemaName?.trim() || undefined,
        username: requiredText(values.connection.username, '用户名'),
        password: values.connection.password || undefined,
        options: values.connection.options,
      };
  }
};

const setEditingConnection = (dataSource: DataSource): DataSourceConnectionFormValues => {
  switch (dataSource.connection.kind) {
    case 'JDBC':
      return {
        host: dataSource.connection.host,
        port: dataSource.connection.port,
        databaseName: dataSource.connection.databaseName,
        schemaName: dataSource.connection.schemaName ?? undefined,
        username: dataSource.connection.username,
        options: dataSource.connection.options,
      };
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
  }
};

const JdbcConnectionFields = ({ definition }: { definition?: DataSourceTypeDefinition }) => (
  <>
    <Col span={16}>
      <Form.Item label="主机" name={['connection', 'host']} rules={[{ required: true, whitespace: true, message: '请输入主机地址' }]}>
        <Input placeholder="如：192.168.1.10" />
      </Form.Item>
    </Col>
    <Col span={8}>
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
        <Input autoComplete="off" />
      </Form.Item>
    </Col>
    <Col span={12}>
      <Form.Item label="密码" name={['connection', 'password']} extra="修改时留空表示不修改已保存的密码。">
        <Input.Password autoComplete="new-password" />
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
        <Input autoComplete="off" />
      </Form.Item>
    </Col>
    <Col span={12}>
      <Form.Item label="密码" name={['connection', 'password']} extra="修改时留空表示不修改已保存的密码。">
        <Input.Password autoComplete="new-password" />
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
        <Input autoComplete="off" />
      </Form.Item>
    </Col>
    <Col span={12}>
      <Form.Item label="SecretKey" name={['connection', 'secretKey']} extra="修改时留空表示不修改已保存的 SecretKey。">
        <Input.Password autoComplete="new-password" />
      </Form.Item>
    </Col>
    <Col span={12}>
      <Form.Item label="路径风格访问" name={['connection', 'pathStyleAccess']} valuePropName="checked">
        <Switch checkedChildren="是" unCheckedChildren="否" />
      </Form.Item>
    </Col>
  </>
);

export const DataSourceDrawer = ({ dataSource, open, canViewDirectories, canTest, onClose }: DataSourceDrawerProps) => {
  const [form] = Form.useForm<DataSourceFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const createMutation = useCreateDataSource();
  const updateMutation = useUpdateDataSource();
  const testMutation = useTestDraftDataSourceConnection();
  const dataSourceTypesQuery = useDataSourceTypes();
  const directoriesQuery = useDirectoryTree('DATA_SOURCE', open && canViewDirectories);
  const editing = Boolean(dataSource);
  const selectedType = Form.useWatch('type', form);
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
      form.setFieldsValue({
        code: dataSource.code,
        name: dataSource.name,
        directoryId: dataSource.directoryId ?? undefined,
        purposes: dataSource.purposes,
        type: dataSource.type,
        enabled: dataSource.enabled,
        description: dataSource.description ?? undefined,
        connection: setEditingConnection(dataSource),
      });
      return;
    }
    const defaultType: DataSourceType = 'POSTGRESQL';
    form.setFieldsValue({
      purposes: ['SOURCE'],
      type: defaultType,
      enabled: true,
      connection: defaultConnection(defaultType, dataSourceTypesQuery.data?.find((item) => item.id === defaultType)),
    });
  }, [dataSource, dataSourceTypesQuery.data, form, open]);

  const changeType = (type: DataSourceType) => {
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
        connection: buildConnectionInput(values),
      };
      if (dataSource) {
        await updateMutation.mutateAsync({ id: dataSource.id, request });
        messageApi.success('数据源已保存');
      } else if (values.code) {
        await createMutation.mutateAsync({ ...request, code: values.code } satisfies CreateDataSourceRequest);
        messageApi.success('数据源已创建');
      }
      onClose();
    } catch (error) {
      messageApi.error(error instanceof ApiError || error instanceof Error ? error.message : '保存数据源失败');
    }
  };

  const testConnection = async () => {
    try {
      const values = await form.validateFields();
      const connection = buildConnectionInput(values);
      if (connection.kind !== 'JDBC') return;
      const result = await testMutation.mutateAsync({ type: values.type, connection });
      if (result.success) {
        const product = result.databaseProduct ? `（${result.databaseProduct} ${result.databaseVersion ?? ''}，${result.elapsedMs} ms）` : '';
        messageApi.success(`${result.message}${product}`);
      } else {
        messageApi.error(result.message);
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
  const testAvailable = canTest && selectedKind === 'JDBC' && Boolean(selectedDefinition?.connectionTestAvailable);

  return (
    <>
      {messageContext}
      <Drawer
        title={editing ? '修改数据源' : '新建数据源'}
        open={open}
        size="large"
        className="data-source-drawer"
        onClose={onClose}
        destroyOnHidden
        footer={<Space>{testAvailable && <Button loading={testMutation.isPending} onClick={() => void testConnection()}>测试连接</Button>}<Button onClick={onClose}>取消</Button><Button type="primary" loading={createMutation.isPending || updateMutation.isPending} onClick={() => form.submit()}>保存</Button></Space>}
      >
        <Form<DataSourceFormValues> form={form} layout="vertical" onFinish={(values) => void submit(values)}>
          <Row gutter={12}>
            <Col span={12}><Form.Item label="名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入名称' }, { max: 100, message: '名称不能超过 100 个字符' }]}><Input placeholder="如：业务系统 PostgreSQL" /></Form.Item></Col>
            <Col span={12}><Form.Item label="编码" name="code" rules={editing ? [] : [{ required: true, whitespace: true, message: '请输入编码' }, { pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/, message: '编码以字母开头，只能包含字母、数字和下划线' }]}><Input disabled={editing} placeholder="如：business_postgresql" /></Form.Item></Col>
            <Col span={12}><Form.Item label="用途" name="purposes" rules={[{ required: true, type: 'array', min: 1, message: '至少选择一个用途' }]}><Checkbox.Group options={purposeOptions} /></Form.Item></Col>
            {canViewDirectories && <Col span={12}><Form.Item label="目录" name="directoryId"><TreeSelect allowClear treeDefaultExpandAll treeData={directoryTreeSelectData(directoriesQuery.data ?? [])} placeholder="未分类" /></Form.Item></Col>}
            <Col span={12}><Form.Item label="连接类型" name="type" rules={[{ required: true, message: '请选择连接类型' }]}><Select options={typeOptions} onChange={changeType} /></Form.Item></Col>
            <Col span={12}><Form.Item label="启用" name="enabled" valuePropName="checked"><Switch checkedChildren="启用" unCheckedChildren="停用" /></Form.Item></Col>
            <Col span={12}><Form.Item label="说明" name="description" rules={[{ max: 1000, message: '说明不能超过 1000 个字符' }]}><Input placeholder="可选" maxLength={1000} /></Form.Item></Col>
          </Row>
          <div className="data-source-form-section-title">连接配置</div>
          <Row gutter={12}>
            {selectedKind === 'JDBC' && <JdbcConnectionFields definition={selectedDefinition} />}
            {selectedKind === 'KAFKA' && <KafkaConnectionFields />}
            {selectedKind === 'S3' && <S3ConnectionFields />}
          </Row>
          {selectedKind === 'JDBC' && selectedDefinition && selectedDefinition.connectionOptions.length > 0 && <><div className="data-source-form-section-title">高级连接参数</div><Row gutter={12}>{selectedDefinition.connectionOptions.map((option) => <Col span={12} key={option.key}><Form.Item label={option.label} name={['connection', 'options', option.key]}>{option.type === 'TEXT' ? <Input placeholder={option.defaultValue ?? '可选'} /> : <Select allowClear placeholder="使用驱动默认值" options={option.choices} />}</Form.Item></Col>)}</Row></>}
          {selectedKind !== 'JDBC' && <div className="data-source-form-section-title">连接器状态：Kafka、S3 的真实测试与资源读取将在下一阶段开放。</div>}
        </Form>
      </Drawer>
    </>
  );
};
