import { ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Space, Table, Tag, message } from 'antd';
import { api, post } from '../api';
import type { RuntimeSummary } from '../model';

export const RuntimePage = () => {
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ['runtime'], queryFn: () => api<RuntimeSummary>('/runtime'), refetchInterval: 5_000 });
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
      <div className="toolbar">
        <Space>目标 Revision：<Tag color="blue">{query.data?.targetRevision ?? '—'}</Tag></Space>
        <Button type="primary" icon={<ReloadOutlined />} loading={reload.isPending} onClick={() => reload.mutate()}>重新加载全部节点</Button>
      </div>
      <Table
        rowKey="id"
        loading={query.isLoading}
        dataSource={query.data?.instances ?? []}
        pagination={false}
        scroll={{ y: 'calc(100vh - 210px)' }}
        columns={[
          { title: '实例 ID', dataIndex: 'id', ellipsis: true },
          { title: '状态', dataIndex: 'state', width: 110, render: (state: string) => <Tag color={state === 'READY' ? 'success' : 'error'}>{state}</Tag> },
          { title: '已加载 Revision', dataIndex: 'loadedRevision', width: 150 },
          { title: '启动时间', dataIndex: 'startedAt', width: 190 },
          { title: '最近心跳', dataIndex: 'lastSeenAt', width: 190 },
          { title: '版本', dataIndex: 'applicationVersion', width: 120 },
          { title: '最近错误', dataIndex: 'lastError', ellipsis: true, render: (value?: string) => value || '—' },
        ]}
      />
    </div>
  );
};
