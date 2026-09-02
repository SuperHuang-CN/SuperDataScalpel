import { FileTextOutlined, UsergroupAddOutlined } from '@ant-design/icons';
import { Badge, Button, Col, Drawer, Form, Input, Row, Space, Tag, Typography, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ContextHelp } from '../../../shared/components/ContextualFeedback';
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
        rootClassName="business-overlay business-drawer-overlay"
        className="data-model-drawer api-consumer-drawer"
        title={(
          <div className="data-model-drawer-title">
            <span className="data-model-drawer-title-icon" aria-hidden="true"><UsergroupAddOutlined /></span>
            <span className="data-model-drawer-title-copy">
              <span>{editing ? '修改 API 消费者' : '新建 API 消费者'}</span>
              <Typography.Text type="secondary">维护调用方身份及其跨网关稳定标识</Typography.Text>
            </span>
          </div>
        )}
        extra={<Tag className="data-model-drawer-header-tag">{consumer ? `v${consumer.revision}` : '新消费者'}</Tag>}
        open={open}
        size={720}
        onClose={close}
        destroyOnHidden
        footer={(
          <div className="data-model-drawer-footer">
            <Badge
              status={consumer?.gatewayBindings.length ? 'success' : 'processing'}
              text={consumer
                ? consumer.gatewayBindings.length
                  ? `v${consumer.revision} · 已绑定 ${consumer.gatewayBindings.length} 个网关`
                  : `v${consumer.revision} · 尚未同步到网关`
                : '创建后自动同步到已配置网关'}
            />
            <Space>
              <Button onClick={close}>取消</Button>
              <Button
                type="primary"
                loading={createMutation.isPending || updateMutation.isPending}
                onClick={() => form.submit()}
              >
                {editing ? '保存修改' : '创建消费者'}
              </Button>
            </Space>
          </div>
        )}
      >
        <Form<ApiConsumerFormValues>
          name="api-consumer-editor-form"
          className="data-model-form api-consumer-form"
          autoComplete="off"
          form={form}
          layout="vertical"
          onFinish={(values) => void submit(values)}
        >
          <section className="data-model-form-section">
            <header className="data-model-form-section-header">
              <span className="data-model-form-section-icon" aria-hidden="true"><FileTextOutlined /></span>
              <span className="data-model-form-section-copy">
                <span className="data-model-form-section-title">消费者身份</span>
                <Typography.Text type="secondary">设置稳定编码、显示名称与调用方说明</Typography.Text>
              </span>
            </header>
            <div className="data-model-form-section-body">
              <Row gutter={14}>
                <Col xs={24} sm={12}>
                  <div className="data-model-form-help-field">
                    <div className="data-model-form-external-label">
                      <label htmlFor="api-consumer-editor-form_code">
                        {!editing && <span aria-hidden="true">*</span>}
                        消费者编码
                      </label>
                      <ContextHelp
                        ariaLabel="查看消费者编码规则"
                        content={editing
                          ? '编码是跨网关稳定标识，创建后不可修改。'
                          : '创建后不可修改；建议使用调用系统的稳定英文标识。'}
                      />
                    </div>
                    <Form.Item
                      name="code"
                      rules={editing ? [] : [
                        { required: true, whitespace: true, message: '请输入消费者编码' },
                        {
                          pattern: /^[a-z][a-z0-9._-]{1,63}$/,
                          message: '以小写字母开头，仅支持小写字母、数字、点、下划线和连字符，长度 2～64 位',
                        },
                      ]}
                    >
                      <Input
                        aria-label="消费者编码"
                        name="api-consumer-code"
                        autoComplete="off"
                        autoFocus={!editing}
                        disabled={editing}
                        maxLength={64}
                        placeholder="如：customer-portal"
                      />
                    </Form.Item>
                  </div>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item
                    label="名称"
                    name="name"
                    rules={[
                      { required: true, whitespace: true, message: '请输入消费者名称' },
                      { max: 100, message: '名称不能超过 100 个字符' },
                    ]}
                  >
                    <Input name="api-consumer-name" autoComplete="off" autoFocus={editing} maxLength={100} placeholder="输入调用方名称" />
                  </Form.Item>
                </Col>
                <Col span={24}>
                  <Form.Item
                    label="说明"
                    name="description"
                    rules={[{ max: 1000, message: '说明不能超过 1000 个字符' }]}
                  >
                    <Input.TextArea name="api-consumer-description" autoComplete="off" rows={4} maxLength={1000} showCount placeholder="说明调用系统、责任主体或使用场景" />
                  </Form.Item>
                </Col>
              </Row>
            </div>
          </section>
        </Form>
      </Drawer>
    </>
  );
};
