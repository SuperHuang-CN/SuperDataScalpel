import { DatabaseOutlined, DeleteOutlined, EditOutlined, EyeOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Drawer, Form, Input, InputNumber, Modal, Popconfirm, Space, Switch, Table, Tag, Tooltip, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  useCreateSpatialFeatureResource,
  useDeleteSpatialFeatureResource,
  useRefreshSpatialFeatureResourceSchema,
  useSpatialCatalog,
  useSpatialFeaturePreview,
  useSpatialFeatureResources,
  useUpdateSpatialFeatureResource,
} from '../hooks/useDataSources';
import type {
  CreateSpatialFeatureResourceRequest,
  DataSource,
  SpatialCatalogEntry,
  SpatialFeatureResource,
  UpdateSpatialFeatureResourceRequest,
} from '../model/dataSource';

interface SpatialFeatureResourceListDrawerProps {
  dataSource: DataSource | null;
  open: boolean;
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
  canReadMetadata: boolean;
  onClose: () => void;
}

const resourceCode = (remoteIdentifier: string) => {
  const candidate = remoteIdentifier.split('/').filter(Boolean).at(-1)?.replace(/[^A-Za-z0-9_]/g, '_').toLowerCase();
  return candidate && /^[a-z]/.test(candidate) ? candidate.slice(0, 64) : `spatial_${candidate ?? 'resource'}`.slice(0, 64);
};

export const SpatialFeatureResourceListDrawer = ({
  dataSource, open, canCreate, canUpdate, canDelete, canReadMetadata, onClose,
}: SpatialFeatureResourceListDrawerProps) => {
  const dataSourceId = dataSource?.id ?? '';
  const [registerOpen, setRegisterOpen] = useState(false);
  const [catalogParent, setCatalogParent] = useState<string>();
  const [editing, setEditing] = useState<SpatialFeatureResource | null>(null);
  const [previewing, setPreviewing] = useState<SpatialFeatureResource | null>(null);
  const [registerForm] = Form.useForm<CreateSpatialFeatureResourceRequest>();
  const [editForm] = Form.useForm<UpdateSpatialFeatureResourceRequest>();
  const [messageApi, contextHolder] = message.useMessage();
  const resourcesQuery = useSpatialFeatureResources(dataSourceId || undefined, open);
  const catalogQuery = useSpatialCatalog(dataSourceId || undefined, catalogParent, open && registerOpen);
  const createMutation = useCreateSpatialFeatureResource(dataSourceId);
  const updateMutation = useUpdateSpatialFeatureResource(dataSourceId);
  const refreshMutation = useRefreshSpatialFeatureResourceSchema(dataSourceId);
  const deleteMutation = useDeleteSpatialFeatureResource(dataSourceId);
  const previewQuery = useSpatialFeaturePreview(dataSourceId, previewing?.id, Boolean(previewing));

  useEffect(() => {
    if (!editing) return;
    editForm.setFieldsValue({ name: editing.name, enabled: editing.enabled });
  }, [editing, editForm]);

  const startRegistration = () => {
    registerForm.resetFields();
    setCatalogParent(undefined);
    setRegisterOpen(true);
  };

  const selectCatalogEntry = (entry: SpatialCatalogEntry) => {
    if (!entry.selectable) {
      setCatalogParent(entry.remoteIdentifier);
      return;
    }
    registerForm.setFieldsValue({
      remoteIdentifier: entry.remoteIdentifier,
      name: entry.title || entry.name,
      code: resourceCode(entry.remoteIdentifier),
      outputEpsgCode: entry.epsgCode ?? undefined,
    });
  };

  const register = async () => {
    try {
      const request = await registerForm.validateFields();
      await createMutation.mutateAsync(request);
      messageApi.success('空间要素资源已登记');
      setRegisterOpen(false);
    } catch (error) {
      if (error instanceof ApiError || error instanceof Error) messageApi.error(error.message);
    }
  };

  const update = async () => {
    if (!editing) return;
    try {
      const request = await editForm.validateFields();
      await updateMutation.mutateAsync({ resourceId: editing.id, request });
      messageApi.success('空间要素资源已更新');
      setEditing(null);
    } catch (error) {
      if (error instanceof ApiError || error instanceof Error) messageApi.error(error.message);
    }
  };

  const refresh = async (resource: SpatialFeatureResource) => {
    try {
      await refreshMutation.mutateAsync(resource.id);
      messageApi.success(`已刷新“${resource.name}”的 Schema`);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '刷新 Schema 失败');
    }
  };

  const remove = async (resource: SpatialFeatureResource) => {
    try {
      await deleteMutation.mutateAsync(resource.id);
      messageApi.success('空间要素资源已删除');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '删除空间要素资源失败');
    }
  };

  const resourceColumns = useMemo<TableProps<SpatialFeatureResource>['columns']>(() => [
    { title: '名称', dataIndex: 'name', width: 150, ellipsis: true },
    { title: '编码', dataIndex: 'code', width: 150, ellipsis: true, render: (value: string) => <code>{value}</code> },
    { title: '远程图层 / FeatureType', dataIndex: 'remoteIdentifier', width: 250, ellipsis: true, render: (value: string) => <code>{value}</code> },
    { title: '坐标系', dataIndex: 'epsgCode', width: 105, render: (value: number | null) => value ? `EPSG:${value}` : '—' },
    { title: '字段', key: 'columns', width: 70, render: (_, value) => value.columns.length },
    { title: '状态', dataIndex: 'enabled', width: 75, render: (value: boolean) => <Tag color={value ? 'success' : 'default'}>{value ? '启用' : '停用'}</Tag> },
    {
      title: '操作', key: 'actions', width: 156, fixed: 'right', render: (_, resource) => <Space size={2}>
        {canReadMetadata && <Tooltip title="预览属性"><Button type="text" icon={<EyeOutlined />} aria-label={`预览${resource.name}属性`} onClick={() => setPreviewing(resource)} /></Tooltip>}
        {canUpdate && <Tooltip title="修改"><Button type="text" icon={<EditOutlined />} aria-label={`修改${resource.name}`} onClick={() => setEditing(resource)} /></Tooltip>}
        {canUpdate && <Tooltip title="刷新 Schema"><Button type="text" icon={<ReloadOutlined />} aria-label={`刷新${resource.name}Schema`} loading={refreshMutation.isPending && refreshMutation.variables === resource.id} onClick={() => void refresh(resource)} /></Tooltip>}
        {canDelete && <Popconfirm title="删除空间要素资源" description={`确认删除“${resource.name}”吗？`} okText="删除" cancelText="取消" onConfirm={() => void remove(resource)}><Tooltip title="删除"><Button type="text" danger icon={<DeleteOutlined />} aria-label={`删除${resource.name}`} /></Tooltip></Popconfirm>}
      </Space>,
    },
  ], [canDelete, canReadMetadata, canUpdate, deleteMutation.isPending, deleteMutation.variables, refreshMutation.isPending, refreshMutation.variables]);

  return <>
    {contextHolder}
    <Drawer
      open={open}
      width={1040}
      title={`空间资源 · ${dataSource?.name ?? ''}`}
      destroyOnHidden
      onClose={onClose}
      extra={<Space><Button icon={<ReloadOutlined />} onClick={() => void resourcesQuery.refetch()}>刷新</Button>{canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={startRegistration}>发现并登记</Button>}</Space>}
    >
      <Table<SpatialFeatureResource>
        size="small" rowKey="id" pagination={false} loading={resourcesQuery.isFetching}
        dataSource={resourcesQuery.data ?? []} columns={resourceColumns} scroll={{ x: 980, y: 'calc(100vh - 190px)' }}
      />
    </Drawer>
    <Modal
      open={registerOpen} width={980} title="发现并登记空间要素资源" destroyOnHidden
      onCancel={() => setRegisterOpen(false)} onOk={() => void register()} okText="登记资源" confirmLoading={createMutation.isPending}
    >
      <Space style={{ marginBottom: 8 }}>
        <Button size="small" onClick={() => setCatalogParent(undefined)} disabled={!catalogParent}>返回服务根目录</Button>
        <Button size="small" icon={<ReloadOutlined />} onClick={() => void catalogQuery.refetch()}>刷新目录</Button>
      </Space>
      <Table<SpatialCatalogEntry>
        size="small" rowKey={(item) => `${item.kind}:${item.remoteIdentifier}`} pagination={false}
        loading={catalogQuery.isFetching} dataSource={catalogQuery.data ?? []} scroll={{ y: 250 }}
        columns={[
          { title: '名称', dataIndex: 'title', ellipsis: true },
          { title: '类型', dataIndex: 'kind', width: 120 },
          { title: '标识', dataIndex: 'remoteIdentifier', width: 360, ellipsis: true, render: (value: string) => <code>{value}</code> },
          { title: '坐标系', dataIndex: 'epsgCode', width: 100, render: (value: number | null) => value ? `EPSG:${value}` : '—' },
          { title: '操作', width: 100, render: (_, entry) => <Button type="link" size="small" onClick={() => selectCatalogEntry(entry)}>{entry.selectable ? '选择' : '进入'}</Button> },
        ]}
      />
      <Form<CreateSpatialFeatureResourceRequest> form={registerForm} layout="vertical" autoComplete="off" style={{ marginTop: 16 }}>
        <Space align="start" wrap>
          <Form.Item name="code" label="资源编码" rules={[{ required: true }, { pattern: /^[a-z][a-z0-9_]{0,63}$/, message: '以小写字母开头，只能使用小写字母、数字和下划线' }]}><Input name="spatial-resource-code" style={{ width: 220 }} /></Form.Item>
          <Form.Item name="name" label="资源名称" rules={[{ required: true }, { max: 100 }]}><Input name="spatial-resource-name" style={{ width: 240 }} /></Form.Item>
          <Form.Item name="outputEpsgCode" label="输出 EPSG（可选）"><InputNumber min={1} max={99999999} style={{ width: 170 }} /></Form.Item>
        </Space>
        <Form.Item name="remoteIdentifier" label="远程图层 / FeatureType 标识" rules={[{ required: true }, { max: 1000 }]}><Input name="spatial-resource-identifier" placeholder="从上方目录选择，或手工输入" /></Form.Item>
      </Form>
    </Modal>
    <Modal open={Boolean(editing)} title={`修改空间要素资源 · ${editing?.name ?? ''}`} destroyOnHidden onCancel={() => setEditing(null)} onOk={() => void update()} okText="保存" confirmLoading={updateMutation.isPending}>
      <Form<UpdateSpatialFeatureResourceRequest> form={editForm} layout="vertical" autoComplete="off">
        <Form.Item name="name" label="名称" rules={[{ required: true }, { max: 100 }]}><Input name="spatial-resource-edit-name" /></Form.Item>
        <Form.Item name="enabled" label="状态" valuePropName="checked"><Switch checkedChildren="启用" unCheckedChildren="停用" /></Form.Item>
      </Form>
    </Modal>
    <Modal open={Boolean(previewing)} title={`属性预览 · ${previewing?.name ?? ''}`} footer={<Button onClick={() => setPreviewing(null)}>关闭</Button>} width={980} destroyOnHidden onCancel={() => setPreviewing(null)}>
      <Table<Record<string, unknown>>
        size="small" rowKey={(_, index) => String(index)} pagination={false} loading={previewQuery.isFetching}
        dataSource={previewQuery.data?.rows ?? []} scroll={{ x: true, y: 340 }}
        columns={(previewQuery.data?.columns ?? []).map((column) => ({ title: column.name, dataIndex: column.name, width: 160, ellipsis: true, render: (value: unknown) => value == null ? '—' : String(value) }))}
      />
      {previewQuery.data?.truncated && <div style={{ marginTop: 8 }}>仅显示前 {previewQuery.data.limit} 条属性记录。</div>}
    </Modal>
  </>;
};
