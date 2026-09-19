import { DashboardOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Drawer, Empty, Space, Spin, Switch, Table, Tabs, Tag, Tooltip, Typography } from 'antd';
import type { TableProps } from 'antd';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { DetailTableToolbar } from '../../../shared/components/DetailTableToolbar';
import { ManagementDateTime, ManagementListCell } from '../../../shared/components/ManagementListCells';
import { useDataSourcePoolMonitor } from '../hooks/useServiceEngineMonitoring';
import type { ServiceEngineDataSourceRegistration } from '../model/serviceEngine';
import {
  monitorDuration, monitorNumber, monitorTime, poolUnavailableLabels,
  type JdbcActiveConnection, type JdbcPoolSummary, type JdbcRecentSql,
  type JdbcSaturationIncident, type JdbcSqlConsumer,
} from '../model/serviceEngineMonitoring';
import './serviceEngineMonitoring.css';

const connectionStates: Record<string, string> = {
  EXECUTING: '执行 SQL', IDLE_IN_TRANSACTION: '事务中空闲', BORROWED_IDLE: '借出未执行',
};
const incidentReasons: Record<string, string> = {
  POOL_FULL: '连接池已满', WAITING_FOR_CONNECTION: '等待连接', ACQUIRE_TIMEOUT: '获取连接超时',
  SLOW_ACQUIRE: '获取连接缓慢', POOL_SATURATED: '连接池饱和',
};

const SqlPreview = ({ value }: { value: string | null }) => (
  <Typography.Paragraph className="jdbc-monitor-sql-preview" ellipsis={{ rows: 2, tooltip: value }}>
    {value || '—'}
  </Typography.Paragraph>
);

const SqlDetail = ({ value }: { value: string | null }) => (
  <pre className="jdbc-monitor-sql-detail">{value || '没有 SQL 记录'}</pre>
);

/** Each table paginates the bounded snapshot locally; polling never changes the selected page. */
const MonitorTable = <T extends object,>({ title, rows, ...props }: {
  title: string;
  rows: T[];
} & Pick<TableProps<T>, 'columns' | 'rowKey' | 'expandable'>) => {
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const current = Math.min(page, Math.max(1, Math.ceil(rows.length / pageSize)));
  return <section className="jdbc-monitor-table">
    <DetailTableToolbar title={title} total={rows.length} current={current} pageSize={pageSize}
      onChange={(nextPage, nextSize) => { setPage(nextPage); setPageSize(nextSize); }} />
    <Table<T> {...props} className="management-table" size="small" tableLayout="fixed"
      dataSource={rows.slice((current - 1) * pageSize, current * pageSize)} pagination={false}
      scroll={{ x: 780 }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前快照暂无记录" /> }} />
  </section>;
};

const consumerColumns: TableProps<JdbcSqlConsumer>['columns'] = [
  { title: 'SQL / 操作', render: (_, row) => <ManagementListCell primary={<SqlPreview value={row.sqlPreview} />} secondary={row.operation ?? '—'} /> },
  { title: '占用连接', width: 100, align: 'right', dataIndex: 'connectionCount', render: monitorNumber },
  { title: '最长占用', width: 115, align: 'right', dataIndex: 'maxHeldMs', render: monitorDuration },
  { title: '最长执行', width: 115, align: 'right', dataIndex: 'maxExecutingMs', render: monitorDuration },
];

const Consumers = ({ rows }: { rows: JdbcSqlConsumer[] }) => <MonitorTable<JdbcSqlConsumer>
  title="SQL 连接占用" rows={rows} rowKey={(row) => row.fingerprint ?? 'unknown'} columns={consumerColumns}
  expandable={{ expandedRowRender: (row) => <>
    <SqlDetail value={row.sqlPreview} />
    <Typography.Text type="secondary">指纹：{row.fingerprint ?? '—'} · 合计占用：{monitorDuration(row.totalHeldMs)}</Typography.Text>
  </> }}
/>;

const connectionColumns: TableProps<JdbcActiveConnection>['columns'] = [
  { title: '状态 / 标记', width: 155, render: (_, row) => <ManagementListCell
    primary={<Tag color={row.longRunning || row.longHeld ? 'warning' : row.state === 'EXECUTING' ? 'processing' : 'default'}>{connectionStates[row.state] ?? row.state}</Tag>}
    secondary={[row.longRunning && '长 SQL', row.longHeld && '长占用'].filter(Boolean).join(' · ') || '—'} /> },
  { title: '当前 / 最近 SQL', render: (_, row) => <SqlPreview value={row.sqlPreview || row.lastSqlPreview} /> },
  { title: '占用 / 执行', width: 130, align: 'right', render: (_, row) => <ManagementListCell primary={monitorDuration(row.heldMs)} secondary={monitorDuration(row.executingMs)} /> },
  { title: '调用方', width: 170, render: (_, row) => <Tooltip title={row.apiPath || row.apiId || '单表、SQL 服务或无 API 上下文的调用可能无法关联调用方'}>
    <span className="jdbc-monitor-truncate">{row.apiPath || row.apiId || '未知调用方'}</span>
  </Tooltip> },
];

const recentColumns: TableProps<JdbcRecentSql>['columns'] = [
  { title: 'SQL / 操作', render: (_, row) => <ManagementListCell primary={<SqlPreview value={row.sqlPreview} />} secondary={row.operation ?? '—'} /> },
  { title: '次数 / 失败', width: 100, align: 'right', render: (_, row) => <ManagementListCell primary={monitorNumber(row.executions)} secondary={<Typography.Text type={(row.failures ?? 0) > 0 ? 'danger' : 'secondary'}>{monitorNumber(row.failures)} 失败</Typography.Text>} /> },
  { title: '平均 / 最长', width: 130, align: 'right', render: (_, row) => <ManagementListCell
    primary={monitorDuration(row.totalDurationMs != null && row.executions ? Math.round(row.totalDurationMs / row.executions) : null)}
    secondary={monitorDuration(row.maxDurationMs)} /> },
  { title: '最近执行', width: 145, dataIndex: 'lastExecutedAt', render: (value: string | null) => <ManagementDateTime value={value} /> },
];

const incidentColumns: TableProps<JdbcSaturationIncident>['columns'] = [
  { title: '时间', width: 145, dataIndex: 'occurredAt', render: (value: string) => <ManagementDateTime value={value} /> },
  { title: '事件', width: 150, dataIndex: 'reason', render: (value: string) => <Tag color="warning">{incidentReasons[value] ?? value}</Tag> },
  { title: '当时占用 / 等待', width: 145, render: (_, row) => <ManagementListCell primary={`${monitorNumber(row.pool?.active)} / ${monitorNumber(row.pool?.maximum)}`} secondary={`等待 ${monitorNumber(row.pool?.waiting)}`} /> },
  { title: '请求 SQL', render: (_, row) => <SqlPreview value={row.requestedSqlPreview} /> },
];

const PoolMetrics = ({ pool }: { pool: JdbcPoolSummary }) => <>
  <div className="jdbc-monitor-metrics">
    <div><span>使用中 / 上限</span><strong>{monitorNumber(pool.active)} <small>/ {monitorNumber(pool.maximum)}</small></strong>
      <span>利用率 {pool.utilizationPercent == null ? '—' : `${pool.utilizationPercent.toFixed(1)}%`}</span></div>
    <div><span>空闲连接</span><strong>{monitorNumber(pool.idle)}</strong><span>已创建 {monitorNumber(pool.total)}</span></div>
    <div className={(pool.waiting ?? 0) > 0 ? 'jdbc-monitor-risk' : undefined}><span>等待连接的线程</span><strong>{monitorNumber(pool.waiting)}</strong>
      <span>累计获取超时 {monitorNumber(pool.acquisitionTimeoutCount)}</span></div>
    <div className={(pool.longRunningQueryCount ?? 0) + (pool.longHeldConnectionCount ?? 0) > 0 ? 'jdbc-monitor-risk' : undefined}>
      <span>长 SQL / 长占用</span><strong>{monitorNumber(pool.longRunningQueryCount)} <small>/ {monitorNumber(pool.longHeldConnectionCount)}</small></strong>
      <span>按 API Studio 监控阈值判断</span></div>
  </div>
  <div className="jdbc-monitor-states">
    <span>执行 SQL {monitorNumber(pool.executingConnections)}</span>
    <span>事务中空闲 {monitorNumber(pool.idleInTransactionConnections)}</span>
    <span>借出未执行 {monitorNumber(pool.borrowedIdleConnections)}</span>
    <Tooltip title={`连接池：${pool.poolName ?? '—'}；最近饱和：${monitorTime(pool.lastSaturationAt)}`}><span tabIndex={0}>连接池详情</span></Tooltip>
  </div>
</>;

export const ServiceEnginePoolMonitorDrawer = ({ registration, onClose }: {
  registration: ServiceEngineDataSourceRegistration;
  onClose: () => void;
}) => {
  const [autoRefresh, setAutoRefresh] = useState(false);
  const query = useDataSourcePoolMonitor(registration.id, autoRefresh);
  const data = query.data;
  return <Drawer
    rootClassName="business-overlay business-drawer-overlay"
    className="jdbc-monitor-drawer"
    open size="min(1080px, 100vw)" onClose={onClose} destroyOnHidden closable={{ placement: 'end' }}
    title={<div className="service-engine-drawer-title">
      <span className="service-engine-drawer-title-icon" aria-hidden><DashboardOutlined /></span>
      <span className="service-engine-drawer-title-copy"><span>JDBC 监控</span>
        <Typography.Text type="secondary">{registration.dataSourceName} · {registration.dataSourceCode}</Typography.Text>
      </span>
    </div>}
    extra={<Tag>{registration.databaseType ?? 'JDBC'}</Tag>}
    footer={<div className="jdbc-monitor-footer"><Typography.Text type="secondary">{registration.engineName} · 只读诊断</Typography.Text><Button onClick={onClose}>关闭</Button></div>}
  >
    <div className="jdbc-monitor-controls">
      <Space size={4} wrap><Typography.Text type="secondary">{data ? `采样于 ${monitorTime(data.capturedAt)}` : '等待采样'}</Typography.Text>
        <ContextHelp ariaLabel="JDBC 监控统计口径" presentation="popover" content={<>
          <p>读取引擎当前进程内的监控快照，不执行探测 SQL。引擎重启或连接池重建后记录清空。</p>
          <p>最近 SQL 是有限执行窗口的聚合，默认保留最近 200 次执行；饱和事件默认保留 20 条，同原因事件会限流。不是全量审计或长期统计。</p>
          <p>SQL 预览由 API Studio 归一化并截断，不采集 JDBC 参数或结果，但不代表完整的敏感信息脱敏。默认长 SQL 阈值 5 秒、长占用 30 秒，以引擎实际配置为准。</p>
        </>} />
      </Space>
      <Space><span>每 5 秒刷新</span><Switch size="small" checked={autoRefresh} onChange={setAutoRefresh} aria-label="自动刷新 JDBC 监控" />
        <Tooltip title="刷新监控"><Button icon={<ReloadOutlined />} loading={query.isFetching} aria-label="刷新 JDBC 监控" onClick={() => void query.refetch()} /></Tooltip>
      </Space>
    </div>
    {query.isError && <InlineFeedback tone="error" label={data ? '监控刷新失败，以下为上次快照' : 'JDBC 监控不可用'}
      detail={query.error instanceof ApiError ? query.error.message : '读取失败，请检查网络和引擎状态'}
      action={<Button size="small" type="link" onClick={() => void query.refetch()}>重试</Button>} />}
    {query.isPending && <div className="jdbc-monitor-loading"><Spin tip="读取 JDBC 监控"><div /></Spin></div>}
    {data && data.status !== 'AVAILABLE' && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={poolUnavailableLabels[data.status]}>
      <Typography.Text type="secondary">{data.status === 'NOT_LOADED' ? '请检查数据源同步状态或 API Studio 中的加载错误。' : '请确认 API Studio JDBC 监控已启用，且使用受支持的动态 JDBC 连接池。'}</Typography.Text>
    </Empty>}
    {data?.status === 'AVAILABLE' && data.pool && <>
      <PoolMetrics pool={data.pool} />
      <Tabs size="small" items={[
        { key: 'connections', label: `活动连接 (${data.activeConnections.length})`, children: <MonitorTable<JdbcActiveConnection>
          title="当前借出的连接" rows={data.activeConnections} columns={connectionColumns} rowKey="connectionId"
          expandable={{ expandedRowRender: (row) => <div className="jdbc-monitor-expanded">
            <div>连接：{row.connectionId} · 线程：{row.threadName ?? '—'} · 事务：{row.transactionActive ? '开启' : '未开启'}</div>
            <div>借出：{monitorTime(row.borrowedAt)} · SQL 开始：{monitorTime(row.sqlStartedAt)}</div>
            <div>API：{row.apiPath || row.apiId || '未知调用方'} · 执行标识：{row.executionId ?? '—'}</div>
            <SqlDetail value={row.sqlPreview || row.lastSqlPreview} />
          </div> }} /> },
        { key: 'consumers', label: 'SQL 占用', children: <Consumers rows={data.topConsumers} /> },
        { key: 'recent', label: '最近 SQL', children: <MonitorTable<JdbcRecentSql>
          title="最近执行窗口" rows={data.recentSql} columns={recentColumns} rowKey="fingerprint"
          expandable={{ expandedRowRender: (row) => <><SqlDetail value={row.sqlPreview} /><Typography.Text type="secondary">指纹：{row.fingerprint} · 总耗时：{monitorDuration(row.totalDurationMs)}</Typography.Text></> }} /> },
        { key: 'incidents', label: `饱和事件 (${data.incidents.length})`, children: <MonitorTable<JdbcSaturationIncident>
          title="最近饱和事件" rows={data.incidents} columns={incidentColumns} rowKey={(row) => `${row.occurredAt}-${row.reason}`}
          expandable={{ expandedRowRender: (row) => <div className="jdbc-monitor-expanded">
            <Typography.Text type="secondary">请求执行标识：{row.requestedExecutionId ?? '—'}</Typography.Text>
            <SqlDetail value={row.requestedSqlPreview} />
            <Consumers rows={row.topConsumers} />
          </div> }} /> },
      ]} />
    </>}
  </Drawer>;
};
