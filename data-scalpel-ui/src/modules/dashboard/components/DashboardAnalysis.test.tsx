import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { RuntimeOverview as RuntimeData } from '../../operations';
import type { GatewayAccessOverview, GatewayAccessTrend, GatewayUsage } from '../../dataservice';
import { RuntimeOverview } from './RuntimeOverview';
import { ServiceUsage } from './ServiceUsage';

afterEach(cleanup);
const from = '2026-09-24T08:00:00Z';
const to = '2026-10-01T08:00:00Z';
const runtime: RuntimeData = {
  from, to, collectedAt: to, engines: null, openAlerts: 0, pendingSignals: null, failedDeliveries: null,
  tasks: { current: {}, completed: { SUCCESS: 10, FAILED: 1 }, qualityFailed: 0, successRate: 10 / 11, trend: [] },
};
const Runtime = ({ data }: { data: RuntimeData }) => {
  const query = useQuery({ queryKey: ['runtime'], queryFn: async () => data, initialData: data, enabled: false });
  return <RuntimeOverview query={query} days={7} onDaysChange={vi.fn()} />;
};
const wrapper = ({ children }: { children: React.ReactNode }) => <MemoryRouter><QueryClientProvider client={new QueryClient()}>{children}</QueryClientProvider></MemoryRouter>;

describe('dashboard analysis states', () => {
  it('shows real result distribution when completed totals exist but time buckets are missing', () => {
    render(<Runtime data={runtime} />, { wrapper });
    expect(screen.getByText('90.9%')).toBeInTheDocument();
    expect(screen.getByText('已完成任务 · 结果分布')).toBeInTheDocument();
    expect(screen.getByText('10 次')).toBeInTheDocument();
    expect(screen.getByText('1 次')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重新加载' })).toBeInTheDocument();
    expect(screen.queryByText(/没有已完成的任务/)).not.toBeInTheDocument();
  });

  it('explains a zero-completion window without inventing a success rate', () => {
    render(<Runtime data={{ ...runtime, tasks: { current: { RUNNING: 2 }, completed: {}, qualityFailed: 0, successRate: null, trend: [] } }} />, { wrapper });
    expect(screen.getByText('近7天没有已完成的任务')).toBeInTheDocument();
    expect(screen.getByText('—')).toBeInTheDocument();
    expect(screen.queryByText('100.0%')).not.toBeInTheDocument();
  });

  it('shows interval values when hovering the chart, not just the date labels', async () => {
    const metrics: GatewayAccessOverview = {
      fromInclusive: from, toExclusive: to, requestCount: 11, status2xxCount: 10, status3xxCount: 0, status4xxCount: 0, status5xxCount: 1,
      status401Count: 0, status403Count: 0, status429Count: 0, gatewayRejectedCount: 0, gatewayErrorCount: 0, upstreamErrorCount: 1,
      successRate: 10 / 11, clientErrorRate: 0, serverErrorRate: 1 / 11, requestBytes: 0, responseBytes: 0,
      averageRequestLatencyMs: 25, maximumRequestLatencyMs: 80, peakHourlyRequestLatencyP95Ms: 60, peakHourlyRequestLatencyP99Ms: 80,
      averageProxyLatencyMs: null, peakHourlyProxyLatencyP95Ms: null, peakHourlyProxyLatencyP99Ms: null,
    };
    const trendData: GatewayAccessTrend = { fromInclusive: from, toExclusive: to, points: [{
      ...metrics, hourStart: from, peakGroupedRequestLatencyP95Ms: 60, peakGroupedRequestLatencyP99Ms: 80,
      peakGroupedProxyLatencyP95Ms: null, peakGroupedProxyLatencyP99Ms: null,
    }] };
    const usage: GatewayUsage = { from, to, collectedAt: to, collectionEnabled: true, hasSamples: true, activeServices: 2, activeConsumers: 1, successCount: 10, serverErrorCount: 1, latestRecordedHour: from };
    const Usage = () => {
      const overview = useQuery({ queryKey: ['overview'], queryFn: async () => metrics, initialData: metrics, enabled: false });
      const trend = useQuery({ queryKey: ['trend'], queryFn: async () => trendData, initialData: trendData, enabled: false });
      const query = useQuery({ queryKey: ['usage'], queryFn: async () => usage, initialData: usage, enabled: false });
      return <ServiceUsage overview={overview} trend={trend} query={query} />;
    };
    render(<Usage />, { wrapper });
    expect(screen.getByText('平均响应耗时')).toBeInTheDocument();
    const point = screen.getByRole('button', { name: /请求 11 次，5xx错误 1 次/ });
    fireEvent.mouseEnter(point);
    await waitFor(() => expect(screen.getByText('请求总数：11 次')).toBeVisible());
    expect(screen.getByText('5xx错误：1 次')).toBeVisible();
  });
});
