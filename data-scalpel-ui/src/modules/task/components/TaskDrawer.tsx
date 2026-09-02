import { ApartmentOutlined, CloudServerOutlined, FileTextOutlined } from '@ant-design/icons';
import { Badge, Button, Col, Drawer, Form, Input, Row, Select, Space, Tag, TreeSelect, Typography } from 'antd';
import { useEffect } from 'react';
import { ContextHelp } from '../../../shared/components/ContextualFeedback';
import { directoryTreeSelectData, type DirectoryTreeNode } from '../../directory';
import { computeEngineRegistrationStateLabels, isComputeEngineSelectable, useComputeEngines } from '../../computeengine';
import { taskStatusLabels, taskTypeLabels, type DataTask, type TaskType } from '../model/task';
import type { TaskAssistantCreateDraft } from '../model/taskAssistant';

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
  initialDraft?: TaskAssistantCreateDraft;
  directories: DirectoryTreeNode[];
  onClose: () => void;
  onSubmit: (values: TaskDrawerValues) => Promise<void>;
}

export const TaskDrawer = ({
  open,
  task,
  initialDirectoryId,
  initialDraft,
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
    } : initialDraft ? {
      name: initialDraft.name,
      type: 'SPARK_CANVAS',
      directoryId: initialDraft.directoryId ?? undefined,
      description: initialDraft.description ?? '',
      computeEngineId: undefined,
    } : { name: '', type: 'LOCAL_SQL', directoryId: initialDirectoryId, description: '', computeEngineId: undefined });
  }, [form, initialDirectoryId, initialDraft, open, task]);

  const submit = async () => {
    let values: TaskDrawerValues;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    await onSubmit(values);
  };

  return (
    <Drawer
      rootClassName="business-overlay business-drawer-overlay"
      className="data-model-drawer task-basic-drawer"
      title={(
        <div className="data-model-drawer-title">
          <span className="data-model-drawer-title-icon" aria-hidden="true"><ApartmentOutlined /></span>
          <span className="data-model-drawer-title-copy">
            <span>{task ? '修改任务基本信息' : '新建任务'}</span>
            <Typography.Text type="secondary">维护任务身份、所属目录与默认执行资源</Typography.Text>
          </span>
        </div>
      )}
      extra={<Tag className="data-model-drawer-header-tag">{taskType ? taskTypeLabels[taskType] : '待选择类型'}</Tag>}
      open={open}
      width={720}
      onClose={onClose}
      destroyOnHidden
      footer={(
        <div className="data-model-drawer-footer">
          <Badge
            status={task?.status === 'PUBLISHED' ? 'success' : task?.status === 'DISABLED' ? 'default' : 'processing'}
            text={task ? `${taskStatusLabels[task.status]} · ${taskTypeLabels[task.type]}` : '创建后进入草稿状态'}
          />
          <Space>
            <Button onClick={onClose}>取消</Button>
            <Button type="primary" onClick={() => void submit()}>{task ? '保存修改' : '创建任务'}</Button>
          </Space>
        </div>
      )}
    >
      <Form name="task-basic-editor-form" className="data-model-form task-basic-form" autoComplete="off" form={form} layout="vertical">
        <section className="data-model-form-section">
          <header className="data-model-form-section-header">
            <span className="data-model-form-section-icon" aria-hidden="true"><FileTextOutlined /></span>
            <span className="data-model-form-section-copy">
              <span className="data-model-form-section-title">任务信息</span>
              <Typography.Text type="secondary">设置任务类型、名称、目录与业务说明</Typography.Text>
            </span>
          </header>
          <div className="data-model-form-section-body">
            <Row gutter={14}>
              <Col span={12} xs={24} sm={12}>
                <Form.Item name="type" label="任务类型" rules={[{ required: true, message: '请选择任务类型' }]}>
                  <Select
                    disabled={Boolean(task || initialDraft)}
                    options={Object.entries(taskTypeLabels).map(([value, label]) => ({ value, label }))}
                  />
                </Form.Item>
              </Col>
              <Col span={12} xs={24} sm={12}>
                <Form.Item name="name" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }, { max: 100 }]}>
                  <Input name="task-basic-name" autoComplete="off" placeholder="输入便于识别的任务名称" />
                </Form.Item>
              </Col>
              <Col span={24}>
                <Form.Item name="directoryId" label="所属目录">
                  <TreeSelect allowClear treeData={directoryTreeSelectData(directories)} treeDefaultExpandAll placeholder="未分类" />
                </Form.Item>
              </Col>
              <Col span={24}>
                <Form.Item name="description" label="说明" rules={[{ max: 1000 }]}>
                  <Input.TextArea name="task-basic-description" autoComplete="off" rows={3} showCount maxLength={1000} placeholder="说明任务目标、数据口径或运行约束" />
                </Form.Item>
              </Col>
            </Row>
          </div>
        </section>

        {sparkTask && (
          <section className="data-model-form-section">
            <header className="data-model-form-section-header">
              <span className="data-model-form-section-icon" aria-hidden="true"><CloudServerOutlined /></span>
              <span className="data-model-form-section-copy">
                <span className="data-model-form-section-title-row">
                  <span className="data-model-form-section-title">执行资源</span>
                  <ContextHelp
                    ariaLabel="查看任务计算引擎规则"
                    content={task?.status === 'PUBLISHED' ? '已发布任务需先停用，才能更换计算引擎。' : '发布和执行前，计算引擎必须已激活且健康。'}
                  />
                </span>
                <Typography.Text type="secondary">选择任务发布和运行时使用的计算引擎</Typography.Text>
              </span>
            </header>
            <div className="data-model-form-section-body">
              <Form.Item
                name="computeEngineId"
                label="计算引擎"
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
              </Form.Item>
            </div>
          </section>
        )}
      </Form>
    </Drawer>
  );
};
