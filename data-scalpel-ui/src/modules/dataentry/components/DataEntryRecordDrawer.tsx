import { EditOutlined } from '@ant-design/icons';
import { Button, Descriptions, Drawer, Empty, Form, Space, Spin, Table, Tabs, Tag, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';
import { useCurrentUser } from '../../system';
import { useDataEntryRecordChanges, useDataEntryRecordDetail, useUpdateDataEntryRecord } from '../hooks/useDataEntry';
import type { DataEntryFormDetail, DataEntryRecordChange } from '../model/dataEntry';
import { DataEntryInputField } from './DataEntryInputField';

const parseObject = (value: string | null): Record<string, unknown> => {
  if (!value) return {};
  try { return JSON.parse(value) as Record<string, unknown>; } catch { return {}; }
};

const operationLabels = { INSERT: '新增', UPDATE: '编辑', IMPORT: '导入', DELETE: '删除' } as const;
const statusLabels = { PREPARED: '待确认', SUCCEEDED: '成功', FAILED: '失败', UNKNOWN: '待核对' } as const;

const initialValues = (detail: DataEntryFormDetail, values: Record<string, unknown>) => Object.fromEntries(
  detail.fields.map((field) => [field.code, values[field.code]]),
);

const submittedValues = (detail: DataEntryFormDetail, form: ReturnType<typeof Form.useForm<Record<string, unknown>>>[0],
  original: Record<string, unknown>, values: Record<string, unknown>) => Object.fromEntries(detail.fields.map((field) => {
  if (!form.isFieldTouched(field.code)) return [field.code, original[field.code] ?? null];
  const value = values[field.code];
  if (value && typeof value === 'object' && 'format' in value && typeof value.format === 'function') {
    const dateValue = value as { format: (pattern: string) => string; toISOString: () => string };
    if (field.fieldType === 'DATE') return [field.code, dateValue.format('YYYY-MM-DD')];
    if (field.fieldType === 'TIMESTAMP_NTZ') return [field.code, dateValue.format('YYYY-MM-DDTHH:mm:ss.SSS')];
    return [field.code, dateValue.toISOString()];
  }
  return [field.code, value === undefined ? null : value];
}));

const ChangeSummary = ({ change }: { change: DataEntryRecordChange }) => {
  const before = parseObject(change.beforeSnapshot);
  const after = parseObject(change.afterSnapshot);
  const fields = (() => {
    try { return JSON.parse(change.fieldSnapshot) as Array<{ code: string; name: string }>; } catch { return []; }
  })();
  const names = new Map(fields.map((field) => [field.code, field.name]));
  const codes = [...new Set([...Object.keys(before), ...Object.keys(after)])]
    .filter((code) => JSON.stringify(before[code]) !== JSON.stringify(after[code]));
  if (change.operationType === 'DELETE') return <span>删除前完整记录已保存</span>;
  if (change.operationType === 'INSERT' || change.operationType === 'IMPORT') return <span>新增完整记录</span>;
  if (!codes.length) return <span>—</span>;
  return <span>{codes.slice(0, 4).map((code) => names.get(code) ?? code).join('、')}{codes.length > 4 ? ` 等 ${codes.length} 项` : ''}</span>;
};

export const DataEntryRecordDrawer = ({ open, detail, recordKey, onClose, onUpdated, initialEditing = false }: {
  open: boolean;
  detail: DataEntryFormDetail;
  recordKey: Record<string, unknown> | undefined;
  onClose: () => void;
  onUpdated: () => void;
  initialEditing?: boolean;
}) => {
  const [form] = Form.useForm<Record<string, unknown>>();
  const currentUser = useCurrentUser();
  const canEdit = new Set(currentUser.data?.permissions ?? []).has('dataentry.submit')
    && (detail.health.canUpdateEntries ?? detail.health.canSubmit);
  const [editing, setEditing] = useState(initialEditing && canEdit);
  const [historyPage, setHistoryPage] = useState(0);
  const [messageApi, contextHolder] = message.useMessage();
  const recordQuery = useDataEntryRecordDetail(detail.form.id, recordKey);
  const historyQuery = useDataEntryRecordChanges(detail.form.id, recordQuery.data?.recordKey, historyPage);
  const updateMutation = useUpdateDataEntryRecord();
  const primaryKeys = useMemo(() => new Set(detail.fields.filter((field) => field.primaryKey).map((field) => field.code)), [detail.fields]);

  useEffect(() => {
    if (!recordQuery.data) return;
    form.resetFields();
    form.setFieldsValue(initialValues(detail, recordQuery.data.values));
  }, [detail, form, recordQuery.data]);

  const save = async () => {
    if (!recordKey || !recordQuery.data) return;
    try {
      const values = await form.validateFields();
      const result = await updateMutation.mutateAsync({
        id: detail.form.id,
        recordKey,
        values: submittedValues(detail, form, recordQuery.data.values, values),
      });
      if (result.manualVerificationRequired) messageApi.warning(result.warningMessage ?? '记录已更新，历史状态需要人工核对');
      else messageApi.success(result.changed ? '记录已更新' : '记录没有变化');
      setEditing(false);
      onUpdated();
    } catch (error) {
      if (error instanceof ApiError) messageApi.error(error.message);
    }
  };

  const currentContent = recordQuery.isLoading ? <Spin /> : recordQuery.isError ? (
    <Empty description={recordQuery.error instanceof Error ? recordQuery.error.message : '记录加载失败'} />
  ) : recordQuery.data ? (
    <Form autoComplete="off" form={form} layout="vertical">
      <div className="data-entry-field-grid">
        {detail.fields.map((field) => <DataEntryInputField
          key={field.id}
          formId={detail.form.id}
          field={field}
          existingValue={recordQuery.data.values[field.code]}
          disabled={!editing || primaryKeys.has(field.code)}
          preserveDatePrecision
        />)}
      </div>
    </Form>
  ) : <Empty description="记录不存在" />;

  return (
    <Drawer
      open={open}
      width={760}
      title="记录详情"
      onClose={onClose}
      extra={<Space>{canEdit && !editing && <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>编辑</Button>}
        {editing && <><Button onClick={() => { form.resetFields(); form.setFieldsValue(initialValues(detail, recordQuery.data?.values ?? {})); setEditing(false); }}>取消</Button>
          <Button type="primary" loading={updateMutation.isPending} onClick={() => void save()}>保存</Button></>}</Space>}
    >
      {contextHolder}
      <Tabs items={[
        { key: 'current', label: '当前记录', children: currentContent },
        { key: 'history', label: '变更历史', children: (
          <Table<DataEntryRecordChange>
            size="small"
            rowKey="id"
            loading={historyQuery.isLoading}
            dataSource={historyQuery.data?.content ?? []}
            columns={[
              { title: '时间', dataIndex: 'createdAt', width: 165, render: (value: string) => formatManagementDateTime(value) },
              { title: '操作', dataIndex: 'operationType', width: 75, render: (value: keyof typeof operationLabels) => operationLabels[value] },
              { title: '结果', dataIndex: 'status', width: 85, render: (value: keyof typeof statusLabels) => <Tag color={value === 'SUCCEEDED' ? 'success' : value === 'UNKNOWN' ? 'warning' : value === 'FAILED' ? 'error' : 'default'}>{statusLabels[value]}</Tag> },
              { title: '操作者', dataIndex: 'operatorUsername', width: 110 },
              { title: '变化', render: (_value, change) => <ChangeSummary change={change} /> },
            ]}
            expandable={{ expandedRowRender: (change) => {
              const before = parseObject(change.beforeSnapshot);
              const after = parseObject(change.afterSnapshot);
              return <Descriptions size="small" column={1} items={[
                { key: 'before', label: '变更前', children: change.beforeSnapshot ? <pre>{JSON.stringify(before, null, 2)}</pre> : '—' },
                { key: 'after', label: '变更后', children: change.afterSnapshot ? <pre>{JSON.stringify(after, null, 2)}</pre> : '—' },
                ...(change.errorMessage ? [{ key: 'error', label: '说明', children: change.errorMessage }] : []),
              ]} />;
            } }}
            pagination={{ current: historyPage + 1, pageSize: 20, total: historyQuery.data?.totalElements ?? 0,
              onChange: (page) => setHistoryPage(page - 1), showSizeChanger: false }}
          />
        ) },
      ]} />
    </Drawer>
  );
};
