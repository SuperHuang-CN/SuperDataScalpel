import { CompactAlert as Alert, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  FolderOpenOutlined,
  GlobalOutlined,
  LoadingOutlined,
  MoreOutlined,
  PlusOutlined,
  RadarChartOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import {
  Button,
  Col,
  Drawer,
  Dropdown,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  ManagementCode,
  ManagementListCell,
  ManagementStatusIndicator,
} from '../../../shared/components/ManagementListCells';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';
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
  embedded?: boolean;
}

const resourceCode = (remoteIdentifier: string) => {
  const candidate = remoteIdentifier.split('/').filter(Boolean).at(-1)?.replace(/[^A-Za-z0-9_]/g, '_').toLowerCase();
  return candidate && /^[a-z]/.test(candidate) ? candidate.slice(0, 64) : `spatial_${candidate ?? 'resource'}`.slice(0, 64);
};

export const SpatialFeatureResourceListDrawer = ({
  dataSource, open, canCreate, canUpdate, canDelete, canReadMetadata, onClose, embedded = false,
}: SpatialFeatureResourceListDrawerProps) => {
  const dataSourceId = dataSource?.id ?? '';
  const [registerOpen, setRegisterOpen] = useState(false);
  const [catalogParent, setCatalogParent] = useState<string>();
  const [editing, setEditing] = useState<SpatialFeatureResource | null>(null);
  const [previewing, setPreviewing] = useState<SpatialFeatureResource | null>(null);
  const [registerForm] = Form.useForm<CreateSpatialFeatureResourceRequest>();
  const [editForm] = Form.useForm<UpdateSpatialFeatureResourceRequest>();
  const selectedRemoteIdentifier = Form.useWatch('remoteIdentifier', registerForm);
  const editingEnabled = Form.useWatch('enabled', editForm) ?? editing?.enabled ?? false;
  const [messageApi, contextHolder] = message.useMessage();
  const [modalApi, modalContextHolder] = Modal.useModal();
  const resourcesQuery = useSpatialFeatureResources(dataSourceId || undefined, open || embedded);
  const catalogQuery = useSpatialCatalog(dataSourceId || undefined, catalogParent, (open || embedded) && registerOpen);
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

  const refresh = useCallback(async (resource: SpatialFeatureResource) => {
    try {
      await refreshMutation.mutateAsync(resource.id);
      messageApi.success(`已刷新“${resource.name}”的 Schema`);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '刷新 Schema 失败');
    }
  }, [messageApi, refreshMutation]);

  const remove = useCallback(async (resource: SpatialFeatureResource) => {
    try {
      await deleteMutation.mutateAsync(resource.id);
      messageApi.success('空间要素资源已删除');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '删除空间要素资源失败');
    }
  }, [deleteMutation, messageApi]);

  const resourceActionItems = useCallback((resource: SpatialFeatureResource): MenuProps['items'] => [
    ...(canReadMetadata ? [{
      key: 'preview',
      icon: <EyeOutlined />,
      label: '预览属性',
      onClick: () => setPreviewing(resource),
    }] : []),
    ...(canUpdate ? [{
      key: 'edit',
      icon: <EditOutlined />,
      label: '修改',
      onClick: () => setEditing(resource),
    }, {
      key: 'refresh',
      icon: refreshMutation.isPending && refreshMutation.variables === resource.id
        ? <LoadingOutlined />
        : <ReloadOutlined />,
      label: '刷新 Schema',
      disabled: refreshMutation.isPending && refreshMutation.variables === resource.id,
      onClick: () => void refresh(resource),
    }] : []),
    ...(canDelete ? [
      { type: 'divider' as const },
      {
        key: 'delete',
        icon: <DeleteOutlined />,
        label: '删除',
        danger: true,
        onClick: () => modalApi.confirm({
          rootClassName: 'business-overlay business-modal-overlay',
          title: '删除空间要素资源',
          content: `确认删除“${resource.name}”吗？`,
          okText: '删除',
          okButtonProps: { danger: true },
          cancelText: '取消',
          onOk: () => remove(resource),
        }),
      },
    ] : []),
  ], [canDelete, canReadMetadata, canUpdate, modalApi, refresh, refreshMutation.isPending, refreshMutation.variables, remove]);

  const resourceColumns = useMemo<TableProps<SpatialFeatureResource>['columns']>(() => [
    {
      title: '空间资源',
      key: 'identity',
      width: 220,
      render: (_, resource) => (
        <ManagementListCell
          icon={<GlobalOutlined />}
          iconTone="cyan"
          primary={<Tooltip title={resource.name}><span className="management-list-ellipsis">{resource.name}</span></Tooltip>}
          secondary={<ManagementCode value={resource.code} />}
        />
      ),
    },
    {
      title: '远程目标',
      key: 'remote',
      width: 330,
      render: (_, resource) => (
        <ManagementListCell
          primary={<ManagementCode value={resource.remoteIdentifier} />}
          secondary={`${resource.protocol === 'ARCGIS_REST' ? 'ArcGIS REST' : 'WFS'} · ${resource.serviceTitle || '未命名服务'}`}
        />
      ),
    },
    {
      title: 'Schema',
      key: 'schema',
      width: 180,
      render: (_, resource) => (
        <ManagementListCell
          primary={resource.epsgCode ? `EPSG:${resource.epsgCode}` : '坐标系未识别'}
          secondary={`${resource.columns.length} 个字段 · ${resource.geometryFieldName || '无几何字段'}`}
        />
      ),
    },
    {
      title: '状态与更新时间',
      key: 'status',
      width: 170,
      render: (_, resource) => (
        <ManagementListCell
          primary={<ManagementStatusIndicator label={resource.enabled ? '启用' : '停用'} tone={resource.enabled ? 'success' : 'default'} />}
          secondary={formatManagementDateTime(resource.updatedAt)}
        />
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 58,
      align: 'center',
      render: (_, resource) => (
        <Dropdown menu={{ items: resourceActionItems(resource) }} trigger={['click']}>
          <Tooltip title="更多操作">
            <Button type="text" icon={<MoreOutlined />} aria-label={`管理空间要素资源 ${resource.name}`} />
          </Tooltip>
        </Dropdown>
      ),
    },
  ], [resourceActionItems]);

  const toolbar = (
    <div className="spatial-resource-result-toolbar">
      <div className="spatial-resource-result-heading">
        <strong>已登记空间资源</strong>
        <span>共 {resourcesQuery.data?.length ?? 0} 项</span>
      </div>
      <Space size={8}>
        <Tooltip title="刷新空间资源">
          <Button
            type="text"
            icon={<ReloadOutlined />}
            aria-label="刷新空间资源"
            loading={resourcesQuery.isFetching}
            onClick={() => void resourcesQuery.refetch()}
          />
        </Tooltip>
        {canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={startRegistration}>发现并登记</Button>}
      </Space>
    </div>
  );
  const resourceError = resourcesQuery.error ? (
    <Alert
      showIcon
      type="error"
      message="空间资源加载失败"
      description={resourcesQuery.error instanceof ApiError ? resourcesQuery.error.message : '请稍后重试。'}
      action={<Button size="small" onClick={() => void resourcesQuery.refetch()}>重试</Button>}
    />
  ) : null;
  const resourceTable = (
    <Table<SpatialFeatureResource>
      className="management-table management-table-comfortable spatial-resource-table"
      size="small"
      rowKey="id"
      pagination={false}
      loading={resourcesQuery.isFetching}
      dataSource={resourcesQuery.data ?? []}
      columns={resourceColumns}
      tableLayout="fixed"
      scroll={{ y: embedded ? 'calc(100vh - 430px)' : 'calc(100vh - 236px)' }}
    />
  );

  return <>
    {contextHolder}
    {modalContextHolder}
    {embedded ? (
      <div className="data-source-resource-panel spatial-resource-panel">
        {toolbar}
        {resourceError}
        {resourceTable}
      </div>
    ) : (
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        className="spatial-resource-list-drawer"
        open={open}
        size={1040}
        title={(
          <div className="data-model-drawer-title">
            <span className="data-model-drawer-title-icon" aria-hidden="true"><GlobalOutlined /></span>
            <span className="data-model-drawer-title-copy">
              <span>空间要素资源</span>
              <Typography.Text type="secondary">管理“{dataSource?.name ?? '当前数据源'}”已登记的远程图层</Typography.Text>
            </span>
          </div>
        )}
        destroyOnHidden
        onClose={onClose}
        extra={<Tag className="data-model-drawer-header-tag">空间服务</Tag>}
      >
        <div className="spatial-resource-list-surface">
          {toolbar}
          {resourceError}
          {resourceTable}
        </div>
      </Drawer>
    )}
    <Modal
      rootClassName="business-overlay business-modal-overlay"
      className="spatial-resource-register-modal"
      open={registerOpen}
      width={980}
      title={(
        <div className="spatial-resource-modal-title">
          <span className="spatial-resource-modal-title-icon" aria-hidden="true"><RadarChartOutlined /></span>
          <span>
            <strong>发现并登记空间要素资源</strong>
            <Typography.Text type="secondary">浏览服务目录，选择一个远程图层并确认平台内标识</Typography.Text>
          </span>
        </div>
      )}
      destroyOnHidden
      onCancel={() => setRegisterOpen(false)}
      footer={(
        <div className="spatial-resource-modal-footer">
          <div className="spatial-resource-modal-context">
            {selectedRemoteIdentifier
              ? <InlineFeedback tone="success" label="已选择远程资源" detail={selectedRemoteIdentifier} />
              : <InlineFeedback tone="info" label="尚未选择远程资源" />}
          </div>
          <Space>
            <Button disabled={createMutation.isPending} onClick={() => setRegisterOpen(false)}>取消</Button>
            <Button type="primary" loading={createMutation.isPending} onClick={() => void register()}>登记资源</Button>
          </Space>
        </div>
      )}
    >
      <div className="spatial-resource-register-workspace">
        <section className="spatial-resource-section spatial-resource-catalog-section">
          <header className="spatial-resource-section-header">
            <span className="spatial-resource-section-icon" aria-hidden="true"><FolderOpenOutlined /></span>
            <span className="spatial-resource-section-copy">
              <strong>服务目录</strong>
              <Typography.Text type="secondary">目录用于导航，带“选择”的条目才是可登记图层</Typography.Text>
            </span>
            <span className="spatial-resource-section-path">
              {catalogParent ? <ManagementCode value={catalogParent} /> : '服务根目录'}
            </span>
            <Space size={6}>
              <Button onClick={() => setCatalogParent(undefined)} disabled={!catalogParent}>返回根目录</Button>
              <Tooltip title="刷新当前目录">
                <Button icon={<ReloadOutlined />} aria-label="刷新空间服务目录" loading={catalogQuery.isFetching} onClick={() => void catalogQuery.refetch()} />
              </Tooltip>
            </Space>
          </header>
          {catalogQuery.error && (
            <InlineFeedback
              className="spatial-resource-catalog-feedback"
              tone="error"
              label="目录读取失败"
              detail={catalogQuery.error instanceof ApiError ? catalogQuery.error.message : '请稍后重试。'}
              action={<Button type="link" onClick={() => void catalogQuery.refetch()}>重试</Button>}
            />
          )}
          <Table<SpatialCatalogEntry>
            className="management-table management-table-comfortable spatial-resource-catalog-table"
            size="small"
            rowKey={(item) => `${item.kind}:${item.remoteIdentifier}`}
            pagination={false}
            loading={catalogQuery.isFetching}
            dataSource={catalogQuery.data ?? []}
            tableLayout="fixed"
            rowClassName={(entry) => entry.remoteIdentifier === selectedRemoteIdentifier ? 'is-selected' : ''}
            scroll={{ y: 250 }}
            columns={[
              {
                title: '目录资源',
                key: 'identity',
                render: (_, entry) => (
                  <ManagementListCell
                    primary={<Tooltip title={entry.title || entry.name}><span className="management-list-ellipsis">{entry.title || entry.name}</span></Tooltip>}
                    secondary={entry.kind}
                  />
                ),
              },
              {
                title: '远程标识',
                dataIndex: 'remoteIdentifier',
                width: 360,
                render: (value: string) => <ManagementCode value={value} />,
              },
              {
                title: '坐标系',
                dataIndex: 'epsgCode',
                width: 105,
                render: (value: number | null) => value ? `EPSG:${value}` : '—',
              },
              {
                title: '操作',
                width: 80,
                align: 'center',
                render: (_, entry) => (
                  <Button type={entry.selectable ? 'primary' : 'text'} onClick={() => selectCatalogEntry(entry)}>
                    {entry.selectable ? '选择' : '进入'}
                  </Button>
                ),
              },
            ]}
          />
        </section>

        <section className="spatial-resource-section spatial-resource-registration-section">
          <header className="spatial-resource-section-header">
            <span className="spatial-resource-section-icon" aria-hidden="true"><GlobalOutlined /></span>
            <span className="spatial-resource-section-copy">
              <strong>登记配置</strong>
              <Typography.Text type="secondary">确认平台内名称、稳定编码和需要转换的输出坐标系</Typography.Text>
            </span>
            <Tag className="data-model-drawer-header-tag">{selectedRemoteIdentifier ? '已选择' : '待选择'}</Tag>
          </header>
          <Form<CreateSpatialFeatureResourceRequest>
            form={registerForm}
            layout="vertical"
            autoComplete="off"
            className="spatial-resource-register-form"
          >
            <Row gutter={14}>
              <Col xs={24} sm={9}>
                <Form.Item name="code" label="资源编码" rules={[{ required: true }, { pattern: /^[a-z][a-z0-9_]{0,63}$/, message: '以小写字母开头，只能使用小写字母、数字和下划线' }]}>
                  <Input name="spatial-resource-code" autoComplete="off" />
                </Form.Item>
              </Col>
              <Col xs={24} sm={9}>
                <Form.Item name="name" label="资源名称" rules={[{ required: true }, { max: 100 }]}>
                  <Input name="spatial-resource-name" autoComplete="off" />
                </Form.Item>
              </Col>
              <Col xs={24} sm={6}>
                <Form.Item name="outputEpsgCode" label="输出 EPSG（可选）">
                  <InputNumber name="spatial-resource-output-epsg" min={1} max={99999999} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item name="remoteIdentifier" label="远程图层 / FeatureType 标识" rules={[{ required: true }, { max: 1000 }]}>
              <Input name="spatial-resource-identifier" autoComplete="off" placeholder="从上方目录选择，或手工输入" />
            </Form.Item>
          </Form>
        </section>
      </div>
    </Modal>
    <Modal
      rootClassName="business-overlay business-modal-overlay"
      className="spatial-resource-edit-modal"
      open={Boolean(editing)}
      title={(
        <div className="spatial-resource-modal-title">
          <span className="spatial-resource-modal-title-icon" aria-hidden="true"><EditOutlined /></span>
          <span>
            <strong>修改空间要素资源</strong>
            <Typography.Text type="secondary">远程标识与 Schema 保持不变，仅调整平台展示名称和可用状态</Typography.Text>
          </span>
        </div>
      )}
      destroyOnHidden
      onCancel={() => setEditing(null)}
      footer={(
        <div className="spatial-resource-modal-footer">
          <div className="spatial-resource-modal-context">
            <ManagementStatusIndicator label={editingEnabled ? '保存后启用' : '保存后停用'} tone={editingEnabled ? 'success' : 'default'} />
          </div>
          <Space>
            <Button disabled={updateMutation.isPending} onClick={() => setEditing(null)}>取消</Button>
            <Button type="primary" loading={updateMutation.isPending} onClick={() => void update()}>保存修改</Button>
          </Space>
        </div>
      )}
    >
      <Form<UpdateSpatialFeatureResourceRequest> form={editForm} layout="vertical" autoComplete="off" className="spatial-resource-edit-form">
        <section className="spatial-resource-section spatial-resource-edit-section">
          <header className="spatial-resource-section-header">
            <span className="spatial-resource-section-icon" aria-hidden="true"><GlobalOutlined /></span>
            <span className="spatial-resource-section-copy">
              <strong>资源身份</strong>
              <Typography.Text type="secondary">名称用于列表识别，启停状态控制该资源是否可被业务引用</Typography.Text>
            </span>
            <span className="spatial-resource-section-switch-label">{editingEnabled ? '启用' : '停用'}</span>
            <Form.Item name="enabled" valuePropName="checked" noStyle>
              <Switch aria-label="空间要素资源启用状态" />
            </Form.Item>
          </header>
          <div className="spatial-resource-edit-body">
            <Form.Item name="name" label="资源名称" rules={[{ required: true }, { max: 100 }]}>
              <Input name="spatial-resource-edit-name" autoComplete="off" />
            </Form.Item>
            <div className="spatial-resource-readonly-summary">
              <div><span>资源编码</span><ManagementCode value={editing?.code ?? '—'} /></div>
              <div><span>远程标识</span><ManagementCode value={editing?.remoteIdentifier ?? '—'} /></div>
              <div><span>当前 Schema</span><strong>{editing ? `${editing.columns.length} 个字段` : '—'}</strong></div>
            </div>
          </div>
        </section>
      </Form>
    </Modal>
    <Modal
      rootClassName="business-overlay business-modal-overlay"
      className="spatial-resource-preview-modal"
      open={Boolean(previewing)}
      title={(
        <div className="spatial-resource-modal-title">
          <span className="spatial-resource-modal-title-icon" aria-hidden="true"><EyeOutlined /></span>
          <span>
            <strong>属性预览 · {previewing?.name ?? ''}</strong>
            <Typography.Text type="secondary">读取少量属性记录，用于确认字段内容与远端 Schema</Typography.Text>
          </span>
        </div>
      )}
      footer={(
        <div className="spatial-resource-modal-footer">
          <Typography.Text type="secondary">
            {previewQuery.data?.truncated ? `仅显示前 ${previewQuery.data.limit} 条属性记录` : '已显示本次返回的全部属性记录'}
          </Typography.Text>
          <Button onClick={() => setPreviewing(null)}>关闭</Button>
        </div>
      )}
      width={980}
      destroyOnHidden
      onCancel={() => setPreviewing(null)}
    >
      <Table<Record<string, unknown>>
        className="management-table spatial-resource-preview-table"
        size="small" rowKey={(_, index) => String(index)} pagination={false} loading={previewQuery.isFetching}
        dataSource={previewQuery.data?.rows ?? []} scroll={{ x: true, y: 340 }} tableLayout="fixed"
        columns={(previewQuery.data?.columns ?? []).map((column) => ({ title: column.name, dataIndex: column.name, width: 160, ellipsis: true, render: (value: unknown) => value == null ? '—' : String(value) }))}
      />
    </Modal>
  </>;
};

export const SpatialFeatureResourcePanel = (
  props: Omit<SpatialFeatureResourceListDrawerProps, 'open' | 'onClose' | 'embedded'>,
) => <SpatialFeatureResourceListDrawer {...props} open onClose={() => undefined} embedded />;
