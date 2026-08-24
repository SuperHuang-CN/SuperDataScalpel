import {
  DeleteOutlined,
  EditOutlined,
  LeftOutlined,
  PlusOutlined,
  ReloadOutlined,
  RightOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import type { ReactNode } from 'react';
import type { TableProps } from 'antd';
import { Alert, Button, Form, Input, Modal, Popover, Select, Space, Table, Tooltip } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  type DataModelDataQueryFilterOperator,
  type DataModelDataQueryRequest,
  type DataModelDataQueryResponse,
  type DataModelField,
} from '../model/dataModel';
import {
  calculateDataModelPreviewColumnWidths,
  dataModelPreviewCellText,
  effectiveDataModelPreviewColumnWidth,
  totalDataModelPreviewColumnsWidth,
  type DataModelPreviewColumnSizing,
  type DataModelPreviewColumnWidths,
} from '../model/dataModelPreviewColumnSizing';
import { DataModelPreviewColumnTitle } from './DataModelPreviewColumnTitle';

export type DataModelQueryRow = Record<string, unknown> & { __rowKey: string };

export interface DataModelDataQueryPanelProps {
  fields: DataModelField[];
  query: (request: DataModelDataQueryRequest) => Promise<DataModelDataQueryResponse>;
  rowKey?: (row: Record<string, unknown>, index: number) => string;
  renderCell?: (value: unknown, fieldCode: string, row: Record<string, unknown>) => ReactNode;
  rowSelection?: (rows: DataModelQueryRow[]) => TableProps<DataModelQueryRow>['rowSelection'];
  toolbar?: ReactNode;
  onResult?: (result: DataModelDataQueryResponse) => void;
  columnSizing?: DataModelPreviewColumnSizing;
}

interface QueryFormValues {
  conditionType: 'AND' | 'OR';
  columns?: string[];
  filters: Array<{
    field: string;
    operator: DataModelDataQueryFilterOperator;
    value?: string;
    secondValue?: string;
    values?: string[];
  }>;
  orders: Array<{ field: string; direction: 'ASC' | 'DESC' }>;
}

const pageSizeOptions = [20, 50, 100].map((value) => ({ value, label: `${value} 条/页` }));
const filterOperatorOptions: Array<{ value: DataModelDataQueryFilterOperator; label: string }> = [
  { value: 'EQ', label: '等于' }, { value: 'NE', label: '不等于' },
  { value: 'GT', label: '大于' }, { value: 'GE', label: '大于等于' },
  { value: 'LT', label: '小于' }, { value: 'LE', label: '小于等于' },
  { value: 'LIKE', label: '包含' }, { value: 'NOT_LIKE', label: '不包含' },
  { value: 'IN', label: '属于' }, { value: 'NOT_IN', label: '不属于' },
  { value: 'BETWEEN', label: '介于' }, { value: 'NOT_BETWEEN', label: '不介于' },
  { value: 'IS_NULL', label: '为空值' }, { value: 'IS_NOT_NULL', label: '非空值' },
  { value: 'IS_EMPTY', label: '为空字符串' }, { value: 'IS_NOT_EMPTY', label: '非空字符串' },
];
const valueFreeOperators = new Set<DataModelDataQueryFilterOperator>(['IS_NULL', 'IS_NOT_NULL', 'IS_EMPTY', 'IS_NOT_EMPTY']);
const multiValueOperators = new Set<DataModelDataQueryFilterOperator>(['IN', 'NOT_IN']);
const rangeOperators = new Set<DataModelDataQueryFilterOperator>(['BETWEEN', 'NOT_BETWEEN']);
const emptyQueryValues = (): QueryFormValues => ({
  conditionType: 'AND',
  columns: [],
  filters: [],
  orders: [],
});

const normalizeQueryValues = (values: QueryFormValues): QueryFormValues => ({
  conditionType: values.conditionType ?? 'AND',
  columns: [...(values.columns ?? [])],
  filters: (values.filters ?? []).map((filter) => ({
    ...filter,
    ...(filter.values ? { values: [...filter.values] } : {}),
  })),
  orders: (values.orders ?? []).map((order) => ({ ...order })),
});

const defaultRenderCell = (value: unknown) => {
  if (typeof value === 'object') return <code>{JSON.stringify(value)}</code>;
  return dataModelPreviewCellText(value);
};

const FilterValueInput = ({ index, form }: { index: number; form: ReturnType<typeof Form.useForm<QueryFormValues>>[0] }) => {
  const operator = Form.useWatch(['filters', index, 'operator'], form) as DataModelDataQueryFilterOperator | undefined;
  if (!operator || valueFreeOperators.has(operator)) return <span className="model-data-query-value-placeholder">无需值</span>;
  if (multiValueOperators.has(operator)) {
    return <Form.Item name={[index, 'values']} className="model-data-query-value-item"><Select mode="tags" tokenSeparators={[',']} placeholder="输入多个值后回车" /></Form.Item>;
  }
  if (rangeOperators.has(operator)) {
    return (
      <Space.Compact className="model-data-query-range">
        <Form.Item name={[index, 'value']} className="model-data-query-value-item"><Input placeholder="起始值" /></Form.Item>
        <Form.Item name={[index, 'secondValue']} className="model-data-query-value-item"><Input placeholder="结束值" /></Form.Item>
      </Space.Compact>
    );
  }
  return <Form.Item name={[index, 'value']} className="model-data-query-value-item"><Input placeholder="输入条件值" /></Form.Item>;
};

export const DataModelDataQueryPanel = ({
  fields,
  query,
  rowKey = (row, index) => `${index}-${JSON.stringify(row)}`,
  renderCell = defaultRenderCell,
  rowSelection,
  toolbar,
  onResult,
  columnSizing,
}: DataModelDataQueryPanelProps) => {
  const [form] = Form.useForm<QueryFormValues>();
  const [pageSize, setPageSize] = useState(20);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editorDirty, setEditorDirty] = useState(false);
  const [appliedValues, setAppliedValues] = useState<QueryFormValues>(emptyQueryValues);
  const [queryResult, setQueryResult] = useState<DataModelDataQueryResponse>();
  const [lastRequest, setLastRequest] = useState<DataModelDataQueryRequest>();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>();
  const [autoColumnWidths, setAutoColumnWidths] = useState<DataModelPreviewColumnWidths>({});
  const initializedQueryRef = useRef<DataModelDataQueryPanelProps['query'] | undefined>(undefined);
  const requestSequenceRef = useRef(0);
  const [modalApi, modalContext] = Modal.useModal();
  const options = useMemo(() => fields
    .filter((field) => field.fieldType !== 'BINARY' && field.fieldType !== 'GEOMETRY')
    .map((field) => ({ value: field.code, label: `${field.name} (${field.code})` })), [fields]);
  const rows = useMemo<DataModelQueryRow[]>(() => (queryResult?.rows ?? []).map((row, index) => ({
    ...row,
    __rowKey: rowKey(row, index),
  })), [queryResult, rowKey]);
  const columns = useMemo<NonNullable<TableProps<DataModelQueryRow>['columns']>>(() => (
    (queryResult?.columns ?? []).map((column) => {
      const width = columnSizing
        ? effectiveDataModelPreviewColumnWidth(column.code, autoColumnWidths, columnSizing.manualWidths)
        : 180;
      return {
        title: (
          <DataModelPreviewColumnTitle
            code={column.code}
            name={column.name}
            fieldType={column.fieldType}
            width={columnSizing ? width : undefined}
            onWidthChange={columnSizing
              ? (nextWidth) => columnSizing.onManualWidthChange(column.code, nextWidth)
              : undefined}
            onWidthReset={columnSizing
              ? () => columnSizing.onManualWidthReset(column.code)
              : undefined}
          />
        ),
        dataIndex: column.code,
        key: column.code,
        width,
        ellipsis: true,
        render: (value: unknown, record: DataModelQueryRow) => renderCell(value, column.code, record),
      };
    })
  ), [autoColumnWidths, columnSizing, queryResult, renderCell]);
  const tableWidth = useMemo(() => columnSizing
    ? totalDataModelPreviewColumnsWidth((queryResult?.columns ?? []).map((column) => (
        effectiveDataModelPreviewColumnWidth(column.code, autoColumnWidths, columnSizing.manualWidths)
      )))
    : Math.max(900, columns.length * 180), [autoColumnWidths, columnSizing, columns.length, queryResult?.columns]);
  const filterCount = appliedValues.filters.length;
  const orderCount = appliedValues.orders.length;
  const hasAppliedConditions = Boolean(
    appliedValues.columns?.length
      || filterCount
      || orderCount
      || appliedValues.conditionType === 'OR',
  );
  const appliedSummary = [
    appliedValues.columns?.length ? `返回 ${appliedValues.columns.length} 个字段` : '返回全部字段',
    appliedValues.conditionType === 'OR' ? '任一条件满足' : '全部条件满足',
    `${filterCount} 个筛选条件`,
    `${orderCount} 个排序`,
  ].join(' · ');

  useEffect(() => {
    if (!editorOpen) return;
    form.resetFields();
    form.setFieldsValue(normalizeQueryValues(appliedValues));
  }, [appliedValues, editorOpen, form]);

  const run = useCallback(async (request: DataModelDataQueryRequest) => {
    const requestSequence = ++requestSequenceRef.current;
    setPending(true);
    setError(undefined);
    try {
      const result = await query(request);
      if (requestSequence !== requestSequenceRef.current) return;
      if (columnSizing && result.pageNo === 1) {
        setAutoColumnWidths(calculateDataModelPreviewColumnWidths(result.columns, result.rows));
      }
      setQueryResult(result);
      onResult?.(result);
    } catch (nextError) {
      if (requestSequence === requestSequenceRef.current) setError(nextError);
    } finally {
      if (requestSequence === requestSequenceRef.current) setPending(false);
    }
  }, [columnSizing, onResult, query]);

  const executeQuery = async (
    pageNo: number,
    requestedPageSize = pageSize,
    values: QueryFormValues = appliedValues,
  ) => {
    const request: DataModelDataQueryRequest = {
      pageNo, pageSize: requestedPageSize, conditionType: values.conditionType ?? 'AND',
      columns: values.columns ?? [], filters: values.filters ?? [], orders: values.orders ?? [], returnCount: false,
    };
    setLastRequest(request);
    await run(request);
  };

  useEffect(() => {
    if (options.length === 0 || initializedQueryRef.current === query) return;
    initializedQueryRef.current = query;
    const values = emptyQueryValues();
    const request: DataModelDataQueryRequest = {
      pageNo: 1,
      pageSize: 20,
      conditionType: 'AND',
      columns: [],
      filters: [],
      orders: [],
      returnCount: false,
    };
    setPageSize(20);
    setAppliedValues(values);
    setQueryResult(undefined);
    setLastRequest(request);
    void run(request);
  }, [options.length, query, run]);

  const clearEditor = () => {
    form.resetFields();
    form.setFieldsValue(emptyQueryValues());
    setEditorDirty(true);
  };

  const cancelEditor = () => {
    setEditorDirty(false);
    setEditorOpen(false);
  };

  const requestCloseEditor = () => {
    if (!editorDirty) {
      cancelEditor();
      return;
    }
    modalApi.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: '放弃条件修改？',
      content: '当前查询条件尚未应用，关闭后本次修改会丢失。',
      okText: '放弃修改',
      okButtonProps: { danger: true },
      cancelText: '继续编辑',
      onOk: cancelEditor,
    });
  };

  const applyAndQuery = async () => {
    let values: QueryFormValues;
    try {
      values = normalizeQueryValues(await form.validateFields());
    } catch {
      return;
    }
    setAppliedValues(values);
    setEditorDirty(false);
    setEditorOpen(false);
    await executeQuery(1, pageSize, values);
  };

  const resetAndQuery = async () => {
    const values = emptyQueryValues();
    form.resetFields();
    form.setFieldsValue(values);
    setAppliedValues(values);
    setEditorDirty(false);
    setEditorOpen(false);
    await executeQuery(1, pageSize, values);
  };

  const changeFilterOperator = (index: number, operator: DataModelDataQueryFilterOperator) => {
    form.setFieldValue(['filters', index, 'operator'], operator);
    form.setFieldValue(['filters', index, 'value'], undefined);
    form.setFieldValue(['filters', index, 'secondValue'], undefined);
    form.setFieldValue(['filters', index, 'values'], undefined);
  };

  return (
    <div className="model-data-query-public-panel">
      {modalContext}
      {toolbar}
      <div className="model-data-query-summary-bar">
        <div className="model-data-query-summary-main">
          <span className="model-data-query-summary-title">查询条件</span>
          <Tooltip title={appliedSummary}>
            <span className="model-data-query-summary-text">{appliedSummary}</span>
          </Tooltip>
        </div>
        <Space size={6}>
          {hasAppliedConditions && <Button type="link" size="small" onClick={() => void resetAndQuery()}>重置</Button>}
          <Popover
            trigger="click"
            placement="bottomRight"
            arrow={false}
            rootClassName="business-overlay management-more-filter-overlay model-data-query-editor-overlay"
            open={editorOpen}
            onOpenChange={(open) => {
              if (open) {
                setEditorDirty(false);
                setEditorOpen(true);
              }
              else requestCloseEditor();
            }}
            content={(
              <div className="model-data-query-editor">
                <div className="model-data-query-editor-title">
                  <span>编辑查询条件</span>
                  <small>应用后将从第一页重新查询</small>
                </div>
                <Form<QueryFormValues>
                  autoComplete="off"
                  form={form}
                  size="small"
                  initialValues={emptyQueryValues()}
                  className="model-data-query-form"
                  onValuesChange={() => setEditorDirty(true)}
                >
                  <div className="model-data-query-editor-body">
                    <div className="model-data-query-main-row">
                      <Form.Item label="返回字段" name="columns" className="model-data-query-columns"><Select mode="multiple" allowClear maxTagCount="responsive" options={options} placeholder="留空则返回全部可查询字段" /></Form.Item>
                      <Form.Item label="条件关系" name="conditionType"><Select options={[{ value: 'AND', label: '全部满足' }, { value: 'OR', label: '任一满足' }]} /></Form.Item>
                    </div>
                    <Form.List name="filters">{(filterFields, { add, remove }) => (
                      <div className="model-data-query-section">
                        <div className="model-data-query-section-header"><span>筛选条件 <em>{filterFields.length}</em></span><Button type="link" size="small" icon={<PlusOutlined />} onClick={() => add({ operator: 'EQ' })}>添加条件</Button></div>
                        {filterFields.length === 0 && <div className="model-data-query-section-empty">暂无筛选条件，将返回全部匹配数据。</div>}
                        {filterFields.map((field) => (
                          <div className="model-data-query-row" key={field.key}>
                            <Form.Item name={[field.name, 'field']} rules={[{ required: true, message: '请选择字段' }]}><Select options={options} placeholder="字段" /></Form.Item>
                            <Form.Item name={[field.name, 'operator']} rules={[{ required: true, message: '请选择运算符' }]}><Select options={filterOperatorOptions} onChange={(value) => changeFilterOperator(field.name, value)} /></Form.Item>
                            <FilterValueInput index={field.name} form={form} />
                            <Tooltip title="删除条件"><Button type="text" danger icon={<DeleteOutlined />} aria-label="删除筛选条件" onClick={() => remove(field.name)} /></Tooltip>
                          </div>
                        ))}
                      </div>
                    )}</Form.List>
                    <Form.List name="orders">{(orderFields, { add, remove }) => (
                      <div className="model-data-query-section">
                        <div className="model-data-query-section-header"><span>排序 <em>{orderFields.length}</em></span><Button type="link" size="small" icon={<PlusOutlined />} disabled={orderFields.length >= 3} onClick={() => add({ direction: 'ASC' })}>添加排序</Button></div>
                        {orderFields.length === 0 && <div className="model-data-query-section-empty">暂无排序，翻页稳定性取决于模型主键或物理排序键。</div>}
                        {orderFields.map((field) => (
                          <div className="model-data-query-row model-data-query-order-row" key={field.key}>
                            <Form.Item name={[field.name, 'field']} rules={[{ required: true, message: '请选择字段' }]}><Select options={options} placeholder="字段" /></Form.Item>
                            <Form.Item name={[field.name, 'direction']} rules={[{ required: true, message: '请选择方向' }]}><Select options={[{ value: 'ASC', label: '升序' }, { value: 'DESC', label: '降序' }]} /></Form.Item>
                            <Tooltip title="删除排序"><Button type="text" danger icon={<DeleteOutlined />} aria-label="删除排序字段" onClick={() => remove(field.name)} /></Tooltip>
                          </div>
                        ))}
                      </div>
                    )}</Form.List>
                  </div>
                  <div className="model-data-query-editor-footer">
                    <Button type="link" size="small" onClick={clearEditor}>清空条件</Button>
                    <Space size={6}>
                      <Button size="small" onClick={cancelEditor}>取消</Button>
                      <Button type="primary" size="small" icon={<SearchOutlined />} loading={pending} onClick={() => void applyAndQuery()}>应用并查询</Button>
                    </Space>
                  </div>
                </Form>
              </div>
            )}
          >
            <Button size="small" icon={<EditOutlined />}>编辑条件{filterCount + orderCount > 0 ? ` · ${filterCount + orderCount}` : ''}</Button>
          </Popover>
          <Button size="small" icon={<ReloadOutlined />} disabled={!lastRequest} loading={pending} onClick={() => lastRequest && void run(lastRequest)}>刷新</Button>
        </Space>
      </div>
      {Boolean(error) && <Alert type="warning" showIcon className="model-data-query-alert" title="条件查询失败" description={error instanceof ApiError ? error.message : '请检查查询条件后重试。'} />}
      {queryResult && !queryResult.stableOrder && <Alert type="info" showIcon className="model-data-query-alert" title="当前模型未定义主键或排序键，翻页结果可能不稳定。" />}
      <Table<DataModelQueryRow>
        size="small" className={`management-table${columnSizing ? ' model-preview-resizable-table' : ''}`}
        rowKey="__rowKey" loading={pending} columns={columns} dataSource={rows}
        rowSelection={rowSelection?.(rows)} locale={{
          emptyText: options.length === 0
            ? '当前模型没有可查询字段'
            : pending
              ? '正在查询第一页…'
              : error
                ? '查询失败，请刷新重试'
                : queryResult
                  ? '未查询到匹配数据'
                  : '正在准备查询…',
        }}
        scroll={{ x: tableWidth || undefined, y: '100%' }} pagination={false}
      />
      <div className="model-data-query-pagination">
        <span>{queryResult ? `第 ${queryResult.pageNo} 页` : pending ? '正在查询第 1 页' : options.length === 0 ? '无可查询字段' : '尚未查询'}</span>
        <Select disabled={pending || options.length === 0} value={pageSize} options={pageSizeOptions} onChange={(value) => { setPageSize(value); if (lastRequest) void executeQuery(1, value); }} />
        <Button icon={<LeftOutlined />} aria-label="上一页" disabled={!queryResult || queryResult.pageNo <= 1} onClick={() => queryResult && void executeQuery(queryResult.pageNo - 1)} />
        <Button icon={<RightOutlined />} aria-label="下一页" disabled={!queryResult?.hasNext} onClick={() => queryResult && void executeQuery(queryResult.pageNo + 1)} />
      </div>
    </div>
  );
};
