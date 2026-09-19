import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Segmented, Table, Tooltip } from 'antd';
import { useCallback, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useDataModelDataQuery, useDataModelPreview } from '../hooks/useDataModels';
import {
  type DataModel,
  type DataModelDataQueryRequest,
  type DataModelField,
  type DataModelPreview,
} from '../model/dataModel';
import {
  calculateDataModelPreviewColumnWidths,
  dataModelPreviewCellText,
  effectiveDataModelPreviewColumnWidth,
  totalDataModelPreviewColumnsWidth,
  type DataModelPreviewColumnSizing,
  type DataModelPreviewColumnWidths,
} from '../model/dataModelPreviewColumnSizing';
import { DataModelDataQueryPanel } from './DataModelDataQueryPanel';
import { DataModelPreviewColumnTitle } from './DataModelPreviewColumnTitle';
import { DataModelSpatialPreviewPanel } from './DataModelSpatialPreviewPanel';

interface DataModelPreviewPanelProps {
  model: DataModel;
  fields: DataModelField[];
}

type PreviewMode = 'QUICK' | 'CONDITION' | 'SPATIAL';
type QueryRow = Record<string, unknown> & { key: string };
type QueryColumn = Pick<DataModelPreview['columns'][number], 'code' | 'name' | 'fieldType'>;

const renderCell = (value: unknown, fieldType: QueryColumn['fieldType']) => {
  if (typeof value === 'object') return <code>{JSON.stringify(value)}</code>;
  return dataModelPreviewCellText(value, fieldType);
};

const toRows = (rows: Record<string, unknown>[]): QueryRow[] => rows.map((row, index) => ({
  ...row,
  key: `${index}-${JSON.stringify(row)}`,
}));

const toColumns = (
  columns: QueryColumn[],
  autoWidths: DataModelPreviewColumnWidths,
  columnSizing: DataModelPreviewColumnSizing,
): NonNullable<TableProps<QueryRow>['columns']> => columns.map((column) => {
  const width = effectiveDataModelPreviewColumnWidth(
    column.code,
    autoWidths,
    columnSizing.manualWidths,
  );
  return {
    title: (
      <DataModelPreviewColumnTitle
        code={column.code}
        name={column.name}
        fieldType={column.fieldType}
        width={width}
        onWidthChange={(nextWidth) => columnSizing.onManualWidthChange(column.code, nextWidth)}
        onWidthReset={() => columnSizing.onManualWidthReset(column.code)}
      />
    ),
    dataIndex: column.code,
    key: column.code,
    width,
    ellipsis: true,
    render: (value: unknown) => renderCell(value, column.fieldType),
  };
});

export const DataModelPreviewPanel = ({ model, fields }: DataModelPreviewPanelProps) => {
  const [mode, setMode] = useState<PreviewMode>('QUICK');
  const [manualColumnWidths, setManualColumnWidths] = useState<DataModelPreviewColumnWidths>({});
  const quickPreviewQuery = useDataModelPreview(model.id, mode === 'QUICK');
  const { mutateAsync: queryModelData } = useDataModelDataQuery();
  const executeDataQuery = useCallback(
    (request: DataModelDataQueryRequest) => queryModelData({ id: model.id, request }),
    [model.id, queryModelData],
  );
  const quickRows = useMemo(() => toRows(quickPreviewQuery.data?.rows ?? []), [quickPreviewQuery.data]);
  const quickAutoWidths = useMemo(() => calculateDataModelPreviewColumnWidths(
    quickPreviewQuery.data?.columns ?? [],
    quickPreviewQuery.data?.rows ?? [],
  ), [quickPreviewQuery.data]);
  const changeManualColumnWidth = useCallback((code: string, width: number) => {
    setManualColumnWidths((current) => current[code] === width ? current : { ...current, [code]: width });
  }, []);
  const resetManualColumnWidth = useCallback((code: string) => {
    setManualColumnWidths((current) => {
      if (current[code] === undefined) return current;
      const next = { ...current };
      delete next[code];
      return next;
    });
  }, []);
  const columnSizing = useMemo<DataModelPreviewColumnSizing>(() => ({
    manualWidths: manualColumnWidths,
    onManualWidthChange: changeManualColumnWidth,
    onManualWidthReset: resetManualColumnWidth,
  }), [changeManualColumnWidth, manualColumnWidths, resetManualColumnWidth]);
  const quickColumns = useMemo(() => toColumns(
    quickPreviewQuery.data?.columns ?? [],
    quickAutoWidths,
    columnSizing,
  ), [columnSizing, quickAutoWidths, quickPreviewQuery.data?.columns]);
  const quickTableWidth = useMemo(() => totalDataModelPreviewColumnsWidth(
    (quickPreviewQuery.data?.columns ?? []).map((column) => effectiveDataModelPreviewColumnWidth(
      column.code,
      quickAutoWidths,
      manualColumnWidths,
    )),
  ), [manualColumnWidths, quickAutoWidths, quickPreviewQuery.data?.columns]);
  const hasUnsupportedFields = fields.some((field) => field.fieldType === 'BINARY' || field.fieldType === 'GEOMETRY');
  const hasGeometryFields = fields.some((field) => field.fieldType === 'GEOMETRY');
  const modeOptions = useMemo(() => [
    { label: '快速预览', value: 'QUICK' as const },
    { label: '条件查询', value: 'CONDITION' as const },
    ...(hasGeometryFields ? [{ label: '空间预览', value: 'SPATIAL' as const }] : []),
  ], [hasGeometryFields]);
  const modeCaption = mode === 'QUICK'
    ? `读取最多 50 条数据，不执行总数统计；结果仅用于快速查看物理表。${hasUnsupportedFields ? '二进制和空间字段不会返回。' : ''}`
    : mode === 'CONDITION' && hasUnsupportedFields
      ? '二进制和空间字段不会返回，也不能参与筛选或排序。'
      : undefined;

  return (
    <div className="model-detail-tab-panel model-data-preview-panel">
      <div className="model-data-preview-mode-bar">
        <div className="model-data-preview-mode-main">
          <Segmented<PreviewMode>
            size="small"
            value={mode}
            options={modeOptions}
            onChange={setMode}
          />
          {modeCaption && (
            <Tooltip title={modeCaption}>
              <span className="model-preview-caption model-data-preview-mode-caption">{modeCaption}</span>
            </Tooltip>
          )}
        </div>
        {mode === 'QUICK' && (
          <Button
            size="small"
            icon={<ReloadOutlined />}
            loading={quickPreviewQuery.isFetching}
            onClick={() => void quickPreviewQuery.refetch()}
          >
            刷新
          </Button>
        )}
      </div>
      {mode === 'QUICK' ? (
        <>
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
              size="small" className="management-table model-preview-resizable-table" rowKey="key" loading={quickPreviewQuery.isFetching}
              columns={quickColumns} dataSource={quickRows}
              locale={{ emptyText: quickPreviewQuery.isPending ? '正在读取物理表数据…' : '物理表暂无数据' }}
              scroll={{ x: quickTableWidth || undefined, y: '100%' }} pagination={false}
              footer={quickPreviewQuery.data?.truncated ? () => '还存在更多数据，请使用条件查询浏览。' : undefined}
            />
          )}
        </>
      ) : mode === 'CONDITION' ? (
        <DataModelDataQueryPanel
          fields={fields}
          query={executeDataQuery}
          columnSizing={columnSizing}
        />
      ) : (
        <DataModelSpatialPreviewPanel modelId={model.id} />
      )}
    </div>
  );
};
