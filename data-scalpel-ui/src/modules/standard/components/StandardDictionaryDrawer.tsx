import { Button, Col, Drawer, Form, Input, Row, Select, Space, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  useCreateStandardDictionary,
  useUpdateStandardDictionary,
} from '../hooks/useStandardDictionaries';
import { useStandardFormLeaveGuard } from '../hooks/useStandardFormLeaveGuard';
import {
  standardDictionaryValueTypeLabels,
  type CreateStandardDictionaryRequest,
  type StandardDictionary,
  type StandardDictionaryValueType,
} from '../model/standardDictionary';

interface Props {
  open: boolean;
  dictionary: StandardDictionary | null;
  onClose: () => void;
}

const valueTypeOptions = (
  Object.entries(standardDictionaryValueTypeLabels) as [StandardDictionaryValueType, string][]
).map(([value, label]) => ({ value, label }));

export const StandardDictionaryDrawer = ({ open, dictionary, onClose }: Props) => {
  const [form] = Form.useForm<CreateStandardDictionaryRequest>();
  const [messageApi, messageContext] = message.useMessage();
  const [dirty, setDirty] = useState(false);
  const createMutation = useCreateStandardDictionary();
  const updateMutation = useUpdateStandardDictionary();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue(dictionary ? {
      code: dictionary.code,
      name: dictionary.name,
      valueType: dictionary.valueType,
      description: dictionary.description ?? undefined,
    } : { valueType: 'STRING' });
  }, [dictionary, form, open]);

  const discard = useCallback(() => {
    setDirty(false);
    form.resetFields();
    onClose();
  }, [form, onClose]);
  const close = useStandardFormLeaveGuard({
    dirty: open && dirty,
    content: '码表表单中的修改尚未保存。',
    onDiscard: discard,
  });

  const submit = async (values: CreateStandardDictionaryRequest) => {
    const request = {
      ...values,
      code: values.code.trim(),
      name: values.name.trim(),
      description: values.description?.trim() || undefined,
    };
    try {
      if (dictionary) {
        await updateMutation.mutateAsync({
          id: dictionary.id,
          request: { ...request, expectedVersion: dictionary.version },
        });
        messageApi.success('码表已保存');
      } else {
        await createMutation.mutateAsync(request);
        messageApi.success('码表已创建');
      }
      setDirty(false);
      form.resetFields();
      onClose();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存码表失败');
    }
  };

  const definitionLocked = Boolean(dictionary);

  return (
    <>
      {messageContext}
      <Drawer
        title={dictionary ? '修改码表' : '新建码表'}
        open={open}
        width={560}
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
        <Form<CreateStandardDictionaryRequest>
          autoComplete="off"
          form={form}
          layout="vertical"
          onFieldsChange={() => setDirty(true)}
          onFinish={(values) => void submit(values)}
        >
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                label="码表编码"
                name="code"
                rules={[
                  { required: true, whitespace: true, message: '请输入码表编码' },
                  { pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/, message: '编码以字母开头，只能包含字母、数字和下划线' },
                ]}
                extra={definitionLocked ? '已有节点或字段引用时，服务端会保护编码不可修改。' : undefined}
              >
                <Input placeholder="如：ORDER_STATUS" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="码表名称"
                name="name"
                rules={[
                  { required: true, whitespace: true, message: '请输入码表名称' },
                  { max: 100 },
                ]}
              >
                <Input placeholder="如：订单状态" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="取值类型"
                name="valueType"
                rules={[{ required: true, message: '请选择取值类型' }]}
                extra={definitionLocked ? '已有节点或字段引用时，服务端会保护类型不可修改。' : undefined}
              >
                <Select options={valueTypeOptions} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item label="说明" name="description" rules={[{ max: 500 }]}>
                <Input.TextArea rows={4} showCount maxLength={500} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Drawer>
    </>
  );
};
