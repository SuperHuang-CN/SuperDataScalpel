import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../shared/api/http';
import type { ServiceEngineDataSourceRegistration } from '../model/serviceEngine';
import type { DataSourcePoolMonitor } from '../model/serviceEngineMonitoring';

const hooks = vi.hoisted(() => ({ monitor: vi.fn() }));
vi.mock('../hooks/useServiceEngineMonitoring', () => ({ useDataSourcePoolMonitor: hooks.monitor }));

import { ServiceEnginePoolMonitorDrawer } from './ServiceEnginePoolMonitorDrawer';

const registration: ServiceEngineDataSourceRegistration = {
  id: 'registration', engineId: 'engine', engineCode: 'engine_test', engineName: '测试引擎',
  dataSourceId: 'source', dataSourceCode: 'jdbc_test', dataSourceName: '测试数据源', databaseType: 'CLICKHOUSE',
  status: 'READY', synchronizedAt: null, lastError: null, createdAt: '2026-09-06T00:00:00Z', updatedAt: '2026-09-06T00:00:00Z',
};

const snapshot: DataSourcePoolMonitor = {
  engineCode: 'engine_test', dataSourceId: 'source', status: 'AVAILABLE', capturedAt: '2026-09-06T00:00:00Z',
  pool: {
    poolName: 'jdbc_test', maximum: 10, total: 2, active: 1, idle: 1, waiting: 0, utilizationPercent: 10,
    acquisitionTimeoutCount: 0, lastSaturationAt: null, executingConnections: 1, idleInTransactionConnections: 0,
    borrowedIdleConnections: 0, longRunningQueryCount: 0, longHeldConnectionCount: 0,
  },
  topConsumers: [], activeConnections: [], recentSql: [], incidents: [],
};

const result = (data: DataSourcePoolMonitor, error?: ApiError) => ({
  data, error, isError: Boolean(error), isPending: false, isFetching: false, refetch: vi.fn(),
});

describe('ServiceEnginePoolMonitorDrawer', () => {
  beforeEach(() => hooks.monitor.mockReset().mockReturnValue(result(snapshot)));
  afterEach(cleanup);

  it('shows diagnostics and enables polling only when explicitly selected', async () => {
    const user = userEvent.setup();
    render(<ServiceEnginePoolMonitorDrawer registration={registration} onClose={vi.fn()} />);
    expect(hooks.monitor).toHaveBeenLastCalledWith('registration', false);
    expect(screen.getByText('使用中 / 上限')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '最近 SQL' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '饱和事件 (0)' })).toBeInTheDocument();
    await user.click(screen.getByRole('switch', { name: '自动刷新 JDBC 监控' }));
    expect(hooks.monitor).toHaveBeenLastCalledWith('registration', true);
    await user.click(screen.getByRole('tab', { name: '最近 SQL' }));
    expect(screen.getByText('最近执行窗口')).toBeInTheDocument();
  });

  it.each(['NOT_LOADED', 'UNSUPPORTED'] as const)('never presents zero-valued healthy metrics for %s', (status) => {
    hooks.monitor.mockReturnValue(result({ ...snapshot, status, pool: null }));
    render(<ServiceEnginePoolMonitorDrawer registration={registration} onClose={vi.fn()} />);
    expect(screen.queryByText('使用中 / 上限')).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: '最近 SQL' })).not.toBeInTheDocument();
    expect(screen.getByText(status === 'NOT_LOADED' ? '运行时连接池未加载' : '监控未启用或连接池不支持')).toBeInTheDocument();
  });

  it('labels a stale snapshot when refresh fails and offers retry', () => {
    hooks.monitor.mockReturnValue(result(snapshot, new ApiError('引擎暂不可用', 502)));
    render(<ServiceEnginePoolMonitorDrawer registration={registration} onClose={vi.fn()} />);
    expect(screen.getByText('监控刷新失败，以下为上次快照')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /重\s*试/ })).toBeInTheDocument();
    expect(screen.getByText('使用中 / 上限')).toBeInTheDocument();
  });
});
