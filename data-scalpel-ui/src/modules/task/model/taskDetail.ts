export type TaskDetailTabKey = 'basic' | 'definition' | 'models' | 'streaming' | 'schedules' | 'runs';

export const normalizeTaskDetailTab = (value: string | null): TaskDetailTabKey => {
  if (
    value === 'definition'
    || value === 'models'
    || value === 'streaming'
    || value === 'schedules'
    || value === 'runs'
  ) return value;
  return 'basic';
};
