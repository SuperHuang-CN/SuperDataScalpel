import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Alert, Button, Col, Collapse, Drawer, Form, Input, InputNumber, Row, Select, Space, Switch, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
import type { PlatformDataType } from '../../model';
import { useCreateApiResource, useUpdateApiResource } from '../hooks/useDataSources';
import type {
  ApiResource,
  ApiResourceWriteRequest,
  CreateApiResourceRequest,
  HttpApiNamedValue,
  HttpApiRequestTemplate,
  HttpApiSignatureType,
  HttpApiValueLocation,
} from '../model/dataSource';

interface ApiResourceDrawerProps {
  dataSourceId: string;
  resource: ApiResource | null;
  open: boolean;
  onClose: () => void;
}

interface RequestTemplateFormValue {
  method?: 'GET' | 'POST';
  path?: string;
  queryParameters?: HttpApiNamedValue[];
  headers?: HttpApiNamedValue[];
  bodyTemplate?: string | null;
}

interface PaginationFormValue {
  type?: 'NONE' | 'PAGE_NUMBER' | 'OFFSET_LIMIT' | 'CURSOR' | 'NEXT_URL';
  location?: HttpApiValueLocation;
  pageParameter?: string;
  pageSizeParameter?: string;
  initialPage?: number;
  pageSize?: number;
  hasMorePointer?: string | null;
  totalPagesPointer?: string | null;
  offsetParameter?: string;
  limitParameter?: string;
  initialOffset?: number;
  limit?: number;
  totalPointer?: string | null;
  cursorParameter?: string;
  initialCursor?: string | null;
  nextCursorPointer?: string;
  nextUrlPointer?: string;
  sameOriginOnly?: boolean;
}

interface OutputFieldFormValue {
  name?: string;
  jsonPointer?: string;
  fieldType?: PlatformDataType;
  length?: number;
  precision?: number;
  scale?: number;
  nullable?: boolean;
  comment?: string | null;
}

interface ApiResourceFormValues {
  code?: string;
  name?: string;
  enabled?: boolean;
  request?: RequestTemplateFormValue;
  invocationType?: 'SINGLE_REQUEST' | 'PAGINATED_REQUEST' | 'ASYNC_JOB';
  pagination?: PaginationFormValue;
  asyncJob?: {
    statusRequest?: RequestTemplateFormValue;
    jobIdPointer?: string;
    statusPointer?: string;
    runningStatuses?: string[];
    successStatuses?: string[];
    failureStatuses?: string[];
    pollingIntervalMs?: number;
    pollingTimeoutMs?: number;
    resultRequest?: RequestTemplateFormValue;
  };
  signing?: {
    type?: HttpApiSignatureType;
    canonicalTemplate?: string;
    timestampName?: string;
    timestampLocation?: HttpApiValueLocation;
    timestampUnit?: 'SECONDS' | 'MILLISECONDS';
    nonceName?: string;
    nonceLocation?: HttpApiValueLocation;
    outputName?: string;
    outputLocation?: HttpApiValueLocation;
    outputEncoding?: 'HEX_LOWERCASE' | 'HEX_UPPERCASE' | 'BASE64' | 'BASE64_URL';
    outputValueTemplate?: string;
  };
  recordsPointer?: string;
  outputFields?: OutputFieldFormValue[];
  limits?: {
    maxPages?: number;
    maxRows?: number;
    maxResponseBytes?: number;
    maxDurationSeconds?: number;
  };
}

const locationOptions = [
  { value: 'HEADER', label: 'Header' },
  { value: 'QUERY', label: 'Query' },
  { value: 'BODY', label: 'Body' },
];

const platformTypes: PlatformDataType[] = [
  'BOOLEAN', 'BYTE', 'SHORT', 'INTEGER', 'LONG', 'FLOAT', 'DOUBLE', 'DECIMAL',
  'STRING', 'BINARY', 'DATE', 'TIMESTAMP', 'TIMESTAMP_NTZ',
];

const text = (value: string | null | undefined) => value?.trim() ?? '';

const requestTemplate = (value: RequestTemplateFormValue | undefined): HttpApiRequestTemplate => ({
  method: value?.method ?? 'GET',
  path: text(value?.path),
  queryParameters: value?.queryParameters ?? [],
  headers: value?.headers ?? [],
  bodyTemplate: value?.method === 'POST' ? value.bodyTemplate || null : null,
});

const NamedValueList = ({ prefix, label }: { prefix: (string | number)[]; label: string }) => (
  <Form.List name={prefix}>
    {(fields, { add, remove }) => (
      <Space orientation="vertical" size={6} className="http-api-named-values">
        {fields.map((field) => (
          <Space key={field.key} align="baseline" className="http-api-named-value-row">
            <Form.Item name={[field.name, 'name']} rules={[{ required: true, whitespace: true }]}>
              <Input placeholder="名称" />
            </Form.Item>
            <Form.Item name={[field.name, 'value']} rules={[{ required: true }]}>
              <Input placeholder="值或 ${runtime.xxx} 模板" />
            </Form.Item>
            <Button type="text" danger icon={<DeleteOutlined />} aria-label={`删除${label}`} onClick={() => remove(field.name)} />
          </Space>
        ))}
        <Button type="dashed" icon={<PlusOutlined />} onClick={() => add({ name: '', value: '' })}>添加{label}</Button>
      </Space>
    )}
  </Form.List>
);

const RequestTemplateFields = ({ prefix, method }: {
  prefix: (string | number)[];
  method: 'GET' | 'POST';
}) => <Row gutter={12}>
  <Col span={6}>
    <Form.Item label="方法" name={[...prefix, 'method']} rules={[{ required: true }]}>
      <Select options={[{ value: 'GET', label: 'GET' }, { value: 'POST', label: 'POST' }]} />
    </Form.Item>
  </Col>
  <Col span={18}>
    <Form.Item label="相对路径" name={[...prefix, 'path']} rules={[{ required: true, whitespace: true }, { pattern: /^\//, message: '必须以 / 开头' }]}>
      <Input placeholder="/v1/orders" />
    </Form.Item>
  </Col>
  <Col span={12}>
    <Form.Item label="Query 参数">
      <NamedValueList prefix={[...prefix, 'queryParameters']} label="Query 参数" />
    </Form.Item>
  </Col>
  <Col span={12}>
    <Form.Item label="请求 Header">
      <NamedValueList prefix={[...prefix, 'headers']} label="Header" />
    </Form.Item>
  </Col>
  {method === 'POST' && <Col span={24}>
    <Form.Item label="Body 模板" name={[...prefix, 'bodyTemplate']}>
      <Input.TextArea autoSize={{ minRows: 3, maxRows: 10 }} placeholder={'{"startTime":"${runtime.startTime}"}'} />
    </Form.Item>
  </Col>}
</Row>;

const PaginationFields = ({ type }: { type: PaginationFormValue['type'] }) => {
  if (!type || type === 'NONE') return <Alert showIcon type="info" title="不对结果请求进行分页" />;
  if (type === 'NEXT_URL') return <Row gutter={12}>
    <Col span={16}><Form.Item label="下一页 URL Pointer" name={['pagination', 'nextUrlPointer']} rules={[{ required: true }]}><Input placeholder="/links/next" /></Form.Item></Col>
    <Col span={8}><Form.Item label="仅允许同源 URL" name={['pagination', 'sameOriginOnly']} valuePropName="checked"><Switch /></Form.Item></Col>
  </Row>;
  if (type === 'CURSOR') return <Row gutter={12}>
    <Col span={6}><Form.Item label="参数位置" name={['pagination', 'location']} rules={[{ required: true }]}><Select options={locationOptions.filter((item) => item.value !== 'HEADER')} /></Form.Item></Col>
    <Col span={6}><Form.Item label="Cursor 参数名" name={['pagination', 'cursorParameter']} rules={[{ required: true }]}><Input placeholder="cursor" /></Form.Item></Col>
    <Col span={6}><Form.Item label="初始 Cursor" name={['pagination', 'initialCursor']}><Input /></Form.Item></Col>
    <Col span={6}><Form.Item label="下一 Cursor Pointer" name={['pagination', 'nextCursorPointer']} rules={[{ required: true }]}><Input placeholder="/nextCursor" /></Form.Item></Col>
    <Col span={12}><Form.Item label="Has More Pointer" name={['pagination', 'hasMorePointer']}><Input placeholder="可选，如 /hasMore" /></Form.Item></Col>
  </Row>;
  const pageNumber = type === 'PAGE_NUMBER';
  return <Row gutter={12}>
    <Col span={6}><Form.Item label="参数位置" name={['pagination', 'location']} rules={[{ required: true }]}><Select options={locationOptions.filter((item) => item.value !== 'HEADER')} /></Form.Item></Col>
    <Col span={6}><Form.Item label={pageNumber ? '页码参数名' : 'Offset 参数名'} name={['pagination', pageNumber ? 'pageParameter' : 'offsetParameter']} rules={[{ required: true }]}><Input placeholder={pageNumber ? 'page' : 'offset'} /></Form.Item></Col>
    <Col span={6}><Form.Item label={pageNumber ? '每页参数名' : 'Limit 参数名'} name={['pagination', pageNumber ? 'pageSizeParameter' : 'limitParameter']} rules={[{ required: true }]}><Input placeholder={pageNumber ? 'pageSize' : 'limit'} /></Form.Item></Col>
    <Col span={6}><Form.Item label={pageNumber ? '初始页码' : '初始 Offset'} name={['pagination', pageNumber ? 'initialPage' : 'initialOffset']} rules={[{ required: true }]}><InputNumber min={0} precision={0} className="data-source-number-input" /></Form.Item></Col>
    <Col span={6}><Form.Item label={pageNumber ? '每页数量' : 'Limit'} name={['pagination', pageNumber ? 'pageSize' : 'limit']} rules={[{ required: true }]}><InputNumber min={1} max={100000} precision={0} className="data-source-number-input" /></Form.Item></Col>
    <Col span={9}><Form.Item label="Has More Pointer" name={['pagination', 'hasMorePointer']}><Input placeholder="可选" /></Form.Item></Col>
    <Col span={9}><Form.Item label={pageNumber ? '总页数 Pointer' : '总条数 Pointer'} name={['pagination', pageNumber ? 'totalPagesPointer' : 'totalPointer']}><Input placeholder="可选" /></Form.Item></Col>
  </Row>;
};

const initialValues = (resource: ApiResource | null): ApiResourceFormValues => {
  if (resource) {
    return {
      ...resource,
      request: {
        ...resource.request,
        bodyTemplate: resource.request.bodyTemplate ?? undefined,
      },
      asyncJob: resource.asyncJob ? {
        ...resource.asyncJob,
        statusRequest: {
          ...resource.asyncJob.statusRequest,
          bodyTemplate: resource.asyncJob.statusRequest.bodyTemplate ?? undefined,
        },
        resultRequest: {
          ...resource.asyncJob.resultRequest,
          bodyTemplate: resource.asyncJob.resultRequest.bodyTemplate ?? undefined,
        },
      } : undefined,
      signing: {
        type: resource.signing?.type ?? 'NONE',
        canonicalTemplate: resource.signing?.canonicalTemplate ?? undefined,
        timestampName: resource.signing?.timestamp?.name,
        timestampLocation: resource.signing?.timestamp?.location,
        timestampUnit: resource.signing?.timestamp?.unit,
        nonceName: resource.signing?.nonce?.name,
        nonceLocation: resource.signing?.nonce?.location,
        outputName: resource.signing?.output?.name,
        outputLocation: resource.signing?.output?.location,
        outputEncoding: resource.signing?.output?.encoding,
        outputValueTemplate: resource.signing?.output?.valueTemplate ?? undefined,
      },
      outputFields: resource.outputFields.map((field) => ({
        name: field.name, jsonPointer: field.jsonPointer, fieldType: field.type.type,
        length: field.type.length ?? undefined, precision: field.type.precision ?? undefined,
        scale: field.type.scale ?? undefined, nullable: field.nullable, comment: field.comment ?? undefined,
      })),
    };
  }
  return {
    enabled: true,
    request: { method: 'GET', path: '/', queryParameters: [], headers: [] },
    invocationType: 'SINGLE_REQUEST',
    pagination: { type: 'NONE', location: 'QUERY', initialPage: 1, pageSize: 100, initialOffset: 0, limit: 100, sameOriginOnly: true },
    signing: { type: 'NONE', timestampUnit: 'SECONDS', outputEncoding: 'HEX_LOWERCASE', outputLocation: 'HEADER' },
    recordsPointer: '/data',
    outputFields: [{ name: 'id', jsonPointer: '/id', fieldType: 'STRING', nullable: false }],
    limits: { maxPages: 1000, maxRows: 1000000, maxResponseBytes: 536870912, maxDurationSeconds: 3600 },
    asyncJob: {
      statusRequest: { method: 'GET', path: '/jobs/${jobId}', queryParameters: [], headers: [] },
      resultRequest: { method: 'GET', path: '/jobs/${jobId}/result', queryParameters: [], headers: [] },
      jobIdPointer: '/jobId', statusPointer: '/status', runningStatuses: ['PENDING', 'RUNNING'],
      successStatuses: ['SUCCESS'], failureStatuses: ['FAILED'], pollingIntervalMs: 1000, pollingTimeoutMs: 300000,
    },
  };
};

const buildRequest = (values: ApiResourceFormValues): ApiResourceWriteRequest => {
  const pagination = values.pagination ?? { type: 'NONE' };
  const paginationRequest: ApiResourceWriteRequest['pagination'] = (() => {
    switch (pagination.type) {
      case 'PAGE_NUMBER': return {
        type: 'PAGE_NUMBER', location: pagination.location ?? 'QUERY', pageParameter: text(pagination.pageParameter),
        pageSizeParameter: text(pagination.pageSizeParameter), initialPage: pagination.initialPage ?? 1,
        pageSize: pagination.pageSize ?? 100, hasMorePointer: text(pagination.hasMorePointer) || null,
        totalPagesPointer: text(pagination.totalPagesPointer) || null,
      };
      case 'OFFSET_LIMIT': return {
        type: 'OFFSET_LIMIT', location: pagination.location ?? 'QUERY', offsetParameter: text(pagination.offsetParameter),
        limitParameter: text(pagination.limitParameter), initialOffset: pagination.initialOffset ?? 0,
        limit: pagination.limit ?? 100, hasMorePointer: text(pagination.hasMorePointer) || null,
        totalPointer: text(pagination.totalPointer) || null,
      };
      case 'CURSOR': return {
        type: 'CURSOR', location: pagination.location ?? 'QUERY', cursorParameter: text(pagination.cursorParameter),
        initialCursor: text(pagination.initialCursor) || null, nextCursorPointer: text(pagination.nextCursorPointer),
        hasMorePointer: text(pagination.hasMorePointer) || null,
      };
      case 'NEXT_URL': return { type: 'NEXT_URL', nextUrlPointer: text(pagination.nextUrlPointer), sameOriginOnly: pagination.sameOriginOnly ?? true };
      case 'NONE':
      case undefined: return { type: 'NONE' };
    }
  })();
  const signing = values.signing ?? { type: 'NONE' };
  const async = values.asyncJob;
  return {
    name: text(values.name), connectorType: 'GENERIC_HTTP', enabled: values.enabled ?? true,
    request: requestTemplate(values.request), invocationType: values.invocationType ?? 'SINGLE_REQUEST',
    pagination: values.invocationType === 'SINGLE_REQUEST' ? { type: 'NONE' } : paginationRequest,
    asyncJob: values.invocationType === 'ASYNC_JOB' && async ? {
      statusRequest: requestTemplate(async.statusRequest), jobIdPointer: text(async.jobIdPointer),
      statusPointer: text(async.statusPointer), runningStatuses: async.runningStatuses ?? [],
      successStatuses: async.successStatuses ?? [], failureStatuses: async.failureStatuses ?? [],
      pollingIntervalMs: async.pollingIntervalMs ?? 1000, pollingTimeoutMs: async.pollingTimeoutMs ?? 300000,
      resultRequest: requestTemplate(async.resultRequest),
    } : null,
    signing: {
      type: signing.type ?? 'NONE', canonicalTemplate: text(signing.canonicalTemplate) || null,
      timestamp: text(signing.timestampName) ? {
        name: text(signing.timestampName), location: signing.timestampLocation ?? 'HEADER', unit: signing.timestampUnit ?? 'SECONDS',
      } : null,
      nonce: text(signing.nonceName) ? { name: text(signing.nonceName), location: signing.nonceLocation ?? 'HEADER' } : null,
      output: signing.type && signing.type !== 'NONE' ? {
        name: text(signing.outputName), location: signing.outputLocation ?? 'HEADER',
        encoding: signing.outputEncoding ?? 'HEX_LOWERCASE', valueTemplate: text(signing.outputValueTemplate) || null,
      } : null,
    },
    recordsPointer: text(values.recordsPointer),
    outputFields: (values.outputFields ?? []).map((field) => ({
      name: text(field.name), jsonPointer: text(field.jsonPointer), nullable: field.nullable ?? true,
      comment: text(field.comment) || null,
      type: {
        type: field.fieldType ?? 'STRING', length: field.fieldType === 'STRING' ? field.length ?? null : null,
        precision: field.fieldType === 'DECIMAL' ? field.precision ?? null : null,
        scale: field.fieldType === 'DECIMAL' ? field.scale ?? null : null,
      },
    })),
    limits: {
      maxPages: values.limits?.maxPages ?? 1000, maxRows: values.limits?.maxRows ?? 1000000,
      maxResponseBytes: values.limits?.maxResponseBytes ?? 536870912,
      maxDurationSeconds: values.limits?.maxDurationSeconds ?? 3600,
    },
  };
};

export const ApiResourceDrawer = ({ dataSourceId, resource, open, onClose }: ApiResourceDrawerProps) => {
  const [form] = Form.useForm<ApiResourceFormValues>();
  const [messageApi, contextHolder] = message.useMessage();
  const createMutation = useCreateApiResource(dataSourceId);
  const updateMutation = useUpdateApiResource(dataSourceId);
  const invocationType = Form.useWatch('invocationType', form) ?? 'SINGLE_REQUEST';
  const paginationType = Form.useWatch(['pagination', 'type'], form) ?? 'NONE';
  const signingType = Form.useWatch(['signing', 'type'], form) ?? 'NONE';
  const mainMethod = Form.useWatch(['request', 'method'], form) ?? 'GET';
  const statusMethod = Form.useWatch(['asyncJob', 'statusRequest', 'method'], form) ?? 'GET';
  const resultMethod = Form.useWatch(['asyncJob', 'resultRequest', 'method'], form) ?? 'GET';

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue(initialValues(resource));
  }, [form, open, resource]);

  const submit = async (values: ApiResourceFormValues) => {
    try {
      const request = buildRequest(values);
      if (resource) {
        await updateMutation.mutateAsync({ resourceId: resource.id, request });
        messageApi.success('API 资源已保存');
      } else {
        await createMutation.mutateAsync({ ...request, code: text(values.code) } satisfies CreateApiResourceRequest);
        messageApi.success('API 资源已创建');
      }
      onClose();
    } catch (error) {
      messageApi.error(error instanceof ApiError || error instanceof Error ? error.message : '保存 API 资源失败');
    }
  };

  return <>
    {contextHolder}
    <Drawer
      open={open}
      title={resource ? `修改 API 资源 · ${resource.name}` : '新建 API 资源'}
      width={980}
      destroyOnHidden
      onClose={onClose}
      footer={<Space><Button onClick={onClose}>取消</Button><Button type="primary" loading={createMutation.isPending || updateMutation.isPending} onClick={() => form.submit()}>保存</Button></Space>}
    >
      <Form<ApiResourceFormValues> autoComplete="off" form={form} layout="vertical" onFinish={(values) => void submit(values)}>
        <Row gutter={12}>
          <Col span={10}><Form.Item label="名称" name="name" rules={[{ required: true, whitespace: true }]}><Input /></Form.Item></Col>
          <Col span={8}><Form.Item label="编码" name="code" rules={resource ? [] : [{ required: true }, { pattern: /^[A-Za-z][A-Za-z0-9_]{0,63}$/ }]}><Input disabled={Boolean(resource)} /></Form.Item></Col>
          <Col span={6}><Form.Item label="启用" name="enabled" valuePropName="checked"><Switch /></Form.Item></Col>
          <Col span={12}><Form.Item label="调用模式" name="invocationType" rules={[{ required: true }]}><Select options={[
            { value: 'SINGLE_REQUEST', label: '单次请求' }, { value: 'PAGINATED_REQUEST', label: '同步分页请求' },
            { value: 'ASYNC_JOB', label: '异步任务（提交/轮询/结果）' },
          ]} /></Form.Item></Col>
          <Col span={12}><Form.Item label="记录数组 JSON Pointer" name="recordsPointer"><Input placeholder="/data/items；根数组使用空字符串" /></Form.Item></Col>
        </Row>
        <Collapse defaultActiveKey={['request', 'schema']} items={[
          { key: 'request', label: invocationType === 'ASYNC_JOB' ? '1. 提交请求' : '1. 数据请求', children: <RequestTemplateFields prefix={['request']} method={mainMethod} /> },
          ...(invocationType === 'ASYNC_JOB' ? [{
            key: 'async', label: '2. 异步任务轮询与结果请求', children: <>
              <Row gutter={12}>
                <Col span={8}><Form.Item label="Job ID Pointer" name={['asyncJob', 'jobIdPointer']} rules={[{ required: true }]}><Input /></Form.Item></Col>
                <Col span={8}><Form.Item label="状态 Pointer" name={['asyncJob', 'statusPointer']} rules={[{ required: true }]}><Input /></Form.Item></Col>
                <Col span={4}><Form.Item label="轮询间隔 ms" name={['asyncJob', 'pollingIntervalMs']} rules={[{ required: true }]}><InputNumber min={100} max={60000} precision={0} /></Form.Item></Col>
                <Col span={4}><Form.Item label="轮询超时 ms" name={['asyncJob', 'pollingTimeoutMs']} rules={[{ required: true }]}><InputNumber min={100} max={86400000} precision={0} /></Form.Item></Col>
                <Col span={8}><Form.Item label="运行中状态" name={['asyncJob', 'runningStatuses']}><Select mode="tags" tokenSeparators={[',']} /></Form.Item></Col>
                <Col span={8}><Form.Item label="成功状态" name={['asyncJob', 'successStatuses']} rules={[{ required: true }]}><Select mode="tags" tokenSeparators={[',']} /></Form.Item></Col>
                <Col span={8}><Form.Item label="失败状态" name={['asyncJob', 'failureStatuses']} rules={[{ required: true }]}><Select mode="tags" tokenSeparators={[',']} /></Form.Item></Col>
              </Row>
              <div className="data-source-form-section-title">状态请求（可使用 ${'{jobId}'}）</div>
              <RequestTemplateFields prefix={['asyncJob', 'statusRequest']} method={statusMethod} />
              <div className="data-source-form-section-title">结果请求（可使用 ${'{jobId}'}）</div>
              <RequestTemplateFields prefix={['asyncJob', 'resultRequest']} method={resultMethod} />
            </>,
          }] : []),
          { key: 'pagination', label: `${invocationType === 'ASYNC_JOB' ? '3' : '2'}. 分页策略`, children: <>
            <Form.Item label="分页类型" name={['pagination', 'type']} rules={[{ required: true }]}>
              <Select disabled={invocationType === 'SINGLE_REQUEST'} options={[
                { value: 'NONE', label: '不分页' }, { value: 'PAGE_NUMBER', label: 'Page Number' },
                { value: 'OFFSET_LIMIT', label: 'Offset / Limit' }, { value: 'CURSOR', label: 'Cursor' },
                { value: 'NEXT_URL', label: '响应返回 Next URL' },
              ]} />
            </Form.Item>
            <PaginationFields type={invocationType === 'SINGLE_REQUEST' ? 'NONE' : paginationType} />
          </> },
          { key: 'signing', label: `${invocationType === 'ASYNC_JOB' ? '4' : '3'}. 请求签名`, children: <>
            <Row gutter={12}>
              <Col span={8}><Form.Item label="算法" name={['signing', 'type']}><Select options={['NONE', 'MD5', 'HMAC_SHA256', 'HMAC_SHA512', 'RSA_SHA256'].map((value) => ({ value, label: value }))} /></Form.Item></Col>
              {signingType !== 'NONE' && <>
                <Col span={16}><Form.Item label="签名原文模板" name={['signing', 'canonicalTemplate']} rules={[{ required: true }]}><Input placeholder={'${request.method}\n${request.path}\n${request.query}\n${timestamp}'} /></Form.Item></Col>
                <Col span={6}><Form.Item label="时间戳名称" name={['signing', 'timestampName']}><Input placeholder="timestamp" /></Form.Item></Col>
                <Col span={6}><Form.Item label="时间戳位置" name={['signing', 'timestampLocation']}><Select options={locationOptions} /></Form.Item></Col>
                <Col span={6}><Form.Item label="时间戳单位" name={['signing', 'timestampUnit']}><Select options={[{ value: 'SECONDS', label: '秒' }, { value: 'MILLISECONDS', label: '毫秒' }]} /></Form.Item></Col>
                <Col span={6}><Form.Item label="Nonce 名称" name={['signing', 'nonceName']}><Input placeholder="nonce（可选）" /></Form.Item></Col>
                <Col span={6}><Form.Item label="Nonce 位置" name={['signing', 'nonceLocation']}><Select options={locationOptions} /></Form.Item></Col>
                <Col span={6}><Form.Item label="签名参数名称" name={['signing', 'outputName']} rules={[{ required: true }]}><Input placeholder="X-Signature" /></Form.Item></Col>
                <Col span={6}><Form.Item label="签名位置" name={['signing', 'outputLocation']} rules={[{ required: true }]}><Select options={locationOptions} /></Form.Item></Col>
                <Col span={6}><Form.Item label="编码" name={['signing', 'outputEncoding']} rules={[{ required: true }]}><Select options={['HEX_LOWERCASE', 'HEX_UPPERCASE', 'BASE64', 'BASE64_URL'].map((value) => ({ value, label: value }))} /></Form.Item></Col>
                <Col span={24}><Form.Item label="签名输出模板" name={['signing', 'outputValueTemplate']}><Input placeholder="可选，例如 v1:${signature}" /></Form.Item></Col>
              </>}
            </Row>
          </> },
          { key: 'schema', label: `${invocationType === 'ASYNC_JOB' ? '5' : '4'}. 输出 Schema`, children: <Form.List name="outputFields">
            {(fields, { add, remove }) => <Space orientation="vertical" size={8} className="http-api-output-fields">
              {fields.map((field, index) => <Row gutter={8} key={field.key} align="middle">
                <Col span={4}><Form.Item label={index === 0 ? '字段名' : undefined} name={[field.name, 'name']} rules={[{ required: true }, { pattern: /^[A-Za-z_][A-Za-z0-9_]{0,127}$/ }]}><Input /></Form.Item></Col>
                <Col span={4}><Form.Item label={index === 0 ? 'JSON Pointer' : undefined} name={[field.name, 'jsonPointer']} rules={[{ required: true }]}><Input placeholder="/id" /></Form.Item></Col>
                <Col span={4}><Form.Item label={index === 0 ? '平台类型' : undefined} name={[field.name, 'fieldType']} rules={[{ required: true }]}><Select options={platformTypes.map((value) => ({ value, label: value }))} /></Form.Item></Col>
                <Col span={3}><Form.Item label={index === 0 ? '长度' : undefined} name={[field.name, 'length']}><InputNumber min={1} precision={0} /></Form.Item></Col>
                <Col span={3}><Form.Item label={index === 0 ? '精度' : undefined} name={[field.name, 'precision']}><InputNumber min={1} precision={0} /></Form.Item></Col>
                <Col span={2}><Form.Item label={index === 0 ? '小数位' : undefined} name={[field.name, 'scale']}><InputNumber min={0} precision={0} /></Form.Item></Col>
                <Col span={2}><Form.Item label={index === 0 ? '可空' : undefined} name={[field.name, 'nullable']} valuePropName="checked"><Switch /></Form.Item></Col>
                <Col span={1}><Button type="text" danger icon={<DeleteOutlined />} aria-label={`删除输出字段 ${index + 1}`} onClick={() => remove(field.name)} /></Col>
              </Row>)}
              <Button type="dashed" icon={<PlusOutlined />} onClick={() => add({ fieldType: 'STRING', nullable: true })}>添加输出字段</Button>
            </Space>}
          </Form.List> },
          { key: 'limits', label: `${invocationType === 'ASYNC_JOB' ? '6' : '5'}. 执行保护限制`, children: <Row gutter={12}>
            <Col span={6}><Form.Item label="最大页数" name={['limits', 'maxPages']} rules={[{ required: true }]}><InputNumber min={1} max={100000} precision={0} /></Form.Item></Col>
            <Col span={6}><Form.Item label="最大记录数" name={['limits', 'maxRows']} rules={[{ required: true }]}><InputNumber min={1} max={100000000} precision={0} /></Form.Item></Col>
            <Col span={6}><Form.Item label="最大响应字节" name={['limits', 'maxResponseBytes']} rules={[{ required: true }]}><InputNumber min={1} max={10737418240} precision={0} /></Form.Item></Col>
            <Col span={6}><Form.Item label="最大持续秒数" name={['limits', 'maxDurationSeconds']} rules={[{ required: true }]}><InputNumber min={1} max={86400} precision={0} /></Form.Item></Col>
          </Row> },
        ]} />
      </Form>
    </Drawer>
  </>;
};
