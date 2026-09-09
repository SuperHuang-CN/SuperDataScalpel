import { DeploymentUnitOutlined, FileTextOutlined, ProfileOutlined } from '@ant-design/icons';
import { Descriptions, Tag, Typography } from 'antd';
import { BusinessDetailSection } from '../../../shared/components/BusinessDetailSection';
import { BusinessDetailDescriptions } from '../../../shared/components/BusinessDetailDescriptions';
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
    <BusinessDetailSection title="基本标识" description="任务身份、类型与生命周期信息" icon={<ProfileOutlined />}>
      <BusinessDetailDescriptions column={{ xs: 1, md: 2, xl: 4 }}>
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
      </BusinessDetailDescriptions>
    </BusinessDetailSection>

    <BusinessDetailSection title="执行与定义概览" description="执行方式、定义版本和资源绑定摘要" icon={<DeploymentUnitOutlined />}>
      <BusinessDetailDescriptions column={{ xs: 1, md: 2, xl: 4 }}>
        <Descriptions.Item label="定义状态">
          <Tag color={task.definitionConfigured ? 'success' : 'default'}>
            {task.definitionConfigured ? '已配置' : '未配置'}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="定义版本">
          {task.definitionVersion == null ? '—' : `v${task.definitionVersion}`}
        </Descriptions.Item>
        {task.type === 'WORKFLOW' ? <Descriptions.Item label="执行方式" span={2}>依赖推进，子任务使用各自执行方式</Descriptions.Item> : task.type === 'LOCAL_SQL' ? (
          <>
            <Descriptions.Item label="执行方式">本地 JDBC</Descriptions.Item>
            <Descriptions.Item label="输出模型" span={4}>{task.outputModelName ?? '—'}</Descriptions.Item>
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
      </BusinessDetailDescriptions>
    </BusinessDetailSection>

    <BusinessDetailSection title="业务说明" description="任务目标、运行范围与补充信息" icon={<FileTextOutlined />}>
      <div className="task-description-box">{task.description || '—'}</div>
    </BusinessDetailSection>
  </div>
);
