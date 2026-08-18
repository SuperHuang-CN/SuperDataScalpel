import { Alert, Button, Descriptions, Drawer, Table, Tag } from 'antd';
import { useMemo, useState } from 'react';
import { useDataEntryOperationLog, useDataEntryOperationLogs } from '../hooks/useDataEntry';
import { dataEntryOperationStatusLabels, type DataEntryOperationLog } from '../model/dataEntry';

const statusColor = { PROCESSING: 'processing', SUCCEEDED: 'success', PARTIALLY_SUCCEEDED: 'warning', FAILED: 'error' } as const;
const operationLabels: Record<DataEntryOperationLog['operationType'], string> = {
  INSERT: '新增', IMPORT: '批量导入', DELETE: '删除',
};

export const DataEntryOperationLogPanel = ({ formId }: { formId: string }) => {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [selectedId, setSelectedId] = useState<string>();
  const request = useMemo(() => ({ page, size, sort: '-createdAt' }), [page, size]);
  const logsQuery = useDataEntryOperationLogs(formId, request);
  const detailQuery = useDataEntryOperationLog(formId, selectedId);
  const selected = detailQuery.data;

  return (
    <div className="data-entry-tab-panel">
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
        pagination={{ current: page + 1, pageSize: size, total: logsQuery.data?.totalElements ?? 0, showSizeChanger: true, showTotal: (total) => `共 ${total} 项`, onChange: (next, nextSize) => { setPage(nextSize !== size ? 0 : next - 1); setSize(nextSize); } }}
      />
      <Drawer rootClassName="business-overlay business-drawer-overlay" width={640} title="数据操作日志详情" open={Boolean(selectedId)} onClose={() => setSelectedId(undefined)}>
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
      </Drawer>
    </div>
  );
};
