import { EditOutlined, SettingOutlined } from '@ant-design/icons';
import { Alert, Button, Descriptions, Empty, Space, Spin, Table, Tag, Tooltip, Typography } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MonacoSqlEditor } from '../../../shared/components/MonacoSqlEditor';
import { CanvasDesigner } from '../canvas/CanvasDesigner';
import {
  useCanvasTaskDefinition,
  useSparkJarTaskDefinition,
  useTaskDefinition,
} from '../hooks/useTasks';
import type { DataTask } from '../model/task';
import { CanvasTaskDefinitionPanel } from './CanvasTaskDefinitionPanel';

interface TaskDefinitionOverviewProps {
  task: DataTask;
  canUpdate: boolean;
  streamingActive: boolean;
}

export const TaskDefinitionOverview = ({
  task,
  canUpdate,
  streamingActive,
}: TaskDefinitionOverviewProps) => (
  task.type === 'LOCAL_SQL'
    ? <LocalSqlDefinitionOverview task={task} canUpdate={canUpdate} />
    : task.type === 'SPARK_JAR' || task.type === 'SPARK_STREAMING_JAR'
      ? <SparkJarDefinitionOverview task={task} canUpdate={canUpdate} streamingActive={streamingActive} />
      : (
      <CanvasDefinitionOverview
        task={task}
        canUpdate={canUpdate}
        streamingActive={streamingActive}
      />
    )
);

const formatBytes = (value: number): string => {
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(2)} MiB`;
  if (value >= 1024) return `${(value / 1024).toFixed(2)} KiB`;
  return `${value} B`;
};

const SparkJarDefinitionOverview = ({
  task,
  canUpdate,
  streamingActive,
}: {
  task: DataTask;
  canUpdate: boolean;
  streamingActive: boolean;
}) => {
  const definitionQuery = useSparkJarTaskDefinition(task.id);
  if (definitionQuery.isPending) {
    return <div className="task-definition-overview-loading"><Spin tip="正在加载 Spark JAR 定义…" /></div>;
  }
  if (definitionQuery.isError || !definitionQuery.data) {
    return <Alert type="error" showIcon message="Spark JAR 定义加载失败" />;
  }
  const definition = definitionQuery.data;
  const streaming = task.type === 'SPARK_STREAMING_JAR';
  if (!definition.configured || !definition.jar) {
    return (
      <div className="task-detail-tab-panel task-definition-overview-empty">
        <Empty description="尚未上传 Spark JAR">
          <DefinitionEditAction task={task} canUpdate={canUpdate} configured={false} streamingActive={streamingActive} />
        </Empty>
      </div>
    );
  }
  return (
    <div className="task-detail-tab-panel task-definition-overview spark-jar-overview">
      <div className="task-detail-tab-toolbar">
        <Space size={8} wrap>
          <Typography.Text strong>{streaming ? 'Spark 实时 JAR 定义' : 'Spark JAR 定义'}</Typography.Text>
          <Tag color="success">v{definition.definitionVersion}</Tag>
          <Tag color={definition.jobMode === 'STREAMING' ? 'processing' : 'default'}>{definition.jobMode}</Tag>
          <Tag>只读</Tag>
        </Space>
        <DefinitionEditAction task={task} canUpdate={canUpdate} configured streamingActive={streamingActive} />
      </div>
      <div className="spark-jar-overview-scroll">
        <Descriptions bordered size="small" column={3} title="用户作业 JAR">
          <Descriptions.Item label="文件名">{definition.jar.fileName}</Descriptions.Item>
          <Descriptions.Item label="大小">{formatBytes(definition.jar.sizeBytes)}</Descriptions.Item>
          <Descriptions.Item label="Job API">v{definition.jar.jobApiVersion}</Descriptions.Item>
          <Descriptions.Item label="Job Class" span={2}><Typography.Text code copyable>{definition.jar.jobClass}</Typography.Text></Descriptions.Item>
          <Descriptions.Item label="作业模式">{definition.jobMode}</Descriptions.Item>
          <Descriptions.Item label={streaming ? '作业启动超时' : '执行超时'}>{definition.timeoutSeconds} 秒</Descriptions.Item>
          <Descriptions.Item label="SHA-256" span={3}><Typography.Text code copyable ellipsis>{definition.jar.sha256}</Typography.Text></Descriptions.Item>
        </Descriptions>
        <Table
          size="small"
          rowKey={(entry) => entry.name}
          pagination={false}
          title={() => '运行参数'}
          locale={{ emptyText: '未配置运行参数' }}
          dataSource={definition.parameters}
          columns={[{ title: 'Key', dataIndex: 'name', width: '36%' }, { title: 'Value', dataIndex: 'value', ellipsis: true }]}
        />
        <Table
          size="small"
          rowKey={(entry) => entry.name}
          pagination={false}
          title={() => 'Spark Conf'}
          locale={{ emptyText: '未配置 Spark Conf' }}
          dataSource={definition.sparkConf}
          columns={[{ title: 'Key', dataIndex: 'name', width: '46%' }, { title: 'Value', dataIndex: 'value', ellipsis: true }]}
        />
        <Table
          size="small"
          rowKey={(binding) => binding.bindingName}
          pagination={false}
          title={() => '资源绑定'}
          locale={{ emptyText: '未配置平台资源绑定' }}
          dataSource={definition.resourceBindings}
          columns={[
            { title: '绑定名', dataIndex: 'bindingName' },
            { title: '类型', dataIndex: 'resourceType', render: (value) => value === 'MODEL' ? '模型' : value === 'KAFKA_TOPIC' ? 'Kafka Topic' : 'JDBC 数据源' },
            { title: '资源', dataIndex: 'resourceName', render: (value) => value ?? '资源已删除或不可用' },
            ...(streaming ? [{ title: 'Topic', dataIndex: 'topicName', render: (value: string | null) => value ?? '—' }] : []),
            { title: '访问方式', dataIndex: 'accessMode', render: (value) => value === 'READ' ? '只读' : value === 'WRITE' ? '只写' : '读写' },
          ]}
        />
      </div>
    </div>
  );
};

const DefinitionEditAction = ({
  task,
  canUpdate,
  configured,
  incompatible = false,
  streamingActive = false,
  onEdit,
}: {
  task: DataTask;
  canUpdate: boolean;
  configured: boolean;
  incompatible?: boolean;
  streamingActive?: boolean;
  onEdit?: () => void;
}) => {
  const navigate = useNavigate();
  if (!canUpdate) return null;
  if (task.status === 'PUBLISHED') {
    const reason = (task.type === 'SPARK_STREAMING_CANVAS' || task.type === 'SPARK_STREAMING_JAR') && streamingActive
      ? '请先停止实时任务，再停用任务后修改定义'
      : '请先停用任务，再修改任务定义';
    return (
      <Tooltip title={reason}>
        <span>
          <Button disabled icon={<EditOutlined />}>
            {incompatible ? '停用后重新配置' : '停用后编辑'}
          </Button>
        </span>
      </Tooltip>
    );
  }
  return (
    <Button
      type="primary"
      icon={configured && !incompatible ? <EditOutlined /> : <SettingOutlined />}
      onClick={() => onEdit ? onEdit() : navigate(`/task/${task.id}/definition`)}
    >
      {incompatible ? '重新配置' : configured ? '编辑任务定义' : '配置任务定义'}
    </Button>
  );
};

const LocalSqlDefinitionOverview = ({
  task,
  canUpdate,
}: {
  task: DataTask;
  canUpdate: boolean;
}) => {
  const definitionQuery = useTaskDefinition(task.id);
  if (definitionQuery.isLoading) {
    return <div className="task-definition-overview-loading"><Spin tip="正在加载任务定义…" /></div>;
  }
  if (!definitionQuery.data || definitionQuery.error) {
    return <Alert type="error" showIcon message="本地 SQL 定义加载失败" />;
  }
  const definition = definitionQuery.data;
  if (!definition.configured) {
    return (
      <div className="task-detail-tab-panel task-definition-overview-empty">
        <Empty description="尚未配置本地 SQL 任务定义">
          <DefinitionEditAction task={task} canUpdate={canUpdate} configured={false} />
        </Empty>
      </div>
    );
  }
  return (
    <div className="task-detail-tab-panel task-definition-overview task-local-sql-overview">
      <div className="task-detail-tab-toolbar">
        <Space size={8} wrap>
          <Typography.Text strong>本地 SQL 定义</Typography.Text>
          <Tag color="success">v{definition.version}</Tag>
          <Tag>只读</Tag>
        </Space>
        <DefinitionEditAction task={task} canUpdate={canUpdate} configured />
      </div>
      <div className="task-local-sql-overview-grid">
        <section className="task-local-sql-overview-code" aria-label="只读 SQL 定义">
          <MonacoSqlEditor
            value={definition.sql ?? ''}
            readOnly
            height="100%"
            onChange={() => undefined}
          />
        </section>
        <Descriptions bordered size="small" column={1} className="task-local-sql-overview-properties">
          <Descriptions.Item label="输入模型">
            {definition.inputs.length > 0 ? (
              <Space size={[4, 4]} wrap>
                {definition.inputs.map((model) => (
                  <Tag key={model.modelId}>{model.modelName}（{model.modelCode}）</Tag>
                ))}
              </Space>
            ) : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="输出模型">
            {definition.output
              ? `${definition.output.modelName}（${definition.output.modelCode}）`
              : '输出模型已删除或不可用'}
          </Descriptions.Item>
          <Descriptions.Item label="数据存储">
            {definition.resolvedDataSource
              ? `${definition.resolvedDataSource.name}（${definition.resolvedDataSource.code}）`
              : '数据存储已删除或不可用'}
          </Descriptions.Item>
          <Descriptions.Item label="写入方式">
            {definition.writeMode === 'APPEND' ? 'APPEND · 追加写入' : 'OVERWRITE · 清空后重写'}
          </Descriptions.Item>
          <Descriptions.Item label="执行超时">{definition.timeoutSeconds} 秒</Descriptions.Item>
          <Descriptions.Item label="更新时间">{definition.updatedAt ? new Date(definition.updatedAt).toLocaleString('zh-CN', { hour12: false }) : '—'}</Descriptions.Item>
        </Descriptions>
      </div>
    </div>
  );
};

const CanvasDefinitionOverview = ({
  task,
  canUpdate,
  streamingActive,
}: {
  task: DataTask;
  canUpdate: boolean;
  streamingActive: boolean;
}) => {
  const [editing, setEditing] = useState(false);
  const streaming = task.type === 'SPARK_STREAMING_CANVAS';
  const definitionQuery = useCanvasTaskDefinition(task.id);
  if (editing) {
    return (
      <CanvasTaskDefinitionPanel
        task={task}
        canUpdate={canUpdate}
        toolbarContext={(
          <Typography.Text strong>
            {streaming ? '实时 Canvas 定义' : '批处理 Canvas 定义'}
          </Typography.Text>
        )}
        onCancelEdit={() => setEditing(false)}
      />
    );
  }
  if (definitionQuery.isLoading) {
    return <div className="task-definition-overview-loading"><Spin tip="正在加载 Canvas 定义…" /></div>;
  }
  if (!definitionQuery.data || definitionQuery.error) {
    return <Alert type="error" showIcon message="Canvas 定义加载失败" />;
  }
  const definition = definitionQuery.data;
  if (definition.loadStatus === 'INCOMPATIBLE') {
    return (
      <div className="task-detail-tab-panel task-definition-overview task-definition-incompatible">
        <div className="task-detail-tab-toolbar">
          <Space size={8} wrap>
            <Typography.Text strong>{streaming ? '实时 Canvas 定义' : '批处理 Canvas 定义'}</Typography.Text>
            <Tag color="warning">v{definition.version}</Tag>
            <Tag color="error">Canvas {definition.schemaVersion}.{definition.schemaMinorVersion}</Tag>
          </Space>
          <DefinitionEditAction
            task={task}
            canUpdate={canUpdate}
            configured
            incompatible
            streamingActive={streamingActive}
            onEdit={() => setEditing(true)}
          />
        </div>
        <Alert
          type="warning"
          showIcon
          message="任务定义与当前 Canvas 协议不兼容"
          description={definition.message
            ?? `当前定义使用 Canvas ${definition.schemaVersion}.${definition.schemaMinorVersion}，请重新配置。`}
        />
      </div>
    );
  }
  if (!definition.configured) {
    return (
      <div className="task-detail-tab-panel task-definition-overview-empty">
        <Empty description={`尚未配置${streaming ? '实时' : '批处理'} Canvas 定义`}>
          <DefinitionEditAction
            task={task}
            canUpdate={canUpdate}
            configured={false}
            streamingActive={streamingActive}
            onEdit={() => setEditing(true)}
          />
        </Empty>
      </div>
    );
  }
  if (!definition.definition) {
    return <Alert type="error" showIcon message="Canvas 定义内容缺失" />;
  }
  const canvasDefinition = definition.definition;

  return (
    <div className="task-detail-tab-panel task-definition-overview task-canvas-overview">
      <CanvasDesigner
        mode="VIEW"
        initialDefinition={canvasDefinition}
        executionMode={streaming ? 'STREAMING' : 'BATCH'}
        toolbarLeading={(
          <Space size={8} wrap>
            <Typography.Text strong>{streaming ? '实时 Canvas 定义' : '批处理 Canvas 定义'}</Typography.Text>
            <Tag color="success">v{definition.version}</Tag>
            <Tag>{canvasDefinition.nodes.length} 个节点</Tag>
            <Tag>{canvasDefinition.edges.length} 条连线</Tag>
            {streaming && <Tag color="processing">间隔由无界输入节点配置</Tag>}
          </Space>
        )}
        toolbarTrailing={(
          <DefinitionEditAction
            task={task}
            canUpdate={canUpdate}
            configured
            streamingActive={streamingActive}
            onEdit={() => setEditing(true)}
          />
        )}
      />
    </div>
  );
};
