import { Button, Descriptions, Modal, Space, Table, Tag, Typography } from 'antd';
import {
  physicalTableModeLabels,
  type DataModelDetail,
  type DataModelField,
} from '../../../model';

const fieldTypeLabel = (field: DataModelField) => {
  if (field.fieldType === 'STRING' && field.length !== null) return `STRING(${field.length})`;
  if (field.fieldType === 'DECIMAL') return `DECIMAL(${field.precision ?? '?'},${field.scale ?? '?'})`;
  return field.fieldType;
};

const CanvasModelDetailContent = ({ detail }: { detail: DataModelDetail }) => {
  const fields = [...detail.fields].sort((left, right) => left.sortOrder - right.sortOrder);
  return (
    <Space orientation="vertical" size={12} className="canvas-model-detail-content">
      <Descriptions size="small" bordered column={2}>
        <Descriptions.Item label="模型名称">{detail.model.name}</Descriptions.Item>
        <Descriptions.Item label="模型编码"><Typography.Text code>{detail.model.code}</Typography.Text></Descriptions.Item>
        <Descriptions.Item label="结构版本">v{detail.model.schemaVersion}</Descriptions.Item>
        <Descriptions.Item label="物理模式"><Tag>{physicalTableModeLabels[detail.model.physicalTableMode]}</Tag></Descriptions.Item>
        <Descriptions.Item label="数据源" span={2}>{detail.model.storageDataSourceName}</Descriptions.Item>
        <Descriptions.Item label="物理表" span={2}>
          <Typography.Text code>{detail.model.physicalTableName}</Typography.Text>
        </Descriptions.Item>
      </Descriptions>
      <Table<DataModelField>
        size="small"
        rowKey="id"
        pagination={false}
        scroll={{ x: 680, y: 360 }}
        dataSource={fields}
        columns={[
          { title: '字段名称', dataIndex: 'name', width: 150, ellipsis: true },
          { title: '字段编码', dataIndex: 'code', width: 150, ellipsis: true },
          { title: '平台类型', key: 'fieldType', width: 140, render: (_, field) => fieldTypeLabel(field) },
          { title: '非空', dataIndex: 'nullable', width: 70, render: (nullable: boolean) => nullable ? '否' : '是' },
          { title: '主键', dataIndex: 'primaryKey', width: 70, render: (primaryKey: boolean) => primaryKey ? '是' : '否' },
        ]}
      />
    </Space>
  );
};

export const CanvasModelDetailModal = ({
  detail,
  open,
  onClose,
}: {
  detail: DataModelDetail | undefined;
  open: boolean;
  onClose: () => void;
}) => (
  <Modal
    open={open && Boolean(detail)}
    title={detail ? `模型详情 · ${detail.model.name}` : '模型详情'}
    width={820}
    destroyOnHidden
    onCancel={onClose}
    footer={<Button onClick={onClose}>关闭</Button>}
  >
    {detail && <CanvasModelDetailContent detail={detail} />}
  </Modal>
);
