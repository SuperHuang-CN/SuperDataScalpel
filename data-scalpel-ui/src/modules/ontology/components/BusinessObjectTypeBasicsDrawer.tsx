import { Button, Drawer, Form, Input, Space, TreeSelect } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createBusinessObjectType, updateBusinessObjectType } from '../api/businessObjectTypeApi';
import type { BusinessObjectType, BusinessObjectTypeBasics } from '../model/businessObjectType';
import { directoryTreeSelectData, useDirectoryTree } from '../../directory';
import { useCurrentUser } from '../../system';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { invalidateBusinessObjectTypes } from '../hooks/useBusinessObjectTypes';

export const BusinessObjectTypeBasicsDrawer = ({
  objectType, directoryId, onClose, onSaved,
}: {
  objectType?: BusinessObjectType;
  directoryId?: string | null;
  onClose: () => void;
  onSaved: (type: BusinessObjectType) => void;
}) => {
  const [form] = Form.useForm<BusinessObjectTypeBasics & { code: string }>();
  const client = useQueryClient();
  const user = useCurrentUser();
  const canViewDirectories = user.data?.permissions.includes('directory.view') ?? false;
  const directories = useDirectoryTree('BUSINESS_OBJECT', canViewDirectories);
  const mutation = useMutation({
    mutationFn: (value: BusinessObjectTypeBasics & { code: string }) => objectType
      ? updateBusinessObjectType(objectType.id, value)
      : createBusinessObjectType(value),
    onSuccess: async result => {
      await invalidateBusinessObjectTypes(client);
      onSaved(result);
    },
  });
  return <Drawer
    open
    title={objectType ? '修改业务对象类型资料' : '新建业务对象类型'}
    width={640}
    rootClassName="business-overlay business-drawer-overlay"
    onClose={onClose}
    footer={<Space><Button onClick={onClose}>取消</Button><Button type="primary" loading={mutation.isPending} onClick={() => form.submit()}>{objectType ? '保存' : '创建'}</Button></Space>}
  >
    <Form
      form={form}
      autoComplete="off"
      layout="vertical"
      initialValues={objectType ?? { directoryId: directoryId ?? null }}
      onFinish={value => mutation.mutate(value)}
    >
      <Form.Item label="对象类型名称" name="name" rules={[{ required: true, whitespace: true, max: 100 }]}><Input maxLength={100} /></Form.Item>
      <Form.Item label="对象类型编码" name="code" rules={[{ required: !objectType, pattern: /^[a-z][a-z0-9_]{0,63}$/, message: '小写字母开头，仅含小写字母、数字和下划线，最长 64 位' }]}>
        <Input disabled={Boolean(objectType)} maxLength={64} placeholder="例如 reservoir" />
      </Form.Item>
      <Form.Item label="所属目录" name="directoryId"><TreeSelect allowClear disabled={!canViewDirectories} treeData={directoryTreeSelectData(directories.data ?? [])} loading={directories.isFetching} /></Form.Item>
      <Form.Item label="业务负责人" name="ownerName" extra="填写负责解释该对象含义的人员或部门，无需平台账号。"><Input maxLength={100} placeholder="例如：水文监测科 / 张三" /></Form.Item>
      <Form.Item label="业务说明" name="summary"><Input.TextArea rows={4} maxLength={1000} placeholder="说明这个对象是什么，适用在哪些业务场景。" /></Form.Item>
    </Form>
    {mutation.isError && <InlineFeedback tone="error" label={mutation.error.message} />}
  </Drawer>;
};
