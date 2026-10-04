import { DeleteOutlined, EditOutlined, EyeOutlined, PlusOutlined, UploadOutlined } from '@ant-design/icons';
import { Button, Modal, Space, Tag, Tooltip, message } from 'antd';
import { useCallback, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import {
  DataModelDataQueryPanel,
  formatDataModelPreviewValue,
  type DataModelDataQueryRequest,
  type DataModelQueryRow,
} from '../../model';
import { useCurrentUser } from '../../system';
import { queryDataEntryData, queryDataEntryOptions } from '../api/dataEntryApi';
import { useDeleteDataEntries } from '../hooks/useDataEntry';
import type { DataEntryFormDetail, DataEntryMutationResponse } from '../model/dataEntry';
import { DataEntryCreateDrawer } from './DataEntryCreateDrawer';
import { DataEntryImportDrawer } from './DataEntryImportDrawer';
import { DataEntryRecordDrawer } from './DataEntryRecordDrawer';
import { dataEntrySubmitBlockedReason } from './dataEntrySubmitAvailability';
import { OverlayTitle } from '../../../shared/components/OverlayTitle';

export const DataEntryDataPanel = ({ detail, onViewLogs }: { detail: DataEntryFormDetail; onViewLogs: () => void }) => {
  const [selectedRows, setSelectedRows] = useState<DataModelQueryRow[]>([]);
  const [labels, setLabels] = useState<Record<string, { text: string; status: string }>>({});
  const [createOpen, setCreateOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [verificationWarning, setVerificationWarning] = useState<string>();
  const [recordKey, setRecordKey] = useState<Record<string, unknown>>();
  const [recordEditing, setRecordEditing] = useState(false);
  const [refreshToken, setRefreshToken] = useState(0);
  const [messageApi, contextHolder] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const mutation = useDeleteDataEntries();
  const currentUser = useCurrentUser();
  const canDeletePermission = new Set(currentUser.data?.permissions ?? []).has('dataentry.delete');
  const canSubmitPermission = new Set(currentUser.data?.permissions ?? []).has('dataentry.submit');
  const blockedReason = dataEntrySubmitBlockedReason(detail);
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

  const reportMutation = (result: DataEntryMutationResponse, operation: '新增' | '导入' | '删除') => {
    setRefreshToken((value) => value + 1);
    if (result.manualVerificationRequired || result.status === 'PARTIALLY_SUCCEEDED') {
      setVerificationWarning(result.warningMessage ?? `${operation}结果需要人工核对，请查看操作日志`);
      return;
    }
    setVerificationWarning(undefined);
    messageApi.success(operation === '新增' ? '记录已新增'
      : operation === '导入' ? `已导入 ${result.affectedCount} 条数据` : '所选数据已删除');
  };

  const remove = () => modalApi.confirm({
    icon: null,
    width: 440, centered: true, focusable: { autoFocusButton: 'cancel' },
    rootClassName: 'business-overlay business-modal-overlay workspace-resource-overlay resource-delete-modal',
    title: <OverlayTitle title={`删除当前页选中的 ${selectedRows.length} 条数据？`} icon={<DeleteOutlined />} tone="danger" />,
    content: '系统会先检查业务主键是否唯一命中。删除生效后不会回滚，异常结果请通过操作日志核对。',
    okText: '删除', okButtonProps: { danger: true }, cancelText: '取消',
    onOk: async () => {
      try {
        const keys = selectedRows.map((row) => Object.fromEntries(primaryKeys.map((field) => [field.code, row[field.code]])));
        const result = await mutation.mutateAsync({ id: detail.form.id, keys });
        setSelectedRows([]);
        reportMutation(result, '删除');
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.message : '批量删除失败');
        throw error;
      }
    },
  });

  return (
    <div className="data-entry-tab-panel data-entry-data-panel">
      {contextHolder}{modalContext}
      {verificationWarning && <InlineFeedback
        tone="warning"
        label="写入结果待核对"
        detail={verificationWarning}
        action={<Button type="link" size="small" onClick={onViewLogs}>查看操作日志</Button>}
        className="data-entry-verification-warning"
      />}
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
          {canSubmitPermission && <Tooltip title="编辑"><Button type="text" size="small" icon={<EditOutlined />} aria-label="编辑记录" disabled={!(detail.health.canUpdateEntries ?? detail.health.canSubmit)} onClick={() => {
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
        toolbar={(canSubmitPermission || canDeletePermission) ? (
          <Space className="data-entry-data-toolbar">
            {canSubmitPermission && <Tooltip title={blockedReason}>
              <span><Button type="primary" icon={<PlusOutlined />} disabled={Boolean(blockedReason)} onClick={() => setCreateOpen(true)}>新增记录</Button></span>
            </Tooltip>}
            {canSubmitPermission && <Button icon={<UploadOutlined />} onClick={() => setImportOpen(true)}>批量导入</Button>}
            {canDeletePermission && <Button danger icon={<DeleteOutlined />} disabled={!selectedRows.length || !detail.health.canDeleteEntries} loading={mutation.isPending} onClick={remove}>删除当前页所选</Button>}
          </Space>
        ) : undefined}
      />
      {recordKey && <DataEntryRecordDrawer
        key={JSON.stringify(recordKey)}
        open
        detail={detail}
        recordKey={recordKey}
        initialEditing={recordEditing}
        onClose={() => setRecordKey(undefined)}
        onUpdated={() => setRefreshToken((value) => value + 1)}
      />}
      {createOpen && <DataEntryCreateDrawer
        detail={detail}
        onClose={() => setCreateOpen(false)}
        onSubmitted={(result) => reportMutation(result, '新增')}
      />}
      {importOpen && <DataEntryImportDrawer
        open
        detail={detail}
        onClose={() => setImportOpen(false)}
        onImported={(result) => reportMutation(result, '导入')}
      />}
    </div>
  );
};
