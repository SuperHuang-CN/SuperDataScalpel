import {
  ApiOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  EllipsisOutlined,
  PlusOutlined,
  ReloadOutlined,
  RobotOutlined,
  StopOutlined,
} from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import { Button, Dropdown, Form, Modal, Table, Tag, Tooltip, message } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { ManagementCode, ManagementDateTime, ManagementListCell } from '../../../shared/components/ManagementListCells';
import { useCurrentUser } from '../../system';
import { LlmModelDrawer } from '../components/LlmModelDrawer';
import { useDeleteLlmModel, useLlmModelCommand, useLlmModels } from '../hooks/useAssistant';
import type { LlmModelConfiguration, LlmModelTestStatus } from '../model/assistant';

interface ModelFilters { name?: string }

const statusLabels: Record<LlmModelTestStatus, string> = {
  UNTESTED: '未测试', AVAILABLE: '可用', UNAVAILABLE: '不可达', INCOMPATIBLE: '不兼容',
};

const statusColors: Record<LlmModelTestStatus, string> = {
  UNTESTED: 'default', AVAILABLE: 'success', UNAVAILABLE: 'error', INCOMPATIBLE: 'warning',
};

const escapeDslText = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

export const LlmModelManagementPage = () => {
  const [form] = Form.useForm<ModelFilters>();
  const [filters, setFilters] = useState<ModelFilters>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [editing, setEditing] = useState<LlmModelConfiguration | null | undefined>(undefined);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const currentUserQuery = useCurrentUser();
  const canUpdate = currentUserQuery.data?.permissions.includes('system.configuration.update') ?? false;
  const request = useMemo(() => ({
    search: filters.name?.trim() ? `name:*"${escapeDslText(filters.name.trim())}"*` : undefined,
    page,
    size,
    sort: '-defaultModel,-enabled,name',
  }), [filters.name, page, size]);
  const modelsQuery = useLlmModels(request);
  const commandMutation = useLlmModelCommand();
  const deleteMutation = useDeleteLlmModel();

  const showError = (error: unknown, fallback: string) => messageApi.error(error instanceof ApiError ? error.message : fallback);

  const command = async (model: LlmModelConfiguration, action: 'test' | 'enable' | 'disable' | 'set-default') => {
    try {
      const result = await commandMutation.mutateAsync({ id: model.id, command: action });
      const success = action === 'test'
        ? result.testMessage ?? '兼容性测试已完成'
        : action === 'enable' ? 'AI 模型已启用'
          : action === 'disable' ? 'AI 模型已停用' : '默认 AI 模型已更新';
      messageApi.success(success);
    } catch (error) {
      showError(error, action === 'test' ? 'AI 模型兼容性测试失败' : '更新 AI 模型状态失败');
    }
  };

  const remove = (model: LlmModelConfiguration) => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: '删除 AI 模型配置',
    content: `确认删除“${model.name}”吗？已经产生助手运行记录的模型只能停用。`,
    okText: '删除', cancelText: '取消', okButtonProps: { danger: true },
    onOk: async () => {
      try { await deleteMutation.mutateAsync(model.id); messageApi.success('AI 模型配置已删除'); }
      catch (error) { showError(error, '删除 AI 模型配置失败'); throw error; }
    },
  });

  const columns: TableProps<LlmModelConfiguration>['columns'] = [
    {
      title: '模型', dataIndex: 'name', width: 260,
      render: (value: string, model) => <ManagementListCell icon={<RobotOutlined />} iconTone="violet" primary={value} secondary={model.modelName} />,
    },
    {
      title: '服务地址', dataIndex: 'baseUrl', width: 330,
      render: (value: string, model) => <ManagementListCell primary={<ManagementCode value={value} />} secondary={`${model.protocol === 'OPENAI_COMPATIBLE' ? 'OpenAI Compatible' : model.protocol} · ${model.apiKeyConfigured ? '已配置 Key' : '无 Key'}`} />,
    },
    {
      title: '可用状态', width: 180,
      render: (_: unknown, model) => {
        const detail = model.testMessage || '尚未执行兼容性测试';
        const statusTag = <Tag color={statusColors[model.testStatus]}>{statusLabels[model.testStatus]}</Tag>;
        return <ManagementListCell
          primary={<>
            {model.testStatus === 'AVAILABLE'
              ? statusTag
              : <Tooltip title={detail}><span aria-label={`${statusLabels[model.testStatus]}：${detail}`}>{statusTag}</span></Tooltip>}
            {model.defaultModel && <Tag color="blue">默认</Tag>}
            {model.enabled && !model.defaultModel && <Tag color="cyan">已启用</Tag>}
          </>}
          secondary={detail}
        />;
      },
    },
    { title: '最近测试 / 更新', width: 190, render: (_: unknown, model) => <ManagementListCell primary={<ManagementDateTime value={model.lastTestedAt} />} secondary={<ManagementDateTime value={model.updatedAt} />} /> },
    {
      title: '操作', key: 'action', width: 112,
      render: (_: unknown, model) => {
        if (!canUpdate) return '—';
        const items: NonNullable<MenuProps['items']> = [
          { key: 'edit', icon: <EditOutlined />, label: '修改配置', onClick: () => setEditing(model) },
          model.enabled
            ? { key: 'disable', icon: <StopOutlined />, label: '停用', onClick: () => void command(model, 'disable') }
            : { key: 'enable', icon: <CheckCircleOutlined />, label: '启用', disabled: model.testStatus !== 'AVAILABLE', onClick: () => void command(model, 'enable') },
          { key: 'default', icon: <CheckCircleOutlined />, label: '设为默认', disabled: !model.enabled || model.defaultModel, onClick: () => void command(model, 'set-default') },
          { type: 'divider' },
          { key: 'delete', danger: true, icon: <DeleteOutlined />, label: '删除', disabled: model.enabled, onClick: () => remove(model) },
        ];
        return <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">
            <Tooltip title="兼容性测试"><Button type="text" size="small" icon={<ApiOutlined />} aria-label={`测试${model.name}的工具调用兼容性`} loading={commandMutation.isPending && commandMutation.variables?.id === model.id && commandMutation.variables.command === 'test'} onClick={() => void command(model, 'test')} /></Tooltip>
            <Tooltip title="修改配置"><Button type="text" size="small" icon={<EditOutlined />} aria-label={`修改${model.name}`} onClick={() => setEditing(model)} /></Tooltip>
          </div>
          <Dropdown menu={{ items }} trigger={['click']}><Tooltip title="更多操作"><Button className="management-row-actions-more" type="text" icon={<EllipsisOutlined />} aria-label={`${model.name}的更多操作`} /></Tooltip></Dropdown>
        </div>;
      },
    },
  ];

  return <>
    {messageContext}{modalContext}
    <section className="management-workbench">
      <div className="management-filter-strip">
        <Form<ModelFilters> autoComplete="off" form={form} layout="inline" className="management-filter-form" onFinish={(values) => { setFilters(values); setPage(0); }}>
          <Form.Item name="name"><ManagementSearchInput allowClear placeholder="搜索模型名称" /></Form.Item>
        </Form>
        <ManagementFilterActions form={form} appliedFilters={filters} loading={modelsQuery.isFetching} onReset={() => { form.resetFields(); setFilters({}); setPage(0); }} />
      </div>
      <div className="management-results-surface">
        <div className="management-result-toolbar">
          <span className="management-result-title">AI 模型 <span className="management-result-count">共 {modelsQuery.data?.totalElements ?? 0} 项</span></span>
          <div className="management-result-actions">
            <Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新 AI 模型列表" onClick={() => void modelsQuery.refetch()} /></Tooltip>
            {canUpdate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing(null)}>注册模型</Button>}
          </div>
        </div>
        <Table<LlmModelConfiguration>
          size="small" className="management-table" rowKey="id" columns={columns}
          dataSource={modelsQuery.data?.content ?? []} loading={modelsQuery.isFetching} scroll={{ y: '100%' }}
          pagination={{ current: page + 1, pageSize: size, total: modelsQuery.data?.totalElements ?? 0, size: 'small', position: ['bottomRight'], hideOnSinglePage: false, showSizeChanger: true, showTotal: (total) => `共 ${total} 项` }}
          onChange={(pagination) => { setPage((pagination.current ?? 1) - 1); setSize(pagination.pageSize ?? 20); }}
        />
      </div>
    </section>
    <LlmModelDrawer open={editing !== undefined} model={editing ?? null} onClose={() => setEditing(undefined)} />
  </>;
};
