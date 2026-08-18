import { Alert, Button, Drawer, Form, Input, Space, Switch, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  useCreateStandardDictionaryItem,
  useUpdateStandardDictionaryItem,
} from '../hooks/useStandardDictionaries';
import { useStandardFormLeaveGuard } from '../hooks/useStandardFormLeaveGuard';
import type {
  CreateStandardDictionaryItemRequest,
  StandardDictionary,
  StandardDictionaryTreeNode,
} from '../model/standardDictionary';

interface Props {
  open: boolean;
  dictionary: StandardDictionary;
  item: StandardDictionaryTreeNode | null;
  parent: StandardDictionaryTreeNode | null;
  referenced: boolean;
  onClose: () => void;
}

interface FormValue {
  code: string;
  name: string;
  enabled: boolean;
  description?: string;
}

export const StandardDictionaryItemDrawer = ({
  open,
  dictionary,
  item,
  parent,
  referenced,
  onClose,
}: Props) => {
  const [form] = Form.useForm<FormValue>();
  const [messageApi, contextHolder] = message.useMessage();
  const [dirty, setDirty] = useState(false);
  const createMutation = useCreateStandardDictionaryItem();
  const updateMutation = useUpdateStandardDictionaryItem();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue(item ? {
      code: item.code,
      name: item.name,
      enabled: item.enabled,
      description: item.description ?? undefined,
    } : { enabled: true });
  }, [form, item, open]);

  const discard = useCallback(() => {
    setDirty(false);
    form.resetFields();
    onClose();
  }, [form, onClose]);
  const close = useStandardFormLeaveGuard({
    dirty: open && dirty,
    content: '码表节点表单中的修改尚未保存。',
    onDiscard: discard,
  });

  const submit = async (value: FormValue) => {
    try {
      if (item) {
        await updateMutation.mutateAsync({
          dictionaryId: dictionary.id,
          itemId: item.id,
          request: {
            expectedVersion: dictionary.version,
            code: value.code.trim(),
            name: value.name.trim(),
            description: value.description?.trim() || undefined,
          },
        });
        messageApi.success('码表节点已保存');
      } else {
        const request: CreateStandardDictionaryItemRequest = {
          expectedVersion: dictionary.version,
          ...(parent ? { parentId: parent.id } : {}),
          code: value.code.trim(),
          name: value.name.trim(),
          enabled: value.enabled,
          description: value.description?.trim() || undefined,
        };
        await createMutation.mutateAsync({ dictionaryId: dictionary.id, request });
        messageApi.success(parent ? '子节点已创建' : '根节点已创建');
      }
      setDirty(false);
      form.resetFields();
      onClose();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存码表节点失败');
    }
  };

  return (
    <>
      {contextHolder}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        title={item ? '修改码表节点' : parent ? `新增“${parent.name}”的子节点` : '新增根节点'}
        open={open}
        width={520}
        destroyOnHidden
        onClose={close}
        footer={(
          <Space>
            <Button onClick={close}>取消</Button>
            <Button
              type="primary"
              loading={createMutation.isPending || updateMutation.isPending}
              onClick={() => form.submit()}
            >
              保存
            </Button>
          </Space>
        )}
      >
        {item && referenced && (
          <Alert
            showIcon
            type="info"
            title="码表已被模型字段引用，节点编码不可修改；可修改名称、说明、位置和状态。"
            style={{ marginBottom: 12 }}
          />
        )}
        <Form<FormValue>
          autoComplete="off"
          form={form}
          layout="vertical"
          onFieldsChange={() => setDirty(true)}
          onFinish={(value) => void submit(value)}
        >
          <Form.Item
            label="节点编码"
            name="code"
            rules={[
              { required: true, whitespace: true, message: '请输入节点编码' },
              { max: 256 },
            ]}
            extra={`当前码表按 ${dictionary.valueType} 规范化并在整张码表内校验唯一。`}
          >
            <Input disabled={Boolean(item && referenced)} />
          </Form.Item>
          <Form.Item
            label="节点名称"
            name="name"
            rules={[{ required: true, whitespace: true, message: '请输入节点名称' }, { max: 100 }]}
          >
            <Input />
          </Form.Item>
          {!item && (
            <Form.Item label="创建后启用" name="enabled" valuePropName="checked">
              <Switch />
            </Form.Item>
          )}
          <Form.Item label="说明" name="description" rules={[{ max: 500 }]}>
            <Input.TextArea rows={4} showCount maxLength={500} />
          </Form.Item>
        </Form>
      </Drawer>
    </>
  );
};
