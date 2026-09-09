import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { invalidateDirectoryTree } from '../../directory';
import type { SearchRequest } from '../../../shared/search';
import {
  cancelTaskRun,
  compileSparkJarOnlineSource,
  trialRunSparkJarOnlineSource,
  acceptTaskCanvasProposal,
  createTask,
  generateSparkJarDevelopmentKit,
  createTaskSchedule,
  deleteTask,
  deleteTaskSchedule,
  downloadTaskRunArtifact,
  downloadQualityFailureSamples,
  downloadSparkJarTemplate,
  downloadSparkJarDevelopmentKit,
  executeTaskCommand,
  executeTaskScheduleCommand,
  executeTaskStreamingCommand,
  fetchTask,
  fetchTaskCanvasProposal,
  fetchTaskModelRelations,
  fetchTaskTableLineage,
  fetchTaskFieldLineage,
  queryTaskFieldLineage,
  fetchModelRelatedTasks,
  fetchTaskRun,
  fetchTaskRunArtifacts,
  fetchTaskRunArtifactPreview,
  fetchSparkJarTrialPreview,
  fetchCanvasTrialPreview,
  fetchTaskRunLogs,
  fetchTaskRunLineage,
  fetchTaskRunResultArtifact,
  fetchQualityFailureSamples,
  fetchCanvasTaskDefinition,
  fetchModelQualityTaskDefinition,
  fetchSparkJarTaskDefinition,
  fetchSparkJarDevelopmentKit,
  fetchSparkJarOnlineSource,
  fetchTaskDefinition,
  fetchTaskRuns,
  fetchTaskSchedules,
  fetchTaskStreamingConfiguration,
  fetchTaskStreamingStatus,
  fetchTasks,
  forceTerminateTaskRun,
  runTask,
  trialRunCanvas,
  saveSparkJarOnlineSource,
  stopTaskRun,
  updateTask,
  updateCanvasTaskDefinition,
  updateTaskDefinition,
  updateTaskSchedule,
  updateTaskStreamingConfiguration,
  updateModelQualityTaskDefinition,
  updateSparkJarTaskDefinition,
  uploadSparkJar,
  validateTaskDefinition,
  type TaskCommand,
  type TaskRunArtifactKind,
  type TaskScheduleCommand,
  type TaskStreamingCommand,
} from '../api/taskApi';
import type { CanvasDefinition } from '../canvas/canvasTypes';
import type {
  CreateDataTaskRequest,
  CreateSparkJarDevelopmentKitRequest,
  ModelTaskRelationRole,
  TaskScheduleRequest,
  UpdateTaskStreamingConfigurationRequest,
  StreamingCheckpointMode,
  UpdateDataTaskRequest,
  UpdateLocalSqlTaskDefinitionRequest,
  UpdateSparkJarTaskDefinitionRequest,
  CanvasTrialRunRequest,
  TaskRunLog,
} from '../model/task';

const tasksKey = 'tasks';
const taskRunsKey = 'task-runs';
const taskModelRelationsKey = 'task-model-relations';

const invalidateTasks = async (queryClient: ReturnType<typeof useQueryClient>) => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: [tasksKey] }),
    queryClient.invalidateQueries({ queryKey: ['metrics'] }),
    queryClient.invalidateQueries({ queryKey: [taskModelRelationsKey] }),
    invalidateDirectoryTree(queryClient, 'TASK'),
  ]);
};

export const useTasks = (request: SearchRequest, enabled = true) => useQuery({
  queryKey: [tasksKey, request],
  queryFn: () => fetchTasks(request),
  enabled,
});

export const useTask = (id: string | undefined) => useQuery({
  queryKey: [tasksKey, id],
  queryFn: () => fetchTask(id as string),
  enabled: Boolean(id),
});

export const useTaskDefinition = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [tasksKey, id, 'definition'],
  queryFn: () => fetchTaskDefinition(id as string),
  enabled: Boolean(id) && enabled,
});

export const useCanvasTaskDefinition = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [tasksKey, id, 'canvas-definition'],
  queryFn: () => fetchCanvasTaskDefinition(id as string),
  enabled: Boolean(id) && enabled,
});

export const useTaskCanvasProposal = (id: string | undefined) => useQuery({
  queryKey: ['assistant', 'task-canvas-proposal', id],
  queryFn: () => fetchTaskCanvasProposal(id as string),
  enabled: Boolean(id),
  retry: false,
});

export const useAcceptTaskCanvasProposal = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, taskId }: { id: string; taskId: string }) => acceptTaskCanvasProposal(id, taskId),
    onSuccess: async (changeSet) => {
      queryClient.setQueryData(['assistant', 'task-canvas-proposal', changeSet.id], changeSet);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['assistant', 'sessions'] }),
        queryClient.invalidateQueries({ queryKey: ['assistant', 'session'] }),
      ]);
    },
  });
};

export const useModelQualityTaskDefinition = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [tasksKey, id, 'model-quality-definition'],
  queryFn: () => fetchModelQualityTaskDefinition(id as string),
  enabled: Boolean(id) && enabled,
});

export const useSparkJarTaskDefinition = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [tasksKey, id, 'spark-jar-definition'],
  queryFn: () => fetchSparkJarTaskDefinition(id as string),
  enabled: Boolean(id) && enabled,
});

export const useUpdateSparkJarTaskDefinition = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateSparkJarTaskDefinitionRequest }) => (
      updateSparkJarTaskDefinition(id, request)
    ),
    onSuccess: async (definition) => {
      queryClient.setQueryData([tasksKey, definition.taskId, 'spark-jar-definition'], definition);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: [tasksKey, definition.taskId] }),
        queryClient.invalidateQueries({ queryKey: [tasksKey, definition.taskId, 'spark-jar-development-kit'] }),
      ]);
      await invalidateTasks(queryClient);
    },
  });
};

export const useUploadSparkJar = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => uploadSparkJar(id, file),
    onSuccess: async (definition) => {
      queryClient.setQueryData([tasksKey, definition.taskId, 'spark-jar-definition'], definition);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: [tasksKey, definition.taskId] }),
        queryClient.invalidateQueries({ queryKey: [tasksKey, definition.taskId, 'spark-jar-development-kit'] }),
      ]);
      await invalidateTasks(queryClient);
    },
  });
};

export const useDownloadSparkJarTemplate = () => useMutation({
  mutationFn: downloadSparkJarTemplate,
});

export const useSparkJarDevelopmentKit = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [tasksKey, id, 'spark-jar-development-kit'],
  queryFn: () => fetchSparkJarDevelopmentKit(id as string),
  enabled: Boolean(id) && enabled,
  refetchInterval: (query) => {
    const status = query.state.data?.generation?.status;
    return status === 'QUEUED' || status === 'RUNNING'
    ? 1_500
      : false;
  },
});

export const useGenerateSparkJarDevelopmentKit = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: CreateSparkJarDevelopmentKitRequest }) => (
      generateSparkJarDevelopmentKit(id, request)
    ),
    onSuccess: async (developmentKit) => {
      queryClient.setQueryData([tasksKey, developmentKit.taskId, 'spark-jar-development-kit'], developmentKit);
      await queryClient.invalidateQueries({ queryKey: [tasksKey, developmentKit.taskId, 'spark-jar-development-kit'] });
    },
  });
};

export const useDownloadSparkJarDevelopmentKit = () => useMutation({
  mutationFn: downloadSparkJarDevelopmentKit,
});

export const useSparkJarOnlineSource = (id: string | undefined) => useQuery({
  queryKey: [tasksKey, id, 'spark-jar-online-source'],
  queryFn: () => fetchSparkJarOnlineSource(id as string),
  enabled: Boolean(id),
});

export const useSaveSparkJarOnlineSource = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, sourceCode }: { id: string; sourceCode: string }) => (
      saveSparkJarOnlineSource(id, sourceCode)
    ),
    onSuccess: (source) => {
      queryClient.setQueryData([tasksKey, source.taskId, 'spark-jar-online-source'], source);
    },
  });
};

export const useCompileSparkJarOnlineSource = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, sourceCode }: { id: string; sourceCode: string }) => (
      compileSparkJarOnlineSource(id, sourceCode)
    ),
    onSuccess: async (result) => {
      queryClient.setQueryData([tasksKey, result.source.taskId, 'spark-jar-online-source'], result.source);
      if (result.status === 'SUCCEEDED') {
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: [tasksKey, result.source.taskId] }),
          queryClient.invalidateQueries({ queryKey: [tasksKey, result.source.taskId, 'spark-jar-definition'] }),
        ]);
        await invalidateTasks(queryClient);
      }
    },
  });
};

export const useTrialRunSparkJarOnlineSource = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, sourceCode }: { id: string; sourceCode: string }) => (
      trialRunSparkJarOnlineSource(id, sourceCode)
    ),
    onSuccess: async (result) => {
      queryClient.setQueryData([tasksKey, result.source.taskId, 'spark-jar-online-source'], result.source);
      if (result.run) queryClient.setQueryData([taskRunsKey, result.run.id], result.run);
      await queryClient.invalidateQueries({ queryKey: [tasksKey, result.source.taskId, 'runs'] });
    },
  });
};

export const useUpdateModelQualityTaskDefinition = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, modelId, failureSampleLimit }: {
      id: string;
      modelId: string;
      failureSampleLimit: number;
    }) => (
      updateModelQualityTaskDefinition(id, modelId, failureSampleLimit)
    ),
    onSuccess: async (definition) => {
      queryClient.setQueryData([tasksKey, definition.taskId, 'model-quality-definition'], definition);
      await queryClient.invalidateQueries({ queryKey: [tasksKey, definition.taskId] });
      await invalidateTasks(queryClient);
    },
  });
};

export const useTaskModelRelations = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [taskModelRelationsKey, 'task', id],
  queryFn: () => fetchTaskModelRelations(id as string),
  enabled: Boolean(id) && enabled,
});

export const useTaskLineage = (
  id: string | undefined,
  granularity: 'TABLE' | 'FIELD',
  flowKey: string | undefined,
  outputFieldKey: string | undefined,
  enabled = true,
) => useQuery({
  queryKey: [tasksKey, id, 'lineage', granularity, flowKey ?? null, outputFieldKey ?? null],
  queryFn: () => granularity === 'TABLE'
    ? fetchTaskTableLineage(id as string, flowKey)
    : fetchTaskFieldLineage(id as string, flowKey, outputFieldKey),
  enabled: Boolean(id) && enabled,
  placeholderData: (previous) => previous,
});

export const useTaskFieldLineage = (
  id: string | undefined,
  flowKey: string | undefined,
  outputFieldKeys: string[] | null,
  enabled = true,
) => useQuery({
  queryKey: [tasksKey, id, 'lineage', 'FIELD_BATCH', flowKey ?? null, outputFieldKeys],
  queryFn: ({ signal }) => queryTaskFieldLineage(id as string, flowKey, outputFieldKeys, signal),
  enabled: Boolean(id) && enabled,
  staleTime: 30_000,
  placeholderData: (previous) => previous,
});

export const useModelRelatedTasks = (
  modelId: string | undefined,
  role: ModelTaskRelationRole | undefined,
  request: SearchRequest,
  enabled = true,
) => useQuery({
  queryKey: [taskModelRelationsKey, 'model', modelId, role ?? null, request],
  queryFn: () => fetchModelRelatedTasks(modelId as string, role, request),
  enabled: Boolean(modelId) && enabled,
});

export const useTaskRuns = (id: string | undefined, request: SearchRequest, poll: boolean) => useQuery({
  queryKey: [tasksKey, id, 'runs', request],
  queryFn: () => fetchTaskRuns(id as string, request),
  enabled: Boolean(id),
  refetchInterval: (query) => {
    const runs = query.state.data?.content ?? [];
    if (!poll) return false;
    return runs.some((run) => (
      run.status === 'QUEUED' || run.status === 'RUNNING' || run.status === 'CANCEL_REQUESTED'
      || run.status === 'STOP_REQUESTED'
    )) ? 2_000 : 10_000;
  },
});

export const useLatestCanvasNodeTrialRun = (
  taskId: string | undefined,
  nodeId: string | undefined,
  enabled = true,
) => useQuery({
  queryKey: [tasksKey, taskId, 'runs', 'canvas-trial-node', nodeId],
  queryFn: () => fetchTaskRuns(taskId as string, {
    search: `executionMode:"TRIAL" AND canvasTrialTargetNodeId:"${nodeId}"`,
    page: 0,
    size: 1,
    sort: '-queuedAt',
  }),
  enabled: Boolean(taskId) && Boolean(nodeId) && enabled,
  staleTime: 5_000,
  refetchInterval: (query) => {
    const run = query.state.data?.content[0];
    return run && (
      run.status === 'QUEUED' || run.status === 'RUNNING'
      || run.status === 'CANCEL_REQUESTED' || run.status === 'STOP_REQUESTED'
    ) ? 2_000 : false;
  },
});

export const useTaskRun = (runId: string | undefined, enabled = true) => useQuery({
  queryKey: [taskRunsKey, runId],
  queryFn: () => fetchTaskRun(runId as string),
  enabled: Boolean(runId) && enabled,
  refetchInterval: (query) => {
    const run = query.state.data;
    const active = run?.status === 'QUEUED' || run?.status === 'RUNNING'
      || run?.status === 'CANCEL_REQUESTED' || run?.status === 'STOP_REQUESTED';
    if (!active) return false;
    return run?.taskType === 'SPARK_JAR' || run?.taskType === 'SPARK_STREAMING_JAR' ? 5_000 : 2_000;
  },
});

export const useTaskRunArtifacts = (runId: string | undefined, enabled = true) => useQuery({
  queryKey: [taskRunsKey, runId, 'artifacts'],
  queryFn: () => fetchTaskRunArtifacts(runId as string),
  enabled: Boolean(runId) && enabled,
  retry: false,
});

export const useTaskRunArtifactPreview = (
  runId: string | undefined,
  kind: TaskRunArtifactKind | null,
  enabled = true,
) => useQuery({
  queryKey: [taskRunsKey, runId, 'artifact-preview', kind],
  queryFn: () => fetchTaskRunArtifactPreview(runId as string, kind as TaskRunArtifactKind),
  enabled: Boolean(runId) && Boolean(kind) && enabled,
  retry: false,
});

export const useSparkJarTrialPreview = (
  runId: string | undefined,
  enabled = true,
  polling = false,
) => useQuery({
  queryKey: [taskRunsKey, runId, 'trial-preview'],
  queryFn: () => fetchSparkJarTrialPreview(runId as string),
  enabled: Boolean(runId) && enabled,
  retry: false,
  refetchInterval: (query) => polling && !query.state.data?.finalResult ? 3_000 : false,
  refetchIntervalInBackground: false,
});

export const useCanvasTrialPreview = (runId: string | undefined, enabled = true) => useQuery({
  queryKey: [taskRunsKey, runId, 'canvas-trial-preview'],
  queryFn: () => fetchCanvasTrialPreview(runId as string),
  enabled: Boolean(runId) && enabled,
  retry: false,
});

export const useTaskRunLogs = (runId: string | undefined, enabled: boolean, polling: boolean) => useQuery<TaskRunLog>({
  queryKey: [taskRunsKey, runId, 'logs'],
  queryFn: () => fetchTaskRunLogs(runId as string),
  enabled: Boolean(runId) && enabled,
  retry: false,
  placeholderData: (previous) => previous,
  structuralSharing: (previous, next) => {
    const previousLog = previous as TaskRunLog | undefined;
    const nextLog = next as TaskRunLog;
    return nextLog.content == null && previousLog?.content
      ? {
        ...nextLog,
        content: previousLog.content,
        windowSizeBytes: previousLog.windowSizeBytes,
        truncated: nextLog.truncated || previousLog.truncated,
      }
      : nextLog;
  },
  refetchInterval: (query) => polling && query.state.data?.status !== 'FINAL' ? 3_000 : false,
});

export const useTaskRunLineage = (runId: string | undefined, enabled = true) => useQuery({
  queryKey: [taskRunsKey, runId, 'lineage'],
  queryFn: () => fetchTaskRunLineage(runId as string),
  enabled: Boolean(runId) && enabled,
  refetchInterval: (query) => {
    const status = query.state.data?.status;
    return status === 'PENDING' || status === 'QUEUED' || status === 'RUNNING' ? 2_000 : false;
  },
});

export const useTaskRunResultArtifact = (runId: string | undefined, enabled = true) => useQuery({
  queryKey: [taskRunsKey, runId, 'result-artifact'],
  queryFn: () => fetchTaskRunResultArtifact(runId as string),
  enabled: Boolean(runId) && enabled,
  staleTime: Infinity,
  retry: false,
});

export const useQualityFailureSamples = (
  runId: string | undefined,
  ruleId: string | undefined,
  enabled = true,
) => useQuery({
  queryKey: [taskRunsKey, runId, 'quality-samples', ruleId],
  queryFn: () => fetchQualityFailureSamples(runId as string, ruleId as string),
  enabled: Boolean(runId) && Boolean(ruleId) && enabled,
  staleTime: Infinity,
  retry: false,
});

export const useDownloadQualityFailureSamples = () => useMutation({
  mutationFn: ({ runId, ruleId }: { runId: string; ruleId: string }) => (
    downloadQualityFailureSamples(runId, ruleId)
  ),
});

export const useTaskSchedules = (id: string | undefined) => useQuery({
  queryKey: [tasksKey, id, 'schedules'],
  queryFn: () => fetchTaskSchedules(id as string),
  enabled: Boolean(id),
  refetchInterval: 10_000,
});

export const useTaskStreamingConfiguration = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [tasksKey, id, 'streaming-configuration'],
  queryFn: () => fetchTaskStreamingConfiguration(id as string),
  enabled: Boolean(id) && enabled,
});

export const useTaskStreamingStatus = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [tasksKey, id, 'streaming-status'],
  queryFn: () => fetchTaskStreamingStatus(id as string),
  enabled: Boolean(id) && enabled,
  refetchInterval: (query) => {
    const state = query.state.data?.deployment?.actualState;
    return state === 'STARTING' || state === 'RUNNING' || state === 'STOPPING' ? 5_000 : 15_000;
  },
});

export const useCreateTask = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateDataTaskRequest) => createTask(request),
    onSuccess: () => invalidateTasks(queryClient),
  });
};

export const useUpdateTask = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateDataTaskRequest }) => updateTask(id, request),
    onSuccess: async (task) => {
      queryClient.setQueryData([tasksKey, task.id], task);
      await queryClient.invalidateQueries({ queryKey: [tasksKey, task.id, 'schedules'] });
      await invalidateTasks(queryClient);
    },
  });
};

export const useUpdateTaskDefinition = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateLocalSqlTaskDefinitionRequest }) => updateTaskDefinition(id, request),
    onSuccess: async (definition) => {
      queryClient.setQueryData([tasksKey, definition.taskId, 'definition'], definition);
      await invalidateTasks(queryClient);
    },
  });
};

export const useUpdateCanvasTaskDefinition = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, definition }: { id: string; definition: CanvasDefinition }) => (
      updateCanvasTaskDefinition(id, definition)
    ),
    onSuccess: async (saved) => {
      queryClient.setQueryData([tasksKey, saved.taskId, 'canvas-definition'], saved);
      await queryClient.invalidateQueries({ queryKey: [tasksKey, saved.taskId] });
      await invalidateTasks(queryClient);
    },
  });
};

export const useValidateTaskDefinition = () => useMutation({ mutationFn: validateTaskDefinition });

export const useTaskCommand = (command: TaskCommand) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => executeTaskCommand(id, command),
    onSuccess: async (task) => {
      queryClient.setQueryData([tasksKey, task.id], task);
      await queryClient.invalidateQueries({ queryKey: [tasksKey, task.id, 'schedules'] });
      await invalidateTasks(queryClient);
    },
  });
};

export const useDeleteTask = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteTask,
    onSuccess: () => invalidateTasks(queryClient),
  });
};

export const useRunTask = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: runTask,
    onSuccess: (run) => queryClient.invalidateQueries({ queryKey: [tasksKey, run.taskId, 'runs'] }),
  });
};

export const useTrialRunCanvas = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: CanvasTrialRunRequest }) => (
      trialRunCanvas(id, request)
    ),
    onSuccess: async (run) => {
      queryClient.setQueryData([taskRunsKey, run.id], run);
      await queryClient.invalidateQueries({ queryKey: [tasksKey, run.taskId, 'runs'] });
    },
  });
};

export const useUpdateTaskStreamingConfiguration = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      request,
    }: {
      id: string;
      request: UpdateTaskStreamingConfigurationRequest;
    }) => updateTaskStreamingConfiguration(id, request),
    onSuccess: (configuration) => {
      queryClient.setQueryData(
        [tasksKey, configuration.taskId, 'streaming-configuration'],
        configuration,
      );
    },
  });
};

export const useTaskStreamingCommand = (command: TaskStreamingCommand) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: string | { id: string; checkpointMode?: StreamingCheckpointMode }) => {
      const id = typeof request === 'string' ? request : request.id;
      const checkpointMode = typeof request === 'string' ? undefined : request.checkpointMode;
      return executeTaskStreamingCommand(id, command, checkpointMode);
    },
    onSuccess: async (status) => {
      queryClient.setQueryData([tasksKey, status.taskId, 'streaming-status'], status);
      await queryClient.invalidateQueries({ queryKey: [tasksKey, status.taskId, 'runs'] });
      await queryClient.invalidateQueries({ queryKey: [tasksKey, status.taskId] });
    },
  });
};

export const useCancelTaskRun = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: cancelTaskRun,
    onSuccess: async (run) => {
      queryClient.setQueryData([taskRunsKey, run.id], run);
      await queryClient.invalidateQueries({ queryKey: [tasksKey, run.taskId, 'runs'] });
    },
  });
};

export const useStopTaskRun = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: stopTaskRun,
    onSuccess: async (run) => {
      queryClient.setQueryData([taskRunsKey, run.id], run);
      await queryClient.invalidateQueries({ queryKey: [tasksKey, run.taskId, 'runs'] });
      if (run.streamingDeploymentId) {
        await queryClient.invalidateQueries({ queryKey: [tasksKey, run.taskId, 'streaming-status'] });
      }
    },
  });
};

export const useForceTerminateTaskRun = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: forceTerminateTaskRun,
    onSuccess: async (run) => {
      queryClient.setQueryData([taskRunsKey, run.id], run);
      await queryClient.invalidateQueries({ queryKey: [tasksKey, run.taskId, 'runs'] });
      if (run.streamingDeploymentId) {
        await queryClient.invalidateQueries({ queryKey: [tasksKey, run.taskId, 'streaming-status'] });
      }
    },
  });
};

export const useDownloadTaskRunArtifact = () => useMutation({
  mutationFn: ({ runId, kind }: { runId: string; kind: TaskRunArtifactKind }) => (
    downloadTaskRunArtifact(runId, kind)
  ),
});

const invalidateTaskSchedules = (
  queryClient: ReturnType<typeof useQueryClient>,
  taskId: string,
) => queryClient.invalidateQueries({ queryKey: [tasksKey, taskId, 'schedules'] });

export const useCreateTaskSchedule = (taskId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: TaskScheduleRequest) => createTaskSchedule(taskId, request),
    onSuccess: () => invalidateTaskSchedules(queryClient, taskId),
  });
};

export const useUpdateTaskSchedule = (taskId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ scheduleId, request }: { scheduleId: string; request: TaskScheduleRequest }) => (
      updateTaskSchedule(scheduleId, request)
    ),
    onSuccess: () => invalidateTaskSchedules(queryClient, taskId),
  });
};

export const useTaskScheduleCommand = (taskId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ scheduleId, command }: { scheduleId: string; command: TaskScheduleCommand }) => (
      executeTaskScheduleCommand(scheduleId, command)
    ),
    onSuccess: () => invalidateTaskSchedules(queryClient, taskId),
  });
};

export const useDeleteTaskSchedule = (taskId: string) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteTaskSchedule,
    onSuccess: () => invalidateTaskSchedules(queryClient, taskId),
  });
};
