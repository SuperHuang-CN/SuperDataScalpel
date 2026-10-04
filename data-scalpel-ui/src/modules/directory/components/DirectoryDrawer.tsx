import { ApartmentOutlined, FolderOpenOutlined, FolderOutlined } from '@ant-design/icons';
import { OverlayTitle } from '../../../shared/components/OverlayTitle';
import { Badge, Button, Drawer, Form, Input, InputNumber, Space, Tag, TreeSelect, Typography, message, ConfigProvider } from 'antd';
import { useEffect, useId } from 'react';
import { ApiError } from '../../../shared/api/http';
import { workspaceResourceTheme } from '../../../shared/theme/workspaceResourceTheme';
import { useCreateDirectory, useUpdateDirectory } from '../hooks/useDirectories';
import { directoryTreeSelectData, type DirectoryScope, type DirectoryTreeNode } from '../model/directory';
import { directoryTreeSelectOptions } from './directoryTreeSelectOptions';

interface DirectoryDrawerProps {
  scope: DirectoryScope;
  label?: string;
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

const findDirectoryName = (nodes: DirectoryTreeNode[], id: string | undefined): string | undefined => {
  if (!id) return undefined;
  for (const node of nodes) {
    if (node.id === id) return node.name;
    const nested = findDirectoryName(node.children, id);
    if (nested) return nested;
  }
  return undefined;
};

export const DirectoryDrawer = ({ scope, label = '目录', open, directory, initialParentId, tree, onClose }: DirectoryDrawerProps) => {
  const [form] = Form.useForm<DirectoryFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const createMutation = useCreateDirectory(scope);
  const updateMutation = useUpdateDirectory(scope);
  const editing = Boolean(directory);
  const resourceStyle = scope === 'DATA_SOURCE' || scope === 'FILE_DATASET' || scope === 'PANORAMA' || scope === 'MODEL' || scope === 'METRIC' || scope === 'BUSINESS_OBJECT' || scope === 'TASK' || scope === 'DATA_SERVICE' || scope === 'MCP_SERVER' || scope === 'ASSET';
  const sortHintId = useId();
  const watchedParentId = Form.useWatch('parentId', form);
  const parentName = findDirectoryName(tree, watchedParentId);
  const pending = createMutation.isPending || updateMutation.isPending;

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
        messageApi.success(`${label}已保存`);
      } else {
        await createMutation.mutateAsync({ ...values, scope });
        messageApi.success(`${label}已创建`);
      }
      onClose();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存目录失败');
    }
  };

  return (
    <ConfigProvider theme={resourceStyle ? workspaceResourceTheme : undefined}>
      {messageContext}
      <Drawer
        rootClassName={`business-overlay business-drawer-overlay${resourceStyle ? ' workspace-resource-overlay' : ''}`}
        className={resourceStyle ? 'resource-directory-editor' : 'directory-editor-drawer'}
        title={<OverlayTitle title={editing ? `修改${label}` : `新建${label}`} icon={<FolderOpenOutlined />} description={resourceStyle ? undefined : '组织资源层级、显示顺序与目录说明'} />}
        extra={resourceStyle ? undefined : <Tag className="directory-editor-drawer-header-tag">{editing ? directory?.name : label}</Tag>}
        open={open}
        size={resourceStyle ? 'min(520px, 100vw)' : 'min(600px, 100vw)'}
        closable={pending ? false : { placement: 'end' }}
        maskClosable={!pending}
        onClose={onClose}
        destroyOnHidden
        footer={(
          <div className={resourceStyle ? 'resource-directory-footer' : 'directory-editor-drawer-footer'}>
            {resourceStyle ? <span className="resource-directory-location" title={parentName}>{parentName ? `上级：${parentName}` : `位于顶级${label}`}</span> : <Badge status="default" text={parentName ? `上级：${parentName}` : `位于顶级${label}`} />}
            <Space>
              <Button disabled={pending} onClick={onClose}>取消</Button>
              <Button type="primary" loading={pending} onClick={() => form.submit()}>{editing ? '保存修改' : `创建${label}`}</Button>
            </Space>
          </div>
        )}
      >
        <Form<DirectoryFormValues> autoComplete="off" form={form} layout="vertical" className={resourceStyle ? 'resource-directory-form' : 'directory-editor-form'} onFinish={(values) => void submit(values)}>
          {resourceStyle ? (
            <>
              <Form.Item label={`${label}名称`} name="name" rules={[{ required: true, whitespace: true, message: '请输入目录名称' }, { max: 100, message: '名称不能超过 100 个字符' }]}>
                <Input name="directory-display-name" autoComplete="off" autoFocus placeholder="输入目录名称" />
              </Form.Item>
              <Form.Item label={`上级${label}`} name="parentId">
                <TreeSelect allowClear treeDefaultExpandAll treeIcon treeData={directoryTreeSelectOptions(tree)} placeholder={`顶级${label}`}
                  prefix={<FolderOutlined className="workspace-directory-prefix" />}
                  classNames={{ popup: { root: 'workspace-resource-select' } }}
                />
              </Form.Item>
              <div className="resource-directory-order">
                <Form.Item label="显示排序" name="sortOrder" rules={[{ required: true, message: '请输入排序值' }]}>
                  <InputNumber precision={0} aria-describedby={sortHintId} />
                </Form.Item>
                <span id={sortHintId}>数字越小，排列越靠前</span>
              </div>
              <Form.Item label={<span>说明 <span className="resource-field-optional">选填</span></span>} name="description" rules={[{ max: 500, message: '说明不能超过 500 个字符' }]}>
                <Input.TextArea name="directory-description" autoComplete="off" autoSize={{ minRows: 4, maxRows: 6 }} maxLength={500} showCount placeholder="补充目录用途或内容说明" />
              </Form.Item>
            </>
          ) : <section className="directory-editor-form-section">
            <header className="directory-editor-form-section-header">
              <span className="directory-editor-form-section-icon" aria-hidden="true"><ApartmentOutlined /></span>
              <span className="directory-editor-form-section-copy">
                <span>目录信息</span>
                <Typography.Text type="secondary">设置层级归属和在目录树中的展示方式</Typography.Text>
              </span>
            </header>
            <div className="directory-editor-form-section-body">
              <Form.Item label={`上级${label}`} name="parentId">
                <TreeSelect allowClear treeDefaultExpandAll treeData={directoryTreeSelectData(tree)} placeholder={`顶级${label}`} />
              </Form.Item>
              <div className="directory-editor-form-grid">
                <Form.Item label="名称" name="name" rules={[{ required: true, whitespace: true, message: '请输入目录名称' }, { max: 100, message: '名称不能超过 100 个字符' }]}>
                  <Input name="directory-display-name" autoComplete="off" autoFocus />
                </Form.Item>
                <Form.Item label="显示排序" name="sortOrder" rules={[{ required: true, message: '请输入排序值' }]}>
                  <InputNumber precision={0} className="directory-number-input" />
                </Form.Item>
              </div>
              <Form.Item label="说明" name="description" rules={[{ max: 500, message: '说明不能超过 500 个字符' }]}>
                <Input.TextArea name="directory-description" autoComplete="off" rows={3} maxLength={500} showCount />
              </Form.Item>
            </div>
          </section>}
        </Form>
      </Drawer>
    </ConfigProvider>
  );
};
