import { taskPageHref } from '../model/taskViews';
import { sparkJarReadSnippet } from '../model/sparkJarCodeResource';
import { replaceSparkJarResource, type SparkJarResourceSelection } from '../model/sparkJarResourceConfiguration';
import { SparkJarResourceDrawer } from '../components/SparkJarResourceDrawer';
import { SparkJarResourceActions } from '../components/SparkJarResourceActions';
import { SdkApiDrawer } from '../components/SdkApiDrawer';
import '../components/sparkJarOnlineWorkspace.css';
import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import {
  ArrowLeftOutlined,
  BookOutlined,
  CheckCircleOutlined,
  CloudUploadOutlined,
  CloudServerOutlined,
  CodeOutlined,
  DatabaseOutlined,
  DownOutlined,
  ExclamationCircleOutlined,
  FieldStringOutlined,
  SaveOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  StopOutlined,
  TableOutlined,
} from '@ant-design/icons';
import { useQueries } from '@tanstack/react-query';
import { Button, Empty, List, Modal, Popover, Result, Skeleton, Space, Spin, Switch, Tag, Tabs, Tooltip, Typography, message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useBlocker, useLocation, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { fetchDataModel } from '../../model';
import { fetchTableMetadata } from '../../datasource';
import {
  SparkJarJavaEditor,
  type SparkJarCodeResource,
  type SparkJarJavaEditorHandle,
} from '../components/SparkJarJavaEditor';
import {
  SparkJarTrialPreviewPanel,
} from '../components/SparkJarTrialPreviewPanel';
import { TaskRunLogViewer } from '../components/TaskRunLogViewer';
import {
  useCompileSparkJarOnlineSource,
  useCheckSparkJarOnlineSource,
  useUpdateSparkJarTaskDefinition,
  useTrialRunSparkJarOnlineSource,
  useTaskRuns,
  useTaskRun,
  useSparkJarTrialPreview,
  useCancelTaskRun,
  useForceTerminateTaskRun,
  useStopTaskRun,
  useSaveSparkJarOnlineSource,
  useSparkJarDevelopmentKit,
  useSparkJarOnlineSource,
  useSparkJarTaskDefinition,
  useTask,
} from '../hooks/useTasks';
import {
  executionErrorCategoryLabels,
  executionFailurePhaseLabels,
  taskRunStatusColors,
  taskRunStatusLabels,
} from '../model/task';
import type {
  SparkJarDevelopmentKit,
  SparkJarOnlineDiagnostic,
  SparkJarOnlineSource,
  SparkJarTaskDefinition,
  SparkJarTrialPreview,
} from '../model/task';

const onlineSourceLimit = 256 * 1024;
const autoSavePreferenceKey = 'datascalpel.spark-jar.auto-save';

const countTrialPreviewOutputs = (preview: SparkJarTrialPreview): number => (
  new Set(preview.writes.map((write) => JSON.stringify([
    write.resourceKind, write.bindingName, write.target,
  ]))).size
);

const diagnosticColor = (severity: SparkJarOnlineDiagnostic['severity']) => {
  if (severity === 'ERROR') return 'red';
  if (severity === 'WARNING') return 'orange';
  return 'blue';
};

const diagnosticLabel = (severity: SparkJarOnlineDiagnostic['severity']) => {
  if (severity === 'ERROR') return '错误';
  if (severity === 'WARNING') return '警告';
  return '提示';
};

const resourceSnippet = (resource: SparkJarCodeResource, streaming: boolean, source: string): string => {
  if (resource.accessMode !== 'WRITE') return sparkJarReadSnippet(resource, source);
  const binding = JSON.stringify(resource.bindingName);
  const name = JSON.stringify(`${resource.bindingName}-output`);
  const sdk = 'cn.superhuang.datascalpel.sdk.';
  const reminder = '// TODO: 将 output 替换为待写入的 Dataset<Row>，确认目标与写入方式。\n';
  if (resource.kind === 'KAFKA_TOPIC') {
    return reminder + `context.queries().start(${name}, ${sdk}StreamingSinkType.KAFKA, spec ->\n    context.kafka().writeStream(${binding}, output)\n        .queryName(spec.queryName())\n        .option("checkpointLocation", spec.checkpointLocation())\n        .start());\n`;
  }
  const jdbc = resource.kind === 'JDBC_TABLE' || resource.kind === 'JDBC_CONNECTION';
  const dataset = streaming ? 'batch' : 'output';
  const write = jdbc
    ? `context.jdbc().write(${binding}, ${dataset})\n    .table(${sdk}JdbcTableIdentifier.table("target_table"))\n    .mode(${sdk}JdbcWriteMode.APPEND)\n    .map("target_column", "source_column")\n    .execute();`
    : `context.models().write(${binding}, ${dataset})\n    .mode(${sdk}ModelWriteMode.APPEND)\n    .mapSameName()\n    .checkSchema()\n    .execute();`;
  if (!streaming) return reminder + write + '\n';
  return reminder + `context.queries().start(${name}, ${sdk}StreamingSinkType.JDBC, spec ->\n    output.writeStream()\n        .queryName(spec.queryName())\n        .option("checkpointLocation", spec.checkpointLocation())\n        .foreachBatch((org.apache.spark.api.java.function.VoidFunction2<org.apache.spark.sql.Dataset<org.apache.spark.sql.Row>, Long>) (batch, batchId) -> {\n            ${write.replaceAll('\n', '\n            ')}\n        })\n        .start());\n`;
};

interface OnlineWorkbenchProps {
  taskId: string;
  taskName: string;
  definitionHref: string;
  initialSource: SparkJarOnlineSource;
  definition: SparkJarTaskDefinition;
  developmentConfiguration: SparkJarDevelopmentKit['configuration'];
}

const OnlineWorkbench = ({
  taskId,
  taskName,
  definitionHref,
  initialSource,
  definition,
  developmentConfiguration,
}: OnlineWorkbenchProps) => {
  const jdbcTables = developmentConfiguration.jdbcTables;
  const navigate = useNavigate();
  const editorRef = useRef<SparkJarJavaEditorHandle>(null);
  const [languageReady, setLanguageReady] = useState(false);
  const [sdkApiOpen, setSdkApiOpen] = useState(false);
  const [editingAllowed, setEditingAllowed] = useState(false);
  const [messageApi, messageContext] = message.useMessage();
  const [source, setSource] = useState(initialSource.sourceCode);
  const [entryName, setEntryName] = useState(definition.jobMode === 'STREAMING' ? 'ExampleSparkStreamingJob.java' : 'ExampleSparkJob.java');
  const [entryClassName, setEntryClassName] = useState(`com.example.datascalpel.${entryName.replace(/\.java$/, '')}`);
  const [persistedSource, setPersistedSource] = useState(initialSource.sourceCode);
  const [sourceState, setSourceState] = useState(initialSource);
  const [autoSave, setAutoSave] = useState(() => {
    try { return localStorage.getItem(autoSavePreferenceKey) !== 'false'; } catch { return true; }
  });
  const [savingDraft, setSavingDraft] = useState(false);
  const [saveError, setSaveError] = useState('');
  const saveInFlight = useRef<Promise<void> | null>(null);
  const [diagnostics, setDiagnostics] = useState<SparkJarOnlineDiagnostic[]>([]);
  const [diagnosticsExpanded, setDiagnosticsExpanded] = useState(false);
  const [checkedStatus, setCheckedStatus] = useState<'SUCCEEDED' | 'FAILED' | null>(null);
  const [resourceEditorIndex, setResourceEditorIndex] = useState<number | 'new' | null>(null);
  const [resourceSaveError, setResourceSaveError] = useState('');
  const resourceSaveInFlight = useRef(false);
  const resourceMutation = useUpdateSparkJarTaskDefinition();
  const [selectedTrialRunId, setSelectedTrialRunId] = useState<string | null>(null);
  const [activeWorkbenchTab, setActiveWorkbenchTab] = useState<'online' | 'log' | 'preview'>('online');
  const [previewAutoRefresh, setPreviewAutoRefresh] = useState(true);
  const [pageVisible, setPageVisible] = useState(() => document.visibilityState === 'visible');
  const [finalPreviewWaitExpired, setFinalPreviewWaitExpired] = useState(false);
  const [expandedResourceKeys, setExpandedResourceKeys] = useState<string[]>([]);
  const saveMutation = useSaveSparkJarOnlineSource();
  const compileMutation = useCompileSparkJarOnlineSource();
  const checkMutation = useCheckSparkJarOnlineSource();
  const sourceRef = useRef(source);
  useEffect(() => { sourceRef.current = source; }, [source]);
  const trialMutation = useTrialRunSparkJarOnlineSource();
  const cancelMutation = useCancelTaskRun();
  const stopMutation = useStopTaskRun();
  const forceTerminateMutation = useForceTerminateTaskRun();
  const streaming = definition.jobMode === 'STREAMING';
  const trialRunsQuery = useTaskRuns(taskId, {
    search: 'executionMode:"TRIAL"', page: 0, size: 1, sort: '-queuedAt',
  }, true);
  const trialRunId = selectedTrialRunId ?? trialRunsQuery.data?.content[0]?.id;
  const trialRunQuery = useTaskRun(trialRunId);
  const trialRun = trialRunQuery.data ?? trialRunsQuery.data?.content[0];
  const trialActive = trialRun?.status === 'QUEUED' || trialRun?.status === 'RUNNING'
    || trialRun?.status === 'CANCEL_REQUESTED' || trialRun?.status === 'STOP_REQUESTED';
  const trialTerminal = Boolean(trialRun && !trialActive);
  const previewVisible = activeWorkbenchTab === 'preview' && pageVisible;
  const previewQuery = useSparkJarTrialPreview(
    trialRunId,
    previewVisible,
    previewVisible && previewAutoRefresh && (trialActive || trialTerminal && !finalPreviewWaitExpired),
  );
  const modelBindings = useMemo(() => definition.resourceBindings.filter(
    (binding) => binding.resourceType === 'MODEL',
  ), [definition.resourceBindings]);
  const jdbcResources = useMemo(() => jdbcTables.flatMap((table) => {
    const binding = definition.resourceBindings.find((candidate) => (
      candidate.resourceType === 'JDBC_DATA_SOURCE' && candidate.bindingName === table.bindingName
    ));
    return binding ? [{ binding, table }] : [];
  }), [definition.resourceBindings, jdbcTables]);
  const kafkaBindings = useMemo(() => definition.resourceBindings.filter(
    (binding) => binding.resourceType === 'KAFKA_TOPIC',
  ), [definition.resourceBindings]);
  const modelQueries = useQueries({
    queries: modelBindings.map((binding) => ({
      queryKey: ['data-models', binding.resourceId],
      queryFn: () => fetchDataModel(binding.resourceId),
      staleTime: 30_000,
    })),
  });
  const jdbcQueries = useQueries({
    queries: jdbcResources.map(({ binding, table }) => ({
      queryKey: ['data-sources', binding.resourceId, 'table-metadata', table],
      queryFn: () => fetchTableMetadata(binding.resourceId, {
        catalog: table.catalog ?? null,
        schema: table.schema ?? null,
        table: table.table,
      }),
      staleTime: 30_000,
    })),
  });

  const resources = useMemo<SparkJarCodeResource[]>(() => [
    ...modelBindings.map((binding, index): SparkJarCodeResource => {
      const detail = modelQueries[index]?.data;
      return {
        bindingName: binding.bindingName,
        label: detail ? (detail.model.name === detail.model.code ? detail.model.name : `${detail.model.name} · ${detail.model.code}`) : (binding.resourceName ?? binding.bindingName),
        kind: 'MODEL',
        accessMode: binding.accessMode,
        fieldsLoading: modelQueries[index]?.isPending ?? false,
        fields: detail?.fields.map((field) => ({
          name: field.code,
          type: field.fieldType,
          nullable: field.nullable,
        })) ?? [],
      };
    }),
    ...jdbcResources.map(({ binding, table }, index): SparkJarCodeResource => ({
      bindingName: binding.bindingName,
      label: `${binding.resourceName ?? binding.bindingName} · ${table.table}`,
      kind: 'JDBC_TABLE',
      accessMode: binding.accessMode,
      table: table.table,
      catalog: table.catalog,
      schema: table.schema,
      fieldsLoading: jdbcQueries[index]?.isPending ?? false,
      fields: jdbcQueries[index]?.data?.columns.map((field) => ({
        name: field.name,
        type: field.platformTypeDefinition?.type ?? field.nativeType,
        nullable: field.nullable,
      })) ?? [],
    })),
    ...definition.resourceBindings.filter((binding) => binding.resourceType === 'JDBC_DATA_SOURCE'
      && !jdbcResources.some((resource) => resource.binding.bindingName === binding.bindingName))
      .map((binding): SparkJarCodeResource => ({ bindingName: binding.bindingName,
        label: binding.resourceName ?? binding.bindingName, kind: 'JDBC_CONNECTION', accessMode: binding.accessMode, fields: [] })),
    ...kafkaBindings.map((binding): SparkJarCodeResource => ({
      bindingName: binding.bindingName,
      label: `${binding.resourceName ?? binding.bindingName} · ${binding.topicName ?? 'Topic 未配置'}`,
      kind: 'KAFKA_TOPIC',
      accessMode: binding.accessMode,
      fields: [],
    })),
  ], [definition.resourceBindings, jdbcQueries, jdbcResources, kafkaBindings, modelBindings, modelQueries]);

  // The API normalizes line endings; CRLF must not trigger endless identical saves.
  const localDirty = source.replace(/\r\n?/g, '\n') !== persistedSource.replace(/\r\n?/g, '\n');
  const hasUncompiledChanges = localDirty || sourceState.hasUncompiledChanges;
  const sourceBytes = new Blob([source]).size;
  const busy = savingDraft || saveMutation.isPending || compileMutation.isPending || trialMutation.isPending || checkMutation.isPending || resourceMutation.isPending;
  const editingJdbcTable = typeof resourceEditorIndex === 'number' ? jdbcTables.find((item) => (
    item.bindingName === definition.resourceBindings[resourceEditorIndex]?.bindingName)) : undefined;
  const blocker = useBlocker(({ currentLocation, nextLocation }) => (
    (localDirty || savingDraft) && currentLocation.pathname !== nextLocation.pathname
  ));

  useEffect(() => {
    const handleVisibility = () => setPageVisible(document.visibilityState === 'visible');
    document.addEventListener('visibilitychange', handleVisibility);
    return () => document.removeEventListener('visibilitychange', handleVisibility);
  }, []);

  useEffect(() => {
    if (!trialTerminal) return undefined;
    const timer = window.setTimeout(() => setFinalPreviewWaitExpired(true), 60_000);
    return () => window.clearTimeout(timer);
  }, [trialRunId, trialTerminal]);

  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {
      if (!localDirty && !savingDraft) return;
      event.preventDefault();
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [localDirty, savingDraft]);

  useEffect(() => {
    if (blocker.state !== 'blocked') return;
    Modal.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: '在线源码尚未保存',
      content: '离开后，本次修改将丢失。',
      okText: '放弃修改并离开',
      okButtonProps: { danger: true },
      cancelText: '继续编辑',
      onOk: blocker.proceed,
      onCancel: blocker.reset,
    });
  }, [blocker]);

  const saveDraft = saveMutation.mutateAsync;
  const save = useCallback((automatic = false): Promise<void> => {
    if (saveInFlight.current) return saveInFlight.current;
    const submittedSource = sourceRef.current;
    if (new Blob([submittedSource]).size > onlineSourceLimit) {
      setSaveError('源码不能超过 256 KiB');
      return Promise.resolve();
    }
    setSavingDraft(true);
    setSaveError('');
    const pending = (async () => {
      try {
        const result = await saveDraft({ id: taskId, sourceCode: submittedSource });
        // A save acknowledges its snapshot, never replace newer text in Monaco.
        setPersistedSource(result.sourceCode);
        setSourceState(result);
        if (!automatic) void messageApi.success('草稿已保存');
      } catch (error) {
        setSaveError(error instanceof ApiError ? error.message : '草稿保存失败，请重试');
      } finally {
        saveInFlight.current = null;
        setSavingDraft(false);
      }
    })();
    saveInFlight.current = pending;
    return pending;
  }, [saveDraft, taskId, messageApi]);

  useEffect(() => {
    if (!autoSave || !localDirty || busy || saveError || blocker.state === 'blocked') return;
    const timer = window.setTimeout(() => { void save(true); }, 1500);
    return () => window.clearTimeout(timer);
  }, [autoSave, source, localDirty, busy, saveError, blocker.state, save]);

  const toggleAutoSave = (enabled: boolean) => {
    setAutoSave(enabled);
    if (enabled) setSaveError('');
    try { localStorage.setItem(autoSavePreferenceKey, String(enabled)); } catch { /* Preference is optional. */ }
  };

  const check = async () => {
    if (sourceBytes > onlineSourceLimit) { void messageApi.error('源码不能超过 256 KiB'); return; }
    const checkedSource = source;
    try {
      const result = await checkMutation.mutateAsync({ id: taskId, sourceCode: checkedSource });
      if (sourceRef.current !== checkedSource) {
        void messageApi.info('检查期间代码已修改，请重新检查');
        return;
      }
      setDiagnostics(result.diagnostics);
      setDiagnosticsExpanded(result.diagnostics.length > 0);
      setCheckedStatus(result.status);
      void messageApi[result.status === 'SUCCEEDED' ? 'success' : 'error'](
        result.status === 'SUCCEEDED' ? '检查通过，未替换当前 JAR' : '检查未通过，请查看诊断');
    } catch (error) { void messageApi.error(error instanceof ApiError ? error.message : '代码检查暂时不可用'); }
  };

  const compile = async () => {
    if (sourceBytes > onlineSourceLimit) {
      void messageApi.error('源码不能超过 256 KiB');
      return;
    }
    const submittedSource = source;
    try {
      await saveInFlight.current;
      const result = await compileMutation.mutateAsync({ id: taskId, sourceCode: submittedSource });
      setPersistedSource(result.source.sourceCode);
      setSourceState(result.source);
      setSaveError('');
      if (sourceRef.current === submittedSource) {
        setDiagnostics(result.diagnostics);
        setDiagnosticsExpanded(result.diagnostics.length > 0);
        setCheckedStatus(result.status);
      }
      if (result.status === 'SUCCEEDED') {
        void messageApi.success(`编译成功，已应用为当前 JAR（${result.durationMs} ms）`);
      } else {
        setActiveWorkbenchTab('online');
        void messageApi.error('编译未通过，当前可运行 JAR 未改变');
      }
    } catch (error) {
      void messageApi.error(error instanceof ApiError ? error.message : '在线编译暂时不可用');
    }
  };

  const trialRunSource = async () => {
    if (sourceBytes > onlineSourceLimit) {
      void messageApi.error('源码不能超过 256 KiB');
      return;
    }
    const submittedSource = source;
    try {
      await saveInFlight.current;
      const result = await trialMutation.mutateAsync({ id: taskId, sourceCode: submittedSource });
      setPersistedSource(result.source.sourceCode);
      setSourceState(result.source);
      setSaveError('');
      if (sourceRef.current === submittedSource) {
        setDiagnostics(result.diagnostics);
        setDiagnosticsExpanded(result.status === 'COMPILE_FAILED');
        setCheckedStatus(result.status === 'COMPILE_FAILED' ? 'FAILED' : 'SUCCEEDED');
      }
      if (result.status === 'COMPILE_FAILED') {
        setActiveWorkbenchTab('online');
        void messageApi.error('编译未通过，未创建试运行');
        return;
      }
      setActiveWorkbenchTab('log');
      setFinalPreviewWaitExpired(false);
      setSelectedTrialRunId(result.run?.id ?? null);
      void messageApi.success(streaming
        ? '实时试运行已提交，可在 30 分钟内正常停止'
        : '试运行已提交，SDK 写入不会落库');
    } catch (error) {
      void messageApi.error(error instanceof ApiError ? error.message : '试运行提交失败');
    }
  };

  const stopTrialRun = async () => {
    if (!trialRun) return;
    try {
      await stopMutation.mutateAsync(trialRun.id);
      void messageApi.success('已请求正常停止，平台将等待实时查询退出并生成预览');
    } catch (error) {
      void messageApi.error(error instanceof ApiError ? error.message : '停止试运行失败');
    }
  };

  const forceTerminateTrialRun = async () => {
    if (!trialRun) return;
    try {
      await forceTerminateMutation.mutateAsync(trialRun.id);
      void messageApi.warning('已请求强制终止，最新输出预览可能无法生成');
    } catch (error) {
      void messageApi.error(error instanceof ApiError ? error.message : '强制终止失败');
    }
  };

  const goBack = () => navigate(definitionHref);
  const editResource = (index: number | 'new') => {
    setResourceSaveError('');
    setResourceEditorIndex(index);
  };
  const saveResource = (selection: SparkJarResourceSelection) => {
    if (resourceEditorIndex === null || resourceSaveInFlight.current) return;
    const bindings = definition.resourceBindings.map(({ bindingName, resourceType, resourceId, topicName, accessMode }) => (
      { bindingName, resourceType, resourceId, topicName, accessMode }));
    const index = resourceEditorIndex === 'new' ? bindings.length : resourceEditorIndex;
    const next = replaceSparkJarResource(bindings, developmentConfiguration, index, selection);
    const submit = async () => {
      if (resourceSaveInFlight.current) return;
      resourceSaveInFlight.current = true;
      setResourceSaveError('');
      try {
        await resourceMutation.mutateAsync({ id: taskId, request: {
          // Editing bindings must not switch modes or discard runtime settings / the effective JAR.
          parameters: definition.parameters, sparkConf: definition.sparkConf,
          inheritEngineResources: definition.inheritEngineResources,
          executionResources: definition.inheritEngineResources ? undefined : definition.executionResources,
          timeoutSeconds: definition.timeoutSeconds,
          resourceBindings: next.bindings, developmentConfiguration: next.configuration,
        } });
        setResourceEditorIndex(null);
        void messageApi.success('资源已保存，源码未修改');
      } catch (error) {
        setResourceSaveError(error instanceof ApiError ? error.message : '资源保存失败，请重试');
      } finally { resourceSaveInFlight.current = false; }
    };
    if (next.discardedTableCount > 1) {
      Modal.confirm({ rootClassName: 'business-overlay business-modal-overlay', title: '更新资源并清除原表选择？',
        content: `原绑定包含 ${next.discardedTableCount} 张 JDBC 表，本地开发表选择将被替换；不会删除数据库中的表。`,
        okText: '确认更新', cancelText: '继续编辑', onOk: submit });
    } else void submit();
  };
  const jarOrigin = sourceState.currentJarOrigin === 'ONLINE_COMPILED' ? '在线编译' : '本地上传';
  const toggleResourceFields = (resourceKey: string) => {
    setExpandedResourceKeys((current) => (
      current.includes(resourceKey)
        ? current.filter((key) => key !== resourceKey)
        : [...current, resourceKey]
    ));
  };
  const renderTrialRunStatus = () => {
    if (!trialRun) return null;
    const executionError = trialRun.executionError;
    return (
      <Space className="spark-jar-trial-status-actions" size={8}>
        <Tooltip
          title={(
            <div className="spark-jar-trial-status-tooltip">
              <div><span>运行 ID</span><strong>{trialRun.id}</strong></div>
              <div><span>计算引擎</span><strong>{trialRun.computeEngineId ?? '—'}</strong></div>
              {trialRun.deadlineAt && (
                <div><span>最晚停止时间</span><strong>{new Date(trialRun.deadlineAt).toLocaleString()}</strong></div>
              )}
              {trialRun.message && <div><span>执行消息</span><strong>{trialRun.message}</strong></div>}
              {executionError && (
                <>
                  <div><span>错误码</span><strong>{executionError.code}</strong></div>
                  <div><span>类别</span><strong>{executionErrorCategoryLabels[executionError.category]}</strong></div>
                  <div><span>阶段</span><strong>{executionFailurePhaseLabels[executionError.phase]}</strong></div>
                  <div><span>错误信息</span><strong>{executionError.message}</strong></div>
                </>
              )}
            </div>
          )}
        >
          <Tag color={trialActive ? 'processing' : taskRunStatusColors[trialRun.status]}>
            {taskRunStatusLabels[trialRun.status]}
          </Tag>
        </Tooltip>
        {trialActive && trialRun.status !== 'STOP_REQUESTED' && (
          <Button
            danger
            size="small"
            icon={<StopOutlined />}
            loading={streaming ? stopMutation.isPending : cancelMutation.isPending}
            onClick={() => void (streaming
              ? stopTrialRun()
              : cancelMutation.mutateAsync(trialRun.id))}
          >{streaming ? '停止试运行' : '取消'}</Button>
        )}
        {streaming && trialRun.status === 'STOP_REQUESTED' && (
          <Button
            danger
            size="small"
            icon={<StopOutlined />}
            loading={forceTerminateMutation.isPending}
            onClick={() => void forceTerminateTrialRun()}
          >强制终止</Button>
        )}
      </Space>
    );
  };

  const renderTrialRunError = () => {
    if (!trialRun) return null;
    const executionError = trialRun.executionError;
    if (executionError) {
      return (
        <Alert
          showIcon
          type="error"
          message={executionError.message}
          description={`错误码：${executionError.code} · 类别：${executionErrorCategoryLabels[executionError.category]} · 阶段：${executionFailurePhaseLabels[executionError.phase]}`}
        />
      );
    }
    return !trialActive && trialRun.status !== 'SUCCESS' && trialRun.status !== 'STOPPED' && trialRun.message
      ? <Alert showIcon type="error" message={trialRun.message} />
      : null;
  };

  return (
    <div className="spark-jar-online-page">
      {messageContext}
      {sdkApiOpen && <SdkApiDrawer mode={streaming ? 'STREAMING' : 'BATCH'} onClose={() => setSdkApiOpen(false)} />}
      <header className="spark-jar-online-header">
        <div className="spark-jar-online-identity">
          <Tooltip title="返回任务定义"><Button type="text" className="spark-jar-online-back" aria-label="返回任务定义" icon={<ArrowLeftOutlined />} onClick={goBack} /></Tooltip>
          <span className="spark-jar-online-icon"><CodeOutlined /></span>
          <div>
            <Space size={8} wrap>
              <Typography.Text strong className="spark-jar-online-title">{taskName}</Typography.Text>
              <Tag color={streaming ? 'orange' : 'geekblue'}>{streaming ? '实时在线 Java 开发' : '在线 Java 开发'}</Tag>
              <Typography.Text type="secondary" className="spark-jar-online-runtime">Java 21 · Spark 4.1.1 · Job API v1</Typography.Text>
            </Space>
            <div className="spark-jar-online-entry-status">
              <Typography.Text type="secondary" ellipsis={{ tooltip: entryClassName }}>{entryClassName}</Typography.Text>
              <Tag color={saveError ? 'error' : savingDraft ? 'processing' : localDirty ? 'orange' : 'default'}>
                {saveError ? '草稿保存失败' : savingDraft ? '正在保存草稿…' : localDirty ? (autoSave ? '等待自动保存' : '草稿未保存') : sourceState.persisted ? '草稿已保存' : '草稿尚未保存'}
              </Tag>
              {saveError && <Tooltip title={saveError}><Button type="link" size="small" danger disabled={busy} onClick={() => void save()}>重试保存</Button></Tooltip>}
              <Tag color={hasUncompiledChanges ? 'gold' : 'success'}>{hasUncompiledChanges ? '存在未编译修改' : '当前源码已应用'}</Tag>
              {sourceState.currentJar && <Tag color="blue">当前 JAR：{jarOrigin}</Tag>}
            </div>
          </div>
        </div>
        <Space className="spark-jar-online-main-actions" wrap>
          <Tooltip title="停止输入 1.5 秒后保存草稿，不编译、不运行、不替换当前 JAR；开关记住在当前浏览器。">
            <Space size={8} className="spark-jar-online-autosave"><Typography.Text>自动保存</Typography.Text><Switch size="small" aria-label="自动保存草稿" checked={autoSave} onChange={toggleAutoSave} /></Space>
          </Tooltip>
          {!autoSave && <Button icon={<SaveOutlined />} loading={savingDraft} disabled={busy} onClick={() => void save()}>
            保存草稿
          </Button>}
          <Button icon={<CheckCircleOutlined />} loading={checkMutation.isPending} disabled={busy} onClick={() => void check()}>
            检查代码
          </Button>
          <Button icon={<PlayCircleOutlined />} loading={trialMutation.isPending} disabled={busy || Boolean(trialActive)} onClick={() => void trialRunSource()}>
            试运行
          </Button>
          <Popover
            trigger={['hover', 'click']}
            placement="bottomRight"
            arrow={false}
            rootClassName="business-overlay spark-jar-trial-help-popover"
            content={(
              <div>
                <Typography.Text strong>试运行说明</Typography.Text>
                <ul>
                  <li>读取任务绑定的真实模型、JDBC{streaming ? ' 和 Kafka' : ''} 数据。</li>
                  <li>平台 SDK 的模型、JDBC{streaming ? ' 和 Kafka' : ''} 写入只生成预览，不会写入外部系统。</li>
                  <li>每次写入最多展示前 100 行输出。</li>
                  {streaming && <li>最长运行 30 分钟，可正常停止；强制终止可能缺少最新预览。</li>}
                  <li>原生 Spark Writer、自带凭据或其他客户端产生的外部副作用无法拦截。</li>
                </ul>
              </div>
            )}
          >
            <Button
              type="text"
              className="spark-jar-trial-help-button"
              icon={<ExclamationCircleOutlined />}
              aria-label="查看试运行说明"
            />
          </Popover>
          <Button type="primary" icon={<CloudUploadOutlined />} loading={compileMutation.isPending} disabled={busy} onClick={() => Modal.confirm({
            title: '应用当前代码到任务？',
            content: '编译成功后替换当前生效 JAR，不会立即运行任务。编译失败保留原 JAR。',
            okText: '编译并应用', cancelText: '取消', onOk: compile,
          })}>
            应用到任务
          </Button>
        </Space>
      </header>

      <main className="spark-jar-online-workbench" aria-busy={busy}>
        <Tabs
          className="spark-jar-online-primary-tabs"
          animated={false}
          activeKey={activeWorkbenchTab}
          onChange={(key) => setActiveWorkbenchTab(key as 'online' | 'log' | 'preview')}
          tabBarExtraContent={<Space>{renderTrialRunStatus()}<Button type="text" icon={<BookOutlined />} onClick={() => setSdkApiOpen(true)}>SDK API</Button></Space>}
          items={[
            {
              key: 'online',
              label: '在线开发',
              children: (
                <div className="spark-jar-online-workspace">
                  <aside className="spark-jar-online-resources">
                    <div className="spark-jar-online-panel-heading">
                      <div><Typography.Text strong>任务资源 <Typography.Text type="secondary">{definition.resourceBindings.length}</Typography.Text></Typography.Text><Typography.Text type="secondary">选择操作，插入代码</Typography.Text></div>
                      <Tooltip title="添加任务资源"><Button size="small" icon={<PlusOutlined />} aria-label="添加任务资源"
                        disabled={busy || definition.resourceBindings.length >= 200} onClick={() => editResource('new')} /></Tooltip>
                    </div>
                    <div className="spark-jar-online-resource-list">
                      {resources.length ? resources.map((resource) => {
                        const resourceKey = `${resource.kind}:${resource.bindingName}:${resource.table ?? ''}`;
                        const fieldsExpanded = expandedResourceKeys.includes(resourceKey);
                        return (
                          <section key={resourceKey} className="spark-jar-online-resource-card">
                            <div className="spark-jar-online-resource-card-header">
                              <div className="spark-jar-online-resource-main">
                                <span className="spark-jar-online-resource-icon">
                                  {resource.kind === 'MODEL'
                                    ? <DatabaseOutlined />
                                    : resource.kind === 'KAFKA_TOPIC' ? <CloudServerOutlined /> : <TableOutlined />}
                                </span>
                                <span>
                                  <strong title={resource.bindingName}>{resource.bindingName}</strong>
                                  <small title={resource.label}>{resource.label}</small>
                                </span>
                                <Tag color="geekblue">{resource.accessMode === 'READ' ? '输入' : resource.accessMode === 'WRITE' ? '输出' : '输入及输出'}</Tag>
                              </div>
                            </div>
                            <SparkJarResourceActions resource={resource} busy={busy} editingAllowed={editingAllowed} fieldsExpanded={fieldsExpanded}
                              onEdit={() => editResource(definition.resourceBindings.findIndex((item) => item.bindingName === resource.bindingName))}
                              onRead={(print) => editorRef.current?.insertText(sparkJarReadSnippet(resource, source, print))}
                              onWrite={() => editorRef.current?.insertText(resourceSnippet({ ...resource, accessMode: 'WRITE' }, streaming, source))}
                              onToggleFields={() => toggleResourceFields(resourceKey)} />
                            {resource.kind === 'JDBC_CONNECTION' && resource.accessMode !== 'WRITE' && <div className="spark-jar-online-resource-actions"><Typography.Text type="secondary">未选择表，可在代码中调用 readQuery/readTable。</Typography.Text></div>}
                            {fieldsExpanded && (
                              <div className="spark-jar-online-fields">
                                {resource.fields.length ? resource.fields.map((field) => (
                                  <button
                                    key={field.name}
                                    type="button"
                                    disabled={!editingAllowed}
                                    onClick={() => editorRef.current?.insertText(`col(${JSON.stringify(field.name)})`)}
                                    title={`插入 col(${JSON.stringify(field.name)})`}
                                  >
                                    <FieldStringOutlined />
                                    <span>{field.name}</span>
                                    <small>{field.type}{field.nullable ? '?' : ''}</small>
                                  </button>
                                )) : resource.fieldsLoading ? <Spin size="small" /> : (
                                  <Typography.Text type="secondary">暂无字段信息</Typography.Text>
                                )}
                              </div>
                            )}
                          </section>
                        );
                      }) : (
                        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="任务尚未绑定可用的模型、JDBC 本地表或 Kafka Topic" />
                      )}
                    </div>
                  </aside>

                  <section className="spark-jar-online-editor-panel">
                    <div className="spark-jar-online-filebar">
                      <Space className="spark-jar-online-file-actions"><CodeOutlined /><Typography.Text className="spark-jar-online-java-file" title={entryName}>{entryName}</Typography.Text>
                        <Tooltip title={languageReady ? '同步重命名文件、主类和引用；包名仍在源码中编辑' : 'Java 开发环境准备完成后可重命名'}>
                          <Button type="link" size="small" disabled={!languageReady} onClick={() => editorRef.current?.renameEntry()}>重命名</Button>
                        </Tooltip>
                        {localDirty && <Tag color="orange">未保存</Tag>}</Space>
                      <Space>
                        <Typography.Text className="spark-jar-source-size" type={sourceBytes > onlineSourceLimit ? 'danger' : 'secondary'}>
                          {(sourceBytes / 1024).toFixed(1)} / 256 KiB
                        </Typography.Text>
                        {compileMutation.isPending && <Typography.Text type="secondary"><Spin size="small" /> 正在编译，不会执行用户代码</Typography.Text>}
                      </Space>
                    </div>
                    <SparkJarJavaEditor
                      key={`${taskId}-${definition.jobMode}`}
                      ref={editorRef}
                      taskId={taskId}
                      value={source}
                      diagnostics={diagnostics}
                      resources={resources}
                      jobMode={definition.jobMode}
                      onReadyChange={setLanguageReady}
                      onEditingAllowedChange={setEditingAllowed}
                      onReturn={goBack}
                      onEntryChange={(entry) => { setEntryName(entry.fileName); setEntryClassName(entry.className); }}
                      onChange={(next) => { sourceRef.current = next; setSource(next); setDiagnostics([]); setCheckedStatus(null); }}
                    />
                    <div className={`spark-jar-online-diagnostics${diagnosticsExpanded ? ' spark-jar-online-diagnostics-expanded' : ''}`}>
                      <button type="button" className="spark-jar-online-diagnostics-heading" aria-expanded={diagnosticsExpanded} onClick={() => setDiagnosticsExpanded((current) => !current)}>
                        <span>
                          {diagnostics.some((item) => item.severity === 'ERROR') ? <ExclamationCircleOutlined /> : <CheckCircleOutlined />}
                          编译诊断
                        </span>
                        <span>{diagnostics.length ? `${diagnostics.length} 项` : checkedStatus === 'SUCCEEDED' ? '检查通过' : checkedStatus === 'FAILED' ? '检查未通过' : '当前代码尚未检查'} <DownOutlined className="spark-jar-online-diagnostics-chevron" /></span>
                      </button>
                      {diagnosticsExpanded && (
                        <div className="spark-jar-online-diagnostics-list">
                          {diagnostics.length ? (
                            <List
                              size="small"
                              dataSource={diagnostics}
                              renderItem={(item) => (
                                <List.Item onClick={() => editorRef.current?.revealDiagnostic(item)}>
                                  <Space align="start">
                                    <Tag color={diagnosticColor(item.severity)}>{diagnosticLabel(item.severity)}</Tag>
                                    <div>
                                      <Typography.Text>{item.message}</Typography.Text>
                                      <div><Typography.Text type="secondary">第 {item.line} 行，第 {item.column} 列 · {item.code}</Typography.Text></div>
                                    </div>
                                  </Space>
                                </List.Item>
                              )}
                            />
                          ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={checkedStatus === 'SUCCEEDED' ? '未发现编译问题' : '点击“检查代码”查看结果'} />}
                        </div>
                      )}
                    </div>
                  </section>
                </div>
              ),
            },
            {
              key: 'log',
              label: '控制台日志',
              children: (
                <section className="spark-jar-trial-view">
                  {trialRun ? (
                    <>
                      {renderTrialRunError()}
                      <TaskRunLogViewer
                        key={trialRun.id}
                        runId={trialRun.id}
                        visible={activeWorkbenchTab === 'log'}
                        endedAt={trialRun.endedAt}
                      />
                    </>
                  ) : <Empty className="spark-jar-trial-empty" image={Empty.PRESENTED_IMAGE_SIMPLE} description="点击“试运行”后查看真实数据执行日志" />}
                </section>
              ),
            },
            {
              key: 'preview',
              label: `输出预览（${previewQuery.data?.preview
                ? streaming
                  ? countTrialPreviewOutputs(previewQuery.data.preview)
                  : previewQuery.data.preview.writes.length
                : 0}）`,
              children: (
                <section className="spark-jar-trial-view">
                  {trialRun ? (
                    <>
                      {renderTrialRunError()}
                      <div className="spark-jar-trial-preview-content">
                        <div className="spark-jar-trial-preview-toolbar">
                          <Space wrap>
                            <Tag color={previewQuery.data?.finalResult
                              ? 'success'
                              : previewQuery.data?.source === 'RUNNING_SNAPSHOT' ? 'processing' : 'default'}>
                              {previewQuery.data?.finalResult
                                ? '最终预览'
                                : previewQuery.data?.source === 'RUNNING_SNAPSHOT' ? '运行中' : '等待输出'}
                            </Tag>
                            {streaming && <Typography.Text type="secondary">每个输出保留最近 100 条已捕获样例</Typography.Text>}
                            {previewQuery.data?.capturedAt && (
                              <Typography.Text type="secondary">
                                最后样例更新于 {new Date(previewQuery.data.capturedAt).toLocaleString()}
                              </Typography.Text>
                            )}
                          </Space>
                          <Space wrap>
                            <Space size={6}>
                              <Switch
                                size="small"
                                checked={previewAutoRefresh && !finalPreviewWaitExpired}
                                disabled={previewQuery.data?.finalResult || finalPreviewWaitExpired}
                                onChange={setPreviewAutoRefresh}
                              />
                              <Typography.Text type="secondary">
                                {previewQuery.data?.finalResult
                                  ? '已停止刷新'
                                  : finalPreviewWaitExpired
                                    ? '已停止自动等待，可手动刷新'
                                    : previewAutoRefresh ? '每 3 秒刷新' : '已暂停'}
                              </Typography.Text>
                            </Space>
                            <Tooltip title="立即刷新">
                              <Button
                                icon={<ReloadOutlined />}
                                loading={previewQuery.isFetching}
                                onClick={() => void previewQuery.refetch()}
                              />
                            </Tooltip>
                          </Space>
                        </div>
                        {previewQuery.error && (
                          <Alert
                            showIcon
                            type={streaming && trialRun.status === 'CANCELLED' ? 'warning' : 'error'}
                            message={previewQuery.error instanceof ApiError
                              ? streaming && trialRun.status === 'CANCELLED'
                                ? '任务已被强制终止，Runner 可能来不及生成最新输出预览'
                                : previewQuery.error.message
                              : '输出预览读取失败'}
                          />
                        )}
                        {previewQuery.data?.preview ? (
                          previewQuery.data.preview.writes.length ? (
                            <SparkJarTrialPreviewPanel
                              key={trialRunId ?? 'unknown-trial-run'}
                              preview={previewQuery.data.preview}
                              streaming={streaming}
                            />
                          ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={previewQuery.data.finalResult
                            ? '运行已结束，但没有捕获到 SDK 输出写入'
                            : '等待首批输出'} />
                        ) : <Empty
                          image={Empty.PRESENTED_IMAGE_SIMPLE}
                          description={trialActive
                            ? '等待首批输出'
                            : previewQuery.data?.finalResult
                              ? '运行已结束，但没有捕获到 SDK 输出写入'
                              : trialRun.status !== 'SUCCESS' && trialRun.status !== 'STOPPED'
                              ? '运行在捕获 SDK 写入前失败，没有输出预览'
                              : '任务已结束，正在等待最终预览'}
                        />}
                      </div>
                    </>
                  ) : <Empty className="spark-jar-trial-empty" image={Empty.PRESENTED_IMAGE_SIMPLE} description="点击“试运行”后查看 SDK 输出预览" />}
                </section>
              ),
            },
          ]}
        />
      </main>
      {resourceEditorIndex !== null && <SparkJarResourceDrawer
        initial={typeof resourceEditorIndex === 'number' ? definition.resourceBindings[resourceEditorIndex] : undefined}
        initialTable={editingJdbcTable ? { catalog: editingJdbcTable.catalog ?? null,
          schema: editingJdbcTable.schema ?? null, table: editingJdbcTable.table } : undefined}
        bindingNames={definition.resourceBindings.map((item) => item.bindingName)} streaming={streaming}
        saving={resourceMutation.isPending} error={resourceSaveError}
        onClose={() => setResourceEditorIndex(null)} onConfirm={saveResource} />}
    </div>
  );
};

export const SparkJarOnlineEditorPage = () => {
  const location = useLocation();
  const { taskId } = useParams<{ taskId: string }>();
  const navigate = useNavigate();
  const taskQuery = useTask(taskId);
  const definitionHref = taskQuery.data && !taskQuery.isError
    ? taskPageHref(`/task/${taskQuery.data.id}/definition`, location.search, taskQuery.data.type)
    : '/task';
  const sourceQuery = useSparkJarOnlineSource(taskId);
  const definitionQuery = useSparkJarTaskDefinition(taskId);
  const kitQuery = useSparkJarDevelopmentKit(taskId, Boolean(taskId));

  if (!taskId) {
    return <Result status="404" title="在线开发地址无效" extra={<Button onClick={() => navigate('/task')}>返回任务列表</Button>} />;
  }
  if (taskQuery.isPending || sourceQuery.isPending || definitionQuery.isPending || kitQuery.isPending) {
    return <div className="spark-jar-online-loading"><Skeleton active paragraph={{ rows: 14 }} /></div>;
  }
  const error = taskQuery.error ?? sourceQuery.error ?? definitionQuery.error ?? kitQuery.error;
  if (error || !taskQuery.data || !sourceQuery.data || !definitionQuery.data || !kitQuery.data) {
    return (
      <Result
        status="error"
        title="在线开发工作台加载失败"
        subTitle={error instanceof ApiError ? error.message : '请确认任务和 Task Engine 配置是否有效。'}
        extra={<Button type="primary" onClick={() => navigate(definitionHref)}>{definitionHref === '/task' ? '返回全部任务' : '返回任务定义'}</Button>}
      />
    );
  }
  if (taskQuery.data.type !== 'SPARK_JAR' && taskQuery.data.type !== 'SPARK_STREAMING_JAR') {
    return <Result status="warning" title="当前任务不支持 Spark JAR 在线开发" extra={<Button onClick={() => navigate(definitionHref)}>返回任务定义</Button>} />;
  }
  if (taskQuery.data.status === 'PUBLISHED') {
    return <Result status="warning" title="当前任务状态不可在线编辑" subTitle="请先停用任务，再修改和编译在线源码。" extra={<Button onClick={() => navigate(taskPageHref(`/task/${taskId}`, location.search, taskQuery.data.type))}>返回任务详情</Button>} />;
  }

  return (
    <OnlineWorkbench
      key={taskId}
      taskId={taskId}
      taskName={taskQuery.data.name}
      definitionHref={definitionHref}
      initialSource={sourceQuery.data}
      definition={definitionQuery.data}
      developmentConfiguration={kitQuery.data.configuration}
    />
  );
};
