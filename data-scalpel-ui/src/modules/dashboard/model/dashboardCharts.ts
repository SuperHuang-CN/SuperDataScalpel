import type { RuntimeOverview } from '../../operations';
import type { GatewayAccessTrend } from '../../dataservice';
export const runtimeBuckets = (data: RuntimeOverview) => {
 const buckets = new Map<string, { from: string; to: string; success: number; failed: number; other: number }>();
 for (const point of data.tasks?.trend ?? []) {
  const bucket = buckets.get(point.from) ?? { from: point.from, to: point.to, success: 0, failed: 0, other: 0 };
  if (point.status === 'SUCCESS') bucket.success += point.count;
  else if (point.status === 'FAILED' || point.status === 'TIMED_OUT') bucket.failed += point.count;
  else bucket.other += point.count;
  buckets.set(point.from, bucket);
 }
 return [...buckets.values()].sort((a, b) => a.from.localeCompare(b.from));
};
export const runtimeHref = (from: string, to: string, status?: string) =>
 '/operations?' + new URLSearchParams({ tab: 'runs', timeField: 'endedAt', batch: 'true', from, to, ...(status ? { status } : {}) });
export const usageBuckets = (data: GatewayAccessTrend) => {
 const start = Date.parse(data.fromInclusive);
 const end = Date.parse(data.toExclusive);
 const step = 24 * 3600_000;
 const buckets = Array.from({ length: Math.ceil((end - start) / step) }, (_, index) => ({
  at: new Date(start + index * step).toISOString(), requests: 0, errors: 0,
 }));
 for (const point of data.points) {
  const time = Date.parse(point.hourStart);
  if (time < start || time >= end) continue;
  const index = Math.floor((time - start) / step);
  if (index >= 0 && index < buckets.length) {
   buckets[index].requests += point.requestCount; buckets[index].errors += point.status5xxCount;
  }
 }
 return buckets;
};
