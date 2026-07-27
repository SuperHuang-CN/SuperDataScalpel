import { ApiOutlined, DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Button, Drawer, Form, Input, Modal, Popconfirm, Space, Table, Tag, Tooltip, message } from 'antd';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  useApiResources,
  useDeleteApiResource,
  useTestApiResource,
} from '../hooks/useDataSources';
import type { ApiResource, ApiResourceTestResult, DataSource, HttpApiRuntimeParameter } from '../model/dataSource';
import { ApiResourceDrawer } from './ApiResourceDrawer';
import { ApiResourceTestResultModal } from './ApiResourceTestResultModal';

interface ApiResourceListDrawerProps {
  dataSource: DataSource | null;
  open: boolean;
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
  canTest: boolean;
  onClose: () => void;
}

interface RuntimeParameterFormValues {
  runtimeParameters?: HttpApiRuntimeParameter[];
}

const invocationLabels: Record<ApiResource['invocationType'], string> = {
  SINGLE_REQUEST: '单次请求',
  PAGINATED_REQUEST: '同步分页',
  ASYNC_JOB: '异步任务',
};

export const ApiResourceListDrawer = ({
  dataSource, open, canCreate, canUpdate, canDelete, canTest, onClose,
}: ApiResourceListDrawerProps) => {
  const dataSourceId = dataSource?.id ?? '';
  const [editing, setEditing] = useState<ApiResource | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [testing, setTesting] = useState<ApiResource | null>(null);
  const [testResult, setTestResult] = useState<{ resource: ApiResource; result: ApiResourceTestResult } | null>(null);
  const [form] = Form.useForm<RuntimeParameterFormValues>();
  const [messageApi, contextHolder] = message.useMessage();
  const resourcesQuery = useApiResources(dataSourceId || undefined, open);
  const deleteMutation = useDeleteApiResource(dataSourceId);
  const testMutation = useTestApiResource(dataSourceId);

  const remove = async (resource: ApiResource) => {
    try {
      await deleteMutation.mutateAsync(resource.id);
      messageApi.success('API 资源已删除');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '删除 API 资源失败');
    }
  };

  const beginTest = (resource: ApiResource) => {
    form.resetFields();
    form.setFieldsValue({ runtimeParameters: [] });
    setTesting(resource);
  };

  const runTest = async () => {
    if (!testing) return;
    try {
      const values = await form.validateFields();
      const result = await testMutation.mutateAsync({
        resourceId: testing.id,
        runtimeParameters: values.runtimeParameters ?? [],
      });
      setTestResult({ resource: testing, result });
      setTesting(null);
    } catch (error) {
      if (error instanceof ApiError || error instanceof Error) messageApi.error(error.message);
    }
  };

  return <>
    {contextHolder}
    <Drawer
      open={open}
      width={980}
      title={`API 资源 · ${dataSource?.name ?? ''}`}
      destroyOnHidden
      onClose={onClose}
      extra={<Space><Button icon={<ReloadOutlined />} onClick={() => void resourcesQuery.refetch()}>刷新</Button>{canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditing(null); setEditorOpen(true); }}>新建资源</Button>}</Space>}
    >
      <Table<ApiResource>
        size="small"
        rowKey="id"
        pagination={false}
        loading={resourcesQuery.isFetching}
        dataSource={resourcesQuery.data ?? []}
        scroll={{ x: 900, y: 'calc(100vh - 190px)' }}
        columns={[
          { title: '名称', dataIndex: 'name', width: 170, ellipsis: true },
          { title: '编码', dataIndex: 'code', width: 150, ellipsis: true, render: (value: string) => <code>{value}</code> },
          { title: '请求', key: 'request', width: 260, ellipsis: true, render: (_, resource) => <><Tag>{resource.request.method}</Tag><code>{resource.request.path}</code></> },
          { title: '调用模式', dataIndex: 'invocationType', width: 100, render: (value: ApiResource['invocationType']) => invocationLabels[value] },
          { title: '字段', key: 'fields', width: 72, render: (_, resource) => resource.outputFields.length },
          { title: '状态', dataIndex: 'enabled', width: 78, render: (value: boolean) => <Tag color={value ? 'success' : 'default'}>{value ? '启用' : '停用'}</Tag> },
          { title: '操作', key: 'actions', width: 132, fixed: 'right', render: (_, resource) => <Space size={2}>
            {canUpdate && <Tooltip title="修改"><Button type="text" icon={<EditOutlined />} aria-label={`修改${resource.name}`} onClick={() => { setEditing(resource); setEditorOpen(true); }} /></Tooltip>}
            {canTest && <Tooltip title="测试"><Button type="text" icon={<ApiOutlined />} aria-label={`测试${resource.name}`} loading={testMutation.isPending && testMutation.variables?.resourceId === resource.id} onClick={() => beginTest(resource)} /></Tooltip>}
            {canDelete && <Popconfirm title="删除 API 资源" description={`确认删除“${resource.name}”吗？`} okText="删除" cancelText="取消" onConfirm={() => void remove(resource)}><Tooltip title="删除"><Button type="text" danger icon={<DeleteOutlined />} aria-label={`删除${resource.name}`} /></Tooltip></Popconfirm>}
          </Space> },
        ]}
      />
    </Drawer>
    {dataSource && <ApiResourceDrawer
      dataSourceId={dataSource.id}
      resource={editing}
      open={editorOpen}
      onClose={() => { setEditorOpen(false); setEditing(null); }}
    />}
    <Modal
      open={Boolean(testing)}
      title={`测试 API 资源 · ${testing?.name ?? ''}`}
      destroyOnHidden
      onCancel={() => setTesting(null)}
      onOk={() => void runTest()}
      okText="开始测试"
      confirmLoading={testMutation.isPending}
    >
      <Form<RuntimeParameterFormValues> form={form} layout="vertical">
        <Form.List name="runtimeParameters">
          {(fields, { add, remove }) => <Space orientation="vertical" size={6} className="http-api-named-values">
            {fields.map((field) => <Space key={field.key} align="baseline" className="http-api-named-value-row">
              <Form.Item name={[field.name, 'name']} rules={[{ required: true }, { pattern: /^[A-Za-z][A-Za-z0-9_.-]{0,127}$/ }]}><Input placeholder="参数名，不含 runtime. 前缀" /></Form.Item>
              <Form.Item name={[field.name, 'value']} rules={[{ required: true }]}><Input placeholder="测试值" /></Form.Item>
              <Button type="text" danger icon={<DeleteOutlined />} aria-label="删除运行时参数" onClick={() => remove(field.name)} />
            </Space>)}
            <Button type="dashed" icon={<PlusOutlined />} onClick={() => add({ name: '', value: '' })}>添加运行时参数</Button>
          </Space>}
        </Form.List>
      </Form>
    </Modal>
    {testResult && <ApiResourceTestResultModal result={testResult.result} resourceName={testResult.resource.name} onClose={() => setTestResult(null)} />}
  </>;
};
