import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { EyeOutlined, StopOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Empty, Modal, Space, Table, Tag, Tooltip, message } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { DetailTableToolbar } from '../../../shared/components/DetailTableToolbar';
import { DataModelPhysicalChangeDrawer } from './DataModelPhysicalChangeDrawer';
import { useCancelPhysicalTableChangePlan, usePhysicalTableChangePlans } from '../hooks/useDataModels';
import {
  physicalTableChangeStatusColors,
  physicalTableChangeStatusLabels,
  tableChangeRiskColors,
  tableChangeRiskLabels,
  tableChangeStrategyLabels,
  tableDdlAtomicityLabels,
  type DataModel,
  type DataModelPhysicalChange,
} from '../model/dataModel';

interface DataModelPhysicalChangePanelProps {
  model: DataModel;
  canUpdate: boolean;
}

const formatDateTime = (value: string | null) => {
  if (!value) return '—';
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'medium',
    hour12: false,
  }).format(new Date(value));
};

export const DataModelPhysicalChangePanel = ({ model, canUpdate }: DataModelPhysicalChangePanelProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const planSearchRequest = useMemo(() => ({
    page: page - 1,
    size: pageSize,
    sort: '-createdAt',
  }), [page, pageSize]);
  const plansQuery = usePhysicalTableChangePlans(model.id, planSearchRequest, true);
  const cancelMutation = useCancelPhysicalTableChangePlan();
  const [selectedChange, setSelectedChange] = useState<DataModelPhysicalChange | null>(null);
  const [cancellingPlanId, setCancellingPlanId] = useState<string | null>(null);

  const cancel = (change: DataModelPhysicalChange) => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: '取消变更计划',
    content: '取消后不会修改模型字段或物理表；如需继续修改，请重新生成计划。',
    okText: '确认取消',
    cancelText: '返回',
    okButtonProps: { danger: true },
    onOk: async () => {
      setCancellingPlanId(change.id);
      try {
        await cancelMutation.mutateAsync({ modelId: model.id, planId: change.id });
        messageApi.success('变更计划已取消');
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.message : '取消变更计划失败');
        throw error;
      } finally {
        setCancellingPlanId(null);
      }
    },
  });

  const columns: TableProps<DataModelPhysicalChange>['columns'] = [
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 174,
      render: (value: string) => formatDateTime(value),
    },
    {
      title: '版本',
      key: 'version',
      width: 100,
      render: (_value: unknown, change) => `v${change.baseSchemaVersion} → v${change.targetSchemaVersion}`,
    },
    {
      title: '策略',
      dataIndex: ['plan', 'strategy'],
      width: 136,
      render: (value: DataModelPhysicalChange['plan']['strategy']) => <Tag>{tableChangeStrategyLabels[value]}</Tag>,
    },
    {
      title: '风险',
      dataIndex: ['plan', 'risk'],
      width: 96,
      render: (value: DataModelPhysicalChange['plan']['risk']) => <Tag color={tableChangeRiskColors[value]}>{tableChangeRiskLabels[value]}</Tag>,
    },
    {
      title: '原子性',
      dataIndex: ['plan', 'atomicity'],
      width: 150,
      ellipsis: true,
      render: (value: DataModelPhysicalChange['plan']['atomicity']) => tableDdlAtomicityLabels[value],
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: DataModelPhysicalChange['status']) => <Tag color={physicalTableChangeStatusColors[value]}>{physicalTableChangeStatusLabels[value]}</Tag>,
    },
    {
      title: '完成时间',
      dataIndex: 'completedAt',
      width: 174,
      render: (value: string | null) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 76,
      fixed: 'right',
      render: (_value: unknown, change) => (
        <Space size={2}>
          <Tooltip title="查看计划">
            <Button type="text" icon={<EyeOutlined />} aria-label={`查看物理表变更计划${change.id}`} onClick={() => setSelectedChange(change)} />
          </Tooltip>
          {canUpdate && change.status === 'PLANNED' && (
            <Tooltip title="取消计划">
              <Button type="text" danger icon={<StopOutlined />} aria-label={`取消物理表变更计划${change.id}`} loading={cancellingPlanId === change.id} onClick={() => cancel(change)} />
            </Tooltip>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div className="model-detail-tab-panel model-physical-change-panel">
      {messageContext}
      {modalContext}
      {plansQuery.error && (
        <Alert
          showIcon
          type="error"
          title="物理表变更计划加载失败"
          description={plansQuery.error instanceof Error ? plansQuery.error.message : '请稍后重试。'}
          action={<Button size="small" onClick={() => void plansQuery.refetch()}>重试</Button>}
        />
      )}
      <DetailTableToolbar
        title={<span>变更计划 <span className="model-plan-toolbar-caption">字段变更执行成功后才同步模型字段</span></span>}
        total={plansQuery.data?.totalElements ?? 0}
        current={page}
        pageSize={pageSize}
        onChange={(nextPage, nextPageSize) => { setPage(nextPage); setPageSize(nextPageSize); }}
        onRefresh={() => void plansQuery.refetch()}
        refreshing={plansQuery.isFetching}
        refreshLabel="刷新计划列表"
      />
      <Table<DataModelPhysicalChange>
        size="small"
        className="management-table model-physical-change-table"
        rowKey="id"
        columns={columns}
        dataSource={plansQuery.data?.content ?? []}
        loading={plansQuery.isPending}
        scroll={{ x: 980, y: '100%' }}
        pagination={false}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无物理表变更计划" /> }}
      />
      <DataModelPhysicalChangeDrawer
        open={Boolean(selectedChange)}
        modelId={model.id}
        change={selectedChange}
        canUpdate={canUpdate}
        onClose={() => setSelectedChange(null)}
      />
    </div>
  );
};
