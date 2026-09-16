import { Table } from 'antd';
import { platformTypeLabel } from '../../canvasSchema';
import type { CanvasColumnSchema } from '../../canvasTypes';

export const CanvasFieldPreview = ({
  columns,
  loading = false,
}: {
  columns: CanvasColumnSchema[];
  loading?: boolean;
}) => <Table<CanvasColumnSchema>
  size="small"
  rowKey="name"
  pagination={false}
  loading={loading}
  scroll={{ y: 220 }}
  dataSource={columns}
  columns={[
    { title: '字段', dataIndex: 'name', ellipsis: true },
    { title: '平台类型', key: 'type', width: 125, render: (_, column) => platformTypeLabel(column) },
    { title: '可空', dataIndex: 'nullable', width: 54, render: (nullable: boolean) => nullable ? '是' : '否' },
  ]}
/>;
