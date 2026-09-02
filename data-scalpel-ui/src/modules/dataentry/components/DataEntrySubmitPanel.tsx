import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { Button, Dropdown, Form, Space, message } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { useCurrentUser } from '../../system';
import { downloadDataEntryImportTemplate } from '../api/dataEntryApi';
import { useSubmitDataEntry } from '../hooks/useDataEntry';
import type { DataEntryFormDetail, DataEntryMutationResponse } from '../model/dataEntry';
import { DataEntryInputField } from './DataEntryInputField';
import { DataEntryImportDrawer } from './DataEntryImportDrawer';

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

export const DataEntrySubmitPanel = ({
  detail,
  onImported,
  onSubmitted,
}: {
  detail: DataEntryFormDetail;
  onImported: (result: DataEntryMutationResponse) => void;
  onSubmitted: () => void;
}) => {
  const [form] = Form.useForm<Record<string, unknown>>();
  const [messageApi, contextHolder] = message.useMessage();
  const [importOpen, setImportOpen] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const mutation = useSubmitDataEntry();
  const currentUser = useCurrentUser();
  const canSubmitPermission = new Set(currentUser.data?.permissions ?? []).has('dataentry.submit');
  const unsupported = useMemo(() => detail.fields.some((field) => field.fieldType === 'BINARY' || field.fieldType === 'GEOMETRY'), [detail.fields]);

  const submit = async () => {
    try {
      const values = await form.validateFields();
      const result = await mutation.mutateAsync({ id: detail.form.id, values: normalizeValues(detail, values) });
      if (result.status === 'PARTIALLY_SUCCEEDED') {
        messageApi.warning(result.warningMessage ?? '写入结果需要人工核对，请查看操作日志');
      } else {
        messageApi.success('数据已写入目标物理表');
      }
      onSubmitted();
      form.resetFields();
    } catch (error) {
      if (error instanceof ApiError) messageApi.error(error.message);
    }
  };

  const downloadTemplate: NonNullable<MenuProps['onClick']> = async ({ key }) => {
    const format = key === 'CSV' ? 'CSV' : 'XLSX';
    setDownloading(true);
    try {
      const blob = await downloadDataEntryImportTemplate(detail.form.id, format);
      const extension = format === 'CSV' ? 'csv' : 'xlsx';
      downloadBlob(blob, `DataScalpel-${detail.form.modelCode ?? 'model'}-填报模板.${extension}`);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '下载导入模板失败');
    } finally {
      setDownloading(false);
    }
  };

  return (
    <div className="data-entry-tab-panel">
      {contextHolder}
      {!detail.health.canSubmit && <Alert type="warning" showIcon title="当前不能提交数据" description={detail.health.issues.filter((issue) => issue.affectedOperations.includes('SUBMIT')).map((issue) => issue.message).join('；')} />}
      {canSubmitPermission && (
        <div className="data-entry-import-toolbar">
          <Dropdown menu={{ items: [
            { key: 'XLSX', label: '下载 Excel 模板' },
            { key: 'CSV', label: '下载 CSV 模板' },
          ], onClick: (info) => void downloadTemplate(info) }}>
            <Button icon={<DownloadOutlined />} loading={downloading}>下载模板</Button>
          </Dropdown>
          <Button
            icon={<UploadOutlined />}
            disabled={!detail.health.canSubmit || unsupported}
            onClick={() => setImportOpen(true)}
          >
            批量导入
          </Button>
        </div>
      )}
      <Form autoComplete="off" form={form} layout="vertical" className="data-entry-submit-form">
        <div className="data-entry-field-grid">
          {detail.fields.map((field) => <DataEntryInputField key={field.id} formId={detail.form.id} field={field} />)}
        </div>
        <Space>
          {canSubmitPermission && <Button type="primary" disabled={!detail.health.canSubmit || unsupported} loading={mutation.isPending} onClick={() => void submit()}>提交并立即生效</Button>}
          <Button onClick={() => form.resetFields()}>清空</Button>
        </Space>
      </Form>
      {importOpen && (
        <DataEntryImportDrawer
          key={`${detail.form.modelSchemaVersion}-${detail.form.publishedModelSchemaVersion}-${detail.health.issues.map((issue) => issue.code).join(',')}`}
          open
          detail={detail}
          onClose={() => setImportOpen(false)}
          onImported={onImported}
        />
      )}
    </div>
  );
};
