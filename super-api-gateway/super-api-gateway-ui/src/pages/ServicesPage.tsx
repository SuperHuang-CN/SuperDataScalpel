import {
  DeleteOutlined,
  EditOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import { useMemo, useState } from 'react';
import { api, post } from '../api';
import type { AccessMode, GatewayHttpMethod, PageResponse, Route, Service } from '../model';

interface ServiceForm {
  code: string;
  name: string;
  upstreamUri: string;
  accessMode: AccessMode;
  connectTimeoutMs: number;
  responseTimeoutMs: number;
  enabled: boolean;
  description?: string;
  source?: string;
  externalId?: string;
}

interface RouteForm {
  serviceId: string;
  code: string;
  name: string;
  pathPattern: string;
  methods: GatewayHttpMethod[];
  order: number;
  stripPrefixSegments: number;
  upstreamPath?: string;
  enabled: boolean;
  source?: string;
  externalId?: string;
}

const methodOptions = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']
  .map((value) => ({ label: value, value }));

export const ServicesPage = () => {
  const queryClient = useQueryClient();
  const [selectedServiceId, setSelectedServiceId] = useState<string>();
  const [editingService, setEditingService] = useState<Service>();
  const [serviceDrawerOpen, setServiceDrawerOpen] = useState(false);
  const [editingRoute, setEditingRoute] = useState<Route>();
  const [routeDrawerOpen, setRouteDrawerOpen] = useState(false);
  const [serviceForm] = Form.useForm<ServiceForm>();
  const [routeForm] = Form.useForm<RouteForm>();

  const servicesQuery = useQuery({
    queryKey: ['services'],
    queryFn: () => api<PageResponse<Service>>('/services?page=0&size=200'),
  });
  const routesQuery = useQuery({
    queryKey: ['routes', selectedServiceId],
    queryFn: () => api<Route[]>(`/routes${selectedServiceId ? `?serviceId=${selectedServiceId}` : ''}`),
  });

  const selectedService = useMemo(
    () => servicesQuery.data?.content.find((item) => item.id === selectedServiceId),
    [selectedServiceId, servicesQuery.data],
  );

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['services'] });
    queryClient.invalidateQueries({ queryKey: ['routes'] });
    queryClient.invalidateQueries({ queryKey: ['runtime'] });
  };
  const command = useMutation({
    mutationFn: ({ path, body }: { path: string; body?: unknown }) => post(path, body),
    onSuccess: () => { message.success('操作成功'); refresh(); },
    onError: (error: Error) => message.error(error.message),
  });
  const saveService = useMutation({
    mutationFn: (values: ServiceForm) => editingService
      ? post<Service>(`/services/${editingService.id}/actions/update`, {
          name: values.name,
          upstreamUri: values.upstreamUri,
          accessMode: values.accessMode,
          connectTimeoutMs: values.connectTimeoutMs,
          responseTimeoutMs: values.responseTimeoutMs,
          enabled: values.enabled,
          description: values.description,
        })
      : post<Service>('/services', values),
    onSuccess: (saved) => {
      message.success('服务已保存');
      setServiceDrawerOpen(false);
      setSelectedServiceId(saved.id);
      refresh();
    },
    onError: (error: Error) => message.error(error.message),
  });
  const saveRoute = useMutation({
    mutationFn: (values: RouteForm) => editingRoute
      ? post<Route>(`/routes/${editingRoute.id}/actions/update`, {
          name: values.name,
          pathPattern: values.pathPattern,
          methods: values.methods,
          order: values.order,
          stripPrefixSegments: values.stripPrefixSegments,
          upstreamPath: values.upstreamPath,
          enabled: values.enabled,
        })
      : post<Route>('/routes', values),
    onSuccess: () => {
      message.success('路由已保存');
      setRouteDrawerOpen(false);
      refresh();
    },
    onError: (error: Error) => message.error(error.message),
  });

  const openService = (service?: Service) => {
    setEditingService(service);
    serviceForm.setFieldsValue(service ?? {
      accessMode: 'PUBLIC',
      connectTimeoutMs: 3000,
      responseTimeoutMs: 30000,
      enabled: true,
      source: 'MANUAL',
    } as ServiceForm);
    setServiceDrawerOpen(true);
  };
  const openRoute = (route?: Route) => {
    setEditingRoute(route);
    routeForm.setFieldsValue(route ?? {
      serviceId: selectedServiceId,
      methods: ['POST'],
      order: 0,
      stripPrefixSegments: 0,
      enabled: true,
      source: 'MANUAL',
    } as RouteForm);
    setRouteDrawerOpen(true);
  };

  return (
    <div className="split-panel">
      <section className="panel">
        <div className="toolbar">
          <Space><strong>服务</strong><Tag>{servicesQuery.data?.totalElements ?? 0}</Tag></Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openService()}>新建服务</Button>
        </div>
        <Table
          rowKey="id"
          size="small"
          loading={servicesQuery.isLoading}
          dataSource={servicesQuery.data?.content ?? []}
          rowSelection={{
            type: 'radio',
            selectedRowKeys: selectedServiceId ? [selectedServiceId] : [],
            onChange: (keys) => setSelectedServiceId(keys[0] as string),
          }}
          pagination={{ pageSize: 20, showSizeChanger: false }}
          scroll={{ y: 'calc(100vh - 210px)', x: 850 }}
          columns={[
            { title: 'Code', dataIndex: 'code', width: 150, fixed: 'left', ellipsis: true },
            { title: '名称', dataIndex: 'name', width: 150, ellipsis: true },
            { title: '访问', dataIndex: 'accessMode', width: 150, render: (value: AccessMode) => <Tag color={value === 'PUBLIC' ? 'blue' : 'gold'}>{value}</Tag> },
            { title: '路由', dataIndex: 'routeCount', width: 70 },
            { title: '状态', dataIndex: 'enabled', width: 75, render: (enabled: boolean) => <Tag color={enabled ? 'success' : 'default'}>{enabled ? '启用' : '停用'}</Tag> },
            {
              title: '操作', fixed: 'right', width: 130,
              render: (_: unknown, record: Service) => (
                <Space size={2}>
                  <Tooltip title="编辑"><Button type="text" icon={<EditOutlined />} onClick={() => openService(record)} /></Tooltip>
                  <Tooltip title={record.enabled ? '停用' : '启用'}>
                    <Button type="text" icon={record.enabled ? <PauseCircleOutlined /> : <PlayCircleOutlined />} onClick={() => command.mutate({ path: `/services/${record.id}/actions/${record.enabled ? 'disable' : 'enable'}` })} />
                  </Tooltip>
                  <Popconfirm title={`删除服务 ${record.name}？`} description="需要先停用服务并撤回有效订阅。" onConfirm={() => command.mutate({ path: `/services/${record.id}/actions/delete` })}>
                    <Tooltip title="删除"><Button danger type="text" icon={<DeleteOutlined />} /></Tooltip>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
        />
      </section>

      <section className="panel">
        <div className="toolbar">
          <Space><strong>路由</strong><span>{selectedService?.name ?? '请选择服务'}</span></Space>
          <Button type="primary" icon={<PlusOutlined />} disabled={!selectedServiceId} onClick={() => openRoute()}>新建路由</Button>
        </div>
        <Table
          rowKey="id"
          size="small"
          loading={routesQuery.isLoading}
          dataSource={selectedServiceId ? routesQuery.data ?? [] : []}
          pagination={false}
          scroll={{ y: 'calc(100vh - 185px)', x: 850 }}
          columns={[
            { title: 'Code', dataIndex: 'code', width: 140, ellipsis: true },
            { title: '路径模板', dataIndex: 'pathPattern', width: 220, ellipsis: true },
            { title: '固定上游路径', dataIndex: 'upstreamPath', width: 180, ellipsis: true, render: (value?: string) => value || '—' },
            { title: '方法', dataIndex: 'methods', width: 160, render: (methods: string[]) => methods.map((method) => <Tag key={method}>{method}</Tag>) },
            { title: 'Order', dataIndex: 'order', width: 70 },
            { title: '状态', dataIndex: 'enabled', width: 75, render: (enabled: boolean) => <Tag color={enabled ? 'success' : 'default'}>{enabled ? '启用' : '停用'}</Tag> },
            {
              title: '操作', fixed: 'right', width: 130,
              render: (_: unknown, record: Route) => (
                <Space size={2}>
                  <Tooltip title="编辑"><Button type="text" icon={<EditOutlined />} onClick={() => openRoute(record)} /></Tooltip>
                  <Tooltip title={record.enabled ? '停用' : '启用'}>
                    <Button type="text" icon={record.enabled ? <PauseCircleOutlined /> : <PlayCircleOutlined />} onClick={() => command.mutate({ path: `/routes/${record.id}/actions/${record.enabled ? 'disable' : 'enable'}` })} />
                  </Tooltip>
                  <Popconfirm title={`删除路由 ${record.name}？`} onConfirm={() => command.mutate({ path: `/routes/${record.id}/actions/delete` })}>
                    <Tooltip title="删除"><Button danger type="text" icon={<DeleteOutlined />} /></Tooltip>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
        />
      </section>

      <Drawer title={editingService ? '编辑服务' : '新建服务'} width={620} open={serviceDrawerOpen} onClose={() => setServiceDrawerOpen(false)} destroyOnHidden extra={<Button type="primary" loading={saveService.isPending} onClick={() => serviceForm.submit()}>保存</Button>}>
        <Form form={serviceForm} layout="vertical" autoComplete="off" onFinish={(values) => saveService.mutate(values)}>
          <Space align="start" style={{ width: '100%' }}>
            <Form.Item label="Code" name="code" rules={[{ required: true }]}><Input disabled={!!editingService} style={{ width: 270 }} /></Form.Item>
            <Form.Item label="名称" name="name" rules={[{ required: true }]}><Input style={{ width: 270 }} /></Form.Item>
          </Space>
          <Form.Item label="上游 URI" name="upstreamUri" rules={[{ required: true }]}><Input placeholder="http://service-engine:8081" /></Form.Item>
          <Space align="start">
            <Form.Item label="访问模式" name="accessMode" rules={[{ required: true }]}><Select style={{ width: 220 }} options={[{ value: 'PUBLIC', label: '公开' }, { value: 'SUBSCRIPTION_REQUIRED', label: '需要订阅' }]} /></Form.Item>
            <Form.Item label="连接超时(ms)" name="connectTimeoutMs" rules={[{ required: true }]}><InputNumber min={100} max={120000} /></Form.Item>
            <Form.Item label="响应超时(ms)" name="responseTimeoutMs" rules={[{ required: true }]}><InputNumber min={100} max={600000} /></Form.Item>
          </Space>
          <Form.Item label="说明" name="description"><Input.TextArea rows={3} /></Form.Item>
          {!editingService && <Space align="start">
            <Form.Item label="来源" name="source"><Input style={{ width: 220 }} /></Form.Item>
            <Form.Item label="外部引用" name="externalId"><Input style={{ width: 300 }} /></Form.Item>
          </Space>}
          <Form.Item label="启用" name="enabled" valuePropName="checked"><Switch /></Form.Item>
        </Form>
      </Drawer>

      <Drawer title={editingRoute ? '编辑路由' : '新建路由'} width={620} open={routeDrawerOpen} onClose={() => setRouteDrawerOpen(false)} destroyOnHidden extra={<Button type="primary" loading={saveRoute.isPending} onClick={() => routeForm.submit()}>保存</Button>}>
        <Form form={routeForm} layout="vertical" autoComplete="off" onFinish={(values) => saveRoute.mutate(values)}>
          <Form.Item name="serviceId" hidden><Input /></Form.Item>
          <Space align="start">
            <Form.Item label="Code" name="code" rules={[{ required: true }]}><Input disabled={!!editingRoute} style={{ width: 270 }} /></Form.Item>
            <Form.Item label="名称" name="name" rules={[{ required: true }]}><Input style={{ width: 270 }} /></Form.Item>
          </Space>
          <Form.Item label="路径模板" name="pathPattern" rules={[{ required: true }]} extra="首段必须固定；支持 {variable}、段内 * 和末尾 /**。">
            <Input placeholder="/open-api/v1/orders/{id}" />
          </Form.Item>
          <Form.Item label="HTTP 方法" name="methods" rules={[{ required: true }]}><Select mode="multiple" options={methodOptions} /></Form.Item>
          <Form.Item label="固定上游路径" name="upstreamPath" extra="设置后以该路径转发，去除前缀段数必须为 0。">
            <Input placeholder="/runtime/v1/services/{serviceId}" />
          </Form.Item>
          <Space align="start">
            <Form.Item label="Order" name="order"><InputNumber min={-10000} max={10000} /></Form.Item>
            <Form.Item label="去除前缀段数" name="stripPrefixSegments"><InputNumber min={0} max={16} /></Form.Item>
            <Form.Item label="启用" name="enabled" valuePropName="checked"><Switch /></Form.Item>
          </Space>
          {!editingRoute && <Space align="start">
            <Form.Item label="来源" name="source"><Input style={{ width: 220 }} /></Form.Item>
            <Form.Item label="外部引用" name="externalId"><Input style={{ width: 300 }} /></Form.Item>
          </Space>}
        </Form>
      </Drawer>
    </div>
  );
};
