import { ArrowLeftOutlined, PlayCircleOutlined, SaveOutlined, SendOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Divider,
  Form,
  InputNumber,
  List,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useBlocker, useNavigate, useParams, type BlockerFunction } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { useDataModel, useDataModels, type DataModelField } from '../../model';
import { useCurrentUser } from '../../system';
import { MonacoSqlEditor } from '../components/MonacoSqlEditor';
import { TaskRunsPanel } from '../components/TaskRunsPanel';
import {
  useRunTask,
  useTask,
  useTaskCommand,
  useTaskDefinition,
  useUpdateTaskDefinition,
  useValidateTaskDefinition,
} from '../hooks/useTasks';
import type { LocalSqlDefinitionValidation, LocalSqlWriteMode, UpdateLocalSqlTaskDefinitionRequest } from '../model/task';

interface DefinitionFormValues {
  inputModelIds: string[];
  outputModelId?: string;
  writeMode: LocalSqlWriteMode;
  timeoutSeconds: number;
}

const modelRequest = { search: 'status:"PUBLISHED"', page: 0, size: 500, sort: 'code' } as const;

export const TaskDefinitionPage = () => {
  const { taskId } = useParams();
  const navigate = useNavigate();
  const [form] = Form.useForm<DefinitionFormValues>();
  const [sql, setSql] = useState('SELECT\n');
  const [initialized, setInitialized] = useState(false);
  const [validation, setValidation] = useState<LocalSqlDefinitionValidation | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const taskQuery = useTask(taskId);
  const definitionQuery = useTaskDefinition(taskId);
  const modelsQuery = useDataModels(modelRequest);
  const selectedOutputId = Form.useWatch('outputModelId', form);
  const selectedInputIds = Form.useWatch('inputModelIds', form);
  const selectedWriteMode = Form.useWatch('writeMode', form);
  const selectedTimeoutSeconds = Form.useWatch('timeoutSeconds', form);
  const outputDetailQuery = useDataModel(selectedOutputId, Boolean(selectedOutputId));
  const saveMutation = useUpdateTaskDefinition();
  const validateMutation = useValidateTaskDefinition();
  const publishMutation = useTaskCommand('publish');
  const enableMutation = useTaskCommand('enable');
  const runMutation = useRunTask();
  const currentUser = useCurrentUser();
  const permissions = new Set(currentUser.data?.permissions ?? []);
  const canUpdate = permissions.has('task.update');
  const canPublish = permissions.has('task.publish');
  const canExecute = permissions.has('task.execute');
  const task = taskQuery.data;
  const definition = definitionQuery.data;
  const editable = task?.status === 'DRAFT' || task?.status === 'DISABLED';
  const candidateModels = modelsQuery.data?.content ?? [];
  const selectedOutput = candidateModels.find((model) => model.id === selectedOutputId)
    ?? (definition?.output ? candidateModels.find((model) => model.id === definition.output?.modelId) : undefined);
  const sameStorageModels = selectedOutput
    ? candidateModels.filter((model) => model.storageDataSourceId === selectedOutput.storageDataSourceId)
    : candidateModels;
  const savedFingerprint = useMemo(() => definition?.configured ? JSON.stringify({
    sql: definition.sql,
    inputModelIds: definition.inputs.map((input) => input.modelId),
    outputModelId: definition.output?.modelId,
    writeMode: definition.writeMode,
    timeoutSeconds: definition.timeoutSeconds,
  }) : null, [definition]);
  const formFingerprint = useMemo(() => JSON.stringify({
    sql,
    inputModelIds: selectedInputIds ?? [],
    outputModelId: selectedOutputId,
    writeMode: selectedWriteMode,
    timeoutSeconds: selectedTimeoutSeconds,
  }), [selectedInputIds, selectedOutputId, selectedTimeoutSeconds, selectedWriteMode, sql]);
  const dirty = initialized && formFingerprint !== savedFingerprint;
  const blocker = useBlocker(useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) => dirty && (
      currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search
    ),
    [dirty],
  ));

  useEffect(() => {
    if (!definition || initialized) return;
    const initialization = window.setTimeout(() => {
      setSql(definition.sql ?? 'SELECT\n');
      form.setFieldsValue({
        inputModelIds: definition.inputs.map((input) => input.modelId),
        outputModelId: definition.output?.modelId,
        writeMode: definition.writeMode,
        timeoutSeconds: definition.timeoutSeconds,
      });
      setInitialized(true);
    }, 0);
    return () => window.clearTimeout(initialization);
  }, [definition, form, initialized]);

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!dirty) return;
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  useEffect(() => {
    if (!dirty && blocker.state === 'blocked') blocker.reset();
  }, [blocker, dirty]);

  const save = async () => {
    if (!taskId) return;
    const values = await form.validateFields();
    if (!values.outputModelId) return;
    const request: UpdateLocalSqlTaskDefinitionRequest = { ...values, outputModelId: values.outputModelId, sql };
    try {
      await saveMutation.mutateAsync({ id: taskId, request });
      setValidation(null);
      messageApi.success('任务定义已保存');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存任务定义失败');
    }
  };

  const validate = async () => {
    if (!taskId) return;
    if (dirty) {
      messageApi.warning('请先保存当前修改，再校验已保存的定义版本');
      return;
    }
    try {
      const result = await validateMutation.mutateAsync(taskId);
      setValidation(result);
      if (result.valid) messageApi.success('任务定义校验通过');
      else messageApi.warning('任务定义校验未通过');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '校验任务定义失败');
    }
  };

  const publishOrEnable = async () => {
    if (!taskId || !task) return;
    if (dirty) {
      messageApi.warning('请先保存当前修改');
      return;
    }
    try {
      if (task.status === 'DRAFT') await publishMutation.mutateAsync(taskId);
      else await enableMutation.mutateAsync(taskId);
      messageApi.success(task.status === 'DRAFT' ? '任务已发布' : '任务已启用');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '发布任务失败');
    }
  };

  const run = async () => {
    if (!taskId) return;
    try {
      await runMutation.mutateAsync(taskId);
      messageApi.success('任务已进入执行队列');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '提交任务运行失败');
    }
  };

  if (taskQuery.isLoading || definitionQuery.isLoading) return <Spin tip="正在加载任务定义…" />;
  if (!task) return <Alert type="error" showIcon message="任务不存在或无权查看" />;

  return (
    <div className="page-stack">
      {messageContext}
      <Space>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/task')}>返回任务列表</Button>
        <Typography.Title level={3} style={{ margin: 0 }}>{task.name}</Typography.Title>
        <Tag color={task.status === 'PUBLISHED' ? 'success' : task.status === 'DISABLED' ? 'warning' : 'default'}>{task.status}</Tag>
        {dirty && <Tag color="processing">有未保存修改</Tag>}
      </Space>
      <Descriptions size="small" column={3} bordered>
        <Descriptions.Item label="任务编码"><code>{task.code}</code></Descriptions.Item>
        <Descriptions.Item label="任务类型">本地 SQL</Descriptions.Item>
        <Descriptions.Item label="定义版本">{definition?.configured ? `v${definition.version}` : '未配置'}</Descriptions.Item>
      </Descriptions>
      <Card title="SQL 定义" extra={<Space>
        {canUpdate && editable && <Button icon={<SaveOutlined />} loading={saveMutation.isPending} onClick={() => void save()}>保存定义</Button>}
        {canPublish && <Button loading={validateMutation.isPending} onClick={() => void validate()}>校验</Button>}
        {canPublish && editable && <Button type="primary" icon={<SendOutlined />} loading={publishMutation.isPending || enableMutation.isPending} onClick={() => void publishOrEnable()}>{task.status === 'DRAFT' ? '发布' : '启用'}</Button>}
        {canExecute && task.status === 'PUBLISHED' && <Button type="primary" icon={<PlayCircleOutlined />} loading={runMutation.isPending} onClick={() => void run()}>立即运行</Button>}
      </Space>}>
        {!editable && <Alert type="info" showIcon message="任务已发布，定义为只读。停用任务后才能修改定义。" style={{ marginBottom: 16 }} />}
        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 330px', gap: 16 }}>
          <div>
            <Typography.Paragraph type="secondary">仅支持一条 <code>SELECT</code> 或只读 <code>WITH ... SELECT</code>。查询结果可以是输出模型字段的子集，但必须包含全部主键字段；输出别名必须等于字段编码。</Typography.Paragraph>
            <MonacoSqlEditor value={sql} readOnly={!editable || !canUpdate} onChange={setSql} />
          </div>
          <Form<DefinitionFormValues> form={form} layout="vertical" disabled={!editable || !canUpdate} onValuesChange={() => setValidation(null)}>
            <Form.Item name="outputModelId" label="输出模型" rules={[{ required: true, message: '请选择输出模型' }]}>
              <Select showSearch optionFilterProp="label" options={candidateModels.map((model) => ({ value: model.id, label: `${model.name} (${model.code}) · ${model.storageDataSourceName}` }))} />
            </Form.Item>
            <Form.Item name="inputModelIds" label="输入模型" rules={[{ required: true, message: '至少选择一个输入模型' }]}>
              <Select mode="multiple" showSearch optionFilterProp="label" options={sameStorageModels.filter((model) => model.id !== selectedOutputId).map((model) => ({ value: model.id, label: `${model.name} (${model.code})` }))} />
            </Form.Item>
            {selectedOutput && <Alert type="info" showIcon message={`当前数据存储：${selectedOutput.storageDataSourceName}`} style={{ marginBottom: 16 }} />}
            <Form.Item name="writeMode" label="写入方式" rules={[{ required: true }]}>
              <Select options={[{ value: 'APPEND', label: 'APPEND：追加写入' }, { value: 'OVERWRITE', label: 'OVERWRITE：清空后重写（仅 PostgreSQL）' }]} />
            </Form.Item>
            <Form.Item name="timeoutSeconds" label="超时（秒）" rules={[{ required: true }]}>
              <InputNumber min={1} max={3600} style={{ width: '100%' }} />
            </Form.Item>
            {outputDetailQuery.data && <>
              <Divider titlePlacement="start">输出字段</Divider>
              <List<DataModelField> size="small" bordered dataSource={outputDetailQuery.data.fields} renderItem={(field) => <List.Item><code>{field.code}</code>&nbsp; {field.fieldType}{field.primaryKey && <Tag color="blue" style={{ marginInlineStart: 8 }}>主键</Tag>}</List.Item>} />
            </>}
          </Form>
        </div>
      </Card>
      {validation && <Card title={validation.valid ? '校验通过' : '校验问题'}>
        {!validation.valid && <List dataSource={validation.problems} renderItem={(problem) => <List.Item><Tag color="error">{problem.code}</Tag>{problem.message}</List.Item>} />}
        {validation.columns.length > 0 && <Table size="small" rowKey="ordinal" pagination={false} dataSource={validation.columns} columns={[
          { title: '#', dataIndex: 'ordinal', width: 60 }, { title: '查询列', dataIndex: 'label' }, { title: '类型', dataIndex: 'logicalType' }, { title: '匹配输出字段', dataIndex: 'matchedOutputFieldCode' },
        ]} />}
      </Card>}
      <Card title="运行记录"><TaskRunsPanel taskId={task.id} /></Card>
      <Modal
        open={blocker.state === 'blocked'}
        title="离开未保存的任务定义？"
        okText="离开"
        cancelText="继续编辑"
        onOk={() => blocker.proceed?.()}
        onCancel={() => blocker.reset?.()}
      >
        当前 SQL 或模型配置尚未保存，离开后这些修改会丢失。
      </Modal>
    </div>
  );
};
