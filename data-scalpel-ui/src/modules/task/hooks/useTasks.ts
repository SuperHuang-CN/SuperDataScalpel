import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { invalidateDirectoryTree } from '../../directory';
import type { SearchRequest } from '../../../shared/search';
import {
  cancelTaskRun,
  createTask,
  createTaskSchedule,
  deleteTask,
  deleteTaskSchedule,
  downloadTaskRunArtifact,
  downloadQualityFailureSamples,
  downloadSparkJarTemplate,
  executeTaskCommand,
  executeTaskScheduleCommand,
  executeTaskStreamingCommand,
  fetchTask,
  fetchTaskModelRelations,
  fetchTaskTableLineage,
  fetchTaskFieldLineage,
  fetchModelRelatedTasks,
  fetchTaskRun,
  fetchTaskRunResultArtifact,
  fetchQualityFailureSamples,
  fetchCanvasTaskDefinition,
  fetchModelQualityTaskDefinition,
  fetchSparkJarTaskDefinition,
  fetchTaskDefinition,
  fetchTaskRuns,
  fetchTaskSchedules,
  fetchTaskStreamingConfiguration,
  fetchTaskStreamingStatus,
  fetchTasks,
  runTask,
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
  ModelTaskRelationRole,
  TaskScheduleRequest,
  UpdateTaskStreamingConfigurationRequest,
  StreamingCheckpointMode,
  UpdateDataTaskRequest,
  UpdateLocalSqlTaskDefinitionRequest,
  UpdateSparkJarTaskDefinitionRequest,
} from '../model/task';

const tasksKey = 'tasks';
const taskRunsKey = 'task-runs';
const taskModelRelationsKey = 'task-model-relations';

const invalidateTasks = async (queryClient: ReturnType<typeof useQueryClient>) => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: [tasksKey] }),
    queryClient.invalidateQueries({ queryKey: [taskModelRelationsKey] }),
    invalidateDirectoryTree(queryClient, 'TASK'),
  ]);
};

export const useTasks = (request: SearchRequest) => useQuery({
  queryKey: [tasksKey, request],
  queryFn: () => fetchTasks(request),
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
      await queryClient.invalidateQueries({ queryKey: [tasksKey, definition.taskId] });
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
      await queryClient.invalidateQueries({ queryKey: [tasksKey, definition.taskId] });
      await invalidateTasks(queryClient);
    },
  });
};

export const useDownloadSparkJarTemplate = () => useMutation({
  mutationFn: downloadSparkJarTemplate,
});

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
