import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { invalidateDirectoryTree } from '../../directory';
import type { SearchRequest } from '../../../shared/search';
import {
  createTask,
  deleteTask,
  executeTaskCommand,
  fetchTask,
  fetchTaskDefinition,
  fetchTaskRuns,
  fetchTasks,
  runTask,
  updateTask,
  updateTaskDefinition,
  validateTaskDefinition,
  type TaskCommand,
} from '../api/taskApi';
import type {
  CreateDataTaskRequest,
  UpdateDataTaskRequest,
  UpdateLocalSqlTaskDefinitionRequest,
} from '../model/task';

const tasksKey = 'tasks';

const invalidateTasks = async (queryClient: ReturnType<typeof useQueryClient>) => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: [tasksKey] }),
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

export const useTaskDefinition = (id: string | undefined) => useQuery({
  queryKey: [tasksKey, id, 'definition'],
  queryFn: () => fetchTaskDefinition(id as string),
  enabled: Boolean(id),
});

export const useTaskRuns = (id: string | undefined, request: SearchRequest, poll: boolean) => useQuery({
  queryKey: [tasksKey, id, 'runs', request],
  queryFn: () => fetchTaskRuns(id as string, request),
  enabled: Boolean(id),
  refetchInterval: (query) => {
    const runs = query.state.data?.content ?? [];
    return poll && runs.some((run) => run.status === 'QUEUED' || run.status === 'RUNNING') ? 2_000 : false;
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

export const useValidateTaskDefinition = () => useMutation({ mutationFn: validateTaskDefinition });

export const useTaskCommand = (command: TaskCommand) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => executeTaskCommand(id, command),
    onSuccess: async (task) => {
      queryClient.setQueryData([tasksKey, task.id], task);
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
