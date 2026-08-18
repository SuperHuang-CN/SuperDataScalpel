import { CheckCircleOutlined, CloseOutlined, ReloadOutlined, WarningOutlined } from '@ant-design/icons';
import type { CollapseProps, TableProps } from 'antd';
import { Alert, Button, Collapse, Descriptions, Drawer, Empty, Modal, Space, Table, Tag, Tooltip, Typography, message } from 'antd';
import { ApiError } from '../../../shared/api/http';
import {
  useCancelPhysicalTableChangePlan,
  useExecutePhysicalTableChangePlan,
  usePhysicalTableChangePlan,
} from '../hooks/useDataModels';
import {
  physicalTableChangeStatusColors,
  physicalTableChangeStatusLabels,
  tableChangeCheckLabels,
  tableChangeExecutionModeLabels,
  tableChangeOperationLabels,
  tableChangeRiskColors,
  tableChangeRiskLabels,
  tableChangeStrategyLabels,
  tableDdlAtomicityLabels,
  type DataModelPhysicalChange,
  type PhysicalTableChangeCheck,
  type PhysicalTableChangeColumn,
  type PhysicalTableChangeOperation,
  type TableChangeExecutionMode,
} from '../model/dataModel';

interface DataModelPhysicalChangeDrawerProps {
  open: boolean;
  modelId: string;
  change: DataModelPhysicalChange | null;
  canUpdate: boolean;
  onClose: () => void;
}

const formatDateTime = (value: string | null) => {
  if (!value) return '—';
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'medium',
    hour12: false,
  }).format(new Date(value));
};

const formatColumn = (column: PhysicalTableChangeColumn | null) => {
  if (!column) return '—';
  if (column.type === 'STRING') return `${column.name} : STRING(${column.length ?? '—'})${column.nullable ? '' : ' NOT NULL'}`;
  if (column.type === 'DECIMAL') return `${column.name} : DECIMAL(${column.precision ?? '—'}, ${column.scale ?? '—'})${column.nullable ? '' : ' NOT NULL'}`;
  return `${column.name} : ${column.type}${column.nullable ? '' : ' NOT NULL'}`;
};

const changeErrorAlert = (change: DataModelPhysicalChange) => {
  if (change.status === 'PARTIAL') {
    return (
      <Alert
        showIcon
        type="warning"
        title="物理表可能已部分完成，必须人工核验后再继续处理。"
        description={change.errorMessage || '系统未能完成后续校验或模型字段快照保存。'}
      />
    );
  }
  if (change.status === 'FAILED') {
    return (
      <Alert
        showIcon
        type="error"
        title={change.errorCode ? `执行失败：${change.errorCode}` : '执行失败'}
        description={change.errorMessage || '请根据失败原因修复后重新生成计划。'}
      />
    );
  }
  if (change.status === 'APPLYING') {
    return <Alert showIcon type="info" title="计划处于执行中状态，请等待执行端完成后刷新。" />;
  }
  if (change.status === 'SUPERSEDED') {
    return <Alert showIcon type="info" title="该计划已被后续计划替代，不能执行。" />;
  }
  return null;
};

const operationColumns: TableProps<PhysicalTableChangeOperation>['columns'] = [
  {
    title: '操作',
    dataIndex: 'type',
    width: 132,
    render: (value: PhysicalTableChangeOperation['type']) => tableChangeOperationLabels[value],
  },
  {
    title: '原定义',
    dataIndex: 'beforeColumn',
    width: 260,
    ellipsis: true,
    render: (value: PhysicalTableChangeColumn | null) => value ? <code>{formatColumn(value)}</code> : '—',
  },
  {
    title: '目标定义',
    dataIndex: 'afterColumn',
    width: 260,
    ellipsis: true,
    render: (value: PhysicalTableChangeColumn | null) => value ? <code>{formatColumn(value)}</code> : '—',
  },
  {
    title: '策略',
    dataIndex: 'strategy',
    width: 128,
    render: (value: PhysicalTableChangeOperation['strategy']) => <Tag>{tableChangeStrategyLabels[value]}</Tag>,
  },
  {
    title: '风险',
    dataIndex: 'risk',
    width: 92,
    render: (value: PhysicalTableChangeOperation['risk']) => <Tag color={tableChangeRiskColors[value]}>{tableChangeRiskLabels[value]}</Tag>,
  },
  {
    title: '说明',
    dataIndex: 'reasons',
    width: 300,
    ellipsis: true,
    render: (value: PhysicalTableChangeOperation['reasons']) => value.length ? value.map((reason) => reason.message).join('；') : '—',
  },
];

const checkColumns: TableProps<PhysicalTableChangeCheck>['columns'] = [
  {
    title: '检查项',
    dataIndex: 'type',
    width: 170,
    render: (value: PhysicalTableChangeCheck['type']) => tableChangeCheckLabels[value],
  },
  { title: '字段', dataIndex: 'columnNames', width: 180, render: (value: string[]) => value.length ? value.map((name) => <code key={name}>{name}</code>) : '—' },
  { title: '说明', dataIndex: 'description', ellipsis: true },
];

const executionButtonLabel = (mode: TableChangeExecutionMode) => (
  mode === 'IN_PLACE' ? '执行原表修改' : '执行重建表'
);

export const DataModelPhysicalChangeDrawer = ({
  open,
  modelId,
  change,
  canUpdate,
  onClose,
}: DataModelPhysicalChangeDrawerProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const detailQuery = usePhysicalTableChangePlan(modelId, change?.id, open && Boolean(change));
  const cancelMutation = useCancelPhysicalTableChangePlan();
  const executeMutation = useExecutePhysicalTableChangePlan();
  const current = detailQuery.data ?? change;

  const cancel = (target: DataModelPhysicalChange) => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: '取消变更计划',
    content: '取消后不会修改模型字段或物理表；如需继续修改，请重新生成计划。',
    okText: '确认取消',
    cancelText: '返回',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await cancelMutation.mutateAsync({ modelId, planId: target.id });
        messageApi.success('变更计划已取消');
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.message : '取消变更计划失败');
        throw error;
      }
    },
  });

  const execute = (target: DataModelPhysicalChange, mode: TableChangeExecutionMode) => {
    const option = target.plan.executionOptions.find((item) => item.mode === mode);
    if (!option) return;
    modalApi.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: executionButtonLabel(mode),
      width: 540,
      content: (
        <Descriptions size="small" column={1} bordered>
          <Descriptions.Item label="执行方式">{tableChangeExecutionModeLabels[mode]}</Descriptions.Item>
          <Descriptions.Item label="最高风险"><Tag color={tableChangeRiskColors[target.plan.risk]}>{tableChangeRiskLabels[target.plan.risk]}</Tag></Descriptions.Item>
          <Descriptions.Item label="原子性">{tableDdlAtomicityLabels[option.atomicity]}</Descriptions.Item>
          <Descriptions.Item label="受控 SQL">{option.statements.length} 条，仅执行当前计划快照</Descriptions.Item>
        </Descriptions>
      ),
      okText: executionButtonLabel(mode),
      cancelText: '取消',
      okButtonProps: { danger: target.plan.risk === 'DESTRUCTIVE' },
      onOk: async () => {
        try {
          const result = await executeMutation.mutateAsync({
            modelId,
            planId: target.id,
            request: { executionMode: mode },
          });
          if (result.status === 'SUCCEEDED') messageApi.success('物理表变更已完成，模型字段已同步更新');
          else if (result.status === 'PARTIAL') messageApi.warning('物理表可能已部分完成，请先人工核验');
          else messageApi.info(`计划状态：${physicalTableChangeStatusLabels[result.status]}`);
        } catch (error) {
          messageApi.error(error instanceof ApiError ? error.message : '执行物理表变更失败');
          throw error;
        }
      },
    });
  };

  const sqlItems: CollapseProps['items'] = current?.plan.executionOptions.map((option) => ({
    key: option.mode,
    label: `${tableChangeExecutionModeLabels[option.mode]} · ${tableDdlAtomicityLabels[option.atomicity]} · ${option.statements.length} 条 SQL`,
    children: (
      <Typography.Paragraph className="physical-table-ddl-code" copyable={{ text: option.statements.join(';\n') }}>
        <pre>{option.statements.join(';\n')}</pre>
      </Typography.Paragraph>
    ),
  })) ?? [];

  return (
    <>
      {messageContext}
      {modalContext}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        title="物理表变更计划"
        open={open}
        size="large"
        className="physical-change-drawer"
        onClose={onClose}
        destroyOnHidden
        extra={(
          <Tooltip title="刷新计划状态">
            <Button type="text" icon={<ReloadOutlined />} aria-label="刷新物理表变更计划" loading={detailQuery.isFetching} onClick={() => void detailQuery.refetch()} />
          </Tooltip>
        )}
        footer={current?.status === 'PLANNED' && canUpdate ? (
          <Space>
            <Button danger icon={<CloseOutlined />} loading={cancelMutation.isPending} onClick={() => cancel(current)}>取消计划</Button>
            {current.plan.executionOptions.map((option) => (
              <Button
                key={option.mode}
                type={option.mode === 'IN_PLACE' ? 'primary' : 'default'}
                danger={option.mode === 'REBUILD'}
                icon={option.mode === 'IN_PLACE' ? <CheckCircleOutlined /> : <WarningOutlined />}
                loading={executeMutation.isPending}
                onClick={() => execute(current, option.mode)}
              >
                {executionButtonLabel(option.mode)}
              </Button>
            ))}
          </Space>
        ) : undefined}
      >
        {!current && detailQuery.isPending && <Alert showIcon type="info" title="正在读取变更计划…" />}
        {!current && detailQuery.error && (
          <Alert
            showIcon
            type="error"
            title="变更计划加载失败"
            description={detailQuery.error instanceof ApiError ? detailQuery.error.message : '请稍后重试。'}
            action={<Button size="small" onClick={() => void detailQuery.refetch()}>重试</Button>}
          />
        )}
        {current && (
          <div className="physical-change-drawer-content">
            {changeErrorAlert(current)}
            <Descriptions size="small" bordered column={2}>
              <Descriptions.Item label="计划状态"><Tag color={physicalTableChangeStatusColors[current.status]}>{physicalTableChangeStatusLabels[current.status]}</Tag></Descriptions.Item>
              <Descriptions.Item label="版本范围">v{current.baseSchemaVersion} → v{current.targetSchemaVersion}</Descriptions.Item>
              <Descriptions.Item label="变更策略"><Tag>{tableChangeStrategyLabels[current.plan.strategy]}</Tag></Descriptions.Item>
              <Descriptions.Item label="最高风险"><Tag color={tableChangeRiskColors[current.plan.risk]}>{tableChangeRiskLabels[current.plan.risk]}</Tag></Descriptions.Item>
              <Descriptions.Item label="DDL 原子性">{tableDdlAtomicityLabels[current.plan.atomicity]}</Descriptions.Item>
              <Descriptions.Item label="执行方式">{current.executionMode ? tableChangeExecutionModeLabels[current.executionMode] : '未选择'}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{formatDateTime(current.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="完成时间">{formatDateTime(current.completedAt)}</Descriptions.Item>
              <Descriptions.Item label="原结构指纹" span={2}><Typography.Text code copyable>{current.plan.beforeFingerprint}</Typography.Text></Descriptions.Item>
              <Descriptions.Item label="目标结构指纹" span={2}><Typography.Text code copyable>{current.plan.targetFingerprint}</Typography.Text></Descriptions.Item>
            </Descriptions>

            {current.plan.reasons.length > 0 && (
              <Alert
                showIcon
                type={current.plan.strategy === 'UNSUPPORTED' ? 'warning' : 'info'}
                title="方言结论"
                description={<ul className="physical-change-reason-list">{current.plan.reasons.map((reason) => <li key={`${reason.code}-${reason.message}`}>{reason.message}</li>)}</ul>}
              />
            )}

            <section className="physical-change-section">
              <div className="model-basic-section-title">变更操作</div>
              <Table<PhysicalTableChangeOperation>
                size="small"
                rowKey={(operation, index) => `${operation.type}-${operation.beforeColumn?.columnId ?? operation.afterColumn?.columnId ?? index}`}
                columns={operationColumns}
                dataSource={current.plan.operations}
                pagination={false}
                locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有物理表操作" /> }}
                scroll={{ x: 1100, y: 220 }}
              />
            </section>

            <section className="physical-change-section">
              <div className="model-basic-section-title">执行前检查</div>
              <Table<PhysicalTableChangeCheck>
                size="small"
                rowKey={(check, index) => `${check.type}-${check.columnNames.join('-')}-${index}`}
                columns={checkColumns}
                dataSource={current.plan.checks}
                pagination={false}
                locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无需额外检查" /> }}
                scroll={{ x: 620, y: 180 }}
              />
            </section>

            <section className="physical-change-section">
              <div className="model-basic-section-title">受控 SQL</div>
              {sqlItems.length ? <Collapse size="small" items={sqlItems} /> : <Alert showIcon type="warning" title="当前计划没有可执行的 SQL 方案。" />}
            </section>
          </div>
        )}
      </Drawer>
    </>
  );
};
