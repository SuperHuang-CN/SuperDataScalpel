import { describe, expect, it } from 'vitest';
import {
  filterMockModelPreviewRows,
  mockModelPreviewRows,
  normalizeModelDetailTab,
} from './modelDetailMock';

describe('model detail mock interactions', () => {
  it('normalizes unsupported tab keys', () => {
    expect(normalizeModelDetailTab('fields')).toBe('fields');
    expect(normalizeModelDetailTab('changes')).toBe('changes');
    expect(normalizeModelDetailTab('unknown')).toBe('basic');
    expect(normalizeModelDetailTab(null)).toBe('basic');
  });

  it('filters preview rows', () => {
    expect(filterMockModelPreviewRows(mockModelPreviewRows, '政务', 'NEW').length).toBeGreaterThan(0);
    expect(filterMockModelPreviewRows(mockModelPreviewRows, undefined, 'PAID').every((row) => row.status === 'PAID')).toBe(true);
  });
});
