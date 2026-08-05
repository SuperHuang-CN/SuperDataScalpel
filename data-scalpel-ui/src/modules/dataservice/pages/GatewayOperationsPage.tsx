import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  LineChartOutlined,
  ReloadOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { Line } from '@ant-design/plots';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Progress,
  Row,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { TableProps } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ManagementCode, ManagementListCell } from '../../../shared/components/ManagementListCells';
import { GatewayAccessLogDetailDrawer } from '../components/GatewayAccessLogDetailDrawer';
import { useApiConsumers } from '../hooks/useApiConsumers';
import { useDataServices } from '../hooks/useDataServices';
import {
  useGatewayAccessLogs,
  useGatewayAccessOverview,
  useGatewayAccessRankings,
  useGatewayAccessTrend,
} from '../hooks/useGatewayAccess';
import { buildApiConsumerSearch } from '../model/apiConsumerSearch';
import { buildDataServiceSearch } from '../model/dataServiceSearch';
import {
  bucketGatewayAccessTrend,
  buildGatewayAccessTimeWindow,
  gatewayAccessRangeLabels,
  gatewayAccessRate,
  type GatewayAccessLog,
  type GatewayAccessRange,
  type GatewayAccessRanking,
  type GatewayAccessTrendMetric,
} from '../model/gatewayAccess';
import './gatewayOperations.css';

const AUTO_REFRESH_MS = 5 * 60 * 1000;
const LOG_PAGE_SIZE = 20;

const compactNumberFormatter = new Intl.NumberFormat('zh-CN', {
  notation: 'compact',
  maximumFractionDigits: 1,
});

const numberFormatter = new Intl.NumberFormat('zh-CN');

const formatCompactNumber = (value: number) => compactNumberFormatter.format(value);
const formatNumber = (value: number) => numberFormatter.format(value);
const formatPercent = (value: number) => `${(value * 100).toFixed(2)}%`;
const formatMilliseconds = (value: number | null) => value === null ? '—' : `${Math.round(value)} ms`;

const formatDateTime = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
  timeZoneName: 'short',
}).format(new Date(value));

const formatChartTime = (value: string, range: GatewayAccessRange) => new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  ...(range === '24h' ? { hour: '2-digit', minute: '2-digit' } : {}),
  hour12: false,
}).format(new Date(value));

const shortId = (value: string | null) => {
  if (!value) return '—';
  return value.length > 16 ? `${value.slice(0, 8)}…${value.slice(-6)}` : value;
};

const requestError = (error: unknown, fallback: string) => (
  error instanceof ApiError || error instanceof Error ? error.message : fallback
);

const useDebouncedValue = (value: string, delay = 300) => {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay);
    return () => window.clearTimeout(timer);
  }, [delay, value]);
  return debounced;
};

interface MetricCardProps {
  title: string;
  value: string;
  hint: string;
  icon: React.ReactNode;
  tone?: 'normal' | 'success' | 'warning';
  loading?: boolean;
}

const MetricCard = ({
  title,
  value,
  hint,
  icon,
  tone = 'normal',
  loading = false,
}: MetricCardProps) => (
  <Card className={`gateway-operations-metric gateway-operations-metric-${tone}`} loading={loading}>
    <div className="gateway-operations-metric-header">
      <Typography.Text type="secondary">{title}</Typography.Text>
      <span className="gateway-operations-metric-icon">{icon}</span>
    </div>
    <div className="gateway-operations-metric-value">{value}</div>
    <Typography.Text type="secondary" className="gateway-operations-metric-hint">{hint}</Typography.Text>
  </Card>
);

const errorOwner = (log: GatewayAccessLog) => {
  if (log.responseStatus === 401) return '调用认证';
  if (log.responseStatus === 403) return '订阅授权';
  if (log.responseStatus === 429) return '网关限流';
  if (log.upstreamError) return '上游服务';
  if (log.gatewayError) return '网关错误';
  if (log.gatewayRejected) return '网关拒绝';
  if (log.responseStatus >= 500) return '服务端未分类';
  return '调用方';
};

const statusColor = (status: number) => {
  if (status >= 500) return 'red';
  if (status >= 400) return 'orange';
  if (status >= 300) return 'blue';
  return 'green';
};

export const GatewayOperationsPage = () => {
  const [range, setRange] = useState<GatewayAccessRange>('24h');
  const [draftRange, setDraftRange] = useState<GatewayAccessRange>('24h');
  const [trendMetric, setTrendMetric] = useState<GatewayAccessTrendMetric>('TRAFFIC');
  const [dataServiceId, setDataServiceId] = useState<string>();
  const [draftDataServiceId, setDraftDataServiceId] = useState<string>();
  const [consumerId, setConsumerId] = useState<string>();
  const [draftConsumerId, setDraftConsumerId] = useState<string>();
  const [selectedServiceLabel, setSelectedServiceLabel] = useState<string>();
  const [draftServiceLabel, setDraftServiceLabel] = useState<string>();
  const [selectedConsumerLabel, setSelectedConsumerLabel] = useState<string>();
  const [draftConsumerLabel, setDraftConsumerLabel] = useState<string>();
  const [serviceKeyword, setServiceKeyword] = useState('');
  const [consumerKeyword, setConsumerKeyword] = useState('');
  const [referenceTime, setReferenceTime] = useState(() => new Date());
  const [logPage, setLogPage] = useState(0);
  const [selectedLog, setSelectedLog] = useState<GatewayAccessLog | null>(null);

  const debouncedServiceKeyword = useDebouncedValue(serviceKeyword);
  const debouncedConsumerKeyword = useDebouncedValue(consumerKeyword);
  const timeWindow = useMemo(
    () => buildGatewayAccessTimeWindow(range, referenceTime),
    [range, referenceTime],
  );

  useEffect(() => {
    const timer = window.setInterval(() => setReferenceTime(new Date()), AUTO_REFRESH_MS);
    return () => window.clearInterval(timer);
  }, []);

  const serviceOptionsQuery = useDataServices({
    search: buildDataServiceSearch({ keyword: debouncedServiceKeyword }),
    page: 0,
    size: 50,
    sort: 'name',
  });
  const consumerOptionsQuery = useApiConsumers({
    search: buildApiConsumerSearch({ keyword: debouncedConsumerKeyword }),
    page: 0,
    size: 50,
    sort: 'name',
  });

  const statisticsScope = useMemo(() => ({
    from: timeWindow.from,
    to: timeWindow.to,
    dataServiceId,
    consumerId,
  }), [consumerId, dataServiceId, timeWindow.from, timeWindow.to]);

  const overviewQuery = useGatewayAccessOverview(statisticsScope);
  const trendQuery = useGatewayAccessTrend(statisticsScope);
  const serviceRankingQuery = useGatewayAccessRankings({
    ...statisticsScope,
    dimension: 'SERVICE',
    metric: 'REQUEST_COUNT',
    limit: 10,
  });
  const consumerRankingQuery = useGatewayAccessRankings({
    ...statisticsScope,
    dimension: 'CONSUMER',
    metric: 'REQUEST_COUNT',
    limit: 10,
  });
  const logsQuery = useGatewayAccessLogs({
    from: timeWindow.rawFrom,
    to: timeWindow.rawTo,
    dataServiceId,
    consumerId,
    abnormalOnly: true,
    page: logPage,
    size: LOG_PAGE_SIZE,
  });

  const serviceOptions = useMemo(() => {
    const options = (serviceOptionsQuery.data?.content ?? []).map((service) => ({
      value: service.id,
      label: `${service.name} · ${service.code}`,
    }));
    if (draftDataServiceId && draftServiceLabel && !options.some((option) => option.value === draftDataServiceId)) {
      options.unshift({ value: draftDataServiceId, label: draftServiceLabel });
    }
    return options;
  }, [draftDataServiceId, draftServiceLabel, serviceOptionsQuery.data?.content]);

  const consumerOptions = useMemo(() => {
    const options = (consumerOptionsQuery.data?.content ?? []).map((consumer) => ({
      value: consumer.id,
      label: `${consumer.name} · ${consumer.code}`,
    }));
    if (draftConsumerId && draftConsumerLabel && !options.some((option) => option.value === draftConsumerId)) {
      options.unshift({ value: draftConsumerId, label: draftConsumerLabel });
    }
    return options;
  }, [consumerOptionsQuery.data?.content, draftConsumerId, draftConsumerLabel]);

  const overview = overviewQuery.data;
  const qps = overview
    ? overview.requestCount / Math.max(
      1,
      (new Date(overview.toExclusive).getTime() - new Date(overview.fromInclusive).getTime()) / 1000,
    )
    : 0;

  const trendBuckets = useMemo(
    () => bucketGatewayAccessTrend(trendQuery.data?.points ?? [], timeWindow),
    [timeWindow, trendQuery.data?.points],
  );
  const trendHasRequests = trendBuckets.some((bucket) => bucket.requestCount > 0);
  const chartData = useMemo(() => trendBuckets.flatMap((bucket) => {
    if (trendMetric === 'TRAFFIC') {
      return [
        { time: bucket.start, series: '调用次数', value: bucket.requestCount },
        { time: bucket.start, series: '5xx 次数', value: bucket.status5xxCount },
      ];
    }
    if (trendMetric === 'ERROR_RATE') {
      return [
        {
          time: bucket.start,
          series: '5xx 错误率',
          value: gatewayAccessRate(bucket.status5xxCount, bucket.requestCount) * 100,
        },
        {
          time: bucket.start,
          series: '4xx 错误率',
          value: gatewayAccessRate(bucket.status4xxCount, bucket.requestCount) * 100,
        },
      ];
    }
    return [
      ...(bucket.peakRequestLatencyP95Ms === null ? [] : [{
        time: bucket.start,
        series: 'P95 分组峰值',
        value: bucket.peakRequestLatencyP95Ms,
      }]),
      ...(bucket.peakRequestLatencyP99Ms === null ? [] : [{
        time: bucket.start,
        series: 'P99 分组峰值',
        value: bucket.peakRequestLatencyP99Ms,
      }]),
    ];
  }), [trendBuckets, trendMetric]);

  const chartValueFormatter = (value: number) => {
    if (trendMetric === 'ERROR_RATE') return `${value.toFixed(2)}%`;
    if (trendMetric === 'LATENCY') return `${Math.round(value)} ms`;
    return formatCompactNumber(value);
  };

  const trendSubtitle = range === '24h'
    ? '按小时'
    : range === '7d' ? '每 6 小时' : '按 24 小时';
  const trendTitle = {
    TRAFFIC: '调用趋势',
    ERROR_RATE: '错误率趋势',
    LATENCY: '延迟趋势',
  }[trendMetric];
  const metricOptions = [
    { label: '调用量', value: 'TRAFFIC' },
    { label: '错误率', value: 'ERROR_RATE' },
    { label: '延迟', value: 'LATENCY' },
  ];

  const selectServiceFromRanking = (ranking: GatewayAccessRanking) => {
    setDataServiceId(ranking.subjectId);
    setDraftDataServiceId(ranking.subjectId);
    const label = `${ranking.subjectName ?? shortId(ranking.subjectId)} · ${ranking.subjectCode ?? shortId(ranking.subjectId)}`;
    setSelectedServiceLabel(label);
    setDraftServiceLabel(label);
    setLogPage(0);
  };

  const selectConsumerFromRanking = (ranking: GatewayAccessRanking) => {
    setConsumerId(ranking.subjectId);
    setDraftConsumerId(ranking.subjectId);
    const label = `${ranking.subjectName ?? shortId(ranking.subjectId)} · ${ranking.subjectCode ?? shortId(ranking.subjectId)}`;
    setSelectedConsumerLabel(label);
    setDraftConsumerLabel(label);
    setLogPage(0);
  };

  const rankingIdentity = (
    ranking: GatewayAccessRanking,
    onClick: (value: GatewayAccessRanking) => void,
  ) => (
    <ManagementListCell
      primary={<Button type="link" className="gateway-operations-ranking-link" onClick={() => onClick(ranking)}>{ranking.subjectName ?? `已删除主体 ${shortId(ranking.subjectId)}`}</Button>}
      secondary={<ManagementCode value={ranking.subjectCode ?? shortId(ranking.subjectId)} />}
    />
  );

  const serviceRankingColumns: TableProps<GatewayAccessRanking>['columns'] = [
    {
      title: '服务',
      key: 'subject',
      width: 210,
      render: (_, ranking) => rankingIdentity(ranking, selectServiceFromRanking),
    },
    {
      title: '调用量',
      dataIndex: 'requestCount',
      align: 'right',
      width: 95,
      render: formatCompactNumber,
    },
    {
      title: '成功率',
      key: 'successRate',
      align: 'right',
      width: 90,
      render: (_, ranking) => formatPercent(
        gatewayAccessRate(ranking.status2xxCount, ranking.requestCount),
      ),
    },
    {
      title: 'P95',
      dataIndex: 'peakHourlyRequestLatencyP95Ms',
      align: 'right',
      width: 90,
      render: formatMilliseconds,
    },
    {
      title: '5xx',
      dataIndex: 'status5xxCount',
      align: 'right',
      width: 80,
      render: formatCompactNumber,
    },
  ];

  const consumerRankingColumns: TableProps<GatewayAccessRanking>['columns'] = [
    {
      title: '消费者',
      key: 'subject',
      width: 210,
      render: (_, ranking) => rankingIdentity(ranking, selectConsumerFromRanking),
    },
    {
      title: '调用量',
      dataIndex: 'requestCount',
      align: 'right',
      width: 95,
      render: formatCompactNumber,
    },
    {
      title: '成功率',
      key: 'successRate',
      align: 'right',
      width: 90,
      render: (_, ranking) => formatPercent(
        gatewayAccessRate(ranking.status2xxCount, ranking.requestCount),
      ),
    },
    {
      title: 'P95',
      dataIndex: 'peakHourlyRequestLatencyP95Ms',
      align: 'right',
      width: 90,
      render: formatMilliseconds,
    },
    {
      title: '4xx',
      dataIndex: 'status4xxCount',
      align: 'right',
      width: 80,
      render: formatCompactNumber,
    },
  ];

  const logColumns: TableProps<GatewayAccessLog>['columns'] = [
    {
      title: '请求', width: 320,
      render: (_value: string, log) => (
        <ManagementListCell primary={<><Tag>{log.requestMethod}</Tag> <ManagementCode value={log.requestPath} /></>} secondary={<Tooltip title={log.gatewayRequestId}><Button type="link" className="gateway-operations-request-link" onClick={() => setSelectedLog(log)}>{shortId(log.gatewayRequestId)}</Button></Tooltip>} />
      ),
    },
    {
      title: '调用主体', width: 250,
      render: (_: unknown, log) => <ManagementListCell primary={log.dataServiceName ?? log.dataServiceCode ?? shortId(log.dataServiceId)} secondary={log.consumerName ?? log.consumerCode ?? '匿名 / 未识别'} />,
    },
    {
      title: '响应', width: 150,
      render: (_: unknown, log) => <ManagementListCell primary={<Tag color={statusColor(log.responseStatus)}>{log.responseStatus}</Tag>} secondary={errorOwner(log)} />,
    },
    {
      title: '延迟', width: 160, align: 'right',
      render: (_: unknown, log) => <ManagementListCell primary={formatMilliseconds(log.requestLatencyMs)} secondary={`网关 ${formatMilliseconds(log.kongLatencyMs)} · 代理 ${formatMilliseconds(log.proxyLatencyMs)}`} />,
    },
    {
      title: '时间 / 网关', width: 190,
      render: (_: unknown, log) => <ManagementListCell primary={formatDateTime(log.occurredAt)} secondary={log.gatewayProvider} />,
    },
  ];

  const resetFilters = () => {
    setDraftRange('24h');
    setRange('24h');
    setDraftDataServiceId(undefined);
    setDataServiceId(undefined);
    setDraftConsumerId(undefined);
    setConsumerId(undefined);
    setDraftServiceLabel(undefined);
    setSelectedServiceLabel(undefined);
    setDraftConsumerLabel(undefined);
    setSelectedConsumerLabel(undefined);
    setServiceKeyword('');
    setConsumerKeyword('');
    setTrendMetric('TRAFFIC');
    setReferenceTime(new Date());
    setLogPage(0);
  };

  const applyFilters = () => {
    setRange(draftRange);
    setDataServiceId(draftDataServiceId);
    setConsumerId(draftConsumerId);
    setSelectedServiceLabel(draftServiceLabel);
    setSelectedConsumerLabel(draftConsumerLabel);
    setReferenceTime(new Date());
    setLogPage(0);
  };

  const refreshAll = async () => {
    setReferenceTime(new Date());
    await Promise.all([
      overviewQuery.refetch(),
      trendQuery.refetch(),
      serviceRankingQuery.refetch(),
      consumerRankingQuery.refetch(),
      logsQuery.refetch(),
    ]);
  };

  const refreshing = [
    overviewQuery,
    trendQuery,
    serviceRankingQuery,
    consumerRankingQuery,
    logsQuery,
  ].some((query) => query.isFetching);

  const scopeLabel = [
    selectedServiceLabel?.split(' · ')[0] ?? '全部服务',
    selectedConsumerLabel?.split(' · ')[0] ?? '全部消费者',
    timeWindow.label,
  ].join(' · ');
  const hasFilters = draftRange !== '24h'
    || range !== '24h'
    || Boolean(draftDataServiceId || dataServiceId || draftConsumerId || consumerId);

  const renderSectionError = (error: unknown, retry: () => void, fallback: string) => (
    <Alert
      showIcon
      type="error"
      message={requestError(error, fallback)}
      action={<Button onClick={retry}>重试</Button>}
    />
  );

  return (
    <div className="gateway-operations-page">
      <div className="management-filter-strip gateway-operations-filter-strip">
        <div className="gateway-operations-filter-row">
          <div className="gateway-operations-filter-item gateway-operations-range-filter">
            <Typography.Text type="secondary">时间范围</Typography.Text>
            <Segmented
              value={draftRange}
              options={Object.entries(gatewayAccessRangeLabels).map(([value, label]) => ({ value, label }))}
              onChange={(value) => setDraftRange(value as GatewayAccessRange)}
            />
          </div>
          <div className="gateway-operations-filter-item">
            <Typography.Text type="secondary">数据服务</Typography.Text>
            <Select
              allowClear
              showSearch
              filterOption={false}
              placeholder="全部服务"
              value={draftDataServiceId}
              options={serviceOptions}
              loading={serviceOptionsQuery.isFetching}
              onSearch={setServiceKeyword}
              onClear={() => {
                setDraftDataServiceId(undefined);
                setDraftServiceLabel(undefined);
              }}
              onChange={(value) => {
                setDraftDataServiceId(value);
                setDraftServiceLabel(serviceOptions.find((option) => option.value === value)?.label);
              }}
            />
          </div>
          <div className="gateway-operations-filter-item">
            <Typography.Text type="secondary">API 消费者</Typography.Text>
            <Select
              allowClear
              showSearch
              filterOption={false}
              placeholder="全部消费者"
              value={draftConsumerId}
              options={consumerOptions}
              loading={consumerOptionsQuery.isFetching}
              onSearch={setConsumerKeyword}
              onClear={() => {
                setDraftConsumerId(undefined);
                setDraftConsumerLabel(undefined);
              }}
              onChange={(value) => {
                setDraftConsumerId(value);
                setDraftConsumerLabel(consumerOptions.find((option) => option.value === value)?.label);
              }}
            />
          </div>
          <div className="gateway-operations-filter-actions">
            <Button type="primary" loading={refreshing} onClick={applyFilters}>查询</Button>
            {hasFilters && <Button type="text" onClick={resetFilters}>重置</Button>}
            <Tooltip title="刷新全部统计"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新全部统计" loading={refreshing} onClick={() => void refreshAll()} /></Tooltip>
          </div>
        </div>
      </div>

      <div className="gateway-operations-scope">
        <Typography.Text>
          当前范围：<Typography.Text strong>{scopeLabel}</Typography.Text>
        </Typography.Text>
        <Typography.Text type="secondary">
          小时统计截至 {formatDateTime(timeWindow.to)}；明细截至当前
        </Typography.Text>
      </div>

      {overviewQuery.isError && renderSectionError(
        overviewQuery.error,
        () => void overviewQuery.refetch(),
        '网关访问概览加载失败',
      )}

      <Row gutter={[12, 12]}>
        <Col xs={24} sm={12} xl={6}>
          <MetricCard
            title="调用总量"
            value={overview ? formatCompactNumber(overview.requestCount) : '—'}
            hint={overview ? `窗口平均 ${qps.toFixed(2)} QPS` : '等待小时统计'}
            icon={<LineChartOutlined />}
            loading={overviewQuery.isLoading}
          />
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <MetricCard
            title="2xx 成功率"
            value={overview ? formatPercent(overview.successRate) : '—'}
            hint={overview ? `${formatNumber(overview.status2xxCount)} 次成功响应` : '等待小时统计'}
            icon={<CheckCircleOutlined />}
            tone="success"
            loading={overviewQuery.isLoading}
          />
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <MetricCard
            title="请求延迟 P95"
            value={formatMilliseconds(overview?.peakHourlyRequestLatencyP95Ms ?? null)}
            hint={`P99 ${formatMilliseconds(overview?.peakHourlyRequestLatencyP99Ms ?? null)} · 小时峰值`}
            icon={<ClockCircleOutlined />}
            loading={overviewQuery.isLoading}
          />
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <MetricCard
            title="5xx 错误率"
            value={overview ? formatPercent(overview.serverErrorRate) : '—'}
            hint={overview ? `${formatNumber(overview.status5xxCount)} 次服务端错误` : '等待小时统计'}
            icon={<WarningOutlined />}
            tone="warning"
            loading={overviewQuery.isLoading}
          />
        </Col>
      </Row>

      <div className="gateway-operations-overview-grid">
        <Card
          title={(
            <Space orientation="vertical" size={0}>
              <Typography.Text strong>{trendTitle}</Typography.Text>
              <Typography.Text type="secondary" className="gateway-operations-section-subtitle">
                {trendSubtitle} · P95/P99 为底层小时分组峰值
              </Typography.Text>
            </Space>
          )}
          extra={(
            <Segmented
              value={trendMetric}
              options={metricOptions}
              onChange={(value) => setTrendMetric(value as GatewayAccessTrendMetric)}
            />
          )}
          className="gateway-operations-chart-card"
          loading={trendQuery.isLoading}
        >
          {trendQuery.isError
            ? renderSectionError(
              trendQuery.error,
              () => void trendQuery.refetch(),
              '调用趋势加载失败',
            )
            : !trendHasRequests
              ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前范围没有已归档的调用数据" />
              : trendMetric === 'LATENCY' && chartData.length === 0
                ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前范围没有延迟样本" />
                : (
                  <Line
                    data={chartData}
                    xField="time"
                    yField="value"
                    colorField="series"
                    height={286}
                    autoFit
                    connectNulls={false}
                    scale={{ color: { range: ['#1668dc', '#d4380d'] } }}
                    axis={{
                      x: {
                        title: false,
                        labelFormatter: (value: string) => formatChartTime(value, range),
                      },
                      y: {
                        title: false,
                        labelFormatter: (value: number) => chartValueFormatter(Number(value)),
                      },
                    }}
                    legend={{ color: { position: 'bottom' } }}
                    tooltip={{
                      title: (datum: { time: string }) => formatDateTime(datum.time),
                      items: [{ channel: 'y', valueFormatter: chartValueFormatter }],
                    }}
                    style={{ lineWidth: 2 }}
                  />
                )}
        </Card>

        <Card title="响应与错误构成" className="gateway-operations-breakdown-card" loading={overviewQuery.isLoading}>
          <Space orientation="vertical" size={16} className="gateway-operations-breakdown">
            <div>
              <div className="gateway-operations-breakdown-title">
                <Typography.Text strong>响应构成</Typography.Text>
                <Typography.Text type="secondary">当前筛选范围</Typography.Text>
              </div>
              {[
                { label: '2xx 成功', count: overview?.status2xxCount ?? 0, color: '#389e0d' },
                { label: '4xx 调用方错误', count: overview?.status4xxCount ?? 0, color: '#d48806' },
                { label: '5xx 服务端错误', count: overview?.status5xxCount ?? 0, color: '#cf1322' },
              ].map((item) => {
                const rate = gatewayAccessRate(item.count, overview?.requestCount ?? 0);
                return (
                  <div key={item.label} className="gateway-operations-progress-row">
                    <div>
                      <span>{item.label}</span>
                      <strong>{formatPercent(rate)}</strong>
                    </div>
                    <Progress percent={rate * 100} showInfo={false} strokeColor={item.color} size="small" />
                  </div>
                );
              })}
            </div>
            <div>
              <div className="gateway-operations-breakdown-title">
                <Typography.Text strong>关键异常</Typography.Text>
                <Typography.Text type="secondary">按责任边界拆分</Typography.Text>
              </div>
              {[
                ['401 未认证', overview?.status401Count ?? 0],
                ['403 未订阅', overview?.status403Count ?? 0],
                ['429 网关限流', overview?.status429Count ?? 0],
                ['网关自身 5xx', overview?.gatewayErrorCount ?? 0],
                ['上游服务 5xx', overview?.upstreamErrorCount ?? 0],
              ].map(([label, count]) => (
                <div key={String(label)} className="gateway-operations-error-row">
                  <span>{label}</span>
                  <strong>{formatNumber(Number(count))}</strong>
                </div>
              ))}
            </div>
          </Space>
        </Card>
      </div>

      <div className="gateway-operations-ranking-grid">
        <Card
          title={(
            <Space orientation="vertical" size={0}>
              <Typography.Text strong>服务调用排行</Typography.Text>
              <Typography.Text type="secondary" className="gateway-operations-section-subtitle">
                点击服务可联动全页筛选
              </Typography.Text>
            </Space>
          )}
          extra={<Typography.Text type="secondary">按调用量</Typography.Text>}
        >
          {serviceRankingQuery.isError && renderSectionError(
            serviceRankingQuery.error,
            () => void serviceRankingQuery.refetch(),
            '服务排行加载失败',
          )}
          {!serviceRankingQuery.isError && (
            <Table
              className="management-stat-table"
              rowKey="subjectId"
              size="small"
              columns={serviceRankingColumns}
              dataSource={serviceRankingQuery.data ?? []}
              loading={serviceRankingQuery.isLoading}
              pagination={false}
              scroll={{ y: 320 }}
            />
          )}
        </Card>

        <Card
          title={(
            <Space orientation="vertical" size={0}>
              <Typography.Text strong>消费者调用排行</Typography.Text>
              <Typography.Text type="secondary" className="gateway-operations-section-subtitle">
                识别调用主体与调用方异常
              </Typography.Text>
            </Space>
          )}
          extra={<Typography.Text type="secondary">按调用量</Typography.Text>}
        >
          {consumerRankingQuery.isError && renderSectionError(
            consumerRankingQuery.error,
            () => void consumerRankingQuery.refetch(),
            '消费者排行加载失败',
          )}
          {!consumerRankingQuery.isError && (
            <Table
              className="management-stat-table"
              rowKey="subjectId"
              size="small"
              columns={consumerRankingColumns}
              dataSource={consumerRankingQuery.data ?? []}
              loading={consumerRankingQuery.isLoading}
              pagination={false}
              scroll={{ y: 320 }}
            />
          )}
        </Card>
      </div>

      <Card
        title={(
          <Space orientation="vertical" size={0}>
            <Typography.Text strong>最近异常调用</Typography.Text>
            <Typography.Text type="secondary" className="gateway-operations-section-subtitle">
              {range === '30d'
                ? '统计范围为 30 天，原始异常明细仍只保留并展示最近 7 天'
                : '原始明细最多保留 7 天；点击 Request ID 查看调用详情'}
            </Typography.Text>
          </Space>
        )}
        extra={<Typography.Text type="secondary">{formatNumber(logsQuery.data?.totalElements ?? 0)} 条</Typography.Text>}
      >
        {logsQuery.isError && renderSectionError(
          logsQuery.error,
          () => void logsQuery.refetch(),
          '异常调用明细加载失败',
        )}
        {!logsQuery.isError && (
          <Table
            className="management-stat-table"
            rowKey="id"
            size="small"
            columns={logColumns}
            dataSource={logsQuery.data?.content ?? []}
            loading={logsQuery.isLoading}
            scroll={{ y: 420 }}
            pagination={{
              current: logPage + 1,
              pageSize: LOG_PAGE_SIZE,
              total: logsQuery.data?.totalElements ?? 0,
              showSizeChanger: false,
              showTotal: (total) => `共 ${formatNumber(total)} 条`,
              onChange: (page) => setLogPage(page - 1),
            }}
          />
        )}
      </Card>

      <GatewayAccessLogDetailDrawer
        log={selectedLog}
        open={selectedLog !== null}
        onClose={() => setSelectedLog(null)}
      />
    </div>
  );
};
