import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { SearchOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Checkbox, Form, Input, Space, Table, Tag, Tooltip, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { DetailTableToolbar } from '../../../shared/components/DetailTableToolbar';
import { useKafkaTopics } from '../hooks/useDataSources';
import type { DataSource, KafkaTopic } from '../model/dataSource';

interface KafkaTopicPanelProps {
  dataSource: DataSource;
  active: boolean;
}

interface KafkaTopicFilters {
  keyword?: string;
  includeInternal?: boolean;
}

const replicationFactor = (topic: KafkaTopic) => {
  if (!topic.metadataAvailable
      || topic.minimumReplicationFactor === null
      || topic.maximumReplicationFactor === null) return '—';
  return topic.minimumReplicationFactor === topic.maximumReplicationFactor
    ? topic.minimumReplicationFactor
    : `${topic.minimumReplicationFactor}–${topic.maximumReplicationFactor}`;
};

const TopicHealth = ({ topic }: { topic: KafkaTopic }) => {
  if (!topic.metadataAvailable) return <Tag>元数据不可用</Tag>;
  const underReplicated = topic.underReplicatedPartitionCount ?? 0;
  const unavailableLeader = topic.unavailableLeaderPartitionCount ?? 0;
  if (!underReplicated && !unavailableLeader) return <Tag color="success">健康</Tag>;
  return (
    <Space size={[4, 4]} wrap>
      {unavailableLeader > 0 && <Tag color="error">{unavailableLeader} 个分区无 Leader</Tag>}
      {underReplicated > 0 && <Tag color="warning">{underReplicated} 个分区 ISR 不足</Tag>}
    </Space>
  );
};

export const KafkaTopicPanel = ({ dataSource, active }: KafkaTopicPanelProps) => {
  const [form] = Form.useForm<KafkaTopicFilters>();
  const [keyword, setKeyword] = useState<string>();
  const [includeInternal, setIncludeInternal] = useState(false);
  const topicsQuery = useKafkaTopics(dataSource.id, keyword, includeInternal, active);
  const topics = useMemo(() => topicsQuery.data ?? [], [topicsQuery.data]);
  const summary = useMemo(() => {
    const metadataCount = topics.filter((topic) => topic.metadataAvailable).length;
    const partitionCount = topics.reduce((total, topic) => total + (topic.partitionCount ?? 0), 0);
    const unhealthyCount = topics.filter((topic) => (
      (topic.underReplicatedPartitionCount ?? 0) > 0
      || (topic.unavailableLeaderPartitionCount ?? 0) > 0
    )).length;
    return { metadataCount, partitionCount, unhealthyCount };
  }, [topics]);
  const columns = useMemo<TableProps<KafkaTopic>['columns']>(() => [
    {
      title: 'Topic', dataIndex: 'name', width: 460,
      render: (value: string, topic) => (
        <div className="kafka-topic-name-cell">
          <Tooltip title={value}><code>{value}</code></Tooltip>
          <Typography.Text type="secondary" ellipsis>
            {topic.topicId ? `Topic ID · ${topic.topicId}` : 'Topic ID 不可用'}
          </Typography.Text>
        </div>
      ),
    },
    {
      title: '分区', dataIndex: 'partitionCount', width: 100, align: 'right',
      render: (value: number | null) => value ?? '—',
    },
    {
      title: '副本', key: 'replicationFactor', width: 100, align: 'right',
      render: (_, topic) => replicationFactor(topic),
    },
    {
      title: '运行状态', key: 'health', width: 280,
      render: (_, topic) => <TopicHealth topic={topic} />,
    },
    {
      title: '类型', dataIndex: 'internal', width: 110,
      render: (internal: boolean) => internal ? <Tag color="purple">内部 Topic</Tag> : <Tag>业务 Topic</Tag>,
    },
  ], []);

  return (
    <div className="data-source-resource-panel kafka-topic-panel">
      <div className="data-source-resource-toolbar detail-table-filter-toolbar">
        <Form<KafkaTopicFilters>
          autoComplete="off"
          form={form}
          layout="inline"
          initialValues={{ includeInternal: false }}
          onFinish={(values) => {
            setKeyword(values.keyword?.trim() || undefined);
            setIncludeInternal(Boolean(values.includeInternal));
          }}
        >
          <Form.Item name="keyword">
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder="搜索 Topic 名称"
              className="detail-table-filter-keyword kafka-topic-keyword"
            />
          </Form.Item>
          <Form.Item name="includeInternal" valuePropName="checked">
            <Checkbox>包含内部 Topic</Checkbox>
          </Form.Item>
        </Form>
        <Space size={4} className="detail-table-filter-actions">
          <Button type="primary" icon={<SearchOutlined />} onClick={() => form.submit()}>查询</Button>
          <Button type="text" onClick={() => {
            form.resetFields();
            setKeyword(undefined);
            setIncludeInternal(false);
          }}>重置</Button>
        </Space>
      </div>
      {topicsQuery.error && (
        <Alert
          showIcon
          type="error"
          message="Topic 加载失败"
          description={topicsQuery.error instanceof ApiError ? topicsQuery.error.message : '请稍后重试。'}
          action={<Button size="small" onClick={() => void topicsQuery.refetch()}>重试</Button>}
        />
      )}
      {!topicsQuery.error && !topicsQuery.isPending && (
        <div className="kafka-topic-summary" aria-label="Topic 概览">
          <span>当前 <strong>{topics.length}</strong> 个 Topic</span>
          <span className="kafka-topic-summary-divider" aria-hidden />
          {summary.metadataCount > 0 ? (
            <span>
              {summary.metadataCount < topics.length ? `已读取 ${summary.metadataCount} 个 Topic，` : '共 '}
              <strong>{summary.partitionCount}</strong> 个分区
            </span>
          ) : <span>分区与副本信息不可用</span>}
          {summary.unhealthyCount > 0
            ? <Tag color="warning">{summary.unhealthyCount} 个 Topic 需要关注</Tag>
            : summary.metadataCount > 0 && <Tag color="success">运行状态正常</Tag>}
          {topics.length > summary.metadataCount && (
            <Tooltip title="集群允许列出 Topic，但当前账号可能没有 Describe Topic 权限。">
              <Tag>{topics.length - summary.metadataCount} 个元数据不可用</Tag>
            </Tooltip>
          )}
        </div>
      )}
      <DetailTableToolbar
        title="Topic 列表"
        total={topics.length}
        showPagination={false}
        onRefresh={() => void topicsQuery.refetch()}
        refreshing={topicsQuery.isFetching}
        refreshLabel="刷新 Kafka Topic"
        itemUnit="个"
      />
      <Table<KafkaTopic>
        className="management-table kafka-topic-table"
        size="small"
        rowKey="name"
        pagination={false}
        loading={topicsQuery.isFetching}
        dataSource={topics}
        columns={columns}
        locale={{ emptyText: keyword ? '没有匹配的 Topic' : '当前集群没有可见 Topic' }}
        scroll={{ x: 1_050, y: '100%' }}
      />
    </div>
  );
};
