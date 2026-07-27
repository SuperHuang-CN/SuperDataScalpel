import { Button, Col, Drawer, Form, Input, Row, Space, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useCreateApiConsumer, useUpdateApiConsumer } from '../hooks/useApiConsumers';
import type {
  ApiConsumer,
  CreateApiConsumerRequest,
  GatewayConsumerBinding,
  UpdateApiConsumerRequest,
} from '../model/apiConsumer';

interface ApiConsumerDrawerProps {
  open: boolean;
  consumer: ApiConsumer | null;
  onClose: () => void;
}

interface ApiConsumerFormValues {
  code?: string;
  name: string;
  description?: string;
}

const normalizedOptionalText = (value: string | undefined): string | undefined => value?.trim() || undefined;

const synchronizationFailure = (bindings: GatewayConsumerBinding[]) => (
  bindings.find((binding) => binding.syncStatus === 'SYNC_FAILED')?.lastError
);

export const ApiConsumerDrawer = ({ open, consumer, onClose }: ApiConsumerDrawerProps) => {
  const [form] = Form.useForm<ApiConsumerFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const createMutation = useCreateApiConsumer();
  const updateMutation = useUpdateApiConsumer();
  const editing = Boolean(consumer);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    if (consumer) {
      form.setFieldsValue({
        code: consumer.code,
        name: consumer.name,
        description: consumer.description ?? undefined,
      });
    }
  }, [consumer, form, open]);

  const close = () => {
    form.resetFields();
    onClose();
  };

  const submit = async (values: ApiConsumerFormValues) => {
    const request: UpdateApiConsumerRequest = {
      name: values.name.trim(),
      description: normalizedOptionalText(values.description),
    };
    try {
      const response = consumer
        ? await updateMutation.mutateAsync({ id: consumer.id, request })
        : await createMutation.mutateAsync({
          ...request,
          code: values.code?.trim().toLowerCase() ?? '',
        } satisfies CreateApiConsumerRequest);
      const failure = synchronizationFailure(response.gatewayBindings);
      if (failure) {
        messageApi.warning(`消费者已保存，但网关同步失败：${failure}`);
      } else {
        messageApi.success(consumer ? '消费者已保存并同步' : '消费者已创建并同步');
      }
      close();
    } catch (error) {
      messageApi.error(error instanceof ApiError
        ? error.problem?.detail ?? error.message
        : '保存消费者失败');
    }
  };

  return (
    <>
      {messageContext}
      <Drawer
        title={editing ? '修改 API 消费者' : '新建 API 消费者'}
        open={open}
        size={560}
        onClose={close}
        destroyOnHidden
        footer={(
          <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
            <Button onClick={close}>取消</Button>
            <Button
              type="primary"
              loading={createMutation.isPending || updateMutation.isPending}
              onClick={() => form.submit()}
            >
              {editing ? '保存' : '创建'}
            </Button>
          </Space>
        )}
      >
        <Form<ApiConsumerFormValues>
          form={form}
          layout="vertical"
          onFinish={(values) => void submit(values)}
        >
          <Row gutter={12}>
            <Col span={24}>
              <Form.Item
                label="消费者编码"
                name="code"
                extra={editing ? '编码是跨网关稳定标识，创建后不可修改。' : '创建后不可修改；建议使用调用系统的稳定英文标识。'}
                rules={editing ? [] : [
                  { required: true, whitespace: true, message: '请输入消费者编码' },
                  {
                    pattern: /^[a-z][a-z0-9._-]{1,63}$/,
                    message: '以小写字母开头，仅支持小写字母、数字、点、下划线和连字符，长度 2～64 位',
                  },
                ]}
              >
                <Input
                  autoFocus={!editing}
                  disabled={editing}
                  maxLength={64}
                  placeholder="如：customer-portal"
                />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item
                label="名称"
                name="name"
                rules={[
                  { required: true, whitespace: true, message: '请输入消费者名称' },
                  { max: 100, message: '名称不能超过 100 个字符' },
                ]}
              >
                <Input autoFocus={editing} maxLength={100} />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item
                label="说明"
                name="description"
                rules={[{ max: 1000, message: '说明不能超过 1000 个字符' }]}
              >
                <Input.TextArea rows={4} maxLength={1000} showCount />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Drawer>
    </>
  );
};
