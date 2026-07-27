import { describe, expect, it } from 'vitest';
import {
  buildMockLineage,
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

  it('builds lineage by direction and depth', () => {
    const upstream = buildMockLineage('UPSTREAM', 1);
    expect(upstream.nodes.some((node) => node.side === 'DOWNSTREAM')).toBe(false);
    expect(upstream.nodes.some((node) => node.id === 'crm-customer')).toBe(false);

    const complete = buildMockLineage('BOTH', 2);
    expect(complete.nodes).toHaveLength(8);
    expect(complete.edges).toHaveLength(7);
  });
});
