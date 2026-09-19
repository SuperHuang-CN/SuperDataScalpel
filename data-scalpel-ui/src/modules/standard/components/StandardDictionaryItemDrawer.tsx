import { NodeIndexOutlined, PartitionOutlined } from '@ant-design/icons';
import { Badge, Button, Col, Drawer, Form, Input, Row, Space, Switch, Tag, Typography, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
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
  const [operationError, setOperationError] = useState<string | null>(null);
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
    setOperationError(null);
    form.resetFields();
    onClose();
  }, [form, onClose]);
  const close = useStandardFormLeaveGuard({
    dirty: open && dirty,
    content: '码表节点表单中的修改尚未保存。',
    onDiscard: discard,
  });

  const submit = async (value: FormValue) => {
    setOperationError(null);
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
      const errorMessage = error instanceof ApiError ? error.message : '保存码表节点失败';
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
    }
  };

  const pending = createMutation.isPending || updateMutation.isPending;
  const footerStatus = operationError ? (
    <InlineFeedback
      tone="error"
      label={item ? '保存失败' : '创建失败'}
      detail={operationError}
      ariaLabel={item ? '查看节点保存失败详情' : '查看节点创建失败详情'}
    />
  ) : item && referenced ? (
    <InlineFeedback
      tone="warning"
      label="节点编码已受引用保护"
      detail="码表已被模型字段引用；可修改名称和说明，节点编码不可修改。"
      ariaLabel="查看节点编码保护详情"
    />
  ) : dirty ? (
    <Badge status="processing" text="有未保存的修改" />
  ) : (
    <Badge
      status="default"
      text={item ? `节点：${item.code}` : parent ? `父级：${parent.name}` : '将在根级创建节点'}
    />
  );

  return (
    <>
      {contextHolder}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        className="standard-dictionary-drawer standard-dictionary-item-drawer"
        title={(
          <div className="standard-dictionary-drawer-title">
            <span className="standard-dictionary-drawer-title-icon" aria-hidden="true"><NodeIndexOutlined /></span>
            <span className="standard-dictionary-drawer-title-copy">
              <span>{item ? '修改码表节点' : parent ? `新增“${parent.name}”的子节点` : '新增根节点'}</span>
              <Typography.Text type="secondary">维护节点值、显示名称和在码表树中的业务含义</Typography.Text>
            </span>
          </div>
        )}
        extra={<Tag className="standard-dictionary-drawer-header-tag">{dictionary.code}</Tag>}
        open={open}
        size="min(600px, 100vw)"
        destroyOnHidden
        closable={pending ? false : { placement: 'end' }}
        maskClosable={!pending}
        onClose={close}
        footer={(
          <div className="standard-dictionary-drawer-footer">
            {footerStatus}
            <Space>
              <Button disabled={pending} onClick={close}>取消</Button>
              <Button type="primary" loading={pending} onClick={() => form.submit()}>
                {item ? '保存修改' : '创建节点'}
              </Button>
            </Space>
          </div>
        )}
      >
        <Form<FormValue>
          name="standard-dictionary-item-editor-form"
          autoComplete="off"
          form={form}
          layout="vertical"
          className="standard-dictionary-form standard-dictionary-item-form"
          onFieldsChange={() => setDirty(true)}
          onFinish={(value) => void submit(value)}
        >
          <section className="standard-dictionary-form-section">
            <header className="standard-dictionary-form-section-header">
              <span className="standard-dictionary-form-section-icon" aria-hidden="true"><PartitionOutlined /></span>
              <span className="standard-dictionary-form-section-copy">
                <span>节点信息</span>
                <Typography.Text type="secondary">
                  {parent ? `归属父节点：${parent.name}` : '根节点可继续挂载多级子节点'}
                </Typography.Text>
              </span>
              {!item && (
                <span className="standard-dictionary-item-section-extra">
                  <span>创建后启用</span>
                  <Form.Item name="enabled" valuePropName="checked" noStyle>
                    <Switch aria-label="创建后启用码表节点" />
                  </Form.Item>
                </span>
              )}
            </header>
            <div className="standard-dictionary-form-section-body">
              {item && referenced && (
                <InlineFeedback
                  className="standard-dictionary-item-reference-feedback"
                  tone="warning"
                  label="节点编码不可修改"
                  detail="该码表已被模型字段引用，服务端会保护节点编码；名称和说明仍可修改。"
                  ariaLabel="查看节点编码不可修改原因"
                />
              )}
              <Row gutter={14}>
                <Col span={12} xs={24} sm={12}>
                  <Form.Item
                    label="节点编码"
                    name="code"
                    rules={[
                      { required: true, whitespace: true, message: '请输入节点编码' },
                      { max: 256 },
                    ]}
                    extra={`按 ${dictionary.valueType} 规范化，并在整张码表内保持唯一。`}
                  >
                    <Input name="standard-dictionary-item-code" autoComplete="off" disabled={Boolean(item && referenced)} autoFocus />
                  </Form.Item>
                </Col>
                <Col span={12} xs={24} sm={12}>
                  <Form.Item
                    label="节点名称"
                    name="name"
                    rules={[{ required: true, whitespace: true, message: '请输入节点名称' }, { max: 100 }]}
                  >
                    <Input name="standard-dictionary-item-display-name" autoComplete="off" />
                  </Form.Item>
                </Col>
                <Col span={24}>
                  <Form.Item label="说明" name="description" rules={[{ max: 500 }]}>
                    <Input.TextArea name="standard-dictionary-item-description" autoComplete="off" rows={4} showCount maxLength={500} />
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
