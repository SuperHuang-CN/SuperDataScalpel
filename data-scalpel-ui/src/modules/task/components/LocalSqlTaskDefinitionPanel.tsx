import { SaveOutlined } from '@ant-design/icons';
import {
  Alert,
  Button,
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
import { useBlocker, type BlockerFunction } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { MonacoSqlEditor } from '../../../shared/components/MonacoSqlEditor';
import { useDataModel, useDataModels, type DataModelField } from '../../model';
import {
  useTaskDefinition,
  useUpdateTaskDefinition,
  useValidateTaskDefinition,
} from '../hooks/useTasks';
import type {
  DataTask,
  LocalSqlDefinitionValidation,
  LocalSqlWriteMode,
  UpdateLocalSqlTaskDefinitionRequest,
} from '../model/task';

interface DefinitionFormValues {
  inputModelIds: string[];
  outputModelId?: string;
  writeMode: LocalSqlWriteMode;
  timeoutSeconds: number;
}

interface LocalSqlTaskDefinitionPanelProps {
  task: DataTask;
  canUpdate: boolean;
  canValidate: boolean;
  onDirtyChange: (dirty: boolean) => void;
  protectNavigation?: boolean;
}

const modelRequest = { search: 'status:"PUBLISHED"', page: 0, size: 500, sort: 'code' } as const;

export const LocalSqlTaskDefinitionPanel = ({
  task,
  canUpdate,
  canValidate,
  onDirtyChange,
  protectNavigation = true,
}: LocalSqlTaskDefinitionPanelProps) => {
  const [form] = Form.useForm<DefinitionFormValues>();
  const [sql, setSql] = useState('SELECT\n');
  const [initialized, setInitialized] = useState(false);
  const [validation, setValidation] = useState<LocalSqlDefinitionValidation | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const definitionQuery = useTaskDefinition(task.id);
  const modelsQuery = useDataModels(modelRequest);
  const selectedOutputId = Form.useWatch('outputModelId', form);
  const selectedInputIds = Form.useWatch('inputModelIds', form);
  const selectedWriteMode = Form.useWatch('writeMode', form);
  const selectedTimeoutSeconds = Form.useWatch('timeoutSeconds', form);
  const outputDetailQuery = useDataModel(selectedOutputId, Boolean(selectedOutputId));
  const saveMutation = useUpdateTaskDefinition();
  const validateMutation = useValidateTaskDefinition();
  const definition = definitionQuery.data;
  const editable = task.status === 'DRAFT' || task.status === 'DISABLED';
  const candidateModels = modelsQuery.data?.content ?? [];
  const selectedOutput = candidateModels.find((model) => model.id === selectedOutputId)
    ?? (definition?.output ? candidateModels.find((model) => model.id === definition.output?.modelId) : undefined);
  const sameStorageModels = selectedOutput
    ? candidateModels.filter((model) => model.storageDataSourceId === selectedOutput.storageDataSourceId)
    : candidateModels;
  const savedFingerprint = useMemo(() => definition ? JSON.stringify({
    sql: definition.sql ?? 'SELECT\n',
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
    ({ currentLocation, nextLocation }) => protectNavigation && dirty && (
      currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search
    ),
    [dirty, protectNavigation],
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
    onDirtyChange(dirty);
    return () => onDirtyChange(false);
  }, [dirty, onDirtyChange]);

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
    if ((!protectNavigation || !dirty) && blocker.state === 'blocked') blocker.reset();
  }, [blocker, dirty, protectNavigation]);

  const save = async () => {
    const values = await form.validateFields();
    if (!values.outputModelId) return;
    const request: UpdateLocalSqlTaskDefinitionRequest = {
      ...values,
      outputModelId: values.outputModelId,
      sql,
    };
    try {
      await saveMutation.mutateAsync({ id: task.id, request });
      setValidation(null);
      messageApi.success('任务定义已保存');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存任务定义失败');
    }
  };

  const validate = async () => {
    if (dirty) {
      messageApi.warning('请先保存当前修改，再校验已保存的定义版本');
      return;
    }
    try {
      const result = await validateMutation.mutateAsync(task.id);
      setValidation(result);
      if (result.valid) messageApi.success('任务定义校验通过');
      else messageApi.warning('任务定义校验未通过');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '校验任务定义失败');
    }
  };

  if (definitionQuery.isLoading) return <Spin tip="正在加载任务定义…" />;
  if (!definition) return <Alert type="error" showIcon message="任务定义加载失败" />;

  return (
    <div className="task-detail-tab-panel task-local-sql-definition-panel">
      {messageContext}
      <div className="task-detail-tab-toolbar">
        <Space size={8}>
          <Typography.Text strong>SQL 定义</Typography.Text>
          <Tag color={definition.configured ? 'success' : 'default'}>
            {definition.configured ? `v${definition.version}` : '未配置'}
          </Tag>
          {dirty && <Tag color="processing">有未保存修改</Tag>}
        </Space>
        <Space size={8}>
          {canValidate && (
            <Button loading={validateMutation.isPending} onClick={() => void validate()}>校验</Button>
          )}
          {canUpdate && editable && (
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={saveMutation.isPending}
              disabled={!dirty}
              onClick={() => void save()}
            >
              保存定义
            </Button>
          )}
        </Space>
      </div>

      <div className="task-local-sql-definition-content">
        {!editable && (
          <Alert
            type={definition.configured ? 'info' : 'error'}
            showIcon
            message={definition.configured
              ? '任务已发布，定义为只读。停用任务后才能修改定义。'
              : '任务已发布，但本地 SQL 定义缺失'}
            description={definition.configured
              ? undefined
              : '当前任务无法运行。请先停用任务，再重新配置本地 SQL 定义或删除任务。'}
          />
        )}
        <div className="task-local-sql-definition-grid">
          <div className="task-local-sql-editor-column">
            <Typography.Paragraph type="secondary">
              仅支持一条 <code>SELECT</code> 或只读 <code>WITH ... SELECT</code>。查询结果可以是输出模型字段的子集，但必须包含全部主键字段；输出别名必须等于字段编码。
            </Typography.Paragraph>
            <MonacoSqlEditor value={sql} readOnly={!editable || !canUpdate} onChange={setSql} />
          </div>
          <Form<DefinitionFormValues> autoComplete="off"
            form={form}
            layout="vertical"
            disabled={!editable || !canUpdate}
            onValuesChange={() => setValidation(null)}
          >
            <Form.Item name="outputModelId" label="输出模型" rules={[{ required: true, message: '请选择输出模型' }]}>
              <Select
                showSearch
                optionFilterProp="label"
                options={candidateModels.map((model) => ({
                  value: model.id,
                  label: `${model.name} (${model.code}) · ${model.storageDataSourceName}`,
                }))}
              />
            </Form.Item>
            <Form.Item name="inputModelIds" label="输入模型" rules={[{ required: true, message: '至少选择一个输入模型' }]}>
              <Select
                mode="multiple"
                showSearch
                optionFilterProp="label"
                options={sameStorageModels
                  .filter((model) => model.id !== selectedOutputId)
                  .map((model) => ({ value: model.id, label: `${model.name} (${model.code})` }))}
              />
            </Form.Item>
            {selectedOutput && (
              <Alert type="info" showIcon message={`当前数据存储：${selectedOutput.storageDataSourceName}`} />
            )}
            <Form.Item name="writeMode" label="写入方式" rules={[{ required: true }]}>
              <Select options={[
                { value: 'APPEND', label: 'APPEND：追加写入' },
                { value: 'OVERWRITE', label: 'OVERWRITE：清空后重写（仅 PostgreSQL）' },
              ]} />
            </Form.Item>
            <Form.Item name="timeoutSeconds" label="超时（秒）" rules={[{ required: true }]}>
              <InputNumber min={1} max={3600} className="task-timeout-input" />
            </Form.Item>
            {outputDetailQuery.data && (
              <>
                <Divider titlePlacement="start">输出字段</Divider>
                <List<DataModelField>
                  size="small"
                  bordered
                  dataSource={outputDetailQuery.data.fields}
                  renderItem={(field) => (
                    <List.Item>
                      <code>{field.code}</code>&nbsp; {field.fieldType}
                      {field.primaryKey && <Tag color="blue" className="task-primary-key-tag">主键</Tag>}
                    </List.Item>
                  )}
                />
              </>
            )}
          </Form>
        </div>

      {validation && (
          <section className="task-definition-validation">
            <div className="task-basic-section-title">{validation.valid ? '校验通过' : '校验问题'}</div>
            {!validation.valid && (
              <List
                bordered
                dataSource={validation.problems}
                renderItem={(problem) => (
                  <List.Item><Tag color="error">{problem.code}</Tag>{problem.message}</List.Item>
                )}
              />
            )}
            {validation.columns.length > 0 && (
              <Table
                size="small"
                rowKey="ordinal"
                pagination={false}
                dataSource={validation.columns}
                columns={[
                  { title: '#', dataIndex: 'ordinal', width: 60 },
                  { title: '查询列', dataIndex: 'label' },
                  { title: '类型', dataIndex: 'logicalType' },
                  { title: '匹配输出字段', dataIndex: 'matchedOutputFieldCode' },
                ]}
              />
            )}
          </section>
        )}
      </div>

      <Modal
        open={blocker.state === 'blocked'}
        title="离开未保存的任务定义？"
        okText="放弃并离开"
        okButtonProps={{ danger: true }}
        cancelText="继续编辑"
        closable={false}
        mask={{ closable: false }}
        onOk={() => blocker.proceed?.()}
        onCancel={() => blocker.reset?.()}
      >
        当前 SQL 或模型配置尚未保存，离开后这些修改会丢失。
      </Modal>
    </div>
  );
};
