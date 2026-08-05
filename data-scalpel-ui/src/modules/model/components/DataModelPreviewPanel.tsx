import {
  DeleteOutlined,
  LeftOutlined,
  PlusOutlined,
  ReloadOutlined,
  RightOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Alert, Button, Form, Input, Segmented, Select, Space, Table, Tag, Tooltip } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useDataModelDataQuery, useDataModelPreview } from '../hooks/useDataModels';
import {
  dataModelFieldTypeLabels,
  type DataModel,
  type DataModelDataQueryFilterOperator,
  type DataModelDataQueryRequest,
  type DataModelDataQueryResponse,
  type DataModelField,
  type DataModelPreview,
} from '../model/dataModel';

interface DataModelPreviewPanelProps {
  model: DataModel;
  fields: DataModelField[];
}

type PreviewMode = 'QUICK' | 'CONDITION';
type QueryRow = Record<string, unknown> & { key: string };
type QueryColumn = Pick<DataModelPreview['columns'][number], 'code' | 'name' | 'fieldType'>;

interface QueryFormValues {
  conditionType: 'AND' | 'OR';
  columns?: string[];
  filters: QueryFilterFormValue[];
  orders: QueryOrderFormValue[];
}

interface QueryFilterFormValue {
  field: string;
  operator: DataModelDataQueryFilterOperator;
  value?: string;
  secondValue?: string;
  values?: string[];
}

interface QueryOrderFormValue {
  field: string;
  direction: 'ASC' | 'DESC';
}

const pageSizeOptions = [20, 50, 100].map((value) => ({ value, label: `${value} 条/页` }));

const filterOperatorOptions: Array<{ value: DataModelDataQueryFilterOperator; label: string }> = [
  { value: 'EQ', label: '等于' },
  { value: 'NE', label: '不等于' },
  { value: 'GT', label: '大于' },
  { value: 'GE', label: '大于等于' },
  { value: 'LT', label: '小于' },
  { value: 'LE', label: '小于等于' },
  { value: 'LIKE', label: '包含' },
  { value: 'NOT_LIKE', label: '不包含' },
  { value: 'IN', label: '属于' },
  { value: 'NOT_IN', label: '不属于' },
  { value: 'BETWEEN', label: '介于' },
  { value: 'NOT_BETWEEN', label: '不介于' },
  { value: 'IS_NULL', label: '为空值' },
  { value: 'IS_NOT_NULL', label: '非空值' },
  { value: 'IS_EMPTY', label: '为空字符串' },
  { value: 'IS_NOT_EMPTY', label: '非空字符串' },
];

const valueFreeOperators = new Set<DataModelDataQueryFilterOperator>([
  'IS_NULL', 'IS_NOT_NULL', 'IS_EMPTY', 'IS_NOT_EMPTY',
]);

const multiValueOperators = new Set<DataModelDataQueryFilterOperator>(['IN', 'NOT_IN']);
const rangeOperators = new Set<DataModelDataQueryFilterOperator>(['BETWEEN', 'NOT_BETWEEN']);

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
  title: (
    <span>
      {column.name}
      <Tag className="model-preview-type-tag">{dataModelFieldTypeLabels[column.fieldType]}</Tag>
    </span>
  ),
  dataIndex: column.code,
  key: column.code,
  width: 180,
  ellipsis: true,
  render: renderCell,
}));

const fieldOptions = (fields: DataModelField[]) => fields
  .filter((field) => field.fieldType !== 'BINARY' && field.fieldType !== 'GEOMETRY')
  .map((field) => ({
    value: field.code,
    label: `${field.name} (${field.code})`,
  }));

const FilterValueInput = ({ index, form }: { index: number; form: ReturnType<typeof Form.useForm<QueryFormValues>>[0] }) => {
  const operator = Form.useWatch(['filters', index, 'operator'], form) as DataModelDataQueryFilterOperator | undefined;
  if (!operator || valueFreeOperators.has(operator)) {
    return <span className="model-data-query-value-placeholder">无需值</span>;
  }
  if (multiValueOperators.has(operator)) {
    return (
      <Form.Item name={[index, 'values']} className="model-data-query-value-item">
        <Select mode="tags" tokenSeparators={[',']} placeholder="输入多个值后回车" />
      </Form.Item>
    );
  }
  if (rangeOperators.has(operator)) {
    return (
      <Space.Compact className="model-data-query-range">
        <Form.Item name={[index, 'value']} className="model-data-query-value-item">
          <Input placeholder="起始值" />
        </Form.Item>
        <Form.Item name={[index, 'secondValue']} className="model-data-query-value-item">
          <Input placeholder="结束值" />
        </Form.Item>
      </Space.Compact>
    );
  }
  return (
    <Form.Item name={[index, 'value']} className="model-data-query-value-item">
      <Input placeholder="输入条件值" />
    </Form.Item>
  );
};

export const DataModelPreviewPanel = ({ model, fields }: DataModelPreviewPanelProps) => {
  const [mode, setMode] = useState<PreviewMode>('QUICK');
  const [form] = Form.useForm<QueryFormValues>();
  const [pageSize, setPageSize] = useState(50);
  const [queryResult, setQueryResult] = useState<DataModelDataQueryResponse>();
  const [lastRequest, setLastRequest] = useState<DataModelDataQueryRequest>();
  const quickPreviewQuery = useDataModelPreview(model.id, mode === 'QUICK');
  const dataQueryMutation = useDataModelDataQuery();
  const queryableFields = useMemo(
    () => fields.filter((field) => field.fieldType !== 'BINARY' && field.fieldType !== 'GEOMETRY'),
    [fields],
  );
  const options = useMemo(() => fieldOptions(fields), [fields]);

  const quickRows = useMemo(() => toRows(quickPreviewQuery.data?.rows ?? []), [quickPreviewQuery.data]);
  const quickColumns = useMemo(() => toColumns(quickPreviewQuery.data?.columns ?? []), [quickPreviewQuery.data]);
  const conditionRows = useMemo(() => toRows(queryResult?.rows ?? []), [queryResult]);
  const conditionColumns = useMemo(() => toColumns(queryResult?.columns ?? []), [queryResult]);

  const executeQuery = async (pageNo: number, requestedPageSize = pageSize) => {
    const values = await form.validateFields();
    const request: DataModelDataQueryRequest = {
      pageNo,
      pageSize: requestedPageSize,
      conditionType: values.conditionType ?? 'AND',
      columns: values.columns ?? [],
      filters: values.filters ?? [],
      orders: values.orders ?? [],
      returnCount: false,
    };
    setLastRequest(request);
    const response = await dataQueryMutation.mutateAsync({ id: model.id, request });
    setQueryResult(response);
  };

  const reloadConditionalQuery = async () => {
    if (!lastRequest) return;
    const response = await dataQueryMutation.mutateAsync({ id: model.id, request: lastRequest });
    setQueryResult(response);
  };

  const resetQuery = () => {
    form.resetFields();
    form.setFieldsValue({ conditionType: 'AND', filters: [], orders: [] });
    setLastRequest(undefined);
    setQueryResult(undefined);
  };

  const changeFilterOperator = (index: number, operator: DataModelDataQueryFilterOperator) => {
    form.setFieldValue(['filters', index, 'operator'], operator);
    form.setFieldValue(['filters', index, 'value'], undefined);
    form.setFieldValue(['filters', index, 'secondValue'], undefined);
    form.setFieldValue(['filters', index, 'values'], undefined);
  };

  const renderQuickPreview = () => (
    <>
      <div className="model-tab-toolbar">
        <div className="model-preview-caption">读取最多 50 条数据，不执行总数统计；结果仅用于快速查看物理表。</div>
        <Button
          size="small"
          icon={<ReloadOutlined />}
          loading={quickPreviewQuery.isFetching}
          onClick={() => void quickPreviewQuery.refetch()}
        >
          刷新
        </Button>
      </div>
      {quickPreviewQuery.error && (
        <Alert
          type="warning"
          showIcon
          title="暂不能预览物理表数据"
          description={quickPreviewQuery.error instanceof ApiError ? quickPreviewQuery.error.message : '请先检查并准备物理表。'}
          action={<Button size="small" onClick={() => void quickPreviewQuery.refetch()}>重试</Button>}
        />
      )}
      {!quickPreviewQuery.error && (
        <Table<QueryRow>
          size="small"
          className="management-table"
          rowKey="key"
          loading={quickPreviewQuery.isFetching}
          columns={quickColumns}
          dataSource={quickRows}
          locale={{ emptyText: quickPreviewQuery.isPending ? '正在读取物理表数据…' : '物理表暂无数据' }}
          scroll={{ x: Math.max(900, quickColumns.length * 180), y: '100%' }}
          pagination={false}
          footer={quickPreviewQuery.data?.truncated ? () => '还存在更多数据，请使用条件查询浏览。' : undefined}
        />
      )}
    </>
  );

  const renderConditionalQuery = () => (
    <>
      <Form<QueryFormValues> autoComplete="off"
        form={form}
        size="small"
        initialValues={{ conditionType: 'AND', filters: [], orders: [] }}
        className="model-data-query-form"
      >
        <div className="model-data-query-main-row">
          <Form.Item label="返回字段" name="columns" className="model-data-query-columns">
            <Select mode="multiple" allowClear maxTagCount="responsive" options={options} placeholder="留空则返回全部可查询字段" />
          </Form.Item>
          <Form.Item label="条件关系" name="conditionType">
            <Select options={[{ value: 'AND', label: '全部满足' }, { value: 'OR', label: '任一满足' }]} />
          </Form.Item>
          <Space size={4} className="model-data-query-actions">
            <Button type="primary" icon={<SearchOutlined />} loading={dataQueryMutation.isPending} onClick={() => void executeQuery(1)}>查询</Button>
            <Button onClick={resetQuery}>重置</Button>
            <Button
              icon={<ReloadOutlined />}
              disabled={!lastRequest}
              loading={dataQueryMutation.isPending}
              onClick={() => void reloadConditionalQuery()}
            >
              刷新
            </Button>
          </Space>
        </div>
        <Form.List name="filters">
          {(filterFields, { add, remove }) => (
            <div className="model-data-query-section">
              <div className="model-data-query-section-header">
                <span>筛选条件</span>
                <Button type="link" size="small" icon={<PlusOutlined />} onClick={() => add({ operator: 'EQ' })}>添加条件</Button>
              </div>
              {filterFields.map((field) => (
                <div className="model-data-query-row" key={field.key}>
                  <Form.Item name={[field.name, 'field']} rules={[{ required: true, message: '请选择字段' }]}>
                    <Select options={options} placeholder="字段" />
                  </Form.Item>
                  <Form.Item name={[field.name, 'operator']} rules={[{ required: true, message: '请选择运算符' }]}>
                    <Select options={filterOperatorOptions} onChange={(value) => changeFilterOperator(field.name, value)} />
                  </Form.Item>
                  <FilterValueInput index={field.name} form={form} />
                  <Tooltip title="删除条件"><Button type="text" danger icon={<DeleteOutlined />} aria-label="删除筛选条件" onClick={() => remove(field.name)} /></Tooltip>
                </div>
              ))}
            </div>
          )}
        </Form.List>
        <Form.List name="orders">
          {(orderFields, { add, remove }) => (
            <div className="model-data-query-section">
              <div className="model-data-query-section-header">
                <span>排序</span>
                <Button type="link" size="small" icon={<PlusOutlined />} disabled={orderFields.length >= 3} onClick={() => add({ direction: 'ASC' })}>添加排序</Button>
              </div>
              {orderFields.map((field) => (
                <div className="model-data-query-row model-data-query-order-row" key={field.key}>
                  <Form.Item name={[field.name, 'field']} rules={[{ required: true, message: '请选择字段' }]}>
                    <Select options={options} placeholder="字段" />
                  </Form.Item>
                  <Form.Item name={[field.name, 'direction']} rules={[{ required: true, message: '请选择方向' }]}>
                    <Select options={[{ value: 'ASC', label: '升序' }, { value: 'DESC', label: '降序' }]} />
                  </Form.Item>
                  <Tooltip title="删除排序"><Button type="text" danger icon={<DeleteOutlined />} aria-label="删除排序字段" onClick={() => remove(field.name)} /></Tooltip>
                </div>
              ))}
            </div>
          )}
        </Form.List>
      </Form>
      {dataQueryMutation.error && (
        <Alert
          type="warning"
          showIcon
          className="model-data-query-alert"
          title="条件查询失败"
          description={dataQueryMutation.error instanceof ApiError ? dataQueryMutation.error.message : '请检查查询条件后重试。'}
        />
      )}
      {queryResult && !queryResult.stableOrder && (
        <Alert
          type="info"
          showIcon
          className="model-data-query-alert"
          title="当前模型未定义主键或排序键，翻页结果可能不稳定。"
        />
      )}
      <Table<QueryRow>
        size="small"
        className="management-table"
        rowKey="key"
        loading={dataQueryMutation.isPending}
        columns={conditionColumns}
        dataSource={conditionRows}
        locale={{ emptyText: queryResult ? '未查询到匹配数据' : '设置筛选条件后点击“查询”' }}
        scroll={{ x: Math.max(900, conditionColumns.length * 180), y: '100%' }}
        pagination={false}
      />
      <div className="model-data-query-pagination">
        <span>{queryResult ? `第 ${queryResult.pageNo} 页` : '尚未查询'}</span>
        <Select
          value={pageSize}
          options={pageSizeOptions}
          onChange={(value) => {
            setPageSize(value);
            if (lastRequest) void executeQuery(1, value);
          }}
        />
        <Button
          icon={<LeftOutlined />}
          aria-label="上一页"
          disabled={!queryResult || queryResult.pageNo <= 1}
          onClick={() => queryResult && void executeQuery(queryResult.pageNo - 1)}
        />
        <Button
          icon={<RightOutlined />}
          aria-label="下一页"
          disabled={!queryResult?.hasNext}
          onClick={() => queryResult && void executeQuery(queryResult.pageNo + 1)}
        />
      </div>
    </>
  );

  return (
    <div className="model-detail-tab-panel model-data-preview-panel">
      <div className="model-data-preview-mode-bar">
        <Segmented<PreviewMode>
          size="small"
          value={mode}
          options={[{ label: '快速预览', value: 'QUICK' }, { label: '条件查询', value: 'CONDITION' }]}
          onChange={setMode}
        />
        {queryableFields.length < fields.length && (
          <span className="model-preview-caption">二进制和空间字段不会返回，也不能参与筛选、排序或聚合。</span>
        )}
      </div>
      {mode === 'QUICK' ? renderQuickPreview() : renderConditionalQuery()}
    </div>
  );
};
