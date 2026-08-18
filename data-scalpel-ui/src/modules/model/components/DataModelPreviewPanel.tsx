import { ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Alert, Button, Segmented, Table, Tag } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useDataModelDataQuery, useDataModelPreview } from '../hooks/useDataModels';
import {
  dataModelFieldTypeLabels,
  type DataModel,
  type DataModelField,
  type DataModelPreview,
} from '../model/dataModel';
import { DataModelDataQueryPanel } from './DataModelDataQueryPanel';
import { DataModelSpatialPreviewPanel } from './DataModelSpatialPreviewPanel';

interface DataModelPreviewPanelProps {
  model: DataModel;
  fields: DataModelField[];
}

type PreviewMode = 'QUICK' | 'CONDITION' | 'SPATIAL';
type QueryRow = Record<string, unknown> & { key: string };
type QueryColumn = Pick<DataModelPreview['columns'][number], 'code' | 'name' | 'fieldType'>;

const renderCell = (value: unknown) => {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'object') return <code>{JSON.stringify(value)}</code>;
  return String(value);
};

const toRows = (rows: Record<string, unknown>[]): QueryRow[] => rows.map((row, index) => ({
  ...row,
  key: `${index}-${JSON.stringify(row)}`,
}));

const toColumns = (columns: QueryColumn[]): NonNullable<TableProps<QueryRow>['columns']> => columns.map((column) => ({
  title: <span>{column.name}<Tag className="model-preview-type-tag">{dataModelFieldTypeLabels[column.fieldType]}</Tag></span>,
  dataIndex: column.code,
  key: column.code,
  width: 180,
  ellipsis: true,
  render: renderCell,
}));

export const DataModelPreviewPanel = ({ model, fields }: DataModelPreviewPanelProps) => {
  const [mode, setMode] = useState<PreviewMode>('QUICK');
  const quickPreviewQuery = useDataModelPreview(model.id, mode === 'QUICK');
  const dataQueryMutation = useDataModelDataQuery();
  const quickRows = useMemo(() => toRows(quickPreviewQuery.data?.rows ?? []), [quickPreviewQuery.data]);
  const quickColumns = useMemo(() => toColumns(quickPreviewQuery.data?.columns ?? []), [quickPreviewQuery.data]);
  const hasUnsupportedFields = fields.some((field) => field.fieldType === 'BINARY' || field.fieldType === 'GEOMETRY');
  const hasGeometryFields = fields.some((field) => field.fieldType === 'GEOMETRY');
  const modeOptions = useMemo(() => [
    { label: '快速预览', value: 'QUICK' as const },
    { label: '条件查询', value: 'CONDITION' as const },
    ...(hasGeometryFields ? [{ label: '空间预览', value: 'SPATIAL' as const }] : []),
  ], [hasGeometryFields]);

  return (
    <div className="model-detail-tab-panel model-data-preview-panel">
      <div className="model-data-preview-mode-bar">
        <Segmented<PreviewMode>
          size="small"
          value={mode}
          options={modeOptions}
          onChange={setMode}
        />
        {hasUnsupportedFields && <span className="model-preview-caption">二进制和空间字段不会返回，也不能参与筛选或排序。</span>}
      </div>
      {mode === 'QUICK' ? (
        <>
          <div className="model-tab-toolbar">
            <div className="model-preview-caption">读取最多 50 条数据，不执行总数统计；结果仅用于快速查看物理表。</div>
            <Button size="small" icon={<ReloadOutlined />} loading={quickPreviewQuery.isFetching} onClick={() => void quickPreviewQuery.refetch()}>刷新</Button>
          </div>
          {quickPreviewQuery.error ? (
            <Alert
              type="warning"
              showIcon
              title="暂不能预览物理表数据"
              description={quickPreviewQuery.error instanceof ApiError ? quickPreviewQuery.error.message : '请先检查并准备物理表。'}
              action={<Button size="small" onClick={() => void quickPreviewQuery.refetch()}>重试</Button>}
            />
          ) : (
            <Table<QueryRow>
              size="small" className="management-table" rowKey="key" loading={quickPreviewQuery.isFetching}
              columns={quickColumns} dataSource={quickRows}
              locale={{ emptyText: quickPreviewQuery.isPending ? '正在读取物理表数据…' : '物理表暂无数据' }}
              scroll={{ x: Math.max(900, quickColumns.length * 180), y: '100%' }} pagination={false}
              footer={quickPreviewQuery.data?.truncated ? () => '还存在更多数据，请使用条件查询浏览。' : undefined}
            />
          )}
        </>
      ) : mode === 'CONDITION' ? (
        <DataModelDataQueryPanel
          fields={fields}
          query={(request) => dataQueryMutation.mutateAsync({ id: model.id, request })}
        />
      ) : (
        <DataModelSpatialPreviewPanel modelId={model.id} />
      )}
    </div>
  );
};
