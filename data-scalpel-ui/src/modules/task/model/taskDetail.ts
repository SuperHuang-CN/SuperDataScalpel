export type TaskDetailTabKey = 'basic' | 'definition' | 'quality' | 'models' | 'lineage' | 'streaming' | 'schedules' | 'runs';

export const normalizeTaskDetailTab = (value: string | null): TaskDetailTabKey => {
  if (
    value === 'definition'
    || value === 'quality'
    || value === 'models'
    || value === 'lineage'
    || value === 'streaming'
    || value === 'schedules'
    || value === 'runs'
  ) return value;
  return 'basic';
};
