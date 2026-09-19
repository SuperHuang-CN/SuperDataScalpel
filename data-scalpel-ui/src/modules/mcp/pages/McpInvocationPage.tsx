import { ReloadOutlined } from '@ant-design/icons';
import { Button, Input, Space, Table, Tag, Tooltip, Typography, type TableColumnsType } from 'antd';
import { useMemo, useState } from 'react';
import { useMcpInvocationOverview, useMcpInvocations } from '../hooks/useMcp';
import type { McpInvocation } from '../model/mcp';
import './mcp.css';

const escape = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

const columns: TableColumnsType<McpInvocation> = [
  { title: '时间', dataIndex: 'startedAt', width: 190 },
  { title: 'Server', dataIndex: 'serverCode', render: value => value || '—' },
  { title: 'Tool', dataIndex: 'toolCode', render: value => value || '—' },
  { title: '访问凭证', key: 'accessToken', render: (_, item) => item.accessTokenName ? <div><span>{item.accessTokenName}</span><div className="mcp-secondary">r{item.tokenRevision}</div></div> : '—' },
  { title: '方法', dataIndex: 'invocationType' },
  { title: '版本', dataIndex: 'releaseVersion', width: 80, render: value => value ? 'v' + value : '—' },
  { title: '状态', dataIndex: 'status', width: 100, render: value => <Tag color={value === 'SUCCESS' ? 'success' : 'error'}>{value}</Tag> },
  { title: '耗时', dataIndex: 'durationMillis', width: 100, align: 'right', render: value => value + ' ms' },
  { title: '错误分类', dataIndex: 'errorSummary', ellipsis: true },
];

export function McpInvocationPage() {
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const search = useMemo(() => appliedKeyword.trim()
    ? '(serverCode:*"' + escape(appliedKeyword.trim()) + '"* OR toolCode:*"' + escape(appliedKeyword.trim()) + '"* OR accessTokenName:*"' + escape(appliedKeyword.trim()) + '"*)'
    : undefined, [appliedKeyword]);
  const query = useMcpInvocations({ page, size, sort: '-startedAt', search });
  const overview = useMcpInvocationOverview();
  const statistics = overview.data;
  const apply = () => { setAppliedKeyword(keyword); setPage(0); };
  const reset = () => { setKeyword(''); setAppliedKeyword(''); setPage(0); };

  return <section className="management-workbench mcp-management-workbench">
    <div className="management-filter-strip">
      <Input autoComplete="off" allowClear value={keyword} onChange={event => setKeyword(event.target.value)}
        onPressEnter={apply} placeholder="Server、Tool 或访问凭证" style={{ width: 320 }} />
      <Space style={{ marginLeft: 'auto' }}>
        <Button type="primary" onClick={apply}>查询</Button>
        {(keyword || appliedKeyword) && <Button type="text" onClick={reset}>重置</Button>}
      </Space>
    </div>
    <div className="management-results-surface">
      <div className="management-result-toolbar">
        <div className="management-result-title">调用日志 <span className="management-result-count">共 {query.data?.totalElements ?? 0} 条</span></div>
        <Tooltip title="刷新调用日志与统计"><Button type="text" aria-label="刷新调用日志与统计" icon={<ReloadOutlined />}
          onClick={() => void Promise.all([query.refetch(), overview.refetch()])} /></Tooltip>
      </div>
      <Space wrap style={{ padding: '8px 12px' }}>
        <Typography.Text type="secondary">24 小时协议请求：{statistics?.total ?? '—'}</Typography.Text>
        <Typography.Text type="secondary">成功：{statistics?.succeeded ?? '—'}</Typography.Text>
        <Typography.Text type="secondary">失败：{statistics?.failed ?? '—'}</Typography.Text>
        <Typography.Text type="secondary">成功率：{statistics ? (statistics.successRate * 100).toFixed(1) + '%' : '—'}</Typography.Text>
        <Typography.Text type="secondary">平均耗时：{statistics ? statistics.averageDurationMillis.toFixed(0) + ' ms' : '—'}</Typography.Text>
      </Space>
      {query.isError && <Space><Typography.Text type="danger">调用日志加载失败：{query.error.message}</Typography.Text>
        <Button onClick={() => void query.refetch()}>重试</Button></Space>}
      {overview.isError && <Space><Typography.Text type="danger">统计加载失败</Typography.Text>
        <Button onClick={() => void overview.refetch()}>重试</Button></Space>}
      <Table className="management-table" size="small" rowKey="id" columns={columns} dataSource={query.data?.content ?? []}
        loading={query.isLoading} scroll={{ y: 'calc(100vh - 350px)' }}
        pagination={{ current: page + 1, pageSize: size, total: query.data?.totalElements ?? 0, showSizeChanger: true,
          onChange: (nextPage, nextSize) => { setPage(nextSize === size ? nextPage - 1 : 0); setSize(nextSize); } }} />
    </div>
  </section>;
}
