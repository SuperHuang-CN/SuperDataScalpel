import { OverlayTitle } from '../../../shared/components/OverlayTitle';
import { SparkJarResourceDrawer, type SparkJarResourceSelection } from './SparkJarResourceDrawer';
import { replaceSparkJarResource } from '../model/sparkJarResourceConfiguration';
import { taskPageHref } from '../model/taskViews';
import './sparkJarEditor.css';
import { CompactAlert as Alert, ContextHelp } from '../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  ExclamationCircleOutlined,
  InfoCircleOutlined,
  PlusOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import { Button, Form, Input, InputNumber, Modal, Radio, Select, Space, Spin, Table, Tag, Tooltip, Typography, Upload, message, type UploadFile, type UploadProps } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useBlocker, useLocation, useNavigate, type BlockerFunction } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { useDataModel } from '../../model';
import { computeBackendTypeLabels, useComputeEngine, type ComputeBackendType, type SparkExecutionResourceSpec as EngineSparkExecutionResourceSpec } from '../../computeengine';
import {
  jdbcTableIdentifierDisplayName,
  useDataSource,
} from '../../datasource';
import {
  useGenerateSparkJarDevelopmentKit,
  useDownloadSparkJarDevelopmentKit,
  useSparkJarDevelopmentKit,
  useSparkJarTaskDefinition,
  useSparkJarOnlineSource,
  useUpdateSparkJarTaskDefinition,
  useUploadSparkJar,
} from '../hooks/useTasks';
import type {
  DataTask,
  SparkJarDefinitionEntry,
  SparkJarDevelopmentKitInputSample,
  SparkJarDevelopmentKitJdbcTable,
  SparkJarDevelopmentKitSampleMode,
  SparkJarResourceBinding,
  UpdateSparkJarTaskDefinitionRequest,
  SparkJarTaskDefinition,
  SparkExecutionResourceSpec,
} from '../model/task';
import {
  SparkJarArtifactSummary,
  SparkJarOnlineCodeSummary,
  SparkJarAuthoringModeChoice,
  SparkJarDevelopmentKitPanel,
  SparkJarRuntimeConfiguration,
} from './SparkJarDefinitionWorkspace';

interface SparkJarDefinitionFormValues {
  inheritEngineResources?: boolean;
  authoringMode?: 'ONLINE' | 'UPLOAD';
  parameters: SparkJarDefinitionEntry[];
  sparkConf: SparkJarDefinitionEntry[];
  driverJavaOptions: string;
  resourceBindings: Array<Omit<SparkJarResourceBinding, 'resourceName'>>;
  executionResources: SparkExecutionResourceSpec;
  timeoutSeconds: number;
}

interface SparkJarTaskDefinitionPanelProps {
  task: DataTask;
  toolbarContext?: ReactNode;
  protectNavigation?: boolean;
}

interface DevelopmentKitDraftState {
  taskId: string;
  dirty: boolean;
  samples: Record<string, SparkJarDevelopmentKitInputSample>;
  jdbcTables: SparkJarDevelopmentKitJdbcTable[] | null;
}

const resourceBindingFormValue = (
  binding: SparkJarResourceBinding,
): Omit<SparkJarResourceBinding, 'resourceName'> => ({
  bindingName: binding.bindingName,
  resourceType: binding.resourceType,
  resourceId: binding.resourceId,
  topicName: binding.topicName,
  accessMode: binding.accessMode,
});

const sampleModeOptions: Array<{ value: SparkJarDevelopmentKitSampleMode; label: string }> = [
  { value: 'NONE', label: '仅 Schema' },
  { value: 'ROW_COUNT', label: '指定条数' },
  { value: 'PERCENTAGE', label: '按比例' },
  { value: 'ALL', label: '全部数据' },
];

type SampleConfiguration = Pick<SparkJarDevelopmentKitInputSample, 'mode' | 'rowCount' | 'percentage'>;

const defaultSampleConfiguration = (): SampleConfiguration => ({ mode: 'ROW_COUNT', rowCount: 1_000 });
const driverJavaOptionsKey = 'spark.driver.extrajavaoptions';
const driverJavaOptionPattern = /^(?:-D[A-Za-z_][A-Za-z0-9_.-]*=[^\s"']+|-XX:[+-][A-Za-z][A-Za-z0-9_.]*|-XX:[A-Za-z][A-Za-z0-9_.]*=[^\s"']+|--add-(?:opens|exports)=[^\s"']+)$/;

const runtimeValidationSections = (error: unknown): string[] => {
  if (!error || typeof error !== 'object' || !('errorFields' in error)) return [];
  const errorFields = (error as { errorFields?: Array<{ name?: Array<string | number> }> }).errorFields ?? [];
  const sections = new Set<string>();
  errorFields.forEach((field) => {
    const root = field.name?.[0];
    if (root === 'parameters') sections.add('parameters');
    if (root === 'sparkConf') sections.add('sparkConf');
    if (root === 'executionResources') sections.add('resources');
    if (root === 'driverJavaOptions') sections.add('jvm');
    if (root === 'timeoutSeconds') sections.add('timeout');
  });
  return [...sections];
};

const memoryGiBProps = (value: number | undefined) => ({
  value: value === undefined ? value : value / 1024,
});

const toMiB = (value: number | null) => (
  value === null || value === undefined ? value : Math.round(value * 1024)
);

const driverJavaOptionsFromSparkConf = (sparkConf: SparkJarDefinitionEntry[]) => {
  const option = sparkConf.find((entry) => entry.name.trim().toLowerCase() === driverJavaOptionsKey)?.value ?? '';
  return {
    driverJavaOptions: option ? option.split(' ').join('\n') : '',
    sparkConf: sparkConf.filter((entry) => entry.name.trim().toLowerCase() !== driverJavaOptionsKey),
  };
};

const containsControlCharacter = (value: string): boolean => Array.from(value).some((character) => {
  const codePoint = character.codePointAt(0) ?? 0;
  return codePoint <= 0x1f || codePoint === 0x7f;
});

const normalizeDriverJavaOptions = (value: string | undefined): string[] => {
  if (!value || !value.trim()) return [];
  const lines = value.split(/\r?\n/).map((line) => line.trim());
  if (lines.length > 100 || lines.some((line) => !line || /\s/.test(line) || containsControlCharacter(line))) {
    throw new Error('每行填写一个不含空白字符的 Driver JVM 参数，最多 100 项');
  }
  if (lines.join(' ').length > 2_000) throw new Error('Driver JVM 参数总长度不能超过 2000 个字符');
  if (lines.some((line) => {
    const normalized = line.toLowerCase();
    return line.includes('"') || line.includes("'")
      || normalized.startsWith('-ddatascalpel.')
      || normalized.startsWith('-djava.class.path')
      || normalized.includes('heapdump')
      || normalized.startsWith('-xx:onerror')
      || normalized.startsWith('-xx:onoutofmemoryerror')
      || normalized.startsWith('-xx:errorfile')
      || !driverJavaOptionPattern.test(line);
  })) {
    throw new Error('仅支持 -D、-XX、--add-opens、--add-exports，不能覆盖内存、Agent、classpath 或错误转储配置');
  }
  return lines;
};

const withDriverJavaOptions = (sparkConf: SparkJarDefinitionEntry[], value: string | undefined) => {
  const options = normalizeDriverJavaOptions(value);
  const remaining = sparkConf.filter((entry) => entry.name.trim().toLowerCase() !== driverJavaOptionsKey);
  return options.length === 0
    ? remaining
    : [...remaining, { name: 'spark.driver.extraJavaOptions', value: options.join(' ') }];
};

const driverJvmRules = [{
  validator: async (_: unknown, value: string | undefined) => {
    normalizeDriverJavaOptions(value);
  },
}];

type ExecutionTimeoutUnit = 'SECONDS' | 'MINUTES' | 'HOURS';

const executionTimeoutUnitSeconds: Record<ExecutionTimeoutUnit, number> = {
  SECONDS: 1,
  MINUTES: 60,
  HOURS: 3600,
};

const preferredExecutionTimeoutUnit = (seconds: number | undefined): ExecutionTimeoutUnit => {
  if (seconds && seconds % 3600 === 0) return 'HOURS';
  if (seconds && seconds % 60 === 0) return 'MINUTES';
  return 'SECONDS';
};

const formatExecutionTimeout = (seconds: number | undefined): string => {
  if (!seconds) return '—';
  if (seconds % 3600 === 0) return `${seconds / 3600} 小时`;
  if (seconds % 60 === 0) return `${seconds / 60} 分钟`;
  return `${seconds} 秒`;
};

const SparkJarExecutionTimeoutEditor = ({
  value,
  onChange,
}: {
  value?: number;
  onChange?: (value: number | undefined) => void;
}) => {
  const [unit, setUnit] = useState<ExecutionTimeoutUnit>(() => preferredExecutionTimeoutUnit(value));
  const multiplier = executionTimeoutUnitSeconds[unit];
  return (
    <Space.Compact className="spark-jar-runtime-timeout-input">
      <InputNumber
        value={value === undefined ? undefined : value / multiplier}
        min={unit === 'SECONDS' ? 1 : 0.01}
        max={86_400 / multiplier}
        precision={unit === 'SECONDS' ? 0 : 2}
        onChange={(nextValue) => onChange?.(nextValue === null ? undefined : Math.max(1, Math.round(nextValue * multiplier)))}
      />
      <Select<ExecutionTimeoutUnit>
        value={unit}
        onChange={setUnit}
        options={[
          { value: 'SECONDS', label: '秒' },
          { value: 'MINUTES', label: '分钟' },
          { value: 'HOURS', label: '小时' },
        ]}
      />
    </Space.Compact>
  );
};

const SparkJarExecutionResourcesEditor = ({
  backend,
  maximums,
  defaults,
  showEnvironment = true,
}: {
  backend: ComputeBackendType | undefined;
  maximums: EngineSparkExecutionResourceSpec | undefined;
  defaults: EngineSparkExecutionResourceSpec | undefined;
  showEnvironment?: boolean;
}) => {
  const form = Form.useFormInstance<SparkJarDefinitionFormValues>();
  const inherited = Form.useWatch('inheritEngineResources', form);
  const localDocker = backend === 'LOCAL_DOCKER';
  const environment = backend === 'LOCAL_DOCKER'
    ? 'Local Docker · local[*]'
    : backend ? computeBackendTypeLabels[backend] : '正在读取计算引擎';
  const driverCoresMax = maximums?.driverCores ?? 256;
  const driverMemoryMax = (maximums?.driverMemoryMiB ?? 1_048_576) / 1024;
  const executorCountMax = maximums?.executorInstances ?? 10_000;
  const executorCoresMax = maximums?.executorCores ?? 256;
  const executorMemoryMax = (maximums?.executorMemoryMiB ?? 1_048_576) / 1024;
  return (
    <div className="spark-jar-runtime-resource-editor">
      {showEnvironment && <Form.Item label="运行环境">
        <Input value={environment} readOnly />
      </Form.Item>}
      <Form.Item name="inheritEngineResources" label="资源配置方式">
        <Radio.Group options={[{ value: true, label: '使用引擎默认值' }, { value: false, label: '任务自定义' }]}
          onChange={(event) => { if (event.target.value && defaults) form.setFieldValue('executionResources', defaults); }} />
      </Form.Item>
      <div className="spark-jar-runtime-resource-grid">
        <Form.Item name={['executionResources', 'driverCores']} label={<span>驱动 CPU <Tooltip title="Spark Driver 使用的 CPU 核数；在 Local Docker 中映射为容器 --cpus。"><InfoCircleOutlined /></Tooltip></span>} rules={[{ required: true }]}>
          <InputNumber disabled={inherited} min={1} max={driverCoresMax} precision={0} addonAfter="Core" />
        </Form.Item>
        <Form.Item name={['executionResources', 'driverMemoryMiB']} label={<span>驱动内存 <Tooltip title="Local Docker：容器总内存，JVM 堆取 75%；YARN/Kubernetes：Driver JVM 堆内存，容器另需非堆开销。"><InfoCircleOutlined /></Tooltip></span>} getValueProps={memoryGiBProps} normalize={toMiB} rules={[{ required: true }]}>
          <InputNumber disabled={inherited} min={1} max={driverMemoryMax} step={0.25} addonAfter="GiB" />
        </Form.Item>
        {!localDocker && <>
          <Form.Item name={['executionResources', 'executorInstances']} label={<span>执行器数量 <Tooltip title="提交到 YARN 或 Kubernetes 的 Executor 实例数量。"><InfoCircleOutlined /></Tooltip></span>} rules={[{ required: true }]}>
            <InputNumber disabled={inherited} min={1} max={executorCountMax} precision={0} />
          </Form.Item>
          <Form.Item name={['executionResources', 'executorCores']} label={<span>单执行器 CPU <Tooltip title="每个 Spark Executor 使用的 CPU 核数。"><InfoCircleOutlined /></Tooltip></span>} rules={[{ required: true }]}>
            <InputNumber disabled={inherited} min={1} max={executorCoresMax} precision={0} addonAfter="Core" />
          </Form.Item>
          <Form.Item name={['executionResources', 'executorMemoryMiB']} label={<span>单执行器内存 <Tooltip title="每个 Spark Executor 的 JVM 堆内存；集群容器还需非堆内存，不是容器总额度。"><InfoCircleOutlined /></Tooltip></span>} getValueProps={memoryGiBProps} normalize={toMiB} rules={[{ required: true }]}>
            <InputNumber disabled={inherited} min={1} max={executorMemoryMax} step={0.25} addonAfter="GiB" />
          </Form.Item>
        </>}
      </div>
      {localDocker && <Typography.Text type="secondary">Local Docker 固定以 local[*] 运行，执行器参数不适用。</Typography.Text>}
    </div>
  );
};

const SparkJarDriverJvmOptionsEditor = ({ backend }: { backend: ComputeBackendType | undefined }) => {
  const launchMeaning = backend === 'LOCAL_DOCKER'
    ? 'Local Docker 会在启动 Runner/Driver 容器前写入 JAVA_TOOL_OPTIONS。'
    : 'YARN 与 Kubernetes 会在提交 Spark Driver 时应用。';
  return <div className="spark-jar-driver-jvm-options-editor">
    <Form.Item
      name="driverJavaOptions"
      label={<span>Driver JVM 参数 <Tooltip title="对应 spark.driver.extraJavaOptions。一行一个参数；不支持参数值中的空格或引号。"><InfoCircleOutlined /></Tooltip></span>}
      rules={driverJvmRules}
    >
      <Input.TextArea
        autoSize={{ minRows: 3, maxRows: 8 }}
        placeholder={'-Duser.timezone=Asia/Shanghai\n-XX:+UseG1GC\n--add-opens=java.base/java.nio=ALL-UNNAMED'}
      />
    </Form.Item>
    <Typography.Text type="secondary">
      {launchMeaning} Driver 堆内存由“驱动内存”自动计算，不能通过这里的 -Xms 或 -Xmx 覆盖。
    </Typography.Text>
  </div>;
};

const SampleConfigurationControls = ({
  value,
  onChange,
  disabled = false,
}: {
  value: SampleConfiguration;
  onChange: (value: SampleConfiguration) => void;
  disabled?: boolean;
}) => (
  <div className="spark-jar-kit-sample-control">
    <Space size={8}>
      <Select
        value={value.mode}
        options={sampleModeOptions}
        disabled={disabled}
        style={{ width: 122 }}
        onChange={(mode: SparkJarDevelopmentKitSampleMode) => onChange({
          mode,
          rowCount: mode === 'ROW_COUNT' ? 1_000 : undefined,
          percentage: mode === 'PERCENTAGE' ? 1 : undefined,
        })}
      />
      {value.mode === 'ROW_COUNT' && (
        <InputNumber
          min={1}
          max={1_000_000}
          precision={0}
          value={value.rowCount}
          disabled={disabled}
          addonAfter="条"
          style={{ width: 170 }}
          onChange={(rowCount) => onChange({ ...value, rowCount: rowCount ?? 1 })}
        />
      )}
      {value.mode === 'PERCENTAGE' && (
        <InputNumber
          min={0.01}
          max={100}
          step={0.01}
          precision={2}
          value={value.percentage}
          disabled={disabled}
          addonAfter="%"
          style={{ width: 150 }}
          onChange={(percentage) => onChange({ ...value, percentage: percentage ?? 0.01 })}
        />
      )}
      {value.mode === 'ALL' && (
        <Tooltip title="全量超过 1,000,000 行时生成失败，不会自动截断">
          <ExclamationCircleOutlined className="spark-jar-kit-sample-warning-icon" aria-label="全量数据生成限制" />
        </Tooltip>
      )}
    </Space>
  </div>
);

const readableBinding = (binding: Omit<SparkJarResourceBinding, 'resourceName'> | undefined): boolean => (
  binding?.accessMode === 'READ' || binding?.accessMode === 'READ_WRITE'
);

const ResourceSelectionButton = ({
  binding,
  jdbcTable,
  disabled,
  onClick,
}: {
  binding: Omit<SparkJarResourceBinding, 'resourceName'> | undefined;
  jdbcTable: SparkJarDevelopmentKitJdbcTable | undefined;
  disabled: boolean;
  onClick: () => void;
}) => {
  const modelId = binding?.resourceType === 'MODEL' ? binding.resourceId : undefined;
  const dataSourceId = binding?.resourceType === 'JDBC_DATA_SOURCE' ? binding.resourceId : undefined;
  const modelQuery = useDataModel(modelId, Boolean(modelId));
  const dataSourceQuery = useDataSource(dataSourceId, Boolean(dataSourceId));
  const tableLabel = jdbcTable
    ? jdbcTableIdentifierDisplayName({ catalog: jdbcTable.catalog ?? null, schema: jdbcTable.schema ?? null, table: jdbcTable.table })
    : null;

  if (!binding?.resourceType) return <Typography.Text type="secondary">请先选择资源类型</Typography.Text>;
  if (binding.resourceType === 'KAFKA_TOPIC') return <Button type="link" disabled={disabled} onClick={onClick}>{binding.topicName ?? 'Kafka Topic'}</Button>;

  const label = binding.resourceType === 'MODEL'
    ? modelQuery.data?.model
      ? modelQuery.data.model.name === modelQuery.data.model.code ? modelQuery.data.model.name : `${modelQuery.data.model.name} · ${modelQuery.data.model.code}`
      : binding.resourceId ? '已选择模型' : '选择模型'
    : dataSourceQuery.data
      ? `${dataSourceQuery.data.name} · ${tableLabel ?? dataSourceQuery.data.code}`
      : binding.resourceId ? (tableLabel ? `已选择 JDBC 表 · ${tableLabel}` : '已选择 JDBC 数据源')
        : readableBinding(binding) ? '选择 JDBC 数据源和表' : '选择 JDBC 数据源';

  const failed = modelQuery.isError || dataSourceQuery.isError;
  return <div className="spark-jar-resource-identity">
    <button type="button" disabled={disabled} onClick={onClick} title={label}>{label}</button>
    <Typography.Text type={failed ? 'danger' : 'secondary'}>
      {failed ? '资源加载失败，点击编辑检查' : binding.resourceType === 'MODEL' ? '数据模型' : tableLabel ? 'JDBC 表' : 'JDBC 连接'}
      {(modelQuery.isFetching || dataSourceQuery.isFetching) && ' · 加载中'}
    </Typography.Text>
  </div>;
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
          scroll={{ y: 146 }}
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
  const navigate = useNavigate();
  const location = useLocation();
  const [form] = Form.useForm<SparkJarDefinitionFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const [uploadFiles, setUploadFiles] = useState<UploadFile[]>([]);
  const [dirty, setDirty] = useState(false);
  const savedNavigation = useRef(false);
  const [kitDraft, setKitDraft] = useState<DevelopmentKitDraftState>({
    taskId: task.id,
    dirty: false,
    samples: {},
    jdbcTables: null,
  });
  const [resourceEditorIndex, setResourceEditorIndex] = useState<number | 'new' | null>(null);
  const [runtimeConfigurationKeys, setRuntimeConfigurationKeys] = useState<string[]>([]);
  const definitionQuery = useSparkJarTaskDefinition(task.id);
  const computeEngineQuery = useComputeEngine(task.computeEngineId ?? undefined);
  const updateMutation = useUpdateSparkJarTaskDefinition();
  const uploadMutation = useUploadSparkJar();
  const createKitMutation = useGenerateSparkJarDevelopmentKit();
  const downloadKitMutation = useDownloadSparkJarDevelopmentKit();
  const configurationSaving = updateMutation.isPending || uploadMutation.isPending;
  const kitQuery = useSparkJarDevelopmentKit(task.id, task.type === 'SPARK_JAR' || task.type === 'SPARK_STREAMING_JAR');
  const watchedParameters = Form.useWatch('parameters', form);
  const authoringMode = Form.useWatch('authoringMode', form);
  const onlineSourceQuery = useSparkJarOnlineSource(authoringMode === 'ONLINE' ? task.id : undefined);
  const watchedSparkConf = Form.useWatch('sparkConf', form);
  const watchedDriverJavaOptions = Form.useWatch('driverJavaOptions', form);
  const watchedTimeoutSeconds = Form.useWatch('timeoutSeconds', form);
  const watchedExecutionResources = Form.useWatch('executionResources', form);
  const watchedResourceBindings = Form.useWatch('resourceBindings', form);
  const resourceBindings = useMemo(() => watchedResourceBindings ?? [], [watchedResourceBindings]);
  const definition = definitionQuery.data;
  const streaming = task.type === 'SPARK_STREAMING_JAR';
  const resourceBackend = computeEngineQuery.data?.expectedBackendType;
  const resourceMaximums = computeEngineQuery.data?.resourcePolicy.maximums;
  const effectiveExecutionResources = watchedExecutionResources ?? definition?.executionResources;
  const effectiveTimeoutSeconds = watchedTimeoutSeconds ?? definition?.timeoutSeconds;
  const resourceEnvironment = resourceBackend
    ? `${computeEngineQuery.data?.name ?? ''} · ${computeBackendTypeLabels[resourceBackend]}`
    : computeEngineQuery.isError ? '计算引擎加载失败' : '正在读取计算引擎';
  const resourceSummary = resourceBackend === 'LOCAL_DOCKER'
    ? `${effectiveExecutionResources?.driverCores ?? '—'} Core CPU 和 ${effectiveExecutionResources?.driverMemoryMiB ? effectiveExecutionResources.driverMemoryMiB / 1024 : '—'} GiB 内存`
    : `Driver ${effectiveExecutionResources?.driverCores ?? '—'} Core / ${effectiveExecutionResources?.driverMemoryMiB ? effectiveExecutionResources.driverMemoryMiB / 1024 : '—'} GiB，${effectiveExecutionResources?.executorInstances ?? '—'} 个 Executor × ${effectiveExecutionResources?.executorCores ?? '—'} Core / ${effectiveExecutionResources?.executorMemoryMiB ? effectiveExecutionResources.executorMemoryMiB / 1024 : '—'} GiB`;
  const timeoutSummary = formatExecutionTimeout(effectiveTimeoutSeconds);
  const driverJavaOptionsCount = watchedDriverJavaOptions?.split(/\r?\n/)
    .filter((line) => line.trim()).length ?? 0;
  const activeKitDraft = kitDraft.taskId === task.id
    ? kitDraft
    : { taskId: task.id, dirty: false, samples: {}, jdbcTables: null };
  const kitConfigDirty = activeKitDraft.dirty;
  const kitSamples = activeKitDraft.samples;
  const kitModelInputs = useMemo(() => resourceBindings.filter((binding) => (
    binding.resourceType === 'MODEL' && (binding.accessMode === 'READ' || binding.accessMode === 'READ_WRITE')
  )), [resourceBindings]);
  const kitModelOutputs = useMemo(() => resourceBindings.filter((binding) => (
    binding.resourceType === 'MODEL' && (binding.accessMode === 'WRITE' || binding.accessMode === 'READ_WRITE')
  )), [resourceBindings]);
  const kitJdbcBindings = useMemo(() => resourceBindings.filter((binding) => (
    binding.resourceType === 'JDBC_DATA_SOURCE'
    && (binding.accessMode === 'READ' || binding.accessMode === 'READ_WRITE')
  )), [resourceBindings]);
  const developmentKitRunning = kitQuery.data?.generation?.status === 'QUEUED'
    || kitQuery.data?.generation?.status === 'RUNNING';
  const developmentKitBusy = developmentKitRunning || createKitMutation.isPending;
  const developmentKitArtifact = kitQuery.data?.artifact;
  const developmentKitGeneration = kitQuery.data?.generation;
  const developmentKitNeedsRefresh = Boolean(developmentKitArtifact && (dirty || kitConfigDirty
    || !developmentKitArtifact.matchesSavedConfiguration
    || (developmentKitGeneration && developmentKitGeneration.definitionVersion !== definitionQuery.data?.definitionVersion)));
  const developmentKitStatus = developmentKitBusy
    ? { label: '生成中', color: 'processing' }
    : developmentKitGeneration?.status === 'FAILED'
      ? { label: '生成失败', color: 'error' }
      : kitConfigDirty || developmentKitNeedsRefresh
        ? { label: '配置待生成', color: 'warning' }
        : developmentKitArtifact
          ? { label: '可下载', color: 'success' }
          : { label: '未生成', color: 'default' };
  const kitJdbcBindingNames = useMemo(() => new Set(kitJdbcBindings.map((binding) => binding.bindingName)), [kitJdbcBindings]);
  const persistedKitSamples = useMemo(() => new Map((kitQuery.data?.configuration.samples ?? [])
    .map((sample) => [sample.bindingName, sample])), [kitQuery.data?.configuration.samples]);
  const persistedKitJdbcTables = useMemo(() => (kitQuery.data?.configuration.jdbcTables ?? []).filter((table) => (
    kitJdbcBindingNames.has(table.bindingName)
  )), [kitJdbcBindingNames, kitQuery.data?.configuration.jdbcTables]);
  const kitJdbcTables = activeKitDraft.jdbcTables ?? persistedKitJdbcTables;
  const effectiveKitJdbcTables = useMemo(() => kitJdbcTables.filter((table) => (
    kitJdbcBindingNames.has(table.bindingName)
  )), [kitJdbcBindingNames, kitJdbcTables]);
  const blocker = useBlocker(useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) => protectNavigation && !savedNavigation.current && (dirty || kitConfigDirty) && (
      currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search
    ),
    [dirty, kitConfigDirty, protectNavigation],
  ));

  useEffect(() => {
    if (!definition || dirty) return;
    const runtimeOptions = driverJavaOptionsFromSparkConf(definition.sparkConf);
    form.setFieldsValue({
      authoringMode: definition.authoringMode ?? undefined,
      parameters: definition.parameters,
      sparkConf: runtimeOptions.sparkConf,
      driverJavaOptions: runtimeOptions.driverJavaOptions,
      resourceBindings: definition.resourceBindings.map(resourceBindingFormValue),
      executionResources: definition.executionResources,
      inheritEngineResources: definition.inheritEngineResources ?? false,
      timeoutSeconds: definition.timeoutSeconds,
    });
  }, [definition, form, dirty]);

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!dirty && !kitConfigDirty) return;
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty, kitConfigDirty]);

  const jdbcTablesForBinding = (bindingName: string): SparkJarDevelopmentKitJdbcTable[] => (
    kitJdbcTables.filter((table) => table.bindingName === bindingName)
  );

  const removeDevelopmentConfiguration = (bindingName: string) => {
    if (!bindingName) return;
    setKitDraft((current) => {
      const samples = { ...(current.taskId === task.id ? current.samples : {}) };
      delete samples[bindingName];
      const jdbcTables = (current.taskId === task.id ? current.jdbcTables ?? persistedKitJdbcTables : persistedKitJdbcTables)
        .filter((table) => table.bindingName !== bindingName);
      return { taskId: task.id, dirty: true, samples, jdbcTables };
    });
  };

  const save = async (): Promise<SparkJarTaskDefinition | null> => {
    try {
      const values = await form.validateFields();
      const sparkConf = withDriverJavaOptions(values.sparkConf ?? [], values.driverJavaOptions);
      const inheritEngineResources = values.inheritEngineResources
        ?? form.getFieldValue('inheritEngineResources') ?? definition?.inheritEngineResources ?? true;
      const request: UpdateSparkJarTaskDefinitionRequest = {
        authoringMode: values.authoringMode,
        developmentConfiguration: kitQuery.isSuccess ? {
          samples: kitModelInputs.map((binding) => kitSamples[binding.bindingName]
            ?? persistedKitSamples.get(binding.bindingName)
            ?? { bindingName: binding.bindingName, ...defaultSampleConfiguration() }),
          jdbcTables: effectiveKitJdbcTables,
        } : undefined,
        parameters: values.parameters ?? [],
        sparkConf,
        resourceBindings: values.resourceBindings ?? [],
        inheritEngineResources,
        executionResources: inheritEngineResources ? undefined : {
          ...definition?.executionResources,
          ...values.executionResources,
        },
        timeoutSeconds: values.timeoutSeconds ?? definition?.timeoutSeconds ?? 3_600,
      };
      const saved = await updateMutation.mutateAsync({ id: task.id, request });
      const savedRuntimeOptions = driverJavaOptionsFromSparkConf(saved.sparkConf);
      form.setFieldsValue({
        authoringMode: saved.authoringMode ?? undefined,
        parameters: saved.parameters,
        sparkConf: savedRuntimeOptions.sparkConf,
        driverJavaOptions: savedRuntimeOptions.driverJavaOptions,
        resourceBindings: saved.resourceBindings.map(resourceBindingFormValue),
        executionResources: saved.executionResources,
        inheritEngineResources: saved.inheritEngineResources ?? false,
        timeoutSeconds: saved.timeoutSeconds,
      });
      setDirty(false);
      if (request.developmentConfiguration) setKitDraft({ taskId: task.id, dirty: false, samples: {}, jdbcTables: null });
      messageApi.success(`Spark JAR 定义已保存为 v${saved.definitionVersion}`);
      return saved;
    } catch (error) {
      const invalidRuntimeSections = runtimeValidationSections(error);
      if (invalidRuntimeSections.length > 0) {
        setRuntimeConfigurationKeys((current) => [...new Set([...current, ...invalidRuntimeSections])]);
      }
      if (error instanceof ApiError) messageApi.error(error.message);
      return null;
    }
  };

  const beforeJarUpload: NonNullable<UploadProps['beforeUpload']> = (file) => {
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
  };

  const upload = async () => {
    const file = uploadFiles[0]?.originFileObj;
    if (!file) {
      messageApi.warning('请先选择 JAR 文件');
      return;
    }
    if ((dirty || kitConfigDirty) && !(await save())) {
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

  const generateDevelopmentKit = async () => {
    if (developmentKitBusy) return;
    const saved = dirty || kitConfigDirty ? await save() : definition;
    if (!saved) return;
    const missingJdbcTables = kitJdbcBindings.filter((binding) => (
      jdbcTablesForBinding(binding.bindingName).length === 0
    ));
    const legacyMultipleJdbcTables = kitJdbcBindings.filter((binding) => (
      jdbcTablesForBinding(binding.bindingName).length > 1
    ));
    if (missingJdbcTables.length > 0) {
      messageApi.warning(`请先为 JDBC 绑定选择一张数据表：${missingJdbcTables.map((binding) => binding.bindingName).join('、')}`);
      return;
    }
    if (legacyMultipleJdbcTables.length > 0) {
      messageApi.warning(`每个 JDBC 绑定只支持一张本地开发表，请拆分以下绑定：${legacyMultipleJdbcTables.map((binding) => binding.bindingName).join('、')}`);
      return;
    }
    try {
      await createKitMutation.mutateAsync({
        id: task.id,
        request: {
          definitionVersion: saved.definitionVersion,
          samples: kitModelInputs.map((binding) => kitSamples[binding.bindingName]
            ?? persistedKitSamples.get(binding.bindingName)
            ?? {
              bindingName: binding.bindingName,
              mode: 'ROW_COUNT' as const,
              rowCount: 1_000,
            }),
          jdbcTables: effectiveKitJdbcTables,
        },
      });
      setKitDraft({ taskId: task.id, dirty: false, samples: {}, jdbcTables: null });
      messageApi.success('开发包已提交后台生成');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '提交开发包生成任务失败');
    }
  };

  const commitResourceSelection = (selection: SparkJarResourceSelection) => {
    const index = resourceEditorIndex === 'new' ? resourceBindings.length : resourceEditorIndex;
    if (index === null) return;
    const next = replaceSparkJarResource(resourceBindings, {
      samples: kitModelInputs.map((binding) => kitSamples[binding.bindingName]
        ?? persistedKitSamples.get(binding.bindingName)
        ?? { bindingName: binding.bindingName, ...defaultSampleConfiguration() }),
      jdbcTables: effectiveKitJdbcTables,
    }, index, selection);
    form.setFieldValue('resourceBindings', next.bindings);
    setKitDraft({ taskId: task.id, dirty: true,
      samples: Object.fromEntries(next.configuration.samples.map((item) => [item.bindingName, item])),
      jdbcTables: next.configuration.jdbcTables });
    setDirty(true);
    setResourceEditorIndex(null);
  };

  const applyResourceSelection = (selection: SparkJarResourceSelection) => {
    const previous = typeof resourceEditorIndex === 'number' ? resourceBindings[resourceEditorIndex] : undefined;
    const oldTables = previous ? jdbcTablesForBinding(previous.bindingName) : [];
    const dropsTables = oldTables.length > 1 && (previous?.resourceId !== selection.binding.resourceId
      || previous.resourceType !== selection.binding.resourceType || !readableBinding(selection.binding));
    if (dropsTables) {
      Modal.confirm({ rootClassName: 'business-overlay business-modal-overlay', title: <OverlayTitle icon={<ExclamationCircleOutlined />} title="更新资源并清除原表选择？" />, icon: null,
        content: `原绑定包含 ${oldTables.length} 张 JDBC 表。更换资源或移除读取用途后，这些本地开发表选择将被清除；不会删除数据库中的表。`,
        okText: '确认更新', cancelText: '继续编辑', onOk: () => commitResourceSelection(selection) });
    } else commitResourceSelection(selection);
  };

  const downloadDevelopmentKit = async () => {
    try {
      downloadBlob(await downloadKitMutation.mutateAsync(task.id), 'datascalpel-spark-job-development-kit.zip');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '下载开发包失败');
    }
  };

  const renderLocalDevelopmentConfiguration = (
    binding: Omit<SparkJarResourceBinding, 'resourceName'> | undefined,
  ) => {
    if (!binding?.resourceType || !binding.accessMode) return <Typography.Text type="secondary">—</Typography.Text>;
    const canRead = binding.accessMode === 'READ' || binding.accessMode === 'READ_WRITE';
    const canWrite = binding.accessMode === 'WRITE' || binding.accessMode === 'READ_WRITE';
    if (binding.resourceType === 'MODEL') {
      if (!canRead) return <Tag color="purple">Schema + 写入示例</Tag>;
      const bindingName = binding.bindingName?.trim() ?? '';
      const sample = kitSamples[bindingName] ?? persistedKitSamples.get(bindingName) ?? {
        bindingName,
        mode: 'ROW_COUNT' as const,
        rowCount: 1_000,
      };
      return (
        <div className="spark-jar-binding-local-config">
          <SampleConfigurationControls
            value={sample}
            disabled={!bindingName || configurationSaving}
            onChange={(next) => {
              if (!bindingName) return;
              setKitDraft((current) => {
                const samples = current.taskId === task.id ? current.samples : {};
                return {
                  taskId: task.id,
                  dirty: true,
                  samples: { ...samples, [bindingName]: { bindingName, ...next } },
                  jdbcTables: current.taskId === task.id ? current.jdbcTables : null,
                };
              });
            }}
          />
          {canWrite && <Typography.Text type="secondary">同时生成输出 Schema 与写入示例</Typography.Text>}
        </div>
      );
    }
    if (binding.resourceType === 'JDBC_DATA_SOURCE') {
      if (!canRead) return <Typography.Text type="secondary">不生成输入样例</Typography.Text>;
      const bindingName = binding.bindingName?.trim() ?? '';
      const tables = jdbcTablesForBinding(bindingName);
      if (!bindingName || tables.length === 0) {
        return <Typography.Text type="secondary">请先在资源列选择数据源和表</Typography.Text>;
      }
      if (tables.length > 1) {
        return <Typography.Text type="warning">历史配置包含 {tables.length} 张表，请拆分为多行 JDBC 绑定</Typography.Text>;
      }
      const table = tables[0];
      return (
        <div className="spark-jar-binding-local-config">
          <SampleConfigurationControls
            value={table}
            disabled={configurationSaving}
            onChange={(next) => {
              setKitDraft((current) => {
                const sourceTables = current.taskId === task.id ? current.jdbcTables ?? persistedKitJdbcTables : persistedKitJdbcTables;
                return {
                  taskId: task.id,
                  dirty: true,
                  samples: current.taskId === task.id ? current.samples : {},
                  jdbcTables: sourceTables.map((item) => item.bindingName === bindingName ? { ...item, ...next } : item),
                };
              });
            }}
          />
          {canWrite && <Typography.Text type="secondary">同时生成输出写入示例</Typography.Text>}
        </div>
      );
    }
    if (binding.resourceType === 'KAFKA_TOPIC') {
      if (binding.accessMode === 'READ_WRITE') {
        return <Typography.Text type="secondary">UTF-8 假消息 · 输入/输出 Test Topic 隔离</Typography.Text>;
      }
      return <Typography.Text type="secondary">
        {binding.accessMode === 'READ' ? '生成可编辑 UTF-8 假消息' : '生成独立输出 Test Topic'}
      </Typography.Text>;
    }
    return <Typography.Text type="secondary">—</Typography.Text>;
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
          {authoringMode && <Tag color="geekblue">{authoringMode === 'ONLINE' ? '在线开发' : '上传 JAR'}</Tag>}
          {authoringMode && <Button type="text" disabled={updateMutation.isPending || uploadMutation.isPending} onClick={() => Modal.confirm({
            rootClassName: 'business-overlay business-modal-overlay', title: <OverlayTitle icon={<ExclamationCircleOutlined />} title="更换开发方式？" />, icon: null, content: '保留在线源码和当前生效 JAR；只有明确应用代码或上传替换才改变 JAR。',
            okText: authoringMode === 'ONLINE' ? '改为上传 JAR' : '改为在线开发', cancelText: '取消',
            onOk: () => { form.setFieldValue('authoringMode', authoringMode === 'ONLINE' ? 'UPLOAD' : 'ONLINE'); setUploadFiles([]); setDirty(true); },
          })}>更换开发方式</Button>}
          {dirty && <Tag color="warning">任务配置未保存</Tag>}
          {authoringMode === 'UPLOAD' && kitConfigDirty && <Tag color="orange">样例配置未保存</Tag>}
          <Button type="primary" icon={<SaveOutlined />} disabled={!authoringMode || uploadMutation.isPending} loading={updateMutation.isPending} onClick={() => void save()}>
            保存配置
          </Button>
        </Space>
      </div>
      <div className="spark-jar-definition-scroll">
        {!authoringMode && <SparkJarAuthoringModeChoice onConfirm={(mode) => { form.setFieldValue('authoringMode', mode); setDirty(true); }} />}

        <Form<SparkJarDefinitionFormValues>
          className="spark-jar-workspace-stack"
          autoComplete="off"
          form={form}
          layout="vertical"
          disabled={updateMutation.isPending || uploadMutation.isPending}
          initialValues={{ parameters: [], sparkConf: [], driverJavaOptions: '', resourceBindings: [], timeoutSeconds: 3600 }}
          onValuesChange={() => setDirty(true)}
          style={{ display: authoringMode ? undefined : 'none' }}
        >
          <Form.Item name="authoringMode" hidden><Input /></Form.Item>
          {authoringMode === 'ONLINE' && <SparkJarOnlineCodeSummary
            source={onlineSourceQuery.data} failed={onlineSourceQuery.isError} loading={onlineSourceQuery.isPending}
            opening={updateMutation.isPending} onRetry={() => void onlineSourceQuery.refetch()}
            onOpen={() => void (async () => {
              if ((dirty || kitConfigDirty) && !await save()) return;
              savedNavigation.current = true;
              navigate(taskPageHref(`/task/${task.id}/online-code`, location.search, task.type));
            })()} />}
          <section className="spark-jar-definition-section spark-jar-resource-bindings-section">
            <div className="spark-jar-resource-heading">
              <div><Space><Typography.Text strong>任务资源</Typography.Text>
                <ContextHelp ariaLabel="任务资源说明" content="可选。输入允许读取，输出允许写入，输入及输出允许两者。绑定后可在代码中引用，实际读写由代码决定，不要求输入输出成对选择。" /></Space>
                <div className="spark-jar-section-hint">可选 · 输入读数据，输出写结果。</div></div>
              <Button icon={<PlusOutlined />} disabled={configurationSaving || resourceBindings.length >= 200 || !kitQuery.isSuccess} onClick={() => setResourceEditorIndex('new')}>添加资源</Button>
            </div>
            {kitQuery.isError && <Alert type="error" message="开发配置加载失败，暂不能修改资源" action={<Button onClick={() => void kitQuery.refetch()}>重试</Button>} />}
            <Form.Item name="resourceBindings" hidden rules={[{ validator: async (_, bindings: SparkJarDefinitionFormValues['resourceBindings']) => {
              if (bindings?.some((binding) => !binding.bindingName?.trim() || !binding.resourceId || !binding.accessMode)) throw new Error('请补全资源绑定');
              if (new Set(bindings?.map((binding) => binding.bindingName.trim())).size !== bindings?.length) throw new Error('代码引用名不能重复');
            } }]}><Input /></Form.Item>
            <Table size="small" rowKey="bindingName" pagination={false} scroll={{ y: 'clamp(112px, calc(100vh - 630px), 208px)' }}
              dataSource={resourceBindings} locale={{ emptyText: '暂未添加资源 · 不访问平台数据时可留空' }}
              columns={[
                { title: '资源', render: (_, binding, index) => <ResourceSelectionButton binding={binding}
                  jdbcTable={jdbcTablesForBinding(binding.bindingName)[0]} disabled={configurationSaving || !kitQuery.isSuccess} onClick={() => setResourceEditorIndex(index)} /> },
                { title: '代码引用名', dataIndex: 'bindingName', width: '20%', ellipsis: true, render: (name: string) => <span className="spark-jar-code-reference">{name}</span> },
                { title: '用途', width: 110, render: (_, binding) => <Tag color="geekblue">{binding.accessMode === 'READ' ? '输入' : binding.accessMode === 'WRITE' ? '输出' : '输入及输出'}</Tag> },
                { hidden: authoringMode !== 'UPLOAD', title: <Space>本地样例<ContextHelp ariaLabel="本地样例说明" content="用于生成开发工程，不限制正式运行读取的数据量。零行仅导出结构；全部数据最多 100 万行。" /></Space>, width: 340,
                  render: (_, binding) => renderLocalDevelopmentConfiguration(binding) },
                { title: '操作', width: 112, render: (_, binding, index) => <Space size={0}>
                  <Button type="link" disabled={configurationSaving || !kitQuery.isSuccess} onClick={() => setResourceEditorIndex(index)}>编辑</Button>
                  <Button type="text" disabled={configurationSaving || !kitQuery.isSuccess} danger icon={<DeleteOutlined />} aria-label={`移除资源 ${binding.bindingName}`} onClick={() => Modal.confirm({
                    rootClassName: 'business-overlay business-modal-overlay', title: <OverlayTitle icon={<DeleteOutlined />} title={`移除资源“${binding.bindingName}”？`} tone="danger" />, icon: null, content: '不会删除数据，已有源码中的引用需要自行修改。', okText: '移除', cancelText: '取消', okButtonProps: { danger: true },
                    onOk: () => { removeDevelopmentConfiguration(binding.bindingName); form.setFieldValue('resourceBindings', resourceBindings.filter((_, i) => i !== index)); setDirty(true); },
                  })} />
                </Space> },
              ]} />
          </section>

          {authoringMode === 'UPLOAD' && <div className="spark-jar-delivery-grid">
          <details open={authoringMode === 'UPLOAD'} key={authoringMode}>
            <summary>本地开发工程</summary>
            <SparkJarDevelopmentKitPanel
              status={developmentKitStatus}
              generation={developmentKitGeneration}
              artifact={developmentKitArtifact}
              configurationDirty={kitConfigDirty || developmentKitNeedsRefresh}
              inputModelCount={kitModelInputs.length}
              jdbcTableCount={effectiveKitJdbcTables.length}
              outputModelCount={kitModelOutputs.length}
              running={developmentKitRunning}
              submitting={createKitMutation.isPending}
              disabled={updateMutation.isPending || uploadMutation.isPending}
              downloading={downloadKitMutation.isPending}
              onGenerate={() => void generateDevelopmentKit()}
              onDownload={() => void downloadDevelopmentKit()}
          />
          </details>
          {authoringMode === 'UPLOAD' && <SparkJarArtifactSummary
            definition={definition} uploadFiles={uploadFiles} uploading={uploadMutation.isPending || updateMutation.isPending}
            beforeUpload={beforeJarUpload} onClearSelection={() => setUploadFiles([])} onUpload={upload}
          />}
          </div>}

          {computeEngineQuery.isError && <Alert type="error" message="计算引擎加载失败"
            action={<Button size="small" onClick={() => void computeEngineQuery.refetch()}>重试</Button>} />}
          <SparkJarRuntimeConfiguration
              activeKeys={runtimeConfigurationKeys}
              onChange={setRuntimeConfigurationKeys}
              overview={{
                environment: resourceEnvironment,
                resources: resourceSummary,
                timeout: timeoutSummary,
                timeoutLabel: streaming ? '查询注册超时' : '最长运行',
              }}
              items={[
                {
                  key: 'resources',
                  label: '运行资源',
                  summary: resourceSummary,
                  placement: 'primary',
                  children: <SparkJarExecutionResourcesEditor backend={resourceBackend} maximums={resourceMaximums} defaults={computeEngineQuery.data?.resourcePolicy.defaults} showEnvironment={false} />,
                },
                {
                  key: 'timeout',
                  label: streaming ? '查询注册超时' : '执行超时',
                  summary: timeoutSummary,
                  placement: 'primary',
                  children: (
                    <div className="spark-jar-runtime-timeout-editor">
                      <Form.Item
                        name="timeoutSeconds"
                        label={streaming ? 'start() 查询注册超时' : '执行超时'}
                        rules={[{ required: true }, { type: 'number', min: 1, max: 86400 }]}
                      >
                        <SparkJarExecutionTimeoutEditor />
                      </Form.Item>
                      <Typography.Text type="secondary">
                        {streaming
                          ? 'start() 必须在时限内完成全部查询注册并返回；Trigger 和 Output Mode 由用户代码控制。'
                          : '达到时限后，平台会终止本次任务运行。'}
                      </Typography.Text>
                    </div>
                  ),
                },
                {
                  key: 'jvm',
                  label: 'Driver JVM',
                  summary: `${driverJavaOptionsCount} 项`,
                  children: <SparkJarDriverJvmOptionsEditor backend={resourceBackend} />,
                },
                {
                  key: 'arguments',
                  label: '参数与 Spark Conf',
                  summary: `参数 ${watchedParameters?.length ?? 0} 项 · Conf ${watchedSparkConf?.length ?? 0} 项`,
                  children: (
                    <div className="spark-jar-runtime-entry-columns">
                      <div>
                        <Typography.Text strong>启动参数</Typography.Text>
                        <EntryEditor
                          name="parameters" addLabel="添加参数" keyPlaceholder="例如 processingDate"
                          valueMax={4000} valueRequired={false}
                          keyRules={[{ required: true, message: '请输入参数 Key' }, { max: 100 }]}
                        />
                      </div>
                      <div>
                        <Typography.Text strong>Spark Conf</Typography.Text>
                        <EntryEditor
                          name="sparkConf" addLabel="添加 Spark Conf" keyPlaceholder="spark.sql.shuffle.partitions"
                          valueMax={2000} valueRequired
                          keyRules={[
                            { required: true, message: '请输入 Spark Conf Key' }, { max: 500 },
                            { pattern: /^spark\./, message: 'Key 必须以 spark. 开头' },
                          ]}
                        />
                      </div>
                    </div>
                  ),
                },
              ]}
          />
        </Form>
      </div>
      <Modal
        rootClassName="business-overlay business-modal-overlay"
        open={blocker.state === 'blocked'}
        title={<OverlayTitle icon={<ExclamationCircleOutlined />} title="存在未保存的 Spark JAR 配置" />}
        okText="放弃修改并离开"
        okButtonProps={{ danger: true }}
        cancelText="继续编辑"
        onOk={() => blocker.state === 'blocked' && blocker.proceed()}
        onCancel={() => blocker.state === 'blocked' && blocker.reset()}
      >
        {dirty && kitConfigDirty
          ? '任务配置和本地样例配置尚未保存。'
          : dirty
            ? '参数、Spark Conf、资源绑定或超时设置尚未保存。'
            : '本地样例配置尚未保存。'}
      </Modal>
      {resourceEditorIndex !== null && <SparkJarResourceDrawer
        key={resourceEditorIndex}
        initial={resourceEditorIndex === 'new' ? undefined : resourceBindings[resourceEditorIndex]}
        initialTable={resourceEditorIndex === 'new' ? undefined : (() => {
          const table = jdbcTablesForBinding(resourceBindings[resourceEditorIndex]?.bindingName ?? '')[0];
          return table ? { catalog: table.catalog ?? null, schema: table.schema ?? null, table: table.table } : undefined;
        })()}
        bindingNames={resourceBindings.map((binding) => binding.bindingName)} streaming={streaming}
        onClose={() => setResourceEditorIndex(null)} onConfirm={applyResourceSelection}
      />}
    </div>
  );
};
