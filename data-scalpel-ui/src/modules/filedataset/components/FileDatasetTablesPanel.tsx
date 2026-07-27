import { ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Alert, Button, Table, Tag } from 'antd';
import {
  fileDatasetParseStatusLabels,
  type FileDataset,
  type FileDatasetParseStatus,
  type FileDatasetTable,
} from '../model/fileDataset';
import { FileDatasetTableResultPanel } from './FileDatasetTableResultPanel';

interface FileDatasetTablesPanelProps {
  dataset: FileDataset;
  tables: FileDatasetTable[];
  selectedTableId?: string;
  canUpdate: boolean;
  loading: boolean;
  error: boolean;
  onSelectTable: (tableId: string) => void;
  onRefresh: () => void;
}

const parseStatusColors: Record<FileDatasetParseStatus, string> = {
  QUEUED: 'blue', PARSING: 'processing', SCHEMA_READY: 'warning', READY: 'success',
};

export const FileDatasetTablesPanel = ({
  dataset,
  tables,
  selectedTableId,
  canUpdate,
  loading,
  error,
  onSelectTable,
  onRefresh,
}: FileDatasetTablesPanelProps) => {
  const selectedTable = tables.find((table) => table.id === selectedTableId) ?? null;
  const columns: TableProps<FileDatasetTable>['columns'] = [
    {
      title: '逻辑表',
      dataIndex: 'name',
      ellipsis: true,
      render: (value: string, table: FileDatasetTable) => (
        <div className="file-dataset-table-list-name">
          <span>{value}</span>
          <small>{table.sourceCount} 个来源 · {table.totalRowCount} 条</small>
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'parseStatus',
      width: 92,
      render: (value: FileDatasetParseStatus, table: FileDatasetTable) => (
        <Tag color={table.currentLoadJobId ? 'processing' : parseStatusColors[value]}>
          {fileDatasetParseStatusLabels[value]}{table.currentLoadJobId ? ' · 装载中' : ''}
        </Tag>
      ),
    },
  ];

  return (
    <div className="file-dataset-detail-tab-panel file-dataset-tables-panel">
      {error && (
        <Alert
          type="error"
          showIcon
          message="逻辑表加载失败"
          action={<Button size="small" onClick={onRefresh}>重试</Button>}
        />
      )}
      <div className="file-dataset-table-workspace">
        <div className="file-dataset-table-list">
          <div className="file-dataset-table-list-toolbar">
            <span>逻辑表 {tables.length}</span>
            <Button type="text" icon={<ReloadOutlined />} aria-label="刷新逻辑表" onClick={onRefresh} />
          </div>
          <Table<FileDatasetTable>
            size="small"
            rowKey="id"
            columns={columns}
            dataSource={tables}
            loading={loading}
            pagination={false}
            showHeader={false}
            scroll={{ y: '100%' }}
            rowClassName={(table) => table.id === selectedTableId ? 'file-dataset-table-list-row-selected' : ''}
            onRow={(table) => ({ onClick: () => onSelectTable(table.id) })}
            locale={{ emptyText: '尚无逻辑表，请先上传文件' }}
          />
        </div>
        <FileDatasetTableResultPanel dataset={dataset} table={selectedTable} canUpdate={canUpdate} />
      </div>
    </div>
  );
};
