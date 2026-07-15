import { Button, Drawer, Form, Input, Space, TreeSelect } from 'antd';
import { useEffect } from 'react';
import { directoryTreeSelectData, type DirectoryTreeNode } from '../../directory';
import type { DataTask } from '../model/task';

interface TaskDrawerProps {
  open: boolean;
  task: DataTask | null;
  directories: DirectoryTreeNode[];
  onClose: () => void;
  onSubmit: (values: { code?: string; name: string; directoryId?: string; description?: string }) => Promise<void>;
}

export const TaskDrawer = ({ open, task, directories, onClose, onSubmit }: TaskDrawerProps) => {
  const [form] = Form.useForm<{ code?: string; name: string; directoryId?: string; description?: string }>();

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue(task ? {
      name: task.name,
      directoryId: task.directoryId ?? undefined,
      description: task.description ?? undefined,
    } : { code: '', name: '', directoryId: undefined, description: '' });
  }, [form, open, task]);

  return (
    <Drawer
      title={task ? '修改任务基本信息' : '新建本地 SQL 任务'}
      open={open}
      width={480}
      onClose={onClose}
      destroyOnHidden
      footer={<Space>
        <Button onClick={onClose}>取消</Button>
        <Button type="primary" onClick={() => void form.validateFields().then(onSubmit)}>{task ? '保存' : '创建'}</Button>
      </Space>}
    >
      <Form form={form} layout="vertical">
        {!task && <Form.Item name="code" label="任务编码" rules={[{ required: true, message: '请输入任务编码' }, { max: 64 }]}>
          <Input placeholder="例如 daily_order_summary" />
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
