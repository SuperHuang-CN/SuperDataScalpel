import { createUuid } from '../../../shared/browser/createUuid';
import { taskPageHref } from '../model/taskViews';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Empty, Form, InputNumber, List, Modal, Select, Space, Spin, Tag, Typography, message } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons';
import { useEffect, useState, type ReactNode } from 'react';
import { useBlocker, useLocation, useNavigate } from 'react-router-dom';
import { CompactAlert } from '../../../shared/components/ContextualFeedback';
import { andSearch, orSearch, searchContains, searchEquals } from '../../../shared/search';
import { ApiError } from '../../../shared/api/http';
import { executeTaskCommand, fetchTask, fetchTasks } from '../api/taskApi';
import type { DataTask } from '../model/task';
import { fetchWorkflowDefinition, saveWorkflowDefinition, validateWorkflowDefinition, workflowDefinitionKey } from './workflowApi';
import { WorkflowGraph, type WorkflowSelection } from './WorkflowGraph';
import type { WorkflowDefinition, WorkflowTaskDefinition, WorkflowValidation } from './workflowTypes';

interface Props { task: DataTask; canUpdate: boolean; canValidate?: boolean; editable?: boolean; toolbarContext?: ReactNode }
export default function WorkflowTaskDefinitionPanel(props: Props) {
  const query = useQuery({ queryKey: workflowDefinitionKey(props.task.id), queryFn: () => fetchWorkflowDefinition(props.task.id) });
  if (query.isPending) return <Spin />;
  if (query.isError) return <CompactAlert type="error" message="工作流定义加载失败" description={query.error.message}
    action={<Button onClick={() => void query.refetch()}>重试</Button>} />;
  return <WorkflowEditor key={props.task.id} {...props} initial={query.data} />;
}
function WorkflowEditor({ task, initial, editable = false, canUpdate, canValidate = false, toolbarContext }: Props & { initial: WorkflowTaskDefinition }) {
  const navigate = useNavigate();
  const location = useLocation();
  const cache = useQueryClient();
  const [messageApi, messageContext] = message.useMessage();
  const [definition, setDefinition] = useState(initial.definition);
  const [saved, setSaved] = useState(JSON.stringify(initial.definition));
  const [version, setVersion] = useState(initial.version);
  const [selection, setSelection] = useState<WorkflowSelection | null>(null);
  const [search, setSearch] = useState('');
  const [validation, setValidation] = useState<WorkflowValidation | null>(null);
  const dirty = editable && JSON.stringify(definition) !== saved;
  const blocker = useBlocker(({ currentLocation, nextLocation }) => dirty
    && (currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search));
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => { if (dirty) { event.preventDefault(); event.returnValue = ''; } };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);
  const referencedIds = [...new Set(definition.nodes.map(node => node.taskId).filter(Boolean))];
  const references = useQueries({ queries: referencedIds.map(id => ({ queryKey: ['tasks', id], queryFn: () => fetchTask(id) })) });
  const names = Object.fromEntries(references.flatMap(query => query.data ? [[query.data.id, query.data.name]] : []));
  const tasks = useQuery({ queryKey: ['workflow-task-options', search], enabled: editable,
    queryFn: () => fetchTasks({ page: 0, size: 100, sort: 'name', search: andSearch(searchEquals('status', 'PUBLISHED'),
      orSearch(...['LOCAL_SQL', 'SPARK_CANVAS', 'SPARK_MODEL_QUALITY', 'SPARK_JAR'].map(type => searchEquals('type', type))), searchContains('name', search)) }),
  });
  const selectedNode = definition.nodes.find(node => node.id === selection?.nodeId);
  const change = (next: WorkflowDefinition) => { setDefinition(next); setValidation(null); };
  const action = useMutation({ mutationFn: async (kind: 'save' | 'validate' | 'publish') => {
    if (kind === 'save' || dirty || version === null) {
      const result = await saveWorkflowDefinition(task.id, definition);
      setDefinition(result.definition); setSaved(JSON.stringify(result.definition)); setVersion(result.version);
      cache.setQueryData(workflowDefinitionKey(task.id), result);
      await cache.invalidateQueries({ queryKey: ['tasks'] });
    }
    if (kind !== 'save') {
      const result = await validateWorkflowDefinition(task.id); setValidation(result);
      if (!result.valid) return;
      if (kind === 'publish') {
        await executeTaskCommand(task.id, task.status === 'DISABLED' ? 'enable' : 'publish');
        navigate(taskPageHref(`/task/${task.id}`, location.search, task.type, { tab: 'definition' }));
        await cache.invalidateQueries({ queryKey: ['tasks'] });
      }
    }
    messageApi.success(kind === 'save' ? '工作流定义已保存' : kind === 'publish' ? '工作流已发布' : '工作流校验通过');
  }, onError: (error) => messageApi.error(error instanceof ApiError ? error.message : '工作流操作失败') });
  const addNode = () => {
    const id = createUuid();
    change({ ...definition, nodes: [...definition.nodes, { id, taskId: '' }] }); setSelection({ nodeId: id });
  };
  const deleteSelected = () => {
    if (selection?.nodeId) change({ ...definition, nodes: definition.nodes.filter(node => node.id !== selection.nodeId),
      edges: definition.edges.filter(edge => edge.source !== selection.nodeId && edge.target !== selection.nodeId),
      layout: Object.fromEntries(Object.entries(definition.layout).filter(([id]) => id !== selection.nodeId)) });
    else if (selection?.edgeIndex !== undefined) change({ ...definition, edges: definition.edges.filter((_, index) => index !== selection.edgeIndex) });
    setSelection(null);
  };
  const options = (tasks.data?.content ?? []).map(candidate => ({ value: candidate.id, label: candidate.name }));
  if (selectedNode?.taskId && !options.some(option => option.value === selectedNode.taskId)) {
    options.unshift({ value: selectedNode.taskId, label: names[selectedNode.taskId] ?? '引用任务不可用' });
  }
  return <section className="workflow-panel">
    {messageContext}
    <div className="workflow-toolbar">
      {toolbarContext ?? <Space><Typography.Text strong>工作流定义</Typography.Text><Tag>{version ? `v${version}` : '未保存'}</Tag><Tag>{definition.nodes.length} 个节点</Tag></Space>}
      <Space wrap>
        {editable ? <>
          {dirty && <Typography.Text type="warning">未保存</Typography.Text>}
          <Button icon={<PlusOutlined />} disabled={action.isPending} onClick={addNode}>添加任务</Button>
          <Button icon={<DeleteOutlined />} disabled={!selection || action.isPending} onClick={deleteSelected}>删除所选</Button>
          {canValidate && <Button loading={action.isPending} onClick={() => action.mutate('validate')}>校验</Button>}
          <Button icon={<SaveOutlined />} loading={action.isPending} onClick={() => action.mutate('save')}>保存</Button>
          {canValidate && <Button type="primary" loading={action.isPending} onClick={() => action.mutate('publish')}>{task.status === 'DISABLED' ? '启用' : '发布'}</Button>}
        </> : canUpdate && <Button type="primary" icon={<EditOutlined />} disabled={task.status === 'PUBLISHED'} onClick={() => navigate(taskPageHref(`/task/${task.id}/definition`, location.search, task.type))}>{task.status === 'PUBLISHED' ? '停用后编辑' : '编辑定义'}</Button>}
      </Space>
    </div>
    {validation && !validation.valid && <List className="workflow-problems" size="small" dataSource={validation.problems}
      renderItem={problem => <List.Item><Button type="link" danger onClick={() => setSelection(problem.nodeId ? { nodeId: problem.nodeId } : problem.edgeIndex !== null ? { edgeIndex: problem.edgeIndex } : null)}>{problem.message}</Button></List.Item>} />}
    <div className="workflow-workspace">
      <WorkflowGraph definition={definition} names={names} selected={selection} editable={editable && !action.isPending}
        onChange={change} onSelect={setSelection} onOpenNode={id => { const node = definition.nodes.find(value => value.id === id); if (node?.taskId) navigate(`/task/${node.taskId}`); }} />
      <aside className="workflow-inspector">
        <Form layout="vertical" autoComplete="off">
          <Form.Item label="最大并行度"><InputNumber min={1} precision={0} value={definition.maxParallelism} disabled={!editable || action.isPending}
            onChange={value => change({ ...definition, maxParallelism: value ?? 4 })} /></Form.Item>
          {selectedNode ? <>
            <Form.Item label="引用任务"><Select showSearch={{ onSearch: setSearch, filterOption: false }} style={{ width: '100%' }}
              placeholder="搜索已发布批任务" value={selectedNode.taskId || undefined} options={options} loading={tasks.isFetching}
              disabled={!editable || action.isPending} onChange={taskId => change({ ...definition, nodes: definition.nodes.map(node => node.id === selectedNode.id ? { ...node, taskId } : node) })} /></Form.Item>
            {tasks.isError && <CompactAlert type="error" message="任务选项加载失败" action={<Button onClick={() => void tasks.refetch()}>重试</Button>} />}
            {selectedNode.taskId && <Button type="link" onClick={() => navigate(`/task/${selectedNode.taskId}`)}>查看引用任务</Button>}
            <Typography.Paragraph type="secondary">所有前置任务执行成功后启动。</Typography.Paragraph>
          </> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="选择节点查看引用任务" />}
        </Form>
      </aside>
    </div>
    <Modal open={blocker.state === 'blocked'} title="工作流定义尚未保存" okText="放弃修改并离开" cancelText="继续编辑"
      onOk={() => blocker.state === 'blocked' && blocker.proceed()} onCancel={() => blocker.state === 'blocked' && blocker.reset()}>
      当前修改尚未保存，离开后将丢失这些修改。
    </Modal>
  </section>;
}
