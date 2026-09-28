import { describe, expect, it } from 'vitest';
import { normalizeTaskDetailTab } from './taskDetail';

describe('normalizeTaskDetailTab', () => {
  it.each(['basic', 'definition', 'quality', 'models', 'metrics', 'lineage', 'streaming', 'schedules', 'runs'])('preserves the supported %s tab', (tab) => {
    expect(normalizeTaskDetailTab(tab)).toBe(tab);
  });

  it('keeps supported deep links and defaults unknown values to basic', () => {
    expect(normalizeTaskDetailTab('definition')).toBe('definition');
    expect(normalizeTaskDetailTab('models')).toBe('models');
    expect(normalizeTaskDetailTab('schedules')).toBe('schedules');
    expect(normalizeTaskDetailTab('runs')).toBe('runs');
    expect(normalizeTaskDetailTab('unknown')).toBe('basic');
    expect(normalizeTaskDetailTab(null)).toBe('basic');
  });
});
