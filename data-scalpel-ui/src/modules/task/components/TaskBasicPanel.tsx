import { Descriptions, Tag, Typography } from 'antd';
import {
  taskStatusColors,
  taskStatusLabels,
  taskTypeColors,
  taskTypeLabels,
  type DataTask,
} from '../model/task';

interface TaskBasicPanelProps {
  task: DataTask;
  directoryName?: string;
}

const dateTime = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium',
  timeStyle: 'medium',
  hour12: false,
}).format(new Date(value));

export const TaskBasicPanel = ({ task, directoryName }: TaskBasicPanelProps) => (
  <div className="task-detail-tab-panel task-basic-panel">
    <section className="task-basic-section">
      <div className="task-basic-section-title">基本标识</div>
      <Descriptions size="small" bordered column={3}>
        <Descriptions.Item label="任务名称">{task.name}</Descriptions.Item>
        <Descriptions.Item label="任务类型">
          <Tag color={taskTypeColors[task.type]}>{taskTypeLabels[task.type]}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="状态">
          <Tag color={taskStatusColors[task.status]}>{taskStatusLabels[task.status]}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="任务 ID" span={2}>
          <Typography.Text code copyable={{ text: task.id }}>{task.id}</Typography.Text>
        </Descriptions.Item>
        <Descriptions.Item label="所属目录">
          {directoryName ?? (task.directoryId ? '已删除目录' : '未分类')}
        </Descriptions.Item>
        <Descriptions.Item label="创建时间">{dateTime(task.createdAt)}</Descriptions.Item>
        <Descriptions.Item label="更新时间">{dateTime(task.updatedAt)}</Descriptions.Item>
      </Descriptions>
    </section>

    <section className="task-basic-section">
      <div className="task-basic-section-title">执行与定义概览</div>
      <Descriptions size="small" bordered column={3}>
        <Descriptions.Item label="定义状态">
          <Tag color={task.definitionConfigured ? 'success' : 'default'}>
            {task.definitionConfigured ? '已配置' : '未配置'}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="定义版本">
          {task.definitionVersion == null ? '—' : `v${task.definitionVersion}`}
        </Descriptions.Item>
        {task.type === 'LOCAL_SQL' ? (
          <>
            <Descriptions.Item label="执行方式">本地 JDBC</Descriptions.Item>
            <Descriptions.Item label="输出模型" span={3}>{task.outputModelName ?? '—'}</Descriptions.Item>
          </>
        ) : (
          <>
            <Descriptions.Item label="执行方式">
              {task.type === 'SPARK_STREAMING_CANVAS' || task.type === 'SPARK_STREAMING_JAR'
                ? 'Spark Structured Streaming'
                : 'Spark 批处理'}
            </Descriptions.Item>
            <Descriptions.Item label="计算引擎" span={2}>
              {task.computeEngineName ?? (task.computeEngineId ? '已删除计算引擎' : '未选择')}
            </Descriptions.Item>
          </>
        )}
      </Descriptions>
    </section>

    <section className="task-basic-section">
      <div className="task-basic-section-title">业务说明</div>
      <div className="task-description-box">{task.description || '—'}</div>
    </section>
  </div>
);
