import type { TaskType } from './task';

export type TaskListView = 'all' | 'batch' | 'streaming' | 'workflows' | 'quality';

export interface TaskViewConfiguration {
  id: TaskListView;
  label: string;
  path: string;
  types: readonly TaskType[];
  defaultType: TaskType;
}

export const taskViews: readonly TaskViewConfiguration[] = [
  { id: 'all', label: '全部任务', path: '/task', types: ['LOCAL_SQL', 'WORKFLOW', 'SPARK_CANVAS', 'SPARK_STREAMING_CANVAS', 'SPARK_MODEL_QUALITY', 'SPARK_JAR', 'SPARK_STREAMING_JAR'], defaultType: 'LOCAL_SQL' },
  { id: 'batch', label: '批处理任务', path: '/task/batch', types: ['LOCAL_SQL', 'SPARK_CANVAS', 'SPARK_JAR'], defaultType: 'LOCAL_SQL' },
  { id: 'streaming', label: '实时任务', path: '/task/streaming', types: ['SPARK_STREAMING_CANVAS', 'SPARK_STREAMING_JAR'], defaultType: 'SPARK_STREAMING_CANVAS' },
  { id: 'workflows', label: '工作流', path: '/task/workflows', types: ['WORKFLOW'], defaultType: 'WORKFLOW' },
  { id: 'quality', label: '质检任务', path: '/task/quality', types: ['SPARK_MODEL_QUALITY'], defaultType: 'SPARK_MODEL_QUALITY' },
];

export const getTaskView = (id: TaskListView) => taskViews.find(view => view.id === id)!;

export const resolveTaskView = (source: string | null, type?: TaskType): TaskViewConfiguration => {
  if (!type) return getTaskView('all');
  const requested = taskViews.find(view => view.id === source);
  if (requested?.types.includes(type)) return requested;
  return taskViews.find(view => view.id !== 'all' && view.types.includes(type))!;
};

export const taskIdFromPath = (pathname: string): string | undefined => (
  /^\/task\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?:\/(?:definition|online-code))?\/?$/i.exec(pathname)?.[1]
);

/** Preserve navigation context only when navigating within the same task. */
export const taskPageHref = (
  pathname: string,
  search: string,
  type: TaskType,
  overrides: Record<string, string | null> = {},
): string => {
  const params = new URLSearchParams(search);
  params.set('taskView', resolveTaskView(params.get('taskView'), type).id);
  Object.entries(overrides).forEach(([key, value]) => {
    if (value === null) params.delete(key);
    else params.set(key, value);
  });
  return `${pathname}?${params.toString()}`;
};
