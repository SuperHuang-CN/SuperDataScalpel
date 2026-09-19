import { ProfileOutlined } from '@ant-design/icons';
import { Badge, Button, Col, Drawer, Form, Input, Row, Select, Space, Tag, Typography, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
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
import { StandardDictionaryValueTypeIcon } from './StandardDictionaryValueTypeIcon';

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
  const [operationError, setOperationError] = useState<string | null>(null);
  const createMutation = useCreateStandardDictionary();
  const updateMutation = useUpdateStandardDictionary();
  const selectedValueType = Form.useWatch('valueType', form) ?? dictionary?.valueType ?? 'STRING';

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
    setOperationError(null);
    form.resetFields();
    onClose();
  }, [form, onClose]);
  const close = useStandardFormLeaveGuard({
    dirty: open && dirty,
    content: '码表表单中的修改尚未保存。',
    onDiscard: discard,
  });

  const submit = async (values: CreateStandardDictionaryRequest) => {
    setOperationError(null);
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
      const errorMessage = error instanceof ApiError ? error.message : '保存码表失败';
      setOperationError(errorMessage);
      messageApi.error(errorMessage);
    }
  };

  const definitionLocked = Boolean(dictionary);
  const pending = createMutation.isPending || updateMutation.isPending;
  const footerStatus = operationError ? (
    <InlineFeedback
      tone="error"
      label={dictionary ? '保存失败' : '创建失败'}
      detail={operationError}
      ariaLabel={dictionary ? '查看码表保存失败详情' : '查看码表创建失败详情'}
    />
  ) : dirty ? (
    <Badge status="processing" text="有未保存的修改" />
  ) : (
    <Badge status="default" text={dictionary ? `当前版本 v${dictionary.version}` : '创建后可继续维护树形节点'} />
  );

  return (
    <>
      {messageContext}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        className="standard-dictionary-drawer"
        title={(
          <div className="standard-dictionary-drawer-title">
            <span className="standard-dictionary-drawer-title-icon" aria-hidden="true">
              <StandardDictionaryValueTypeIcon valueType={selectedValueType} />
            </span>
            <span className="standard-dictionary-drawer-title-copy">
              <span>{dictionary ? '修改码表' : '新建码表'}</span>
              <Typography.Text type="secondary">定义稳定的取值域，并在创建后维护树形节点</Typography.Text>
            </span>
          </div>
        )}
        extra={<Tag className="standard-dictionary-drawer-header-tag">{standardDictionaryValueTypeLabels[selectedValueType]}</Tag>}
        open={open}
        size="min(640px, 100vw)"
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
                {dictionary ? '保存修改' : '创建码表'}
              </Button>
            </Space>
          </div>
        )}
      >
        <Form<CreateStandardDictionaryRequest>
          name="standard-dictionary-editor-form"
          autoComplete="off"
          form={form}
          layout="vertical"
          className="standard-dictionary-form"
          onFieldsChange={() => setDirty(true)}
          onFinish={(values) => void submit(values)}
        >
          <section className="standard-dictionary-form-section">
            <header className="standard-dictionary-form-section-header">
              <span className="standard-dictionary-form-section-icon" aria-hidden="true"><ProfileOutlined /></span>
              <span className="standard-dictionary-form-section-copy">
                <span>码表定义</span>
                <Typography.Text type="secondary">编码与取值类型共同决定节点值的校验方式</Typography.Text>
              </span>
            </header>
            <div className="standard-dictionary-form-section-body">
              <Row gutter={14}>
                <Col span={12} xs={24} sm={12}>
                  <Form.Item
                    label={(
                      <span className="standard-dictionary-field-label">
                        码表编码
                        {definitionLocked && (
                          <ContextHelp
                            ariaLabel="码表编码修改规则"
                            content="已有节点或字段引用时，服务端会保护编码不可修改。"
                          />
                        )}
                      </span>
                    )}
                    name="code"
                    rules={[
                      { required: true, whitespace: true, message: '请输入码表编码' },
                      { pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/, message: '编码以字母开头，只能包含字母、数字和下划线' },
                    ]}
                  >
                    <Input name="standard-dictionary-code" autoComplete="off" placeholder="如：ORDER_STATUS" autoFocus />
                  </Form.Item>
                </Col>
                <Col span={12} xs={24} sm={12}>
                  <Form.Item
                    label="码表名称"
                    name="name"
                    rules={[
                      { required: true, whitespace: true, message: '请输入码表名称' },
                      { max: 100 },
                    ]}
                  >
                    <Input name="standard-dictionary-display-name" autoComplete="off" placeholder="如：订单状态" />
                  </Form.Item>
                </Col>
                <Col span={12} xs={24} sm={12}>
                  <Form.Item
                    label={(
                      <span className="standard-dictionary-field-label">
                        取值类型
                        {definitionLocked && (
                          <ContextHelp
                            ariaLabel="取值类型修改规则"
                            content="已有节点或字段引用时，服务端会保护取值类型不可修改。"
                          />
                        )}
                      </span>
                    )}
                    name="valueType"
                    rules={[{ required: true, message: '请选择取值类型' }]}
                  >
                    <Select options={valueTypeOptions} />
                  </Form.Item>
                </Col>
                <Col span={24}>
                  <Form.Item label="说明" name="description" rules={[{ max: 500 }]}>
                    <Input.TextArea name="standard-dictionary-description" autoComplete="off" rows={4} showCount maxLength={500} />
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
