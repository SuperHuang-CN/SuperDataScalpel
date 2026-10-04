import { describe, expect, it } from 'vitest';
import { runtimeBuckets, runtimeHref, usageBuckets } from './dashboardCharts';
import type { RuntimeOverview } from '../../operations';

describe('homepage chart and drill-down boundaries', () => {
  it('preserves completed time, batch scope and status', () => {
    const params = new URL(runtimeHref('2026-09-29T00:00:00Z', '2026-09-30T00:00:00Z', 'FAILED_OR_TIMED_OUT'), 'http://local').searchParams;
    expect(Object.fromEntries(params)).toEqual({ tab: 'runs', timeField: 'endedAt', batch: 'true', from: '2026-09-29T00:00:00Z', to: '2026-09-30T00:00:00Z', status: 'FAILED_OR_TIMED_OUT' });
  });
  it('does not classify cancellation as failure', () => {
    const data: RuntimeOverview = { from: 'a', to: 'b', collectedAt: 'b', engines: null, openAlerts: 0, pendingSignals: null, failedDeliveries: null,
      tasks: { current: {}, completed: {}, qualityFailed: 0, successRate: null, trend: [
        { from: 'a', to: 'b', status: 'SUCCESS', count: 5 },
        { from: 'a', to: 'b', status: 'FAILED', count: 2 },
        { from: 'a', to: 'b', status: 'TIMED_OUT', count: 1 },
        { from: 'a', to: 'b', status: 'CANCELLED', count: 4 },
      ] } };
    expect(runtimeBuckets(data)).toEqual([{ from: 'a', to: 'b', success: 5, failed: 3, other: 4 }]);
    expect(runtimeBuckets({ ...data, tasks: null })).toEqual([]);
  });
  it('fills seven empty day buckets from the actual aligned window', () => {
    const buckets = usageBuckets({ fromInclusive: '2026-09-23T08:00:00Z', toExclusive: '2026-09-30T08:00:00Z', points: [] });
    expect(buckets).toHaveLength(7);
    expect(buckets[0]).toEqual({ at: '2026-09-23T08:00:00.000Z', requests: 0, errors: 0 });
    expect(buckets[6]).toEqual({ at: '2026-09-29T08:00:00.000Z', requests: 0, errors: 0 });
  });
});
