import { PlusOutlined } from '@ant-design/icons';
import { Button, Drawer, Form, Space, message } from 'antd';
import { ApiError } from '../../../shared/api/http';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { useSubmitDataEntry } from '../hooks/useDataEntry';
import type { DataEntryFormDetail, DataEntryMutationResponse } from '../model/dataEntry';
import { DataEntryInputField } from './DataEntryInputField';
import { dataEntrySubmitBlockedReason } from './dataEntrySubmitAvailability';
import { OverlayTitle } from '../../../shared/components/OverlayTitle';

const normalizeValues = (detail: DataEntryFormDetail, values: Record<string, unknown>) => Object.fromEntries(
  detail.fields.map((field) => {
    const value = values[field.code];
    if (value && typeof value === 'object' && 'format' in value && typeof value.format === 'function') {
      const dateValue = value as { format: (pattern: string) => string; toISOString: () => string };
      if (field.fieldType === 'DATE') return [field.code, dateValue.format('YYYY-MM-DD')];
      if (field.fieldType === 'TIMESTAMP_NTZ') return [field.code, dateValue.format('YYYY-MM-DDTHH:mm:ss')];
      return [field.code, dateValue.toISOString()];
    }
    return [field.code, value === undefined ? null : value];
  }),
);

export const DataEntryCreateDrawer = ({ detail, onClose, onSubmitted }: {
  detail: DataEntryFormDetail;
  onClose: () => void;
  onSubmitted: (result: DataEntryMutationResponse) => void;
}) => {
  const [form] = Form.useForm<Record<string, unknown>>();
  const [messageApi, contextHolder] = message.useMessage();
  const mutation = useSubmitDataEntry();
  const blockedReason = dataEntrySubmitBlockedReason(detail);

  const submit = async () => {
    if (mutation.isPending || blockedReason) return;
    try {
      const values = await form.validateFields();
      const result = await mutation.mutateAsync({ id: detail.form.id, values: normalizeValues(detail, values) });
      onSubmitted(result);
      onClose();
    } catch (error) {
      if (error instanceof ApiError) messageApi.error(error.message);
      else if (!(error && typeof error === 'object' && 'errorFields' in error)) messageApi.error('新增记录失败');
    }
  };

  return <>
    {contextHolder}
    <Drawer
      rootClassName="business-overlay business-drawer-overlay"
      className="data-entry-create-drawer"
      open
      width="min(820px, 100vw)"
      destroyOnHidden
      maskClosable={!mutation.isPending}
      closable={mutation.isPending ? false : { placement: 'end' }}
      keyboard={!mutation.isPending}
      onClose={() => { if (!mutation.isPending) onClose(); }}
      title={<OverlayTitle title="新增记录" icon={<PlusOutlined />} description={`${detail.form.modelName ?? detail.form.modelCode ?? '数据填报'} · 提交后立即生效`} />}
      footer={<div className="data-entry-create-footer"><Space>
        <Button disabled={mutation.isPending} onClick={onClose}>取消</Button>
        <Button type="primary" loading={mutation.isPending} disabled={Boolean(blockedReason)} onClick={() => void submit()}>提交并立即生效</Button>
      </Space></div>}
    >
      {blockedReason && <InlineFeedback tone="warning" label="当前不能提交数据" detail={blockedReason} />}
      <Form autoComplete="off" form={form} layout="vertical" className="data-entry-create-form">
        <div className="data-entry-field-grid">
          {detail.fields.map((field) => <DataEntryInputField key={field.id} formId={detail.form.id} field={field} />)}
        </div>
      </Form>
    </Drawer>
  </>;
};
