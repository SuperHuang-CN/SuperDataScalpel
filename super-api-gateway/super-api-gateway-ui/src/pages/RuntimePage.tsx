import { ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Space, Table, Tabs, Tag, message } from 'antd';
import { api, post } from '../api';
import type { RuntimeSummary } from '../model';

const localTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';

export const RuntimePage = () => {
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ['runtime'], queryFn: () => api<RuntimeSummary>('/runtime'), refetchInterval: 5_000 });
  const telemetry = useQuery({ queryKey: ['telemetry'], queryFn: () => api<{
    traffic: { requestCount: number; serverErrors: number; rejected: number; recentP95Ms: number | null; recentP99Ms: number | null; heapUsedBytes: number; processCpuLoad: number | null; recentCalls: { at: string; requestId: string; serviceCode: string; consumerCode: string | null; status: number; durationMs: number }[] };
    delivery: { enabled: boolean; topic: string; queued: number; capacity: number; dropped: number; failures: number; delivered: number; lastDeliveredAt: string | null };
  }>('/runtime/telemetry'), refetchInterval: 5_000 });
  const reload = useMutation({
    mutationFn: () => post<{ targetRevision: number }>('/runtime/actions/reload'),
    onSuccess: ({ targetRevision }) => {
      message.success(`已触发配置 revision ${targetRevision}`);
      queryClient.invalidateQueries({ queryKey: ['runtime'] });
    },
    onError: (error: Error) => message.error(error.message),
  });
  return (
    <div className="table-page">
      {query.error && <Alert type="error" showIcon message={(query.error as Error).message} />}
      {telemetry.error && <Alert type="error" showIcon title={telemetry.error.message} />}
      {telemetry.data && <Space wrap>
        <Tag>当前连接节点 · 启动以来调用 {telemetry.data.traffic.requestCount}</Tag>
        <Tag>5xx {telemetry.data.traffic.serverErrors} / 拒绝 {telemetry.data.traffic.rejected}</Tag>
        <Tag>近窗 P95 / P99 {telemetry.data.traffic.recentP95Ms?.toFixed(1) ?? '—'} / {telemetry.data.traffic.recentP99Ms?.toFixed(1) ?? '—'} ms</Tag>
        <Tag>堆内存 {(telemetry.data.traffic.heapUsedBytes / 1048576).toFixed(0)} MiB</Tag>
        <Tag>进程 CPU {telemetry.data.traffic.processCpuLoad === null ? '—' : `${(telemetry.data.traffic.processCpuLoad * 100).toFixed(1)}%`}</Tag>
        <Tag color={telemetry.data.delivery.enabled && !telemetry.data.delivery.failures && !telemetry.data.delivery.dropped ? 'blue' : 'warning'}>日志 {telemetry.data.delivery.enabled ? '已启用' : '关闭'} · 排队 {telemetry.data.delivery.queued}/{telemetry.data.delivery.capacity} · 成功 {telemetry.data.delivery.delivered} · 失败 {telemetry.data.delivery.failures} · 丢弃 {telemetry.data.delivery.dropped}</Tag>
      </Space>}
      <div className="toolbar">
        <Space>目标 Revision：<Tag color="blue">{query.data?.targetRevision ?? '—'}</Tag></Space>
        <Button type="primary" icon={<ReloadOutlined />} loading={reload.isPending} onClick={() => reload.mutate()}>重新加载全部节点</Button>
      </div>
      <Tabs items={[{ key: 'nodes', label: '配置与运行节点', children: <Table
        size="small"
        rowKey="id"
        loading={query.isLoading}
        dataSource={[...(query.data?.instances ?? [])].sort((left, right) => Date.parse(right.lastSeenAt) - Date.parse(left.lastSeenAt))}
        pagination={false}
        scroll={{ y: 'calc(100vh - 290px)' }}
        columns={[
          { title: '实例 ID', dataIndex: 'id', ellipsis: true },
          { title: '状态', dataIndex: 'state', width: 110, render: (state: string) => <Tag color={state === 'READY' ? 'success' : 'error'}>{state}</Tag> },
          { title: '已加载 Revision', dataIndex: 'loadedRevision', width: 150, render: (value: number) => <Tag color={value >= (query.data?.targetRevision ?? 0) ? 'success' : 'warning'}>{value}</Tag> },
          { title: '启动时间', dataIndex: 'startedAt', width: 170, render: localTime },
          { title: '最近心跳', dataIndex: 'lastSeenAt', width: 170, render: localTime },
          { title: '版本', dataIndex: 'applicationVersion', width: 120 },
          { title: '最近错误', dataIndex: 'lastError', ellipsis: true, render: (value?: string) => value || '—' },
        ]}
      /> }, { key: 'calls', label: '本节点最近 100 次调用', children: <>
      <small>内存窗口随重启清空，历史统计见 Admin；不含请求正文和凭据。</small>
      <Table rowKey="requestId" size="small" scroll={{ y: 'calc(100vh - 350px)' }} dataSource={telemetry.data?.traffic.recentCalls ?? []} pagination={{ pageSize: 10, showSizeChanger: false }} columns={[
        { title: '时间', dataIndex: 'at', width: 170, render: localTime }, { title: '服务', dataIndex: 'serviceCode', ellipsis: true },
        { title: '消费者', dataIndex: 'consumerCode', ellipsis: true, render: (value: string | null) => value ?? '匿名' },
        { title: '状态码', dataIndex: 'status' }, { title: '耗时(ms)', dataIndex: 'durationMs' },
        { title: 'Request ID', dataIndex: 'requestId', ellipsis: true },
      ]} /></> }]} />
    </div>
  );
};
