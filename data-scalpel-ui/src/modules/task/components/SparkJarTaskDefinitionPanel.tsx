import {
  DeleteOutlined,
  DownloadOutlined,
  InboxOutlined,
  PlusOutlined,
  SaveOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  Upload,
  message,
  type UploadFile,
} from 'antd';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useBlocker, type BlockerFunction } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { useDataModels } from '../../model';
import { useDataSources } from '../../datasource';
import { CanvasKafkaTopicSelect } from '../canvas/components/CanvasKafkaSelectors';
import {
  useDownloadSparkJarTemplate,
  useSparkJarTaskDefinition,
  useUpdateSparkJarTaskDefinition,
  useUploadSparkJar,
} from '../hooks/useTasks';
import type {
  DataTask,
  SparkJarDefinitionEntry,
  SparkJarResourceAccessMode,
  SparkJarResourceBinding,
  SparkJarResourceType,
  UpdateSparkJarTaskDefinitionRequest,
} from '../model/task';

interface SparkJarDefinitionFormValues {
  parameters: SparkJarDefinitionEntry[];
  sparkConf: SparkJarDefinitionEntry[];
  resourceBindings: Array<Omit<SparkJarResourceBinding, 'resourceName'>>;
  timeoutSeconds: number;
}

interface SparkJarTaskDefinitionPanelProps {
  task: DataTask;
  toolbarContext?: ReactNode;
  protectNavigation?: boolean;
}

const accessModeOptions: Array<{ value: SparkJarResourceAccessMode; label: string }> = [
  { value: 'READ', label: '只读' },
  { value: 'WRITE', label: '只写' },
  { value: 'READ_WRITE', label: '读写' },
];

const baseResourceTypeOptions: Array<{ value: SparkJarResourceType; label: string }> = [
  { value: 'MODEL', label: '模型' },
  { value: 'JDBC_DATA_SOURCE', label: 'JDBC 数据源' },
];

const formatBytes = (value: number): string => {
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(2)} MiB`;
  if (value >= 1024) return `${(value / 1024).toFixed(2)} KiB`;
  return `${value} B`;
};

const EntryEditor = ({
  name,
  addLabel,
  keyPlaceholder,
  valueMax,
  valueRequired,
  keyRules,
}: {
  name: 'parameters' | 'sparkConf';
  addLabel: string;
  keyPlaceholder: string;
  valueMax: number;
  valueRequired: boolean;
  keyRules: Array<{ required?: boolean; message?: string; max?: number; pattern?: RegExp }>;
}) => (
  <Form.List name={name}>
    {(fields, { add, remove }) => (
      <Space orientation="vertical" size={8} className="spark-jar-list-editor">
        <Table
          size="small"
          rowKey="key"
          pagination={false}
          dataSource={fields}
          locale={{ emptyText: '暂无配置' }}
          columns={[
            {
              title: 'Key',
              width: '38%',
              render: (_, field) => (
                <Form.Item name={[field.name, 'name']} rules={keyRules} noStyle>
                  <Input placeholder={keyPlaceholder} />
                </Form.Item>
              ),
            },
            {
              title: 'Value',
              render: (_, field) => (
                <Form.Item
                  name={[field.name, 'value']}
                  rules={[
                    ...(valueRequired ? [{ required: true, message: '请输入 Value' }] : []),
                    { max: valueMax },
                  ]}
                  noStyle
                >
                  <Input placeholder="原始字符串" />
                </Form.Item>
              ),
            },
            {
              title: '操作',
              width: 56,
              align: 'center',
              render: (_, field) => (
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  aria-label="删除配置项"
                  onClick={() => remove(field.name)}
                />
              ),
            },
          ]}
        />
        <Button type="dashed" block icon={<PlusOutlined />} disabled={fields.length >= 100} onClick={() => add({ name: '', value: '' })}>
          {addLabel}
        </Button>
      </Space>
    )}
  </Form.List>
);

export const SparkJarTaskDefinitionPanel = ({
  task,
  toolbarContext,
  protectNavigation = true,
}: SparkJarTaskDefinitionPanelProps) => {
  const [form] = Form.useForm<SparkJarDefinitionFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const [uploadFiles, setUploadFiles] = useState<UploadFile[]>([]);
  const [initialized, setInitialized] = useState(false);
  const [dirty, setDirty] = useState(false);
  const definitionQuery = useSparkJarTaskDefinition(task.id);
  const modelsQuery = useDataModels({ search: 'status:"PUBLISHED"', page: 0, size: 500, sort: 'code' });
  const dataSourcesQuery = useDataSources({ page: 0, size: 500, sort: 'code' });
  const updateMutation = useUpdateSparkJarTaskDefinition();
  const uploadMutation = useUploadSparkJar();
  const templateMutation = useDownloadSparkJarTemplate();
  const resourceBindings = Form.useWatch('resourceBindings', form) ?? [];
  const definition = definitionQuery.data;
  const streaming = task.type === 'SPARK_STREAMING_JAR';
  const resourceTypeOptions = useMemo<Array<{ value: SparkJarResourceType; label: string }>>(() => (
    streaming
      ? [...baseResourceTypeOptions, { value: 'KAFKA_TOPIC', label: 'Kafka Topic' }]
      : baseResourceTypeOptions
  ), [streaming]);
  const blocker = useBlocker(useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) => protectNavigation && dirty && (
      currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search
    ),
    [dirty, protectNavigation],
  ));

  useEffect(() => {
    if (!definition) return;
    form.setFieldsValue({
      parameters: definition.parameters,
      sparkConf: definition.sparkConf,
      resourceBindings: definition.resourceBindings.map(({ resourceName: _resourceName, ...binding }) => binding),
      timeoutSeconds: definition.timeoutSeconds,
    });
    setInitialized(true);
    setDirty(false);
  }, [definition, form]);

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!dirty) return;
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const modelOptions = useMemo(() => (modelsQuery.data?.content ?? []).map((model) => ({
    value: model.id,
    label: `${model.name} · ${model.code}`,
  })), [modelsQuery.data?.content]);
  const jdbcOptions = useMemo(() => (dataSourcesQuery.data?.content ?? [])
    .filter((source) => source.connectionKind === 'JDBC')
    .map((source) => ({ value: source.id, label: `${source.name} · ${source.code}` })), [dataSourcesQuery.data?.content]);

  const kafkaOptions = (accessMode: SparkJarResourceAccessMode | undefined) => (
    (dataSourcesQuery.data?.content ?? [])
      .filter((source) => {
        if (!source.enabled || source.connectionKind !== 'KAFKA') return false;
        if (accessMode === 'READ') return source.purposes.includes('SOURCE');
        if (accessMode === 'WRITE') return source.purposes.includes('DISTRIBUTION');
        return source.purposes.includes('SOURCE') && source.purposes.includes('DISTRIBUTION');
      })
      .map((source) => ({ value: source.id, label: `${source.name} · ${source.code}` }))
  );

  const resourceOptions = (binding: Omit<SparkJarResourceBinding, 'resourceName'> | undefined) => {
    const options = binding?.resourceType === 'MODEL'
      ? [...modelOptions]
      : binding?.resourceType === 'KAFKA_TOPIC'
        ? kafkaOptions(binding.accessMode)
        : [...jdbcOptions];
    const saved = definition?.resourceBindings.find((item) => (
      item.bindingName === binding?.bindingName && item.resourceType === binding?.resourceType
    ));
    if (binding?.resourceId && !options.some((option) => option.value === binding.resourceId)) {
      options.push({ value: binding.resourceId, label: `${saved?.resourceName ?? '资源已删除或不可用'} · 当前绑定` });
    }
    return options;
  };

  const save = async (): Promise<boolean> => {
    try {
      const values = await form.validateFields();
      const request: UpdateSparkJarTaskDefinitionRequest = {
        parameters: values.parameters ?? [],
        sparkConf: values.sparkConf ?? [],
        resourceBindings: values.resourceBindings ?? [],
        timeoutSeconds: values.timeoutSeconds,
      };
      const saved = await updateMutation.mutateAsync({ id: task.id, request });
      form.setFieldsValue({
        parameters: saved.parameters,
        sparkConf: saved.sparkConf,
        resourceBindings: saved.resourceBindings.map(({ resourceName: _resourceName, ...binding }) => binding),
        timeoutSeconds: saved.timeoutSeconds,
      });
      setDirty(false);
      messageApi.success(`Spark JAR 定义已保存为 v${saved.definitionVersion}`);
      return true;
    } catch (error) {
      if (error instanceof ApiError) messageApi.error(error.message);
      return false;
    }
  };

  const upload = async () => {
    const file = uploadFiles[0]?.originFileObj;
    if (!file) {
      messageApi.warning('请先选择 JAR 文件');
      return;
    }
    if (dirty && !(await save())) {
      messageApi.warning('请先修正并保存当前配置');
      return;
    }
    try {
      const saved = await uploadMutation.mutateAsync({ id: task.id, file });
      setUploadFiles([]);
      messageApi.success(`已上传 ${saved.jar?.fileName ?? file.name}`);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '上传 Spark JAR 失败');
    }
  };

  const downloadTemplate = async () => {
    try {
      downloadBlob(await templateMutation.mutateAsync(task.id), 'datascalpel-spark-job-template.zip');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '下载 Maven 模板失败');
    }
  };

  if (definitionQuery.isPending) return <div className="task-detail-tab-panel"><Spin tip="正在加载 Spark JAR 定义…" /></div>;
  if (definitionQuery.isError || !definition) return (
    <Alert
      type="error"
      showIcon
      message="Spark JAR 定义加载失败"
      action={<Button size="small" onClick={() => void definitionQuery.refetch()}>重试</Button>}
    />
  );

  return (
    <div className="task-detail-tab-panel spark-jar-definition-panel">
      {messageContext}
      <div className="task-detail-tab-toolbar spark-jar-definition-toolbar">
        {toolbarContext ?? <Typography.Text strong>Spark JAR 定义</Typography.Text>}
        <Space wrap>
          {dirty && <Tag color="warning">未保存</Tag>}
          <Button icon={<DownloadOutlined />} loading={templateMutation.isPending} onClick={() => void downloadTemplate()}>
            下载 Maven 模板
          </Button>
          <Button type="primary" icon={<SaveOutlined />} loading={updateMutation.isPending} onClick={() => void save()}>
            保存配置
          </Button>
        </Space>
      </div>
      <div className="spark-jar-definition-scroll">
        <section className="spark-jar-definition-section">
          <div className="spark-jar-definition-section-title">用户作业 JAR</div>
          {definition.jar ? (
            <Descriptions bordered size="small" column={3}>
              <Descriptions.Item label="文件名">{definition.jar.fileName}</Descriptions.Item>
              <Descriptions.Item label="大小">{formatBytes(definition.jar.sizeBytes)}</Descriptions.Item>
              <Descriptions.Item label="Job API">v{definition.jar.jobApiVersion}</Descriptions.Item>
              <Descriptions.Item label="Job Class" span={2}><Typography.Text code copyable>{definition.jar.jobClass}</Typography.Text></Descriptions.Item>
              <Descriptions.Item label="作业模式">
                <Tag color={definition.jobMode === 'STREAMING' ? 'processing' : 'default'}>
                  {definition.jobMode === 'STREAMING' ? 'STREAMING' : 'BATCH'}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="定义版本">v{definition.definitionVersion}</Descriptions.Item>
              <Descriptions.Item label="SHA-256" span={3}><Typography.Text code copyable ellipsis>{definition.jar.sha256}</Typography.Text></Descriptions.Item>
            </Descriptions>
          ) : <Alert type="warning" showIcon message="尚未上传用户作业 JAR" />}
          <div className="spark-jar-upload-row">
            <Upload.Dragger
              accept=".jar,application/java-archive,application/zip"
              maxCount={1}
              fileList={uploadFiles}
              beforeUpload={(file) => {
                if (file.size > 100 * 1024 * 1024) {
                  messageApi.error('JAR 文件不能超过 100 MiB');
                  return Upload.LIST_IGNORE;
                }
                setUploadFiles([{
                  uid: file.uid,
                  name: file.name,
                  size: file.size,
                  type: file.type,
                  status: 'done',
                  originFileObj: file,
                }]);
                return false;
              }}
              onRemove={() => { setUploadFiles([]); return true; }}
            >
              <p className="ant-upload-drag-icon"><InboxOutlined /></p>
              <p className="ant-upload-text">选择或拖入 JAR 文件</p>
              <p className="ant-upload-hint">平台只读取 Manifest 和类条目，不会在上传阶段加载或执行用户代码。</p>
            </Upload.Dragger>
            <Button
              icon={<UploadOutlined />}
              loading={uploadMutation.isPending || updateMutation.isPending}
              disabled={uploadFiles.length === 0}
              onClick={() => void upload()}
            >
              {definition.jar ? '覆盖当前 JAR' : '上传 JAR'}
            </Button>
          </div>
        </section>

        <Form<SparkJarDefinitionFormValues>
          autoComplete="off"
          form={form}
          layout="vertical"
          initialValues={{ parameters: [], sparkConf: [], resourceBindings: [], timeoutSeconds: 3600 }}
          onValuesChange={() => initialized && setDirty(true)}
        >
          <section className="spark-jar-definition-section">
            <div className="spark-jar-definition-section-title">运行参数</div>
            <EntryEditor
              name="parameters"
              addLabel="添加参数"
              keyPlaceholder="例如 processingDate"
              valueMax={4000}
              valueRequired={false}
              keyRules={[{ required: true, message: '请输入参数 Key' }, { max: 100 }]}
            />
          </section>

          <section className="spark-jar-definition-section">
            <div className="spark-jar-definition-section-title">Spark Conf</div>
            <EntryEditor
              name="sparkConf"
              addLabel="添加 Spark Conf"
              keyPlaceholder="spark.sql.shuffle.partitions"
              valueMax={2000}
              valueRequired
              keyRules={[
                { required: true, message: '请输入 Spark Conf Key' },
                { max: 500 },
                { pattern: /^spark\./, message: 'Key 必须以 spark. 开头' },
              ]}
            />
          </section>

          <section className="spark-jar-definition-section">
            <div className="spark-jar-definition-section-title">资源绑定</div>
            <Form.List name="resourceBindings">
              {(fields, { add, remove }) => (
                <Space orientation="vertical" size={8} className="spark-jar-list-editor">
                  <Table
                    size="small"
                    rowKey="key"
                    pagination={false}
                    dataSource={fields}
                    locale={{ emptyText: '无需平台资源时可以保持为空' }}
                    columns={[
                      {
                        title: '绑定名', width: '22%',
                        render: (_, field) => <Form.Item name={[field.name, 'bindingName']} rules={[{ required: true, message: '请输入绑定名' }, { max: 100 }]} noStyle><Input placeholder="source_model" /></Form.Item>,
                      },
                      {
                        title: '资源类型', width: 150,
                        render: (_, field) => <Form.Item name={[field.name, 'resourceType']} rules={[{ required: true, message: '请选择类型' }]} noStyle><Select options={resourceTypeOptions} onChange={() => {
                          form.setFieldValue(['resourceBindings', field.name, 'resourceId'], undefined);
                          form.setFieldValue(['resourceBindings', field.name, 'topicName'], undefined);
                        }} /></Form.Item>,
                      },
                      {
                        title: '资源',
                        render: (_: unknown, field: { name: number }) => {
                          const binding = resourceBindings[field.name];
                          return <Form.Item name={[field.name, 'resourceId']} rules={[{ required: true, message: '请选择资源' }]} noStyle><Select showSearch optionFilterProp="label" loading={modelsQuery.isFetching || dataSourcesQuery.isFetching} disabled={!binding?.resourceType} options={resourceOptions(binding)} placeholder="请选择资源" /></Form.Item>;
                        },
                      },
                      ...(streaming ? [{
                        title: 'Topic',
                        width: 220,
                        render: (_: unknown, field: { name: number }) => {
                          const binding = resourceBindings[field.name];
                          if (binding?.resourceType !== 'KAFKA_TOPIC') return <Typography.Text type="secondary">—</Typography.Text>;
                          return (
                            <Form.Item name={[field.name, 'topicName']} rules={[{ required: true, message: '请选择 Topic' }]} noStyle>
                              <CanvasKafkaTopicSelect
                                dataSourceId={binding.resourceId ?? ''}
                                placeholder="远程搜索 Topic"
                              />
                            </Form.Item>
                          );
                        },
                      }] : []),
                      {
                        title: '访问方式', width: 130,
                        render: (_, field) => <Form.Item name={[field.name, 'accessMode']} rules={[{ required: true, message: '请选择访问方式' }]} noStyle><Select options={accessModeOptions} /></Form.Item>,
                      },
                      {
                        title: '操作', width: 56, align: 'center',
                        render: (_, field) => <Button type="text" danger icon={<DeleteOutlined />} aria-label="删除资源绑定" onClick={() => remove(field.name)} />,
                      },
                    ]}
                  />
                  <Button type="dashed" block icon={<PlusOutlined />} disabled={fields.length >= 200} onClick={() => add({ bindingName: '', resourceType: 'MODEL', resourceId: undefined, topicName: undefined, accessMode: 'READ' })}>
                    添加资源绑定
                  </Button>
                </Space>
              )}
            </Form.List>
          </section>

          <section className="spark-jar-definition-section spark-jar-runtime-options">
            <Form.Item name="timeoutSeconds" label={streaming ? '作业启动超时（秒）' : '执行超时（秒）'} rules={[{ required: true }, { type: 'number', min: 1, max: 86400 }]}>
              <InputNumber min={1} max={86400} precision={0} />
            </Form.Item>
            <Typography.Text type="secondary">
              {streaming
                ? 'start() 必须完成查询注册后返回。Trigger 与 Output Mode 由用户代码控制，Checkpoint 由平台分配。'
                : 'SDK 与 Spark API 使用 provided 依赖。SDK 写操作立即执行，多个目标之间不提供跨目标事务。'}
            </Typography.Text>
          </section>
        </Form>
      </div>
      <Modal
        rootClassName="business-overlay business-modal-overlay"
        open={blocker.state === 'blocked'}
        title="存在未保存的 Spark JAR 配置"
        okText="放弃修改并离开"
        okButtonProps={{ danger: true }}
        cancelText="继续编辑"
        onOk={() => blocker.state === 'blocked' && blocker.proceed()}
        onCancel={() => blocker.state === 'blocked' && blocker.reset()}
      >
        参数、Spark Conf、资源绑定或超时设置尚未保存。
      </Modal>
    </div>
  );
};
