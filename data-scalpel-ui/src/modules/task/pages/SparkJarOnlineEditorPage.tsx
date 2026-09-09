import { taskPageHref } from '../model/taskViews';
import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import {
  ArrowLeftOutlined,
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
  ReloadOutlined,
  StopOutlined,
  TableOutlined,
} from '@ant-design/icons';
import { useQueries } from '@tanstack/react-query';
import { Button, Empty, List, Modal, Popover, Result, Skeleton, Space, Spin, Switch, Tag, Tabs, Tooltip, Typography, message } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
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
  SparkJarDevelopmentKitJdbcTable,
  SparkJarOnlineDiagnostic,
  SparkJarOnlineSource,
  SparkJarTaskDefinition,
  SparkJarTrialPreview,
} from '../model/task';

const onlineSourceLimit = 256 * 1024;

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

const resourceSnippet = (resource: SparkJarCodeResource, streaming: boolean): string => {
  if (resource.kind === 'KAFKA_TOPIC') {
    const read = `Dataset<Row> kafkaRows = context.kafka().readStream(\n    "${resource.bindingName}", KafkaStartingOffsets.EARLIEST);`;
    const write = `context.queries().start("${resource.bindingName}-output", StreamingSinkType.KAFKA, spec ->\n    context.kafka().writeStream("${resource.bindingName}", output)\n        .queryName(spec.queryName())\n        .option("checkpointLocation", spec.checkpointLocation())\n        .start());`;
    if (resource.accessMode === 'READ') return read;
    if (resource.accessMode === 'WRITE') return write;
    return `${read}\n\n${write}`;
  }
  if (resource.kind === 'JDBC_TABLE') {
    const read = `context.jdbc().readTable("${resource.bindingName}", "${resource.table ?? ''}")`;
    if (resource.accessMode === 'READ') return read;
    const writeCall = `context.jdbc().write("${resource.bindingName}", batch)\n    .table(JdbcTableIdentifier.table("target_table"))\n    .mode(JdbcWriteMode.APPEND)\n    .map("target_column", "source_column")\n    .execute();`;
    const write = streaming
      ? `context.queries().start("${resource.bindingName}-output", StreamingSinkType.JDBC, spec ->\n    output.writeStream()\n        .queryName(spec.queryName())\n        .option("checkpointLocation", spec.checkpointLocation())\n        .foreachBatch((org.apache.spark.api.java.function.VoidFunction2<Dataset<Row>, Long>) (batch, batchId) -> {\n            ${writeCall.replaceAll('\n', '\n            ')}\n        })\n        .start());`
      : writeCall.replaceAll('batch', 'dataset');
    if (resource.accessMode === 'WRITE') return write;
    return `${read}\n\n${write}`;
  }
  const read = `context.models().read("${resource.bindingName}")`;
  if (resource.accessMode === 'READ') return read;
  const writeCall = `context.models()\n    .write("${resource.bindingName}", batch)\n    .mode(ModelWriteMode.APPEND)\n    .mapSameName()\n    .checkSchema()\n    .execute();`;
  const write = streaming
    ? `context.queries().start("${resource.bindingName}-output", StreamingSinkType.JDBC, spec ->\n    output.writeStream()\n        .queryName(spec.queryName())\n        .option("checkpointLocation", spec.checkpointLocation())\n        .foreachBatch((org.apache.spark.api.java.function.VoidFunction2<Dataset<Row>, Long>) (batch, batchId) -> {\n            ${writeCall.replaceAll('\n', '\n            ')}\n        })\n        .start());`
    : writeCall.replaceAll('batch', 'dataset');
  if (resource.accessMode === 'WRITE') return write;
  return `${read}\n\n${write}`;
};

interface OnlineWorkbenchProps {
  taskId: string;
  taskName: string;
  definitionHref: string;
  initialSource: SparkJarOnlineSource;
  definition: SparkJarTaskDefinition;
  jdbcTables: SparkJarDevelopmentKitJdbcTable[];
}

const OnlineWorkbench = ({
  taskId,
  taskName,
  definitionHref,
  initialSource,
  definition,
  jdbcTables,
}: OnlineWorkbenchProps) => {
  const navigate = useNavigate();
  const editorRef = useRef<SparkJarJavaEditorHandle>(null);
  const [messageApi, messageContext] = message.useMessage();
  const [source, setSource] = useState(initialSource.sourceCode);
  const [persistedSource, setPersistedSource] = useState(initialSource.sourceCode);
  const [sourceState, setSourceState] = useState(initialSource);
  const [diagnostics, setDiagnostics] = useState<SparkJarOnlineDiagnostic[]>([]);
  const [diagnosticsExpanded, setDiagnosticsExpanded] = useState(false);
  const [selectedTrialRunId, setSelectedTrialRunId] = useState<string | null>(null);
  const [activeWorkbenchTab, setActiveWorkbenchTab] = useState<'online' | 'log' | 'preview'>('online');
  const [previewAutoRefresh, setPreviewAutoRefresh] = useState(true);
  const [pageVisible, setPageVisible] = useState(() => document.visibilityState === 'visible');
  const [finalPreviewWaitExpired, setFinalPreviewWaitExpired] = useState(false);
  const [expandedResourceKeys, setExpandedResourceKeys] = useState<string[]>([]);
  const saveMutation = useSaveSparkJarOnlineSource();
  const compileMutation = useCompileSparkJarOnlineSource();
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
        label: detail ? `${detail.model.name} · ${detail.model.code}` : (binding.resourceName ?? binding.bindingName),
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
      fieldsLoading: jdbcQueries[index]?.isPending ?? false,
      fields: jdbcQueries[index]?.data?.columns.map((field) => ({
        name: field.name,
        type: field.platformTypeDefinition?.type ?? field.nativeType,
        nullable: field.nullable,
      })) ?? [],
    })),
    ...kafkaBindings.map((binding): SparkJarCodeResource => ({
      bindingName: binding.bindingName,
      label: `${binding.resourceName ?? binding.bindingName} · ${binding.topicName ?? 'Topic 未配置'}`,
      kind: 'KAFKA_TOPIC',
      accessMode: binding.accessMode,
      fields: [],
    })),
  ], [jdbcQueries, jdbcResources, kafkaBindings, modelBindings, modelQueries]);

  const localDirty = source !== persistedSource;
  const hasUncompiledChanges = localDirty || sourceState.hasUncompiledChanges;
  const sourceBytes = new Blob([source]).size;
  const busy = saveMutation.isPending || compileMutation.isPending || trialMutation.isPending;
  const blocker = useBlocker(({ currentLocation, nextLocation }) => (
    localDirty && currentLocation.pathname !== nextLocation.pathname
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
      if (!localDirty) return;
      event.preventDefault();
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [localDirty]);

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

  const save = async () => {
    if (sourceBytes > onlineSourceLimit) {
      void messageApi.error('源码不能超过 256 KiB');
      return;
    }
    try {
      const result = await saveMutation.mutateAsync({ id: taskId, sourceCode: source });
      setPersistedSource(result.sourceCode);
      setSourceState(result);
      void messageApi.success('草稿已保存');
    } catch (error) {
      void messageApi.error(error instanceof ApiError ? error.message : '草稿保存失败');
    }
  };

  const compile = async () => {
    if (sourceBytes > onlineSourceLimit) {
      void messageApi.error('源码不能超过 256 KiB');
      return;
    }
    try {
      const result = await compileMutation.mutateAsync({ id: taskId, sourceCode: source });
      setPersistedSource(result.source.sourceCode);
      setSourceState(result.source);
      setDiagnostics(result.diagnostics);
      setDiagnosticsExpanded(result.diagnostics.length > 0);
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
    try {
      const result = await trialMutation.mutateAsync({ id: taskId, sourceCode: source });
      setPersistedSource(result.source.sourceCode);
      setSourceState(result.source);
      setDiagnostics(result.diagnostics);
      setDiagnosticsExpanded(result.status === 'COMPILE_FAILED');
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
      <header className="spark-jar-online-header">
        <div className="spark-jar-online-identity">
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={goBack}>返回任务定义</Button>
          <span className="spark-jar-online-icon"><CodeOutlined /></span>
          <div>
            <Space size={8} wrap>
              <Typography.Text strong className="spark-jar-online-title">{taskName}</Typography.Text>
              <Tag color={streaming ? 'orange' : 'geekblue'}>{streaming ? '实时在线 Java 开发' : '在线 Java 开发'}</Tag>
              <Tag>Java 21 · Spark 4.1.1 · Job API v1</Tag>
            </Space>
            <Typography.Text type="secondary">
              {streaming
                ? 'com.example.datascalpel.ExampleSparkStreamingJob'
                : 'com.example.datascalpel.ExampleSparkJob'}
            </Typography.Text>
          </div>
        </div>
        <Space wrap>
          <Tag color={localDirty ? 'orange' : 'default'}>{localDirty ? '草稿未保存' : '草稿已保存'}</Tag>
          <Tag color={hasUncompiledChanges ? 'gold' : 'success'}>
            {hasUncompiledChanges ? '存在未编译修改' : '当前源码已应用'}
          </Tag>
          {sourceState.currentJar && <Tag color="blue">当前 JAR：{jarOrigin}</Tag>}
          <Button icon={<SaveOutlined />} loading={saveMutation.isPending} disabled={compileMutation.isPending} onClick={() => void save()}>
            保存草稿
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
          <Button type="primary" icon={<CloudUploadOutlined />} loading={compileMutation.isPending} disabled={saveMutation.isPending} onClick={() => void compile()}>
            编译并应用
          </Button>
        </Space>
      </header>

      <main className="spark-jar-online-workbench" aria-busy={busy}>
        <Tabs
          className="spark-jar-online-primary-tabs"
          animated={false}
          activeKey={activeWorkbenchTab}
          onChange={(key) => setActiveWorkbenchTab(key as 'online' | 'log' | 'preview')}
          tabBarExtraContent={renderTrialRunStatus()}
          items={[
            {
              key: 'online',
              label: '在线开发',
              children: (
                <div className="spark-jar-online-workspace">
                  <aside className="spark-jar-online-resources">
                    <div className="spark-jar-online-panel-heading">
                      <div><Typography.Text strong>任务资源</Typography.Text><Typography.Text type="secondary">点击可插入 SDK 调用</Typography.Text></div>
                      <Tag>{resources.length}</Tag>
                    </div>
                    <div className="spark-jar-online-resource-list">
                      {resources.length ? resources.map((resource) => {
                        const resourceKey = `${resource.kind}:${resource.bindingName}:${resource.table ?? ''}`;
                        const fieldsExpanded = expandedResourceKeys.includes(resourceKey);
                        return (
                          <section key={resourceKey} className="spark-jar-online-resource-card">
                            <div className="spark-jar-online-resource-card-header">
                              <button type="button" className="spark-jar-online-resource-main" onClick={() => editorRef.current?.insertText(resourceSnippet(resource, streaming))}>
                                <span className="spark-jar-online-resource-icon">
                                  {resource.kind === 'MODEL'
                                    ? <DatabaseOutlined />
                                    : resource.kind === 'KAFKA_TOPIC' ? <CloudServerOutlined /> : <TableOutlined />}
                                </span>
                                <span>
                                  <strong>{resource.bindingName}</strong>
                                  <small>{resource.label}</small>
                                </span>
                                <Tag color={resource.accessMode === 'WRITE' ? 'purple' : 'blue'}>{resource.accessMode}</Tag>
                              </button>
                              {resource.kind !== 'KAFKA_TOPIC' && <Tooltip title={fieldsExpanded ? '收起字段' : '展开字段'}>
                                <button
                                  type="button"
                                  className={`spark-jar-online-resource-expand-button${fieldsExpanded ? ' is-expanded' : ''}`}
                                  aria-label={`${fieldsExpanded ? '收起' : '展开'} ${resource.bindingName} 的字段`}
                                  aria-expanded={fieldsExpanded}
                                  onClick={() => toggleResourceFields(resourceKey)}
                                >
                                  <DownOutlined />
                                </button>
                              </Tooltip>}
                            </div>
                            {fieldsExpanded && (
                              <div className="spark-jar-online-fields">
                                {resource.fields.length ? resource.fields.map((field) => (
                                  <button
                                    key={field.name}
                                    type="button"
                                    onClick={() => editorRef.current?.insertText(`col("${field.name}")`)}
                                    title={`插入 col("${field.name}")`}
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
                      <Space><CodeOutlined /><Typography.Text strong>{streaming ? 'ExampleSparkStreamingJob.java' : 'ExampleSparkJob.java'}</Typography.Text></Space>
                      <Space>
                        <Typography.Text type={sourceBytes > onlineSourceLimit ? 'danger' : 'secondary'}>
                          {(sourceBytes / 1024).toFixed(1)} / 256 KiB
                        </Typography.Text>
                        {compileMutation.isPending && <Typography.Text type="secondary"><Spin size="small" /> 正在编译，不会执行用户代码</Typography.Text>}
                      </Space>
                    </div>
                    <SparkJarJavaEditor
                      ref={editorRef}
                      taskId={taskId}
                      value={source}
                      diagnostics={diagnostics}
                      resources={resources}
                      jobMode={definition.jobMode}
                      onChange={setSource}
                    />
                    <div className={`spark-jar-online-diagnostics${diagnosticsExpanded ? ' spark-jar-online-diagnostics-expanded' : ''}`}>
                      <button type="button" className="spark-jar-online-diagnostics-heading" onClick={() => setDiagnosticsExpanded((current) => !current)}>
                        <span>
                          {diagnostics.some((item) => item.severity === 'ERROR') ? <ExclamationCircleOutlined /> : <CheckCircleOutlined />}
                          编译诊断
                        </span>
                        <span>{diagnostics.length ? `${diagnostics.length} 项` : '尚未编译'}</span>
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
                          ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="点击“编译并应用”后查看结果" />}
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
      taskId={taskId}
      taskName={taskQuery.data.name}
      definitionHref={definitionHref}
      initialSource={sourceQuery.data}
      definition={definitionQuery.data}
      jdbcTables={kitQuery.data.configuration.jdbcTables}
    />
  );
};
