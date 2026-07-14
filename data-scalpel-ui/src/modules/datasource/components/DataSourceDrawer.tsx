import { Button, Checkbox, Col, Drawer, Form, Input, InputNumber, Row, Select, Space, Switch, TreeSelect, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
import { directoryTreeSelectData, useDirectoryTree } from '../../directory';
import {
  useCreateDataSource,
  useDatabaseTypes,
  useTestDraftDataSourceConnection,
  useUpdateDataSource,
} from '../hooks/useDataSources';
import {
  databaseTypeLabels,
  dataSourcePurposeLabels,
  type CreateDataSourceRequest,
  type DataSource,
  type DataSourceConnectionInput,
  type DataSourcePurpose,
  type DatabaseType,
  type UpdateDataSourceRequest,
} from '../model/dataSource';

interface DataSourceDrawerProps {
  dataSource: DataSource | null;
  open: boolean;
  canViewDirectories: boolean;
  canTest: boolean;
  onClose: () => void;
}

interface DataSourceFormValues {
  code?: string;
  name: string;
  directoryId?: string;
  purposes: DataSourcePurpose[];
  databaseType: DatabaseType;
  enabled: boolean;
  description?: string;
  connection: DataSourceConnectionInput;
}

const purposeOptions = (Object.entries(dataSourcePurposeLabels) as [DataSourcePurpose, string][])
  .map(([value, label]) => ({ value, label }));

export const DataSourceDrawer = ({ dataSource, open, canViewDirectories, canTest, onClose }: DataSourceDrawerProps) => {
  const [form] = Form.useForm<DataSourceFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const createMutation = useCreateDataSource();
  const updateMutation = useUpdateDataSource();
  const testMutation = useTestDraftDataSourceConnection();
  const databaseTypesQuery = useDatabaseTypes();
  const directoriesQuery = useDirectoryTree('DATA_SOURCE', open && canViewDirectories);
  const editing = Boolean(dataSource);
  const selectedDatabaseType = Form.useWatch('databaseType', form);
  const selectedDefinition = databaseTypesQuery.data?.find((definition) => definition.id === selectedDatabaseType);
  const databaseTypeOptions = databaseTypesQuery.data?.map((definition) => ({
    value: definition.id,
    label: definition.driverAvailable ? definition.displayName : `${definition.displayName}（驱动不可用）`,
  })) ?? Object.entries(databaseTypeLabels).map(([value, label]) => ({ value: value as DatabaseType, label }));

  useEffect(() => {
    if (!open) return;

    form.resetFields();
    if (dataSource) {
      form.setFieldsValue({
        code: dataSource.code,
        name: dataSource.name,
        directoryId: dataSource.directoryId ?? undefined,
        purposes: dataSource.purposes,
        databaseType: dataSource.databaseType,
        enabled: dataSource.enabled,
        description: dataSource.description ?? undefined,
        connection: {
          host: dataSource.connection.host,
          port: dataSource.connection.port,
          databaseName: dataSource.connection.databaseName,
          schemaName: dataSource.connection.schemaName ?? undefined,
          username: dataSource.connection.username,
          options: dataSource.connection.options,
        },
      });
      return;
    }

    form.setFieldsValue({
      purposes: ['SOURCE'],
      databaseType: 'POSTGRESQL',
      enabled: true,
      connection: { port: 5432 },
    });
  }, [dataSource, form, open]);

  const changeDatabaseType = (databaseType: DatabaseType) => {
    const definition = databaseTypesQuery.data?.find((item) => item.id === databaseType);
    if (!definition) return;

    const options = Object.fromEntries(
      definition.connectionOptions
        .filter((option) => option.defaultValue !== null)
        .map((option) => [option.key, option.defaultValue as string]),
    );
    form.setFieldValue(['connection', 'port'], definition.defaultPort);
    form.setFieldValue(['connection', 'schemaName'], definition.defaultSchema ?? undefined);
    form.setFieldValue(['connection', 'options'], options);
  };

  const submit = async (values: DataSourceFormValues) => {
    try {
      const request: UpdateDataSourceRequest = {
        name: values.name,
        directoryId: values.directoryId,
        purposes: values.purposes,
        databaseType: values.databaseType,
        enabled: values.enabled,
        description: values.description,
        connection: values.connection,
      };

      if (dataSource) {
        await updateMutation.mutateAsync({ id: dataSource.id, request });
        messageApi.success('数据源已保存');
      } else {
        if (!values.code) return;
        const createRequest: CreateDataSourceRequest = { ...request, code: values.code };
        await createMutation.mutateAsync(createRequest);
        messageApi.success('数据源已创建');
      }
      onClose();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存数据源失败');
    }
  };

  const testConnection = async () => {
    try {
      const values = await form.validateFields();
      const result = await testMutation.mutateAsync({
        databaseType: values.databaseType,
        connection: values.connection,
      });
      if (result.success) {
        const product = result.databaseProduct
          ? `（${result.databaseProduct} ${result.databaseVersion ?? ''}，${result.elapsedMs} ms）`
          : '';
        messageApi.success(`${result.message}${product}`);
      } else {
        messageApi.error(result.message);
      }
    } catch (error) {
      if (error instanceof ApiError) {
        messageApi.error(error.message);
      }
    }
  };

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
        footer={(
          <Space>
            {canTest && <Button loading={testMutation.isPending} onClick={() => void testConnection()}>测试连接</Button>}
            <Button onClick={onClose}>取消</Button>
            <Button
              type="primary"
              loading={createMutation.isPending || updateMutation.isPending}
              onClick={() => form.submit()}
            >
              保存
            </Button>
          </Space>
        )}
      >
        <Form<DataSourceFormValues> form={form} layout="vertical" onFinish={(values) => void submit(values)}>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                label="名称"
                name="name"
                rules={[{ required: true, whitespace: true, message: '请输入名称' }, { max: 100, message: '名称不能超过 100 个字符' }]}
              >
                <Input placeholder="如：业务系统 PostgreSQL" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="编码"
                name="code"
                rules={editing ? [] : [
                  { required: true, whitespace: true, message: '请输入编码' },
                  { pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/, message: '编码以字母开头，只能包含字母、数字和下划线' },
                ]}
              >
                <Input disabled={editing} placeholder="如：business_postgresql" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="用途" name="purposes" rules={[{ required: true, type: 'array', min: 1, message: '至少选择一个用途' }]}>
                <Checkbox.Group options={purposeOptions} />
              </Form.Item>
            </Col>
            {canViewDirectories && (
              <Col span={12}>
                <Form.Item label="目录" name="directoryId">
                  <TreeSelect
                    allowClear
                    treeDefaultExpandAll
                    treeData={directoryTreeSelectData(directoriesQuery.data ?? [])}
                    placeholder="未分类"
                  />
                </Form.Item>
              </Col>
            )}
            <Col span={12}>
              <Form.Item label="数据库类型" name="databaseType" rules={[{ required: true, message: '请选择数据库类型' }]}>
                <Select options={databaseTypeOptions} onChange={changeDatabaseType} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="启用" name="enabled" valuePropName="checked">
                <Switch checkedChildren="启用" unCheckedChildren="停用" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="说明" name="description" rules={[{ max: 1000, message: '说明不能超过 1000 个字符' }]}>
                <Input placeholder="可选" maxLength={1000} />
              </Form.Item>
            </Col>
          </Row>

          <div className="data-source-form-section-title">连接配置</div>
          <Row gutter={12}>
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
              <Form.Item label={selectedDefinition?.databaseNameLabel ?? '数据库/服务名'} name={['connection', 'databaseName']} rules={[{ required: true, whitespace: true, message: '请输入数据库或服务名' }]}>
                <Input />
              </Form.Item>
            </Col>
            {selectedDefinition?.namespaceMode !== 'CATALOG' && (
              <Col span={12}>
                <Form.Item label={selectedDefinition?.schemaNameLabel ?? 'Schema'} name={['connection', 'schemaName']} rules={[{ max: 128, message: 'Schema 不能超过 128 个字符' }]}>
                  <Input placeholder={selectedDefinition?.defaultSchema ? `可选，默认 ${selectedDefinition.defaultSchema}` : '可选'} />
                </Form.Item>
              </Col>
            )}
            <Col span={12}>
              <Form.Item label="用户名" name={['connection', 'username']} rules={[{ required: true, whitespace: true, message: '请输入用户名' }]}>
                <Input autoComplete="off" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="密码" name={['connection', 'password']} extra={editing ? '留空表示不修改已保存的密码。' : undefined}>
                <Input.Password autoComplete="new-password" />
              </Form.Item>
            </Col>
          </Row>
          {selectedDefinition && selectedDefinition.connectionOptions.length > 0 && (
            <>
              <div className="data-source-form-section-title">高级连接参数</div>
              <Row gutter={12}>
                {selectedDefinition.connectionOptions.map((option) => (
                  <Col span={12} key={option.key}>
                    <Form.Item label={option.label} name={['connection', 'options', option.key]}>
                      {option.type === 'TEXT' ? (
                        <Input placeholder={option.defaultValue ?? '可选'} />
                      ) : (
                        <Select allowClear placeholder="使用驱动默认值" options={option.choices} />
                      )}
                    </Form.Item>
                  </Col>
                ))}
              </Row>
            </>
          )}
        </Form>
      </Drawer>
    </>
  );
};
