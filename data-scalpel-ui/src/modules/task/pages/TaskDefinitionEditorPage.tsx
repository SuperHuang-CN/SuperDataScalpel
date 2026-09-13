import { taskPageHref } from '../model/taskViews';
import { ArrowLeftOutlined, ProfileOutlined } from '@ant-design/icons';
import { Button, Result, Skeleton, Tag } from 'antd';
import { lazy, Suspense } from 'react';
const WorkflowTaskDefinitionPanel = lazy(() => import('../workflow/WorkflowTaskDefinitionPanel'));
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { useCurrentUser } from '../../system';
import { CanvasTaskDefinitionPanel } from '../components/CanvasTaskDefinitionPanel';
import { LocalSqlTaskDefinitionPanel } from '../components/LocalSqlTaskDefinitionPanel';
import { SparkJarTaskDefinitionPanel } from '../components/SparkJarTaskDefinitionPanel';
import { useTask } from '../hooks/useTasks';
import {
  taskStatusColors,
  taskStatusLabels,
  taskTypeColors,
  taskTypeLabels,
} from '../model/task';

export const TaskDefinitionEditorPage = () => {
  const { taskId } = useParams<{ taskId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const taskQuery = useTask(taskId);
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canUpdate = permissions.has('task.update');
  const canValidate = permissions.has('task.publish');
  const backToDefinition = () => navigate(taskQuery.data ? taskPageHref(`/task/${taskQuery.data.id}`, location.search, taskQuery.data.type, { tab: 'definition' }) : '/task');


  if (!taskId) {
    return <Result status="404" title="任务定义地址无效" extra={<Button onClick={() => navigate('/task')}>返回任务列表</Button>} />;
  }
  if (taskQuery.isPending || currentUserQuery.isPending) {
    return <div className="task-definition-editor-loading"><Skeleton active paragraph={{ rows: 10 }} /></div>;
  }
  if (!taskQuery.data || taskQuery.error) {
    return (
      <Result
        status="error"
        title="任务定义加载失败"
        subTitle={taskQuery.error instanceof ApiError ? taskQuery.error.message : '请确认任务是否存在。'}
        extra={<><Button type="primary" onClick={() => void taskQuery.refetch()}>重试</Button><Button onClick={() => navigate('/task')}>返回全部任务</Button></>}
      />
    );
  }
  const task = taskQuery.data;
  if (!canUpdate) {
    return (
      <Result
        status="403"
        title="没有修改任务定义的权限"
        subTitle="你仍然可以在任务详情的任务定义 Tab 中查看已保存定义。"
        extra={<Button type="primary" onClick={backToDefinition}>返回任务定义</Button>}
      />
    );
  }
  if (task.status === 'PUBLISHED') {
    return (
      <Result
        status="warning"
        title="当前任务状态不可编辑"
        subTitle={task.type === 'SPARK_STREAMING_CANVAS' || task.type === 'SPARK_STREAMING_JAR'
          ? '请返回任务详情，先停止实时运行并停用任务，再修改任务定义。'
          : '请返回任务详情并停用任务，再修改任务定义。'}
        extra={<Button type="primary" onClick={backToDefinition}>返回任务定义</Button>}
      />
    );
  }

  const toolbarContext = (
    <div className="task-definition-editor-identity">
      <Button type="text" icon={<ArrowLeftOutlined />} onClick={backToDefinition}>返回任务详情</Button>
      <span className="business-detail-resource-icon business-detail-resource-icon-blue"><ProfileOutlined /></span>
      <span className="task-definition-editor-title">{task.name}</span>
      <Tag color={taskTypeColors[task.type]}>{taskTypeLabels[task.type]}</Tag>
      <Tag color={taskStatusColors[task.status]}>{taskStatusLabels[task.status]}</Tag>
    </div>
  );

  return (
    <div className="task-definition-editor-page">
      <main className="task-definition-editor-body">
        {task.type === 'WORKFLOW' ? (
          <Suspense fallback={<Skeleton active />}><WorkflowTaskDefinitionPanel task={task} canUpdate canValidate={canValidate} editable toolbarContext={toolbarContext} /></Suspense>
        ) : task.type === 'LOCAL_SQL' ? (
          <LocalSqlTaskDefinitionPanel
            task={task}
            canUpdate
            canValidate={canValidate}
            toolbarContext={toolbarContext}
          />
        ) : task.type === 'SPARK_JAR' || task.type === 'SPARK_STREAMING_JAR' ? (
          <SparkJarTaskDefinitionPanel task={task} toolbarContext={toolbarContext} />
        ) : (
          <CanvasTaskDefinitionPanel
            task={task}
            canUpdate
            toolbarContext={toolbarContext}
          />
        )}
      </main>
    </div>
  );
};
