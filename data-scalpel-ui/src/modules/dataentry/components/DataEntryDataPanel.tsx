import { DeleteOutlined, EditOutlined, EyeOutlined } from '@ant-design/icons';
import { Button, Modal, Space, Tag, Tooltip, message } from 'antd';
import { useCallback, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  DataModelDataQueryPanel,
  formatDataModelPreviewValue,
  type DataModelDataQueryRequest,
  type DataModelQueryRow,
} from '../../model';
import { useCurrentUser } from '../../system';
import { queryDataEntryData, queryDataEntryOptions } from '../api/dataEntryApi';
import { useDeleteDataEntries } from '../hooks/useDataEntry';
import type { DataEntryFormDetail } from '../model/dataEntry';
import { DataEntryRecordDrawer } from './DataEntryRecordDrawer';

export const DataEntryDataPanel = ({ detail, onMutated }: { detail: DataEntryFormDetail; onMutated: () => void }) => {
  const [selectedRows, setSelectedRows] = useState<DataModelQueryRow[]>([]);
  const [labels, setLabels] = useState<Record<string, { text: string; status: string }>>({});
  const [recordKey, setRecordKey] = useState<Record<string, unknown>>();
  const [recordEditing, setRecordEditing] = useState(false);
  const [refreshToken, setRefreshToken] = useState(0);
  const [messageApi, contextHolder] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const mutation = useDeleteDataEntries();
  const currentUser = useCurrentUser();
  const canDeletePermission = new Set(currentUser.data?.permissions ?? []).has('dataentry.delete');
  const canEditPermission = new Set(currentUser.data?.permissions ?? []).has('dataentry.submit');
  const primaryKeys = detail.fields.filter((field) => field.primaryKey);
  const fieldTypes = useMemo(
    () => new Map(detail.fields.map((field) => [field.code, field.fieldType])),
    [detail.fields],
  );
  const makeRowKey = useCallback((row: Record<string, unknown>, index: number) => (
    primaryKeys.length ? primaryKeys.map((field) => `${field.code}=${String(row[field.code])}`).join('|') : String(index)
  ), [primaryKeys]);
  const executeDataQuery = useCallback(
    (request: DataModelDataQueryRequest) => queryDataEntryData(detail.form.id, request),
    [detail.form.id],
  );

  const canonical = (value: unknown) => typeof value === 'object' ? JSON.stringify(value) : String(value);
  const loadLabels = async (rows: Record<string, unknown>[]) => {
    const optionFields = detail.fields.filter((field) => field.inputSource !== 'DEFAULT');
    const resolved = await Promise.all(optionFields.map(async (field) => {
      const values = [...new Map(rows
        .map((row) => row[field.code])
        .filter((value) => value !== null && value !== undefined)
        .map((value) => [canonical(value), value])).values()].slice(0, 100);
      if (!values.length) return [];
      try {
        const response = await queryDataEntryOptions(detail.form.id, field.id, { values });
        return response.content.map((option) => [
          `${field.code}:${canonical(option.value)}`,
          { text: option.displayLabel, status: option.status },
        ] as const);
      } catch {
        return values.map((value) => [`${field.code}:${canonical(value)}`, { text: String(value), status: 'SOURCE_UNAVAILABLE' }] as const);
      }
    }));
    setLabels(Object.fromEntries(resolved.flat()));
  };

  const renderCell = useCallback((value: unknown, fieldCode: string) => {
    if (value === null || value === undefined) return '—';
    const resolved = labels[`${fieldCode}:${canonical(value)}`];
    if (!resolved) return typeof value === 'object'
      ? JSON.stringify(value)
      : formatDataModelPreviewValue(value, fieldTypes.get(fieldCode));
    const statusLabel = resolved.status === 'DISABLED' ? '已停用'
      : resolved.status === 'MISSING' ? '无匹配项'
        : resolved.status === 'SOURCE_UNAVAILABLE' ? '来源不可用' : null;
    return <span>{resolved.text}{statusLabel && <Tag color="warning" style={{ marginLeft: 6 }}>{statusLabel}</Tag>}</span>;
  }, [fieldTypes, labels]);

  const remove = () => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: `删除当前页选中的 ${selectedRows.length} 条数据？`,
    content: '系统会先检查业务主键是否唯一命中。删除生效后不会回滚，异常结果请通过操作日志核对。',
    okText: '删除', okButtonProps: { danger: true }, cancelText: '取消',
    onOk: async () => {
      try {
        const keys = selectedRows.map((row) => Object.fromEntries(primaryKeys.map((field) => [field.code, row[field.code]])));
        const result = await mutation.mutateAsync({ id: detail.form.id, keys });
        setSelectedRows([]);
        setRefreshToken((value) => value + 1);
        onMutated();
        if (result.status === 'PARTIALLY_SUCCEEDED') {
          messageApi.warning(result.warningMessage ?? '删除已部分生效，请查看操作日志并人工核对');
        } else {
          messageApi.success('所选数据已删除');
        }
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.message : '批量删除失败');
        throw error;
      }
    },
  });

  return (
    <div className="data-entry-tab-panel data-entry-data-panel">
      {contextHolder}{modalContext}
      <DataModelDataQueryPanel
        fields={detail.fields.map((field) => ({ ...field, modelId: detail.form.modelId, createdAt: '', updatedAt: '' }))}
        query={executeDataQuery}
        rowKey={makeRowKey}
        requiredColumns={primaryKeys.map((field) => field.code)}
        refreshToken={refreshToken}
        rowActions={(row) => <Space size={2}>
          <Tooltip title="查看详情"><Button type="text" size="small" icon={<EyeOutlined />} aria-label="查看记录详情" onClick={() => {
            setRecordEditing(false);
            setRecordKey(Object.fromEntries(primaryKeys.map((field) => [field.code, row[field.code]])));
          }} /></Tooltip>
          {canEditPermission && <Tooltip title="编辑"><Button type="text" size="small" icon={<EditOutlined />} aria-label="编辑记录" disabled={!(detail.health.canUpdateEntries ?? detail.health.canSubmit)} onClick={() => {
            setRecordEditing(true);
            setRecordKey(Object.fromEntries(primaryKeys.map((field) => [field.code, row[field.code]])));
          }} /></Tooltip>}
        </Space>}
        renderCell={renderCell}
        onResult={(result) => { setSelectedRows([]); void loadLabels(result.rows); }}
        rowSelection={() => ({
          selectedRowKeys: selectedRows.map((row) => row.__rowKey),
          preserveSelectedRowKeys: false,
          onChange: (_keys, selected) => setSelectedRows(selected),
        })}
        toolbar={(
          <Space className="data-entry-data-toolbar">
            {canDeletePermission && <Button danger icon={<DeleteOutlined />} disabled={!selectedRows.length || !detail.health.canDeleteEntries} loading={mutation.isPending} onClick={remove}>删除当前页所选</Button>}
          </Space>
        )}
      />
      {recordKey && <DataEntryRecordDrawer
        key={JSON.stringify(recordKey)}
        open
        detail={detail}
        recordKey={recordKey}
        initialEditing={recordEditing}
        onClose={() => setRecordKey(undefined)}
        onUpdated={() => { setRefreshToken((value) => value + 1); onMutated(); }}
      />}
    </div>
  );
};
