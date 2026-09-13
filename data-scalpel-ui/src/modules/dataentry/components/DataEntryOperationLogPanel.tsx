import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { Button, Descriptions, Drawer, Table, Tag } from 'antd';
import { useMemo, useState } from 'react';
import { DetailTableToolbar } from '../../../shared/components/DetailTableToolbar';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';
import { useDataEntryOperationLog, useDataEntryOperationLogs, useDataEntryOperationRecordChanges, useDataEntryRecordChanges } from '../hooks/useDataEntry';
import { dataEntryOperationStatusLabels, type DataEntryOperationLog, type DataEntryRecordChange } from '../model/dataEntry';

const statusColor = { PROCESSING: 'processing', SUCCEEDED: 'success', PARTIALLY_SUCCEEDED: 'warning', FAILED: 'error' } as const;
const operationLabels: Record<DataEntryOperationLog['operationType'], string> = {
  INSERT: '新增', UPDATE: '编辑', IMPORT: '批量导入', DELETE: '删除',
};
const changeStatusLabels = { PREPARED: '待确认', SUCCEEDED: '成功', FAILED: '失败', UNKNOWN: '待核对' } as const;
const parseSnapshot = (value: string | null) => {
  if (!value) return null;
  try { return JSON.parse(value) as unknown; } catch { return value; }
};

export const DataEntryOperationLogPanel = ({ formId }: { formId: string }) => {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [selectedId, setSelectedId] = useState<string>();
  const [changePage, setChangePage] = useState(0);
  const [recordKey, setRecordKey] = useState<string>();
  const [recordPage, setRecordPage] = useState(0);
  const request = useMemo(() => ({ page, size, sort: '-createdAt' }), [page, size]);
  const logsQuery = useDataEntryOperationLogs(formId, request);
  const detailQuery = useDataEntryOperationLog(formId, selectedId);
  const selected = detailQuery.data;
  const changesQuery = useDataEntryOperationRecordChanges(formId, selectedId, changePage);
  const recordChangesQuery = useDataEntryRecordChanges(formId, recordKey, recordPage);

  return (
    <div className="data-entry-tab-panel">
      <DetailTableToolbar
        title="操作日志"
        total={logsQuery.data?.totalElements ?? 0}
        current={page + 1}
        pageSize={size}
        onChange={(nextPage, nextSize) => { setPage(nextPage - 1); setSize(nextSize); }}
        onRefresh={() => void logsQuery.refetch()}
        refreshing={logsQuery.isFetching}
        refreshLabel="刷新操作日志"
      />
      <Table<DataEntryOperationLog>
        size="small" rowKey="id" loading={logsQuery.isFetching} dataSource={logsQuery.data?.content ?? []}
        columns={[
          { title: '操作', dataIndex: 'operationType', width: 100, render: (value: DataEntryOperationLog['operationType']) => operationLabels[value] },
          { title: '状态', dataIndex: 'status', width: 110, render: (value: DataEntryOperationLog['status'], row) => <><Tag color={statusColor[value]}>{dataEntryOperationStatusLabels[value]}</Tag>{row.manualVerificationRequired && <Tag color="warning">待人工核对</Tag>}</> },
          { title: '操作人', dataIndex: 'operatorUsername', width: 150 },
          { title: '数量', key: 'counts', width: 150, render: (_, row) => `${row.affectedCount ?? '—'} / ${row.requestedCount}` },
          { title: '发起时间', dataIndex: 'createdAt', width: 200, render: (value) => new Date(value).toLocaleString() },
          { title: '完成时间', dataIndex: 'completedAt', width: 200, render: (value) => value ? new Date(value).toLocaleString() : '—' },
          { title: '操作', key: 'actions', width: 90, render: (_, row) => <Button type="link" onClick={() => setSelectedId(row.id)}>详情</Button> },
        ]}
        pagination={false}
      />
      <Drawer rootClassName="business-overlay business-drawer-overlay" width={760} title="数据操作日志详情" open={Boolean(selectedId)} onClose={() => { setSelectedId(undefined); setChangePage(0); }}>
        {selected?.manualVerificationRequired && <Alert type="warning" showIcon title="操作结果待人工核对" description="目标数据库可能已完成部分或全部操作；请根据日志和目标数据核对，系统不会自动重放。" />}
        {selected && <Descriptions column={1} bordered size="small" items={[
          { key: 'operation', label: '操作', children: operationLabels[selected.operationType] },
          { key: 'status', label: '状态', children: dataEntryOperationStatusLabels[selected.status] },
          { key: 'operator', label: '操作人', children: selected.operatorUsername },
          { key: 'version', label: '模型结构版本', children: selected.modelSchemaVersion ?? '—' },
          { key: 'count', label: '数量', children: `${selected.affectedCount ?? '—'} / ${selected.requestedCount}` },
          { key: 'error', label: '失败信息', children: selected.errorMessage ?? '—' },
          { key: 'payload', label: '安全请求快照', children: <pre className="data-entry-log-payload">{selected.payloadSnapshot ?? '—'}</pre> },
        ]} />}
        {selected && <>
          <h4>记录明细</h4>
          <Table<DataEntryRecordChange>
            size="small" rowKey="id" loading={changesQuery.isLoading}
            dataSource={changesQuery.data?.content ?? []}
            columns={[
              { title: '序号', dataIndex: 'sequenceNo', width: 70 },
              { title: '记录键', dataIndex: 'businessKeySnapshot', ellipsis: true },
              { title: '状态', dataIndex: 'status', width: 90, render: (value) => <Tag color={value === 'SUCCEEDED' ? 'success' : value === 'UNKNOWN' ? 'warning' : value === 'FAILED' ? 'error' : 'default'}>{value}</Tag> },
              { title: '说明', dataIndex: 'errorMessage', ellipsis: true, render: (value) => value ?? '—' },
              { title: '操作', key: 'recordHistory', width: 90, render: (_, row) => <Button type="link" onClick={() => { setRecordPage(0); setRecordKey(row.recordKey); }}>查看历史</Button> },
            ]}
            expandable={{ expandedRowRender: (row) => <pre className="data-entry-log-payload">{JSON.stringify({
              before: parseSnapshot(row.beforeSnapshot),
              after: parseSnapshot(row.afterSnapshot),
            }, null, 2)}</pre> }}
            pagination={{ current: changePage + 1, pageSize: 20, total: changesQuery.data?.totalElements ?? 0,
              showSizeChanger: false, onChange: (next) => setChangePage(next - 1) }}
          />
        </>}
      </Drawer>
      <Drawer rootClassName="business-overlay business-drawer-overlay" width={760} title="记录变更历史"
        open={Boolean(recordKey)} onClose={() => { setRecordKey(undefined); setRecordPage(0); }}>
        <Table<DataEntryRecordChange>
          size="small" rowKey="id" loading={recordChangesQuery.isLoading}
          dataSource={recordChangesQuery.data?.content ?? []}
          columns={[
            { title: '时间', dataIndex: 'createdAt', width: 165, render: (value: string) => formatManagementDateTime(value) },
            { title: '操作', dataIndex: 'operationType', width: 80, render: (value: DataEntryOperationLog['operationType']) => operationLabels[value] },
            { title: '状态', dataIndex: 'status', width: 90, render: (value: DataEntryRecordChange['status']) => <Tag color={value === 'SUCCEEDED' ? 'success' : value === 'UNKNOWN' ? 'warning' : value === 'FAILED' ? 'error' : 'default'}>{changeStatusLabels[value]}</Tag> },
            { title: '操作人', dataIndex: 'operatorUsername', width: 120 },
            { title: '说明', dataIndex: 'errorMessage', ellipsis: true, render: (value) => value ?? '—' },
          ]}
          expandable={{ expandedRowRender: (row) => <pre className="data-entry-log-payload">{JSON.stringify({
            submitted: parseSnapshot(row.submittedSnapshot),
            before: parseSnapshot(row.beforeSnapshot),
            after: parseSnapshot(row.afterSnapshot),
          }, null, 2)}</pre> }}
          pagination={{ current: recordPage + 1, pageSize: 20, total: recordChangesQuery.data?.totalElements ?? 0,
            showSizeChanger: false, onChange: (next) => setRecordPage(next - 1) }}
        />
      </Drawer>
    </div>
  );
};
