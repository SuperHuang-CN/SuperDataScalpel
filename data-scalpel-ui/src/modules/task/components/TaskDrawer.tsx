import { Button, Drawer, Form, Input, Select, Space, TreeSelect } from 'antd';
import { useEffect } from 'react';
import { directoryTreeSelectData, type DirectoryTreeNode } from '../../directory';
import { computeEngineRegistrationStateLabels, isComputeEngineSelectable, useComputeEngines } from '../../computeengine';
import { taskTypeLabels, type DataTask, type TaskType } from '../model/task';

export interface TaskDrawerValues {
  name: string;
  type: TaskType;
  directoryId?: string;
  description?: string;
  computeEngineId?: string;
}

interface TaskDrawerProps {
  open: boolean;
  task: DataTask | null;
  initialDirectoryId?: string;
  directories: DirectoryTreeNode[];
  onClose: () => void;
  onSubmit: (values: TaskDrawerValues) => Promise<void>;
}

export const TaskDrawer = ({
  open,
  task,
  initialDirectoryId,
  directories,
  onClose,
  onSubmit,
}: TaskDrawerProps) => {
  const [form] = Form.useForm<TaskDrawerValues>();
  const taskType = Form.useWatch('type', form);
  const sparkTask = taskType !== undefined && taskType !== 'LOCAL_SQL';
  const computeEnginesQuery = useComputeEngines(
    { page: 0, size: 500, sort: 'name' },
    open && sparkTask,
  );

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue(task ? {
      name: task.name,
      type: task.type,
      directoryId: task.directoryId ?? undefined,
      description: task.description ?? undefined,
      computeEngineId: task.computeEngineId ?? undefined,
    } : { name: '', type: 'LOCAL_SQL', directoryId: initialDirectoryId, description: '', computeEngineId: undefined });
  }, [form, initialDirectoryId, open, task]);

  return (
    <Drawer
      rootClassName="business-overlay business-drawer-overlay"
      title={task ? '修改任务基本信息' : '新建任务'}
      open={open}
      size={480}
      onClose={onClose}
      destroyOnHidden
      footer={<Space>
        <Button onClick={onClose}>取消</Button>
        <Button type="primary" onClick={() => void form.validateFields().then(onSubmit)}>{task ? '保存' : '创建'}</Button>
      </Space>}
    >
      <Form autoComplete="off" form={form} layout="vertical">
        {!task && <Form.Item name="type" label="任务类型" rules={[{ required: true, message: '请选择任务类型' }]}>
          <Select options={Object.entries(taskTypeLabels).map(([value, label]) => ({ value, label }))} />
        </Form.Item>}
        {task && <Form.Item name="type" label="任务类型">
          <Select disabled options={Object.entries(taskTypeLabels).map(([value, label]) => ({ value, label }))} />
        </Form.Item>}
        {sparkTask && <Form.Item
          name="computeEngineId"
          label="计算引擎"
          extra={task?.status === 'PUBLISHED' ? '已发布任务需先停用，才能更换计算引擎。' : '发布和执行前，计算引擎必须已激活且健康。'}
          rules={[{ required: true, message: '请选择计算引擎' }]}
        >
          <Select
            allowClear
            loading={computeEnginesQuery.isFetching}
            disabled={task?.status === 'PUBLISHED'}
            placeholder="请选择计算引擎"
            options={[
              ...(computeEnginesQuery.data?.content ?? []).map((engine) => ({
                value: engine.id,
                label: `${engine.name} · ${computeEngineRegistrationStateLabels[engine.registrationState]}`,
                disabled: !isComputeEngineSelectable(engine) && engine.id !== task?.computeEngineId,
              })),
              ...((task?.computeEngineId && !(computeEnginesQuery.data?.content ?? []).some((engine) => engine.id === task.computeEngineId))
                ? [{ value: task.computeEngineId, label: `${task.computeEngineName ?? '已删除计算引擎'} · 当前绑定`, disabled: true }]
                : []),
            ]}
          />
        </Form.Item>}
        <Form.Item name="name" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }, { max: 100 }]}>
          <Input />
        </Form.Item>
        <Form.Item name="directoryId" label="目录">
          <TreeSelect allowClear treeData={directoryTreeSelectData(directories)} treeDefaultExpandAll />
        </Form.Item>
        <Form.Item name="description" label="说明" rules={[{ max: 1000 }]}>
          <Input.TextArea rows={4} />
        </Form.Item>
      </Form>
    </Drawer>
  );
};
