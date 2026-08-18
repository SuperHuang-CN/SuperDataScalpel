import { CheckCircleOutlined, CodeOutlined, DatabaseOutlined, ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Alert, Button, Descriptions, Modal, Space, Table, Tag, Typography, message } from 'antd';
import {
  dataModelStatusLabels,
  physicalTableModeLabels,
  physicalTableStateColors,
  physicalTableStateLabels,
  type DataModel,
  type DataModelStatus,
  type PhysicalTableDifference,
} from '../model/dataModel';
import { fetchPhysicalTableDdlPlan } from '../api/dataModelApi';
import {
  useCreatePhysicalTable,
  usePhysicalTableInspection,
} from '../hooks/useDataModels';

interface DataModelBasicPanelProps {
  model: DataModel;
  directoryName?: string;
  canManagePhysicalTable: boolean;
}

const statusColor: Record<DataModelStatus, string> = {
  DRAFT: 'default',
  PUBLISHED: 'success',
  DISABLED: 'warning',
};

const formatDateTime = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium',
  timeStyle: 'medium',
  hour12: false,
}).format(new Date(value));

const differenceLabels: Record<PhysicalTableDifference['type'], string> = {
  MISSING_COLUMN: '缺少字段',
  EXTRA_COLUMN: '多余字段',
  TYPE_MISMATCH: '类型不一致',
  LENGTH_MISMATCH: '长度不一致',
  PRECISION_MISMATCH: '精度不一致',
  NULLABILITY_MISMATCH: '空值约束不一致',
  PRIMARY_KEY_MISMATCH: '主键不一致',
  STORAGE_CONFIGURATION_MISMATCH: '存储配置不一致',
};

const differenceColumns: TableProps<PhysicalTableDifference>['columns'] = [
  { title: '字段', dataIndex: 'column', width: 180, render: (value: string | null) => value ? <code>{value}</code> : '主键约束' },
  { title: '差异', dataIndex: 'type', width: 150, render: (value: PhysicalTableDifference['type']) => differenceLabels[value] },
  { title: '模型定义', dataIndex: 'expected', width: 260, ellipsis: true, render: (value: string) => <code>{value}</code> },
  { title: '实际表结构', dataIndex: 'actual', width: 260, ellipsis: true, render: (value: string) => <code>{value}</code> },
];

export const DataModelBasicPanel = ({ model, directoryName, canManagePhysicalTable }: DataModelBasicPanelProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const inspectionQuery = usePhysicalTableInspection(model.id, true);
  const createMutation = useCreatePhysicalTable();
  const inspection = inspectionQuery.data;
  const actionLoading = createMutation.isPending;

  const showDdl = async (forCreate = false) => {
    try {
      const plan = await fetchPhysicalTableDdlPlan(model.id);
      if (!plan.supported) {
        messageApi.warning(plan.message);
        return;
      }
      const content = (
        <div className="physical-table-ddl-dialog">
          <Alert type="warning" showIcon title="SQL 仅供查看，将由系统根据模型字段执行，不能直接编辑。" />
          <Typography.Paragraph className="physical-table-ddl-code" copyable={{ text: plan.statements.join(';\n') }}>
            <pre>{plan.statements.join(';\n')}</pre>
          </Typography.Paragraph>
        </div>
      );
      if (!forCreate) {
        modalApi.info({ rootClassName: 'business-overlay business-modal-overlay', title: '建表 SQL', content, width: 820, okText: '关闭' });
        return;
      }
      modalApi.confirm({
        rootClassName: 'business-overlay business-modal-overlay',
        title: '创建物理表',
        content,
        width: 820,
        okText: '确认创建',
        cancelText: '取消',
        onOk: async () => {
          const result = await createMutation.mutateAsync(model.id);
          if (result.compatible) messageApi.success('物理表已创建并完成结构校验');
          else messageApi.error(`建表未就绪：${result.message}`);
        },
      });
    } catch (error) {
      messageApi.error(error instanceof Error ? error.message : '读取建表 SQL 失败');
    }
  };

  return (
    <div className="model-detail-tab-panel model-basic-panel">
      {messageContext}
      {modalContext}
    <div className="model-basic-section">
      <div className="model-basic-section-title">基本标识</div>
      <Descriptions size="small" bordered column={3}>
        <Descriptions.Item label="模型名称">{model.name}</Descriptions.Item>
        <Descriptions.Item label="模型编码"><code>{model.code}</code></Descriptions.Item>
        <Descriptions.Item label="状态">
          <Tag color={statusColor[model.status]}>{dataModelStatusLabels[model.status]}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="所属目录">{directoryName ?? (model.directoryId ? '—' : '未分类')}</Descriptions.Item>
        <Descriptions.Item label="数仓分层">
          {model.warehouseLayer ? (
            <Space size={4}>
              <Tag color={model.warehouseLayer.color ?? undefined}>
                {model.warehouseLayer.code} · {model.warehouseLayer.name}
              </Tag>
              {!model.warehouseLayer.enabled && <Tag color="warning">已停用</Tag>}
            </Space>
          ) : <Typography.Text type="secondary">未分层</Typography.Text>}
        </Descriptions.Item>
        <Descriptions.Item label="分层编码规范">
          {model.warehouseLayer?.modelCodePrefix
            ? <code>{model.warehouseLayer.modelCodePrefix}*</code>
            : <Typography.Text type="secondary">未配置</Typography.Text>}
        </Descriptions.Item>
        <Descriptions.Item label="创建时间">{formatDateTime(model.createdAt)}</Descriptions.Item>
        <Descriptions.Item label="更新时间">{formatDateTime(model.updatedAt)}</Descriptions.Item>
      </Descriptions>
    </div>
    <div className="model-basic-section">
      <div className="model-basic-section-title">存储位置</div>
      <Descriptions size="small" bordered column={2}>
        <Descriptions.Item label="JDBC 数据源">{model.storageDataSourceName}</Descriptions.Item>
        <Descriptions.Item label="物理表名"><code>{model.physicalTableName}</code></Descriptions.Item>
      </Descriptions>
    </div>
    <div className="model-basic-section physical-table-section">
      <div className="model-basic-section-title">物理表状态</div>
      {inspectionQuery.error && (
        <Alert
          showIcon
          type="error"
          title="物理表检查失败"
          description={inspectionQuery.error instanceof Error ? inspectionQuery.error.message : '请检查 JDBC 数据源连接后重试。'}
          action={<Button size="small" onClick={() => void inspectionQuery.refetch()}>重试</Button>}
        />
      )}
      {inspection && (
        <>
          <div className="physical-table-status-bar">
            <Space size={8} wrap>
              <DatabaseOutlined />
              <span>{physicalTableModeLabels[inspection.mode]}</span>
              <Tag color={physicalTableStateColors[inspection.state]}>{physicalTableStateLabels[inspection.state]}</Tag>
              <span className="physical-table-status-message">{inspection.message}</span>
            </Space>
            <Space size={4}>
              <Button icon={<ReloadOutlined />} loading={inspectionQuery.isFetching} onClick={() => void inspectionQuery.refetch()}>检查</Button>
              {inspection.mode === 'MANAGED' && inspection.createSupported && (
                <Button icon={<CodeOutlined />} onClick={() => void showDdl()}>查看 DDL</Button>
              )}
              {canManagePhysicalTable && model.status === 'DRAFT' && inspection.mode === 'MANAGED' && inspection.state === 'NOT_FOUND' && inspection.createSupported && (
                <Button type="primary" icon={<CheckCircleOutlined />} loading={actionLoading} onClick={() => void showDdl(true)}>创建物理表</Button>
              )}
            </Space>
          </div>
          {inspection.differences.length > 0 && (
            <Table<PhysicalTableDifference>
              size="small"
              className="physical-table-difference-table"
              rowKey={(difference, index) => `${difference.type}-${difference.column ?? 'pk'}-${index}`}
              columns={differenceColumns}
              dataSource={inspection.differences}
              pagination={false}
              scroll={{ x: 800, y: 190 }}
            />
          )}
        </>
      )}
      {inspectionQuery.isPending && <Alert showIcon type="info" title="正在检查物理表结构…" />}
    </div>
    <div className="model-basic-section">
      <div className="model-basic-section-title">业务说明</div>
      <div className="model-description-box">{model.description || '—'}</div>
    </div>
    </div>
  );
};
