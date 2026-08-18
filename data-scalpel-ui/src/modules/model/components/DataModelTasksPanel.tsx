import { EyeOutlined, ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Alert, Button, Form, Input, Select, Space, Table, Tag, Tooltip } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  buildTaskSearch,
  taskStatusColors,
  taskStatusLabels,
  taskTypeLabels,
  useModelRelatedTasks,
  type ModelRelatedTask,
  type ModelTaskRelationRole,
  type TaskModelReferenceLocation,
  type TaskStatus,
  type TaskType,
} from '../../task';

interface DataModelTasksPanelProps {
  modelId: string;
}

interface TaskFilters {
  keyword?: string;
  role?: ModelTaskRelationRole;
  status?: TaskStatus;
  type?: TaskType;
}

type RelatedTaskSortField = 'name' | 'type' | 'status' | 'updatedAt';

const DEFAULT_SORT = '-updatedAt,name';

const roleLabel: Record<ModelTaskRelationRole, string> = {
  INPUT: '任务输入',
  OUTPUT: '任务输出',
};

const roleTooltip: Record<ModelTaskRelationRole, string> = {
  INPUT: '任务消费当前模型',
  OUTPUT: '任务产出当前模型',
};

const dateTime = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium',
  timeStyle: 'medium',
  hour12: false,
}).format(new Date(value));

const locationLabel = (location: TaskModelReferenceLocation) => {
  if (location.referenceType === 'LOCAL_SQL_INPUT') return `输入 #${location.ordinal}`;
  if (location.referenceType === 'LOCAL_SQL_OUTPUT') return '输出模型';
  if (location.referenceType === 'MODEL_QUALITY_TARGET') return '质检目标模型';
  if (location.referenceType === 'SPARK_JAR_RESOURCE_BINDING') return 'Spark JAR 资源绑定';
  if (location.referenceType === 'CURRENT_LINEAGE') return location.nodeName ? `当前血缘 · ${location.nodeName}` : '当前血缘';
  return location.nodeName || location.nodeId || 'Canvas 节点';
};

const Locations = ({ locations }: { locations: TaskModelReferenceLocation[] }) => {
  const visible = locations.slice(0, 3);
  return (
    <Space size={[4, 4]} wrap>
      {visible.map((location, index) => (
        <Tooltip
          key={`${location.referenceType}-${location.nodeId ?? location.ordinal ?? index}`}
          title={location.nodeId ? `节点 ID：${location.nodeId}` : undefined}
        >
          <Tag>{locationLabel(location)}</Tag>
        </Tooltip>
      ))}
      {locations.length > visible.length && <Tag>+{locations.length - visible.length}</Tag>}
    </Space>
  );
};

export const DataModelTasksPanel = ({ modelId }: DataModelTasksPanelProps) => {
  const navigate = useNavigate();
  const [form] = Form.useForm<TaskFilters>();
  const [filters, setFilters] = useState<TaskFilters>({});
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [sort, setSort] = useState(DEFAULT_SORT);
  const request = useMemo(() => ({
    search: buildTaskSearch({
      keyword: filters.keyword,
      status: filters.status,
      type: filters.type,
    }),
    page: page - 1,
    size: pageSize,
    sort,
  }), [filters, page, pageSize, sort]);
  const relatedTasksQuery = useModelRelatedTasks(modelId, filters.role, request);
  const primarySort = sort.split(',')[0];
  const sortOrder = (field: RelatedTaskSortField) => {
    if (primarySort === field) return 'ascend' as const;
    if (primarySort === `-${field}`) return 'descend' as const;
    return null;
  };

  const openTask = (task: ModelRelatedTask) => navigate(`/task/${task.taskId}`);
  const columns: TableProps<ModelRelatedTask>['columns'] = [
    {
      title: '任务名称',
      dataIndex: 'taskName',
      width: 220,
      fixed: 'left',
      ellipsis: true,
      key: 'name',
      sorter: true,
      sortOrder: sortOrder('name'),
      render: (value: string, task) => (
        <Button type="link" className="model-task-name-button" onClick={() => openTask(task)}>
          {value}
        </Button>
      ),
    },
    {
      title: '关联角色',
      dataIndex: 'roles',
      width: 180,
      render: (roles: ModelTaskRelationRole[]) => (
        <Space size={[4, 4]} wrap>
          {roles.map((role) => (
            <Tooltip key={role} title={roleTooltip[role]}>
              <Tag color={role === 'INPUT' ? 'blue' : 'purple'}>{roleLabel[role]}</Tag>
            </Tooltip>
          ))}
        </Space>
      ),
    },
    {
      title: '任务类型',
      dataIndex: 'taskType',
      width: 130,
      key: 'type',
      sorter: true,
      sortOrder: sortOrder('type'),
      render: (value: TaskType) => taskTypeLabels[value],
    },
    {
      title: '状态',
      dataIndex: 'taskStatus',
      width: 100,
      key: 'status',
      sorter: true,
      sortOrder: sortOrder('status'),
      render: (value: TaskStatus) => (
        <Tag color={taskStatusColors[value]}>{taskStatusLabels[value]}</Tag>
      ),
    },
    {
      title: '引用位置',
      dataIndex: 'locations',
      width: 260,
      render: (locations: TaskModelReferenceLocation[]) => <Locations locations={locations} />,
    },
    {
      title: '定义版本',
      dataIndex: 'definitionVersion',
      width: 100,
      render: (value: number) => `v${value}`,
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 180,
      key: 'updatedAt',
      sorter: true,
      sortOrder: sortOrder('updatedAt'),
      render: dateTime,
    },
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
            aria-label={`查看任务${task.taskName}`}
            onClick={() => openTask(task)}
          />
        </Tooltip>
      ),
    },
  ];

  return (
    <div className="model-detail-tab-panel">
      <div className="model-tab-toolbar">
        <Form<TaskFilters> autoComplete="off"
          form={form}
          layout="inline"
          onFinish={(values) => {
            setFilters(values);
            setPage(1);
          }}
        >
          <Form.Item name="keyword" label="名称">
            <Input allowClear placeholder="筛选任务" className="model-task-keyword-input" />
          </Form.Item>
          <Form.Item name="role" label="角色">
            <Select
              allowClear
              placeholder="全部"
              className="model-task-filter-select"
              options={[
                { value: 'INPUT', label: '任务输入' },
                { value: 'OUTPUT', label: '任务输出' },
              ]}
            />
          </Form.Item>
          <Form.Item name="type" label="类型">
            <Select
              allowClear
              placeholder="全部"
              className="model-task-filter-select"
              options={(Object.entries(taskTypeLabels) as [TaskType, string][])
                .map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              allowClear
              placeholder="全部"
              className="model-task-filter-select"
              options={(Object.entries(taskStatusLabels) as [TaskStatus, string][])
                .map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
        </Form>
        <Space size={4}>
          <Button type="primary" onClick={() => form.submit()}>查询</Button>
          <Button onClick={() => {
            form.resetFields();
            setFilters({});
            setPage(1);
          }}
          >
            重置
          </Button>
          <Tooltip title="刷新关联任务">
            <Button
              icon={<ReloadOutlined />}
              aria-label="刷新关联任务"
              loading={relatedTasksQuery.isFetching}
              onClick={() => void relatedTasksQuery.refetch()}
            />
          </Tooltip>
        </Space>
      </div>
      {relatedTasksQuery.error && (
        <Alert
          type="error"
          showIcon
          message="关联任务加载失败"
          description="请稍后重试。"
          action={<Button size="small" onClick={() => void relatedTasksQuery.refetch()}>重试</Button>}
        />
      )}
      <Table<ModelRelatedTask>
        size="small"
        className="management-table"
        rowKey="taskId"
        columns={columns}
        dataSource={relatedTasksQuery.data?.content ?? []}
        loading={relatedTasksQuery.isPending}
        scroll={{ x: 1250, y: '100%' }}
        pagination={{
          current: page,
          pageSize,
          total: relatedTasksQuery.data?.totalElements ?? 0,
          placement: ['bottomEnd'],
          hideOnSinglePage: false,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 项`,
        }}
        onChange={(pagination, _tableFilters, sorter) => {
          const nextPageSize = pagination.pageSize ?? 20;
          setPage(nextPageSize === pageSize ? pagination.current ?? 1 : 1);
          setPageSize(nextPageSize);
          const activeSorter = Array.isArray(sorter) ? sorter[0] : sorter;
          const field = activeSorter?.columnKey as RelatedTaskSortField | undefined;
          if (!field || !activeSorter.order) {
            setSort(DEFAULT_SORT);
            return;
          }
          setSort(activeSorter.order === 'descend' ? `-${field}` : field);
        }}
      />
    </div>
  );
};
