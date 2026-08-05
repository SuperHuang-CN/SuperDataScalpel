import { Button, Drawer, Form, Input, InputNumber, Space, TreeSelect, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useCreateDirectory, useUpdateDirectory } from '../hooks/useDirectories';
import { directoryTreeSelectData, type DirectoryScope, type DirectoryTreeNode } from '../model/directory';

interface DirectoryDrawerProps {
  scope: DirectoryScope;
  open: boolean;
  directory: DirectoryTreeNode | null;
  initialParentId?: string;
  tree: DirectoryTreeNode[];
  onClose: () => void;
}

interface DirectoryFormValues {
  parentId?: string;
  name: string;
  sortOrder: number;
  description?: string;
}

export const DirectoryDrawer = ({ scope, open, directory, initialParentId, tree, onClose }: DirectoryDrawerProps) => {
  const [form] = Form.useForm<DirectoryFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const createMutation = useCreateDirectory(scope);
  const updateMutation = useUpdateDirectory(scope);
  const editing = Boolean(directory);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({
      parentId: directory?.parentId ?? initialParentId,
      name: directory?.name,
      sortOrder: directory?.sortOrder ?? 0,
      description: directory?.description ?? undefined,
    });
  }, [directory, form, initialParentId, open]);

  const submit = async (values: DirectoryFormValues) => {
    try {
      if (directory) {
        await updateMutation.mutateAsync({ id: directory.id, request: values });
        messageApi.success('目录已保存');
      } else {
        await createMutation.mutateAsync({ ...values, scope });
        messageApi.success('目录已创建');
      }
      onClose();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存目录失败');
    }
  };

  return (
    <>
      {messageContext}
      <Drawer
        title={editing ? '修改目录' : '新建目录'}
        open={open}
        size={420}
        onClose={onClose}
        destroyOnHidden
        footer={<Space><Button onClick={onClose}>取消</Button><Button type="primary" loading={createMutation.isPending || updateMutation.isPending} onClick={() => form.submit()}>保存</Button></Space>}
      >
        <Form<DirectoryFormValues> autoComplete="off" form={form} layout="vertical" onFinish={(values) => void submit(values)}>
          <Form.Item label="上级目录" name="parentId">
            <TreeSelect allowClear treeDefaultExpandAll treeData={directoryTreeSelectData(tree)} placeholder="顶级目录" />
          </Form.Item>
          <Form.Item label="名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入目录名称' }, { max: 100, message: '名称不能超过 100 个字符' }]}>
            <Input autoFocus />
          </Form.Item>
          <Form.Item label="排序" name="sortOrder" rules={[{ required: true, message: '请输入排序值' }]}>
            <InputNumber precision={0} className="directory-number-input" />
          </Form.Item>
          <Form.Item label="说明" name="description" rules={[{ max: 500, message: '说明不能超过 500 个字符' }]}>
            <Input.TextArea rows={3} maxLength={500} />
          </Form.Item>
        </Form>
      </Drawer>
    </>
  );
};
