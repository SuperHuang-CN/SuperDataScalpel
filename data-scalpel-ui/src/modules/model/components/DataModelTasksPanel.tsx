import { EyeOutlined, ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Form, Input, Select, Space, Table, Tag, Tooltip, message } from 'antd';
import { useMemo, useState } from 'react';
import {
  filterMockRelatedTasks,
  mockRelatedTasks,
  type MockRelatedTask,
} from '../model/modelDetailMock';

interface TaskFilters {
  keyword?: string;
  relation?: MockRelatedTask['relation'];
  status?: MockRelatedTask['status'];
}

const taskStatusLabels: Record<MockRelatedTask['status'], string> = {
  RUNNING: '运行中',
  ENABLED: '已启用',
  DISABLED: '已停用',
  FAILED: '失败',
};

const taskStatusColors: Record<MockRelatedTask['status'], string> = {
  RUNNING: 'processing',
  ENABLED: 'success',
  DISABLED: 'default',
  FAILED: 'error',
};

export const DataModelTasksPanel = () => {
  const [form] = Form.useForm<TaskFilters>();
  const [filters, setFilters] = useState<TaskFilters>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [messageApi, messageContext] = message.useMessage();
  const tasks = useMemo(
    () => filterMockRelatedTasks(mockRelatedTasks, filters.keyword, filters.relation, filters.status),
    [filters],
  );

  const columns: TableProps<MockRelatedTask>['columns'] = [
    {
      title: '任务名称',
      dataIndex: 'name',
      width: 220,
      fixed: 'left',
      ellipsis: true,
      render: (value: string, task) => (
        <Button type="link" className="model-task-name-button" onClick={() => messageApi.info(`任务详情尚未接入：${task.name}`)}>
          {value}
        </Button>
      ),
    },
    { title: '任务编码', dataIndex: 'code', width: 190, ellipsis: true, render: (value: string) => <code>{value}</code> },
    {
      title: '关联方向',
      dataIndex: 'relation',
      width: 100,
      render: (value: MockRelatedTask['relation']) => (
        <Tag color={value === 'PRODUCER' ? 'blue' : 'purple'}>{value === 'PRODUCER' ? '生产模型' : '消费模型'}</Tag>
      ),
    },
    { title: '任务类型', dataIndex: 'taskType', width: 110 },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: MockRelatedTask['status']) => <Tag color={taskStatusColors[value]}>{taskStatusLabels[value]}</Tag>,
    },
    { title: '调度周期', dataIndex: 'schedule', width: 120 },
    { title: '最近运行', dataIndex: 'lastRunAt', width: 180, render: (value: string | null) => value || '—' },
    {
      title: '操作',
      key: 'actions',
      width: 58,
      fixed: 'right',
      render: (_value, task) => (
        <Tooltip title="查看任务">
          <Button
            type="text"
            icon={<EyeOutlined />}
            aria-label={`查看任务${task.name}`}
            onClick={() => messageApi.info(`任务详情尚未接入：${task.name}`)}
          />
        </Tooltip>
      ),
    },
  ];

  return (
    <div className="model-detail-tab-panel">
      {messageContext}
      <div className="model-mock-notice">
        <Tag color="gold">前端 Mock</Tag>
        当前用于确认生产任务、消费任务和任务状态的展示方式，后续由任务模块提供真实关联关系。
      </div>
      <div className="model-tab-toolbar">
        <Form<TaskFilters>
          form={form}
          layout="inline"
          onFinish={(values) => { setFilters(values); setPage(1); }}
        >
          <Form.Item name="keyword" label="名称/编码">
            <Input allowClear placeholder="筛选任务" className="model-task-keyword-input" />
          </Form.Item>
          <Form.Item name="relation" label="方向">
            <Select
              allowClear
              placeholder="全部"
              className="model-task-filter-select"
              options={[{ value: 'PRODUCER', label: '生产模型' }, { value: 'CONSUMER', label: '消费模型' }]}
            />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              allowClear
              placeholder="全部"
              className="model-task-filter-select"
              options={(Object.entries(taskStatusLabels) as [MockRelatedTask['status'], string][])
                .map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
        </Form>
        <Space size={4}>
          <Button type="primary" onClick={() => form.submit()}>查询</Button>
          <Button onClick={() => { form.resetFields(); setFilters({}); setPage(1); }}>重置</Button>
          <Button icon={<ReloadOutlined />} onClick={() => messageApi.success('Mock 任务已刷新')}>刷新</Button>
        </Space>
      </div>
      <Table<MockRelatedTask>
        size="small"
        className="management-table"
        rowKey="id"
        columns={columns}
        dataSource={tasks}
        scroll={{ x: 1100, y: '100%' }}
        pagination={{
          current: page,
          pageSize,
          total: tasks.length,
          placement: ['bottomEnd'],
          hideOnSinglePage: false,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 项`,
        }}
        onChange={(pagination) => {
          setPage(pagination.current ?? 1);
          setPageSize(pagination.pageSize ?? 20);
        }}
      />
    </div>
  );
};
