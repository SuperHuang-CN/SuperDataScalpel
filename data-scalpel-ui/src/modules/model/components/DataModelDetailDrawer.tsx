import type { TableProps } from 'antd';
import { Alert, Button, Descriptions, Drawer, Table, Tag } from 'antd';
import { useDataModel } from '../hooks/useDataModels';
import {
  dataModelFieldTypeLabels,
  dataModelStatusLabels,
  physicalLocation,
  type DataModel,
  type DataModelField,
  type DataModelStatus,
} from '../model/dataModel';

interface DataModelDetailDrawerProps {
  open: boolean;
  model: DataModel | null;
  directoryName?: string;
  onClose: () => void;
}

const statusColor: Record<DataModelStatus, string> = {
  DRAFT: 'default',
  PUBLISHED: 'success',
  DISABLED: 'warning',
};

const typeDescription = (field: DataModelField) => {
  if (field.fieldType === 'STRING') return `${dataModelFieldTypeLabels[field.fieldType]}(${field.length ?? '—'})`;
  if (field.fieldType === 'DECIMAL') return `${dataModelFieldTypeLabels[field.fieldType]}(${field.precision ?? '—'},${field.scale ?? 0})`;
  return dataModelFieldTypeLabels[field.fieldType];
};

const fieldColumns: TableProps<DataModelField>['columns'] = [
  { title: '字段编码', dataIndex: 'code', width: 150, ellipsis: true, render: (value: string) => <code>{value}</code> },
  { title: '字段名称', dataIndex: 'name', width: 150, ellipsis: true },
  { title: '类型', key: 'type', width: 130, render: (_value, field) => typeDescription(field) },
  { title: '主键', dataIndex: 'primaryKey', width: 70, render: (value: boolean) => value ? <Tag color="blue">是</Tag> : '—' },
  { title: '允许为空', dataIndex: 'nullable', width: 90, render: (value: boolean) => value ? '是' : '否' },
  { title: '说明', dataIndex: 'description', ellipsis: true, render: (value?: string) => value || '—' },
];

export const DataModelDetailDrawer = ({ open, model, directoryName, onClose }: DataModelDetailDrawerProps) => {
  const detailQuery = useDataModel(model?.id, open);
  const detailModel = detailQuery.data?.model ?? model;

  return (
    <Drawer
      title={`模型详情 · ${detailModel?.name ?? ''}`}
      open={open}
      size="min(960px, 90vw)"
      className="data-model-detail-drawer"
      destroyOnHidden
      onClose={onClose}
    >
      <Alert
        type="info"
        showIcon
        title="当前为元数据模式"
        description="模型发布只更新状态并锁定字段定义，不会连接数据存储或操作物理表。"
      />
      {detailQuery.error && (
        <Alert
          type="error"
          showIcon
          title="模型详情加载失败"
          action={<Button size="small" onClick={() => void detailQuery.refetch()}>重试</Button>}
        />
      )}
      {detailModel && (
        <Descriptions size="small" bordered column={2} className="data-model-descriptions">
          <Descriptions.Item label="模型名称">{detailModel.name}</Descriptions.Item>
          <Descriptions.Item label="模型编码"><code>{detailModel.code}</code></Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={statusColor[detailModel.status]}>{dataModelStatusLabels[detailModel.status]}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="目录">{directoryName ?? (detailModel.directoryId ? '—' : '未分类')}</Descriptions.Item>
          <Descriptions.Item label="数据存储">{detailModel.storageDataSourceName}</Descriptions.Item>
          <Descriptions.Item label="物理位置"><code>{physicalLocation(detailModel)}</code></Descriptions.Item>
          <Descriptions.Item label="说明" span={2}>{detailModel.description || '—'}</Descriptions.Item>
        </Descriptions>
      )}
      <div className="data-model-detail-section-title">字段定义（{detailQuery.data?.fields.length ?? 0}）</div>
      <Table<DataModelField>
        size="small"
        rowKey="id"
        columns={fieldColumns}
        dataSource={detailQuery.data?.fields ?? []}
        loading={detailQuery.isFetching}
        pagination={false}
        scroll={{ x: 760, y: 420 }}
      />
    </Drawer>
  );
};
