import { taskPageHref } from '../model/taskViews';
import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  ExclamationCircleOutlined,
  InfoCircleOutlined,
  PlusOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import { Button, Form, Input, InputNumber, Modal, Select, Space, Spin, Table, Tag, Tooltip, Typography, Upload, message, type UploadFile, type UploadProps } from 'antd';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useBlocker, useLocation, useNavigate, type BlockerFunction } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { DataModelPickerModal, useDataModel } from '../../model';
import { computeBackendTypeLabels, useComputeEngine, type ComputeBackendType, type SparkExecutionResourceSpec as EngineSparkExecutionResourceSpec } from '../../computeengine';
import {
  JdbcResourcePickerModal,
  jdbcTableIdentifierDisplayName,
  useDataSource,
  useDataSources,
  type JdbcResourceSelection,
} from '../../datasource';
import { CanvasKafkaTopicSelect } from '../canvas/components/CanvasKafkaSelectors';
import {
  useGenerateSparkJarDevelopmentKit,
  useDownloadSparkJarDevelopmentKit,
  useSparkJarDevelopmentKit,
  useSparkJarTaskDefinition,
  useUpdateSparkJarTaskDefinition,
  useUploadSparkJar,
} from '../hooks/useTasks';
import type {
  DataTask,
  SparkJarDefinitionEntry,
  SparkJarDevelopmentKitInputSample,
  SparkJarDevelopmentKitJdbcTable,
  SparkJarDevelopmentKitSampleMode,
  SparkJarResourceAccessMode,
  SparkJarResourceBinding,
  SparkJarResourceType,
  UpdateSparkJarTaskDefinitionRequest,
  SparkJarTaskDefinition,
  SparkExecutionResourceSpec,
} from '../model/task';
import {
  SparkJarArtifactSummary,
  SparkJarDevelopmentKitPanel,
  SparkJarRuntimeConfiguration,
} from './SparkJarDefinitionWorkspace';

interface SparkJarDefinitionFormValues {
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

const accessModeOptions: Array<{ value: SparkJarResourceAccessMode; label: string }> = [
  { value: 'READ', label: '只读' },
  { value: 'WRITE', label: '只写' },
  { value: 'READ_WRITE', label: '读写' },
];

const baseResourceTypeOptions: Array<{ value: SparkJarResourceType; label: string }> = [
  { value: 'MODEL', label: '模型' },
  { value: 'JDBC_DATA_SOURCE', label: 'JDBC 数据源' },
];

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
  showEnvironment = true,
}: {
  backend: ComputeBackendType | undefined;
  maximums: EngineSparkExecutionResourceSpec | undefined;
  showEnvironment?: boolean;
}) => {
  const localDocker = backend === 'LOCAL_DOCKER';
  const environment = backend === 'LOCAL_DOCKER'
    ? 'Local Docker · local[*]'
    : backend ? computeBackendTypeLabels[backend] : '正在读取计算引擎';
  const driverCoresMax = maximums?.driverCores ?? 256;
  const driverMemoryMax = Math.floor((maximums?.driverMemoryMiB ?? 1_048_576) / 1024);
  const executorCountMax = maximums?.executorInstances ?? 10_000;
  const executorCoresMax = maximums?.executorCores ?? 256;
  const executorMemoryMax = Math.floor((maximums?.executorMemoryMiB ?? 1_048_576) / 1024);
  return (
    <div className="spark-jar-runtime-resource-editor">
      {showEnvironment && <Form.Item label="运行环境">
        <Input value={environment} readOnly />
      </Form.Item>}
      <div className="spark-jar-runtime-resource-grid">
        <Form.Item name={['executionResources', 'driverCores']} label={<span>驱动 CPU <Tooltip title="Spark Driver 使用的 CPU 核数；在 Local Docker 中映射为容器 --cpus。"><InfoCircleOutlined /></Tooltip></span>} rules={[{ required: true }]}>
          <InputNumber min={1} max={driverCoresMax} precision={0} addonAfter="Core" />
        </Form.Item>
        <Form.Item name={['executionResources', 'driverMemoryMiB']} label={<span>驱动内存 <Tooltip title="Spark Driver 内存；在 Local Docker 中映射为容器 --memory，JVM 堆自动取其中的 75%。"><InfoCircleOutlined /></Tooltip></span>} getValueProps={memoryGiBProps} normalize={toMiB} rules={[{ required: true }]}>
          <InputNumber min={1} max={driverMemoryMax} precision={0} addonAfter="GiB" />
        </Form.Item>
        {!localDocker && <>
          <Form.Item name={['executionResources', 'executorInstances']} label={<span>执行器数量 <Tooltip title="提交到 YARN 或 Kubernetes 的 Executor 实例数量。"><InfoCircleOutlined /></Tooltip></span>} rules={[{ required: true }]}>
            <InputNumber min={1} max={executorCountMax} precision={0} />
          </Form.Item>
          <Form.Item name={['executionResources', 'executorCores']} label={<span>单执行器 CPU <Tooltip title="每个 Spark Executor 使用的 CPU 核数。"><InfoCircleOutlined /></Tooltip></span>} rules={[{ required: true }]}>
            <InputNumber min={1} max={executorCoresMax} precision={0} addonAfter="Core" />
          </Form.Item>
          <Form.Item name={['executionResources', 'executorMemoryMiB']} label={<span>单执行器内存 <Tooltip title="每个 Spark Executor 使用的内存。"><InfoCircleOutlined /></Tooltip></span>} getValueProps={memoryGiBProps} normalize={toMiB} rules={[{ required: true }]}>
            <InputNumber min={1} max={executorMemoryMax} precision={0} addonAfter="GiB" />
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
    <Space size={8} wrap>
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
  if (binding.resourceType === 'KAFKA_TOPIC') return null;

  const label = binding.resourceType === 'MODEL'
    ? modelQuery.data?.model
      ? `${modelQuery.data.model.name} · ${modelQuery.data.model.code}`
      : binding.resourceId ? '已选择模型' : '选择模型'
    : dataSourceQuery.data
      ? `${dataSourceQuery.data.name} · ${tableLabel ?? dataSourceQuery.data.code}`
      : binding.resourceId ? (tableLabel ? `已选择 JDBC 表 · ${tableLabel}` : '已选择 JDBC 数据源')
        : readableBinding(binding) ? '选择 JDBC 数据源和表' : '选择 JDBC 数据源';

  return (
    <Button
      block
      className="spark-jar-resource-picker-button"
      disabled={disabled}
      loading={modelQuery.isFetching || dataSourceQuery.isFetching}
      onClick={onClick}
    >
      <Typography.Text ellipsis>{label}</Typography.Text>
    </Button>
  );
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
  const [kitDraft, setKitDraft] = useState<DevelopmentKitDraftState>({
    taskId: task.id,
    dirty: false,
    samples: {},
    jdbcTables: null,
  });
  const [modelPickerFieldIndex, setModelPickerFieldIndex] = useState<number | null>(null);
  const [jdbcPickerFieldIndex, setJdbcPickerFieldIndex] = useState<number | null>(null);
  const [runtimeConfigurationKeys, setRuntimeConfigurationKeys] = useState<string[]>([]);
  const definitionQuery = useSparkJarTaskDefinition(task.id);
  const computeEngineQuery = useComputeEngine(task.computeEngineId ?? undefined);
  const dataSourcesQuery = useDataSources({ page: 0, size: 500, sort: 'code' }, task.type === 'SPARK_STREAMING_JAR');
  const updateMutation = useUpdateSparkJarTaskDefinition();
  const uploadMutation = useUploadSparkJar();
  const createKitMutation = useGenerateSparkJarDevelopmentKit();
  const downloadKitMutation = useDownloadSparkJarDevelopmentKit();
  const kitQuery = useSparkJarDevelopmentKit(task.id, task.type === 'SPARK_JAR' || task.type === 'SPARK_STREAMING_JAR');
  const watchedParameters = Form.useWatch('parameters', form);
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
    ? computeBackendTypeLabels[resourceBackend]
    : '正在读取计算引擎';
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
  const developmentKitStatus = developmentKitBusy
    ? { label: '生成中', color: 'processing' }
    : developmentKitGeneration?.status === 'FAILED'
      ? { label: '生成失败', color: 'error' }
      : kitConfigDirty || (developmentKitArtifact && !developmentKitArtifact.matchesSavedConfiguration)
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
  const resourceTypeOptions = useMemo<Array<{ value: SparkJarResourceType; label: string }>>(() => (
    streaming
      ? [...baseResourceTypeOptions, { value: 'KAFKA_TOPIC', label: 'Kafka Topic' }]
      : baseResourceTypeOptions
  ), [streaming]);
  const blocker = useBlocker(useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) => protectNavigation && (dirty || kitConfigDirty) && (
      currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search
    ),
    [dirty, kitConfigDirty, protectNavigation],
  ));

  useEffect(() => {
    if (!definition) return;
    const runtimeOptions = driverJavaOptionsFromSparkConf(definition.sparkConf);
    form.setFieldsValue({
      parameters: definition.parameters,
      sparkConf: runtimeOptions.sparkConf,
      driverJavaOptions: runtimeOptions.driverJavaOptions,
      resourceBindings: definition.resourceBindings.map(resourceBindingFormValue),
      executionResources: definition.executionResources,
      timeoutSeconds: definition.timeoutSeconds,
    });
  }, [definition, form]);

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!dirty && !kitConfigDirty) return;
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty, kitConfigDirty]);

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

  const kafkaResourceOptions = (binding: Omit<SparkJarResourceBinding, 'resourceName'> | undefined) => {
    const options = kafkaOptions(binding?.accessMode);
    const saved = definition?.resourceBindings.find((item) => (
      item.bindingName === binding?.bindingName && item.resourceType === 'KAFKA_TOPIC'
    ));
    if (binding?.resourceId && !options.some((option) => option.value === binding.resourceId)) {
      options.push({ value: binding.resourceId, label: `${saved?.resourceName ?? '资源已删除或不可用'} · 当前绑定` });
    }
    return options;
  };

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

  const migrateDevelopmentConfiguration = (previousName: string, nextName: string) => {
    const from = previousName.trim();
    const to = nextName.trim();
    if (!from || !to || from === to) return;
    setKitDraft((current) => {
      const samples = { ...(current.taskId === task.id ? current.samples : {}) };
      const sample = samples[from] ?? persistedKitSamples.get(from);
      delete samples[from];
      if (sample) samples[to] = { ...sample, bindingName: to };
      const sourceTables = current.taskId === task.id ? current.jdbcTables ?? persistedKitJdbcTables : persistedKitJdbcTables;
      const jdbcTables = sourceTables.map((table) => table.bindingName === from ? { ...table, bindingName: to } : table);
      return { taskId: task.id, dirty: true, samples, jdbcTables };
    });
  };

  const save = async (): Promise<SparkJarTaskDefinition | null> => {
    try {
      const values = await form.validateFields();
      const sparkConf = withDriverJavaOptions(values.sparkConf ?? [], values.driverJavaOptions);
      const request: UpdateSparkJarTaskDefinitionRequest = {
        parameters: values.parameters ?? [],
        sparkConf,
        resourceBindings: values.resourceBindings ?? [],
        executionResources: {
          ...definition?.executionResources,
          ...values.executionResources,
        },
        timeoutSeconds: values.timeoutSeconds ?? definition?.timeoutSeconds ?? 3_600,
      };
      const saved = await updateMutation.mutateAsync({ id: task.id, request });
      const savedRuntimeOptions = driverJavaOptionsFromSparkConf(saved.sparkConf);
      form.setFieldsValue({
        parameters: saved.parameters,
        sparkConf: savedRuntimeOptions.sparkConf,
        driverJavaOptions: savedRuntimeOptions.driverJavaOptions,
        resourceBindings: saved.resourceBindings.map(resourceBindingFormValue),
        executionResources: saved.executionResources,
        timeoutSeconds: saved.timeoutSeconds,
      });
      setDirty(false);
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

  const generateDevelopmentKit = async () => {
    if (developmentKitBusy) return;
    const saved = dirty ? await save() : definition;
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

  const applyModelSelection = (modelIds: string[]) => {
    const fieldIndex = modelPickerFieldIndex;
    const modelId = modelIds[0];
    if (fieldIndex === null || !modelId) return;
    form.setFieldValue(['resourceBindings', fieldIndex, 'resourceId'], modelId);
    setDirty(true);
    setModelPickerFieldIndex(null);
  };

  const applyJdbcSelection = (selection: JdbcResourceSelection) => {
    const fieldIndex = jdbcPickerFieldIndex;
    if (fieldIndex === null) return;
    const binding = resourceBindings[fieldIndex];
    if (!binding) return;
    const canRead = readableBinding(binding);
    const previousName = binding.bindingName?.trim() ?? '';
    const nextName = previousName || selection.table?.table || selection.dataSourceCode;
    form.setFieldValue(['resourceBindings', fieldIndex, 'resourceId'], selection.dataSourceId);
    if (!previousName) form.setFieldValue(['resourceBindings', fieldIndex, 'bindingName'], nextName);
    if (canRead && selection.table) {
      const currentTables = jdbcTablesForBinding(previousName || nextName);
      const previousTable = currentTables[0];
      const replacement: SparkJarDevelopmentKitJdbcTable = {
        bindingName: nextName,
        catalog: selection.table.catalog,
        schema: selection.table.schema,
        table: selection.table.table,
        ...(previousTable ? {
          mode: previousTable.mode,
          rowCount: previousTable.rowCount,
          percentage: previousTable.percentage,
        } : defaultSampleConfiguration()),
      };
      setKitDraft((current) => {
        const sourceTables = current.taskId === task.id ? current.jdbcTables ?? persistedKitJdbcTables : persistedKitJdbcTables;
        return {
          taskId: task.id,
          dirty: true,
          samples: current.taskId === task.id ? current.samples : {},
          jdbcTables: [...sourceTables.filter((table) => table.bindingName !== previousName && table.bindingName !== nextName), replacement],
        };
      });
    }
    setDirty(true);
    setJdbcPickerFieldIndex(null);
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
            disabled={!bindingName}
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

  const modelPickerBinding = modelPickerFieldIndex === null ? undefined : resourceBindings[modelPickerFieldIndex];
  const jdbcPickerBinding = jdbcPickerFieldIndex === null ? undefined : resourceBindings[jdbcPickerFieldIndex];
  const jdbcPickerTables = jdbcPickerBinding
    ? jdbcTablesForBinding(jdbcPickerBinding.bindingName?.trim() ?? '')
    : [];
  const jdbcPickerTable = jdbcPickerTables.length === 1 ? jdbcPickerTables[0] : undefined;

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
          {dirty && <Tag color="warning">任务配置未保存</Tag>}
          {kitConfigDirty && <Tag color="orange">开发配置待生成</Tag>}
          <Button type="primary" icon={<SaveOutlined />} loading={updateMutation.isPending} onClick={() => void save()}>
            保存配置
          </Button>
        </Space>
      </div>
      <div className="spark-jar-definition-scroll">
        <SparkJarArtifactSummary
          definition={definition}
          uploadFiles={uploadFiles}
          uploading={uploadMutation.isPending || updateMutation.isPending}
          beforeUpload={beforeJarUpload}
          onClearSelection={() => setUploadFiles([])}
          onUpload={() => void upload()}
          onOpenOnlineEditor={() => navigate(taskPageHref(`/task/${task.id}/online-code`, location.search, task.type))}
        />

        <Form<SparkJarDefinitionFormValues>
          className="spark-jar-workspace-stack"
          autoComplete="off"
          form={form}
          layout="vertical"
          initialValues={{ parameters: [], sparkConf: [], driverJavaOptions: '', resourceBindings: [], timeoutSeconds: 3600 }}
          onValuesChange={() => setDirty(true)}
        >
          <section className="spark-jar-definition-section spark-jar-resource-bindings-section">
            <div className="spark-jar-definition-section-title">资源绑定</div>
            <Form.List name="resourceBindings">
              {(fields, { add, remove }) => (
                <Space orientation="vertical" size={8} className="spark-jar-list-editor">
                  <Table
                    size="small"
                    rowKey="key"
                    pagination={false}
                    scroll={{ x: streaming ? 1300 : 1180 }}
                    dataSource={fields}
                    locale={{ emptyText: '无需平台资源时可以保持为空' }}
                    columns={[
                      {
                        title: '绑定名', width: '22%',
                        render: (_, field) => {
                          const binding = resourceBindings[field.name];
                          return (
                            <Form.Item name={[field.name, 'bindingName']} rules={[{ required: true, message: '请输入绑定名' }, { max: 100 }]} noStyle>
                              <Input
                                placeholder="source_model"
                                onChange={(event) => migrateDevelopmentConfiguration(binding?.bindingName ?? '', event.target.value)}
                              />
                            </Form.Item>
                          );
                        },
                      },
                      {
                        title: '资源类型', width: 200,
                        render: (_, field) => (
                          <Form.Item name={[field.name, 'resourceType']} rules={[{ required: true, message: '请选择类型' }]} noStyle>
                            <Select
                              className="spark-jar-resource-type-select"
                              options={resourceTypeOptions}
                              popupMatchSelectWidth={180}
                              onChange={() => {
                                const binding = resourceBindings[field.name];
                                form.setFieldValue(['resourceBindings', field.name, 'resourceId'], undefined);
                                form.setFieldValue(['resourceBindings', field.name, 'topicName'], undefined);
                                removeDevelopmentConfiguration(binding?.bindingName ?? '');
                              }}
                            />
                          </Form.Item>
                        ),
                      },
                      {
                        title: '资源',
                        render: (_: unknown, field: { name: number }) => {
                          const binding = resourceBindings[field.name];
                          if (binding?.resourceType === 'KAFKA_TOPIC') {
                            return <Form.Item name={[field.name, 'resourceId']} rules={[{ required: true, message: '请选择资源' }]} noStyle><Select showSearch optionFilterProp="label" loading={dataSourcesQuery.isFetching} options={kafkaResourceOptions(binding)} placeholder="请选择 Kafka 数据源" /></Form.Item>;
                          }
                          const bindingName = binding?.bindingName?.trim() ?? '';
                          const jdbcTable = binding?.resourceType === 'JDBC_DATA_SOURCE'
                            ? jdbcTablesForBinding(bindingName)[0]
                            : undefined;
                          return (
                            <>
                              <Form.Item name={[field.name, 'resourceId']} rules={[{ required: true, message: '请选择资源' }]} noStyle>
                                <Input type="hidden" />
                              </Form.Item>
                              <ResourceSelectionButton
                                binding={binding}
                                jdbcTable={jdbcTable}
                                disabled={!binding?.resourceType}
                                onClick={() => {
                                  if (binding?.resourceType === 'MODEL') setModelPickerFieldIndex(field.name);
                                  if (binding?.resourceType === 'JDBC_DATA_SOURCE') setJdbcPickerFieldIndex(field.name);
                                }}
                              />
                            </>
                          );
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
                      ...([{
                        title: '本地开发',
                        width: 390,
                        render: (_: unknown, field: { name: number }) => (
                          renderLocalDevelopmentConfiguration(resourceBindings[field.name])
                        ),
                      }]),
                      {
                        title: '访问方式', width: 130,
                        render: (_, field) => <Form.Item name={[field.name, 'accessMode']} rules={[{ required: true, message: '请选择访问方式' }]} noStyle><Select options={accessModeOptions} onChange={(accessMode: SparkJarResourceAccessMode) => {
                          if (accessMode === 'WRITE') removeDevelopmentConfiguration(resourceBindings[field.name]?.bindingName ?? '');
                        }} /></Form.Item>,
                      },
                      {
                        title: '操作', width: 56, align: 'center',
                        render: (_, field) => <Button type="text" danger icon={<DeleteOutlined />} aria-label="删除资源绑定" onClick={() => {
                          removeDevelopmentConfiguration(resourceBindings[field.name]?.bindingName ?? '');
                          remove(field.name);
                        }} />,
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

          <SparkJarDevelopmentKitPanel
              status={developmentKitStatus}
              generation={developmentKitGeneration}
              artifact={developmentKitArtifact}
              configurationDirty={kitConfigDirty}
              inputModelCount={kitModelInputs.length}
              jdbcTableCount={effectiveKitJdbcTables.length}
              outputModelCount={kitModelOutputs.length}
              running={developmentKitRunning}
              submitting={createKitMutation.isPending}
              downloading={downloadKitMutation.isPending}
              onGenerate={() => void generateDevelopmentKit()}
              onDownload={() => void downloadDevelopmentKit()}
          />

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
                  children: <SparkJarExecutionResourcesEditor backend={resourceBackend} maximums={resourceMaximums} showEnvironment={false} />,
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
        title="存在未保存的 Spark JAR 配置"
        okText="放弃修改并离开"
        okButtonProps={{ danger: true }}
        cancelText="继续编辑"
        onOk={() => blocker.state === 'blocked' && blocker.proceed()}
        onCancel={() => blocker.state === 'blocked' && blocker.reset()}
      >
        {dirty && kitConfigDirty
          ? '任务配置和本地开发配置均未保存。本地开发配置需要通过“生成开发包”保存。'
          : dirty
            ? '参数、Spark Conf、资源绑定或超时设置尚未保存。'
            : '本地开发配置尚未保存，需要通过“生成开发包”保存。'}
      </Modal>
      <DataModelPickerModal
        open={modelPickerFieldIndex !== null}
        value={modelPickerBinding?.resourceId ? [modelPickerBinding.resourceId] : []}
        title="选择已发布模型"
        rootClassName="business-overlay business-modal-overlay"
        onCancel={() => setModelPickerFieldIndex(null)}
        onConfirm={applyModelSelection}
      />
      <JdbcResourcePickerModal
        open={jdbcPickerFieldIndex !== null}
        value={jdbcPickerBinding?.resourceId ? {
          dataSourceId: jdbcPickerBinding.resourceId,
          dataSourceName: '',
          dataSourceCode: '',
          table: jdbcPickerTable
            ? { catalog: jdbcPickerTable.catalog ?? null, schema: jdbcPickerTable.schema ?? null, table: jdbcPickerTable.table }
            : null,
        } : null}
        purposes={jdbcPickerBinding?.accessMode === 'WRITE' ? ['DISTRIBUTION'] : ['SOURCE', ...(jdbcPickerBinding?.accessMode === 'READ_WRITE' ? ['DISTRIBUTION' as const] : [])]}
        requireTable={readableBinding(jdbcPickerBinding)}
        title={readableBinding(jdbcPickerBinding) ? '选择 JDBC 数据源和表' : '选择 JDBC 数据源'}
        rootClassName="business-overlay business-modal-overlay"
        onCancel={() => setJdbcPickerFieldIndex(null)}
        onConfirm={applyJdbcSelection}
      />
    </div>
  );
};
