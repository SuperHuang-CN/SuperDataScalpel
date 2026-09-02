import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { EyeOutlined, SearchOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Descriptions, Drawer, Form, Input, Space, Table, Tag, Tooltip } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { DetailTableToolbar } from '../../../shared/components/DetailTableToolbar';
import { useTdEngineTmqTopic, useTdEngineTmqTopics } from '../hooks/useDataSources';
import type { ColumnMetadata, DataSource, TdEngineTmqTopic } from '../model/dataSource';

interface TdEngineTmqTopicPanelProps {
  dataSource: DataSource;
  active: boolean;
}

export const TdEngineTmqTopicPanel = ({ dataSource, active }: TdEngineTmqTopicPanelProps) => {
  const [form] = Form.useForm<{ keyword?: string }>();
  const [keyword, setKeyword] = useState<string>();
  const [selectedTopic, setSelectedTopic] = useState<string>();
  const topicsQuery = useTdEngineTmqTopics(dataSource.id, keyword, active);
  const detailQuery = useTdEngineTmqTopic(dataSource.id, selectedTopic, active && Boolean(selectedTopic));
  const columns = useMemo<TableProps<TdEngineTmqTopic>['columns']>(() => [
    {
      title: 'Topic', dataIndex: 'topicName', width: 320,
      render: (value: string) => <Tooltip title={value}><code>{value}</code></Tooltip>,
    },
    {
      title: '来源', key: 'source', width: 360,
      render: (_, topic) => topic.databaseName && topic.supertableName
        ? <><code>{topic.databaseName}</code> · <code>{topic.supertableName}</code></>
        : '—',
    },
    {
      title: '支持状态', key: 'supported',
      render: (_, topic) => topic.supported
        ? <Tag color="success">可订阅</Tag>
        : <Tooltip title={topic.unsupportedReason}><Tag color="default">不支持</Tag></Tooltip>,
    },
    {
      title: '创建时间', dataIndex: 'createdAt', width: 190,
      render: (value: string | null) => value ? new Date(value).toLocaleString() : '—',
    },
    {
      title: '操作', key: 'actions', width: 72,
      render: (_, topic) => (
        <Tooltip title="查看 Topic 详情">
          <Button
            type="text"
            icon={<EyeOutlined />}
            aria-label={`查看 ${topic.topicName} 详情`}
            onClick={() => setSelectedTopic(topic.topicName)}
          />
        </Tooltip>
      ),
    },
  ], []);
  const columnDefinitions = useMemo<TableProps<ColumnMetadata>['columns']>(() => [
    { title: '#', dataIndex: 'ordinal', width: 52 },
    { title: '字段', dataIndex: 'name', width: 180, render: (value: string) => <code>{value}</code> },
    {
      title: '角色', dataIndex: 'role', width: 100,
      render: (value: ColumnMetadata['role']) => value === 'TAG'
        ? <Tag color="purple">TAG</Tag>
        : value === 'TIME_KEY' ? <Tag color="blue">时间主列</Tag> : <Tag>指标列</Tag>,
    },
    { title: 'TDengine 类型', dataIndex: 'nativeType', width: 150 },
    {
      title: '平台类型', key: 'platformType',
      render: (_, column) => column.platformTypeDefinition?.type ?? '不可映射',
    },
  ], []);

  return (
    <div className="data-source-resource-panel tdengine-tmq-topic-panel">
      <div className="data-source-resource-toolbar detail-table-filter-toolbar">
        <Form form={form} layout="inline" autoComplete="off" onFinish={(values) => setKeyword(values.keyword?.trim() || undefined)}>
          <Form.Item name="keyword">
            <Input allowClear prefix={<SearchOutlined />} placeholder="TMQ Topic 名称" className="detail-table-filter-keyword" />
          </Form.Item>
        </Form>
        <Space size={4} className="detail-table-filter-actions">
          <Button type="primary" icon={<SearchOutlined />} onClick={() => form.submit()}>查询</Button>
          <Button type="text" onClick={() => { form.resetFields(); setKeyword(undefined); }}>重置</Button>
        </Space>
      </div>
      {topicsQuery.error && (
        <Alert
          showIcon
          type="error"
          message="TMQ Topic 加载失败"
          description={topicsQuery.error instanceof ApiError ? topicsQuery.error.message : '请稍后重试。'}
          action={<Button size="small" onClick={() => void topicsQuery.refetch()}>重试</Button>}
        />
      )}
      <DetailTableToolbar
        title="TMQ Topic 列表"
        total={topicsQuery.data?.length ?? 0}
        showPagination={false}
        onRefresh={() => void topicsQuery.refetch()}
        refreshing={topicsQuery.isFetching}
        refreshLabel="刷新 TDengine TMQ Topic"
        itemUnit="个"
      />
      <Table<TdEngineTmqTopic>
        className="management-table"
        size="small"
        rowKey="topicName"
        pagination={false}
        loading={topicsQuery.isFetching}
        dataSource={topicsQuery.data ?? []}
        columns={columns}
        locale={{ emptyText: keyword ? '没有匹配的 TMQ Topic' : '当前账号没有可见的 TMQ Topic' }}
        scroll={{ y: '100%' }}
      />
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        title={selectedTopic ? `TMQ Topic · ${selectedTopic}` : 'TMQ Topic 详情'}
        width={760}
        open={Boolean(selectedTopic)}
        onClose={() => setSelectedTopic(undefined)}
      >
        {detailQuery.error && <Alert type="error" showIcon message="详情加载失败" description={detailQuery.error.message} />}
        {detailQuery.data && <Space orientation="vertical" size={16} style={{ width: '100%' }}>
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="数据库"><code>{detailQuery.data.databaseName ?? '—'}</code></Descriptions.Item>
            <Descriptions.Item label="超级表"><code>{detailQuery.data.supertableName ?? '—'}</code></Descriptions.Item>
            <Descriptions.Item label="时间精度">{detailQuery.data.timePrecision ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="支持状态">{detailQuery.data.supported ? <Tag color="success">可订阅</Tag> : <Tag>不支持</Tag>}</Descriptions.Item>
          </Descriptions>
          {!detailQuery.data.supported && <Alert type="warning" showIcon message={detailQuery.data.unsupportedReason ?? '当前 Topic 不受支持'} />}
          <Table<ColumnMetadata>
            size="small"
            rowKey="name"
            pagination={false}
            dataSource={detailQuery.data.columns}
            columns={columnDefinitions}
          />
        </Space>}
      </Drawer>
    </div>
  );
};
