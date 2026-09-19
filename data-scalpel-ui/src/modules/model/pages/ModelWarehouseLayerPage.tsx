import { CompactAlert as Alert, ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import {
  ApartmentOutlined,
  DeleteOutlined,
  EditOutlined,
  FileTextOutlined,
  MoreOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import { Badge, Button, Col, Drawer, Dropdown, Form, Input, InputNumber, Modal, Radio, Row, Select, Space, Table, Tag, Tooltip, Typography, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ManagementCode, ManagementDateTime, ManagementListCell, ManagementStatusIndicator } from '../../../shared/components/ManagementListCells';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { useCurrentUser } from '../../system';
import {
  useCreateModelWarehouseLayer,
  useDeleteModelWarehouseLayer,
  useModelWarehouseLayerCommand,
  useModelWarehouseLayers,
  useUpdateModelWarehouseLayer,
} from '../hooks/useDataModels';
import { ModelWarehouseLayerIcon } from '../components/ModelWarehouseLayerIcon';
import type {
  CreateModelWarehouseLayerRequest,
  ModelWarehouseLayer,
} from '../model/dataModel';

interface LayerFilters {
  code?: string;
  name?: string;
  enabled?: boolean;
}

interface LayerDrawerProps {
  open: boolean;
  layer: ModelWarehouseLayer | null;
  onClose: () => void;
}

const escapeDslText = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

const buildSearch = (filters: LayerFilters) => {
  const conditions = [
    filters.code?.trim() ? `code:*"${escapeDslText(filters.code.trim())}"*` : undefined,
    filters.name?.trim() ? `name:*"${escapeDslText(filters.name.trim())}"*` : undefined,
    filters.enabled === undefined ? undefined : `enabled:"${filters.enabled}"`,
  ].filter((condition): condition is string => Boolean(condition));
  return conditions.length ? conditions.join(' AND ') : undefined;
};

const allWarehouseLayerRequest = {
  page: 0,
  size: 500,
  sort: 'sortOrder,code',
} as const;

const LayerDrawer = ({ open, layer, onClose }: LayerDrawerProps) => {
  const [form] = Form.useForm<CreateModelWarehouseLayerRequest>();
  const [messageApi, messageContext] = message.useMessage();
  const createMutation = useCreateModelWarehouseLayer();
  const updateMutation = useUpdateModelWarehouseLayer();
  const layersQuery = useModelWarehouseLayers(allWarehouseLayerRequest, open);
  const inputLayerPolicy = Form.useWatch('inputLayerPolicy', form) ?? 'UNRESTRICTED';
  const selectedInputLayerIds = Form.useWatch('allowedInputLayerIds', form);
  const inputLayerOptions = useMemo(() => {
    const layers = [...(layersQuery.data?.content ?? [])];
    const selectedIds = selectedInputLayerIds ?? [];
    if (layer && !layers.some((candidate) => candidate.id === layer.id)) {
      layers.push(layer);
    }
    return layers.map((candidate) => ({
      value: candidate.id,
      label: `${candidate.code} · ${candidate.name}${candidate.enabled ? '' : '（已停用）'}`,
      disabled: !candidate.enabled && !selectedIds.includes(candidate.id),
    }));
  }, [layer, layersQuery.data, selectedInputLayerIds]);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue(layer ? {
      code: layer.code,
      name: layer.name,
      description: layer.description ?? undefined,
      color: layer.color ?? undefined,
      sortOrder: layer.sortOrder,
      modelCodePrefix: layer.modelCodePrefix ?? undefined,
      inputLayerPolicy: layer.inputLayerPolicy,
      allowedInputLayerIds: layer.allowedInputLayers.map((inputLayer) => inputLayer.id),
    } : {
      sortOrder: 100,
      inputLayerPolicy: 'UNRESTRICTED',
      allowedInputLayerIds: [],
    });
  }, [form, layer, open]);

  const submit = async (request: CreateModelWarehouseLayerRequest) => {
    try {
      const normalizedRequest: CreateModelWarehouseLayerRequest = {
        ...request,
        code: request.code.trim(),
        name: request.name.trim(),
        description: request.description?.trim() || undefined,
        color: request.color?.trim() || undefined,
        modelCodePrefix: request.modelCodePrefix?.trim() || undefined,
        inputLayerPolicy: request.inputLayerPolicy ?? 'UNRESTRICTED',
        allowedInputLayerIds: request.inputLayerPolicy === 'ALLOW_LIST'
          ? request.allowedInputLayerIds ?? []
          : [],
      };
      if (layer) {
        await updateMutation.mutateAsync({ id: layer.id, request: normalizedRequest });
        messageApi.success('数仓分层已保存');
      } else {
        await createMutation.mutateAsync(normalizedRequest);
        messageApi.success('数仓分层已创建');
      }
      onClose();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存数仓分层失败');
    }
  };

  return (
    <>
      {messageContext}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        className="data-model-drawer warehouse-layer-drawer"
        title={(
          <div className="data-model-drawer-title">
            <span className="data-model-drawer-title-icon" aria-hidden="true"><ApartmentOutlined /></span>
            <span className="data-model-drawer-title-copy">
              <span>{layer ? '修改数仓分层' : '新建数仓分层'}</span>
              <Typography.Text type="secondary">维护分层身份、模型编码规范与允许的数据流向</Typography.Text>
            </span>
          </div>
        )}
        extra={<Tag className="data-model-drawer-header-tag">{layer?.code ?? '待创建'}</Tag>}
        open={open}
        width={720}
        onClose={onClose}
        destroyOnHidden
        footer={(
          <div className="data-model-drawer-footer">
            <Badge
              status={layer?.enabled === false ? 'default' : 'processing'}
              text={layer ? `${layer.enabled ? '启用' : '停用'} · ${layer.referencedModelCount} 个模型引用` : '创建后默认启用'}
            />
            <Space>
              <Button onClick={onClose}>取消</Button>
              <Button
                type="primary"
                loading={createMutation.isPending || updateMutation.isPending}
                onClick={() => form.submit()}
              >
                {layer ? '保存修改' : '创建分层'}
              </Button>
            </Space>
          </div>
        )}
      >
        <Form<CreateModelWarehouseLayerRequest>
          name="warehouse-layer-editor-form"
          autoComplete="off"
          form={form}
          layout="vertical"
          className="data-model-form warehouse-layer-form"
          onFinish={(values) => void submit(values)}
        >
          <section className="data-model-form-section">
            <header className="data-model-form-section-header">
              <span className="data-model-form-section-icon" aria-hidden="true"><FileTextOutlined /></span>
              <span className="data-model-form-section-copy">
                <span className="data-model-form-section-title">分层信息</span>
                <Typography.Text type="secondary">设置分层标识、展示顺序与业务语义</Typography.Text>
              </span>
            </header>
            <div className="data-model-form-section-body">
              {layer && layer.referencedModelCount > 0 && (
                <InlineFeedback
                  className="warehouse-layer-reference-feedback"
                  tone="info"
                  label={`已被 ${layer.referencedModelCount} 个模型引用，编码不可修改`}
                />
              )}
              <Row gutter={14}>
                <Col span={12} xs={24} sm={12}>
              <Form.Item
                label="分层编码"
                name="code"
                rules={[
                  { required: true, whitespace: true, message: '请输入分层编码' },
                  { pattern: /^[A-Za-z][A-Za-z0-9_]{0,31}$/, message: '编码以字母开头，只能包含字母、数字和下划线，最多 32 个字符' },
                ]}
              >
                <Input name="warehouse-layer-code" autoComplete="off" disabled={Boolean(layer && layer.referencedModelCount > 0)} placeholder="如：DWD" />
              </Form.Item>
            </Col>
                <Col span={12} xs={24} sm={12}>
              <Form.Item
                label="分层名称"
                name="name"
                rules={[
                  { required: true, whitespace: true, message: '请输入分层名称' },
                  { max: 100, message: '名称不能超过 100 个字符' },
                ]}
              >
                <Input name="warehouse-layer-name" autoComplete="off" placeholder="如：明细数据层" />
              </Form.Item>
            </Col>
                <Col span={12} xs={24} sm={12}>
              <Form.Item
                label="展示颜色"
                name="color"
                rules={[{ pattern: /^#[0-9A-Fa-f]{6}$/, message: '颜色必须是 #RRGGBB 格式' }]}
              >
                <Input name="warehouse-layer-color" autoComplete="off" placeholder="#1677FF" />
              </Form.Item>
            </Col>
                <Col span={12} xs={24} sm={12}>
              <Form.Item
                label="排序值"
                name="sortOrder"
                rules={[{ required: true, message: '请输入排序值' }]}
              >
                <InputNumber min={0} max={9999} precision={0} className="data-model-number-input" />
              </Form.Item>
            </Col>
                <Col span={24}>
              <Form.Item
                label="说明"
                name="description"
                rules={[{ max: 500, message: '说明不能超过 500 个字符' }]}
              >
                <Input.TextArea name="warehouse-layer-description" autoComplete="off" rows={3} showCount maxLength={500} placeholder="说明该层的数据语义和使用范围" />
              </Form.Item>
            </Col>
              </Row>
            </div>
          </section>

          <section className="data-model-form-section">
            <header className="data-model-form-section-header">
              <span className="data-model-form-section-icon" aria-hidden="true"><SafetyCertificateOutlined /></span>
              <span className="data-model-form-section-copy">
                <span className="data-model-form-section-title">建模规范</span>
                <Typography.Text type="secondary">约束新模型编码与跨层数据输入关系</Typography.Text>
              </span>
            </header>
            <div className="data-model-form-section-body">
              <Row gutter={14}>
                <Col span={12} xs={24} sm={12}>
              <Form.Item
                label={(
                  <span className="warehouse-layer-field-label">
                    模型编码前缀
                    <ContextHelp
                      ariaLabel="查看模型编码前缀说明"
                      content="用于新建模型和 JDBC 表结构导入的编码候选，不会修改已有模型，也不作为发布阻断条件。"
                    />
                  </span>
                )}
                name="modelCodePrefix"
                rules={[
                  { max: 32, message: '前缀不能超过 32 个字符' },
                  {
                    pattern: /^[a-z][a-z0-9_]{0,30}_$/,
                    message: '前缀以小写字母开头、以下划线结尾，只能包含小写字母、数字和下划线',
                  },
                ]}
              >
                <Input name="warehouse-layer-model-code-prefix" autoComplete="off" placeholder="如：dwd_" />
              </Form.Item>
            </Col>
                <Col span={12} xs={24} sm={12}>
              <Form.Item
                label="输入策略"
                name="inputLayerPolicy"
                rules={[{ required: true, message: '请选择输入策略' }]}
              >
                <Radio.Group onChange={(event) => {
                  if (event.target.value === 'UNRESTRICTED') {
                    form.setFieldValue('allowedInputLayerIds', []);
                  }
                }}>
                  <Radio value="UNRESTRICTED">不限制</Radio>
                  <Radio value="ALLOW_LIST">允许列表</Radio>
                </Radio.Group>
              </Form.Item>
            </Col>
            {inputLayerPolicy === 'ALLOW_LIST' && (
              <Col span={24}>
                <Form.Item
                  label={(
                    <span className="warehouse-layer-field-label">
                      允许输入分层
                      <ContextHelp
                        ariaLabel="查看允许输入分层说明"
                        content="允许选择当前分层自身；留空表示不允许任何模型分层输入。已配置的停用分层可以保留或移除。"
                        presentation="popover"
                      />
                    </span>
                  )}
                  name="allowedInputLayerIds"
                >
                  <Select
                    mode="multiple"
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    loading={layersQuery.isFetching}
                    options={inputLayerOptions}
                    placeholder="留空表示不允许任何模型分层输入"
                  />
                </Form.Item>
              </Col>
            )}
              </Row>
            </div>
          </section>
        </Form>
      </Drawer>
    </>
  );
};

export const ModelWarehouseLayerPage = () => {
  const [filterForm] = Form.useForm<LayerFilters>();
  const [filters, setFilters] = useState<LayerFilters>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingLayer, setEditingLayer] = useState<ModelWarehouseLayer | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const currentUserQuery = useCurrentUser();
  const canUpdate = currentUserQuery.data?.permissions.includes('system.configuration.update') ?? false;
  const request = useMemo(() => ({
    search: buildSearch(filters),
    page,
    size,
    sort: 'sortOrder,code',
  }), [filters, page, size]);
  const layersQuery = useModelWarehouseLayers(request);
  const enableMutation = useModelWarehouseLayerCommand('enable');
  const disableMutation = useModelWarehouseLayerCommand('disable');
  const deleteMutation = useDeleteModelWarehouseLayer();

  const search = (next: LayerFilters) => {
    setFilters(next);
    setPage(0);
  };

  const reset = () => {
    filterForm.resetFields();
    search({});
  };

  const toggle = async (layer: ModelWarehouseLayer) => {
    try {
      const mutation = layer.enabled ? disableMutation : enableMutation;
      await mutation.mutateAsync(layer.id);
      messageApi.success(layer.enabled ? '数仓分层已停用' : '数仓分层已启用');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '数仓分层状态操作失败');
    }
  };

  const remove = (layer: ModelWarehouseLayer) => {
    modalApi.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: '删除数仓分层',
      content: `确认删除“${layer.name}（${layer.code}）”吗？删除后不会自动恢复。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteMutation.mutateAsync(layer.id);
          messageApi.success('数仓分层已删除');
        } catch (error) {
          messageApi.error(error instanceof ApiError ? error.message : '删除数仓分层失败');
          throw error;
        }
      },
    });
  };

  const deleteBlockedReason = (layer: ModelWarehouseLayer) => {
    const reasons = [];
    if (layer.referencedModelCount > 0) {
      reasons.push(`${layer.referencedModelCount} 个模型`);
    }
    if (layer.referencedAsInputByLayerCount > 0) {
      reasons.push(`${layer.referencedAsInputByLayerCount} 个分层规范`);
    }
    return reasons.length ? `已被${reasons.join('、')}引用` : '删除';
  };

  const columns: TableProps<ModelWarehouseLayer>['columns'] = [
    {
      title: '分层', dataIndex: 'name', width: 280,
      render: (value: string, layer) => (
        <ManagementListCell
          icon={<ModelWarehouseLayerIcon code={layer.code} color={layer.color} />}
          iconLabel={`数仓分层：${layer.code}`}
          iconTone="slate"
          primary={value}
          secondary={<><ManagementCode value={layer.code} /> {layer.description || ''}</>}
        />
      ),
    },
    {
      title: '规范 / 排序', width: 150,
      render: (_value: unknown, layer) => <ManagementListCell primary={layer.modelCodePrefix ? <ManagementCode value={`${layer.modelCodePrefix}*`} /> : '未配置'} secondary={`排序 ${layer.sortOrder}`} />,
    },
    {
      title: '允许输入',
      key: 'allowedInputLayers',
      width: 300,
      render: (_value, layer) => {
        if (layer.inputLayerPolicy === 'UNRESTRICTED') {
          return <Tag>未限制</Tag>;
        }
        if (layer.allowedInputLayers.length === 0) {
          return <Tag color="warning">无模型分层输入</Tag>;
        }
        return (
          <Space size={[4, 4]} wrap>
            {layer.allowedInputLayers.map((inputLayer) => (
              <Tooltip
                key={inputLayer.id}
                title={inputLayer.enabled ? undefined : '该允许输入分层已停用，可以在编辑时保留或移除'}
              >
                <Tag color={inputLayer.enabled ? inputLayer.color ?? undefined : 'warning'}>
                  {inputLayer.code}
                  {!inputLayer.enabled ? ' · 已停用' : ''}
                </Tag>
              </Tooltip>
            ))}
          </Space>
        );
      },
    },
    {
      title: '引用 / 状态', width: 150,
      render: (_value: unknown, layer) => <ManagementListCell primary={`模型 ${layer.referencedModelCount} · 规范 ${layer.referencedAsInputByLayerCount}`} secondary={<ManagementStatusIndicator label={layer.enabled ? '启用' : '停用'} tone={layer.enabled ? 'success' : 'default'} />} />,
    },
    { title: '更新时间', dataIndex: 'updatedAt', width: 160, render: (value: string) => <ManagementDateTime value={value} /> },
    {
      title: '操作',
      key: 'actions',
      width: 112,
      render: (_value, layer) => canUpdate ? (
        <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">
            <Tooltip title="修改"><Button type="text" icon={<EditOutlined />} aria-label={`修改${layer.name}`} onClick={() => { setEditingLayer(layer); setDrawerOpen(true); }} /></Tooltip>
            <Tooltip title={layer.enabled ? '停用' : '启用'}><Button type="text" icon={layer.enabled ? <PauseCircleOutlined /> : <PlayCircleOutlined />} loading={(layer.enabled ? disableMutation : enableMutation).isPending && (layer.enabled ? disableMutation : enableMutation).variables === layer.id} aria-label={`${layer.enabled ? '停用' : '启用'}${layer.name}`} onClick={() => void toggle(layer)} /></Tooltip>
          </div>
          <Dropdown trigger={['click']} menu={{ items: [
            { key: 'edit', icon: <EditOutlined />, label: '修改', onClick: () => { setEditingLayer(layer); setDrawerOpen(true); } },
            { key: 'lifecycle', icon: layer.enabled ? <PauseCircleOutlined /> : <PlayCircleOutlined />, label: layer.enabled ? '停用' : '启用', onClick: () => void toggle(layer) },
            { type: 'divider' },
            { key: 'delete', icon: <DeleteOutlined />, label: deleteBlockedReason(layer), danger: true, disabled: !layer.deletable, onClick: () => remove(layer) },
          ] satisfies MenuProps['items'] }}><Tooltip title="更多操作"><Button className="management-row-actions-more" type="text" icon={<MoreOutlined />} aria-label={`${layer.name}的更多操作`} /></Tooltip></Dropdown>
        </div>
      ) : '—',
    },
  ];

  return (
    <>
      {messageContext}
      {modalContext}
      <section className="management-workbench">
        <div className="management-filter-strip">
          <Form<LayerFilters>
            autoComplete="off"
            form={filterForm}
            layout="inline"
            className="management-filter-form"
            onFinish={search}
          >
            <Form.Item name="code"><ManagementSearchInput allowClear placeholder="搜索分层编码" /></Form.Item>
            <Form.Item name="name"><Input allowClear placeholder="搜索分层名称" /></Form.Item>
            <Form.Item name="enabled">
              <Select
                allowClear
                placeholder="全部状态"
                options={[
                  { value: true, label: '启用' },
                  { value: false, label: '停用' },
                ]}
              />
            </Form.Item>
          </Form>
          <ManagementFilterActions form={filterForm} appliedFilters={filters} loading={layersQuery.isFetching} onReset={reset} />
        </div>
        <div className="management-results-surface">
          <div className="management-result-toolbar">
          <div className="management-result-title">数仓分层 <span className="management-result-count">共 {layersQuery.data?.totalElements ?? 0} 项</span></div>
          <Space size={4} className="management-result-actions">
            <Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新数仓分层" onClick={() => void layersQuery.refetch()} /></Tooltip>
            {canUpdate && (
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => { setEditingLayer(null); setDrawerOpen(true); }}
              >
                新建
              </Button>
            )}
          </Space>
          </div>
          {layersQuery.isError && (
            <Alert
              showIcon
              type="error"
              title="数仓分层加载失败"
              action={<Button size="small" onClick={() => void layersQuery.refetch()}>重试</Button>}
              className="management-inline-alert"
            />
          )}
          <Table<ModelWarehouseLayer>
          size="small"
          className="management-table"
          rowKey="id"
          columns={columns}
          dataSource={layersQuery.data?.content ?? []}
          loading={layersQuery.isFetching}
          scroll={{ y: '100%' }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: layersQuery.data?.totalElements ?? 0,
            size: 'small',
            hideOnSinglePage: false,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 项`,
          }}
          onChange={(pagination) => {
            setPage((pagination.current ?? 1) - 1);
            setSize(pagination.pageSize ?? 20);
          }}
          />
        </div>
      </section>
      <LayerDrawer
        open={drawerOpen}
        layer={editingLayer}
        onClose={() => { setDrawerOpen(false); setEditingLayer(null); }}
      />
    </>
  );
};
