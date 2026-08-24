import {
  ClearOutlined,
  ColumnWidthOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeInvisibleOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import { Alert, Button, Dropdown, Form, Input, Modal, Select, Space, Table, Tooltip, message } from 'antd';
import { useMemo, useState, type ReactNode } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ManagementCode, ManagementDateTime, ManagementListCell } from '../../../shared/components/ManagementListCells';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { useCurrentUser } from '../../system';
import { MaskingRuleDrawer } from '../components/MaskingRuleDrawer';
import { useDeleteMaskingRule, useMaskingRules } from '../hooks/useMaskingRules';
import {
  maskingStrategyLabels,
  type DataMaskingRule,
  type MaskingStrategy,
} from '../model/maskingRule';

interface MaskingRuleFilters {
  code?: string;
  name?: string;
  strategy?: MaskingStrategy;
}

interface MaskingRuleDrawerState {
  rule: DataMaskingRule | null;
  readOnly: boolean;
}

const maskingStrategyVisuals: Record<MaskingStrategy, {
  icon: ReactNode;
  tone: 'violet' | 'cyan' | 'orange' | 'slate';
}> = {
  PARTIAL_MASK: { icon: <EyeInvisibleOutlined />, tone: 'violet' },
  POSITION_MASK: { icon: <EyeInvisibleOutlined />, tone: 'cyan' },
  KEEP_LENGTH_MASK: { icon: <ColumnWidthOutlined />, tone: 'cyan' },
  FIXED_VALUE: { icon: <SwapOutlined />, tone: 'orange' },
  NULLIFY: { icon: <ClearOutlined />, tone: 'slate' },
};

const escapeDsl = (value: string) => value.replaceAll('\\', '\\\\').replaceAll('"', '\\"');

const buildSearch = (filters: MaskingRuleFilters) => {
  const conditions = [
    filters.code?.trim()
      ? `code:*"${escapeDsl(filters.code.trim().toLowerCase())}"*`
      : undefined,
    filters.name?.trim() ? `name:*"${escapeDsl(filters.name.trim())}"*` : undefined,
    filters.strategy ? `strategy:"${filters.strategy}"` : undefined,
  ].filter((condition): condition is string => Boolean(condition));
  return conditions.length ? conditions.join(' AND ') : undefined;
};

export const MaskingRulePage = () => {
  const [form] = Form.useForm<MaskingRuleFilters>();
  const [filters, setFilters] = useState<MaskingRuleFilters>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [drawerState, setDrawerState] = useState<MaskingRuleDrawerState | null>(null);
  const [messageApi, contextHolder] = message.useMessage();
  const currentUser = useCurrentUser();
  const canManage = currentUser.data?.permissions.includes('task.update') ?? false;
  const request = useMemo(() => ({
    search: buildSearch(filters),
    page,
    size,
    sort: '-updatedAt,code',
  }), [filters, page, size]);
  const rulesQuery = useMaskingRules(request);
  const deleteMutation = useDeleteMaskingRule();

  const reset = () => {
    form.resetFields();
    setFilters({});
    setPage(0);
  };

  const remove = async (rule: DataMaskingRule) => {
    try {
      await deleteMutation.mutateAsync(rule.id);
      messageApi.success('脱敏规则已删除');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '删除脱敏规则失败');
    }
  };

  const columns: TableProps<DataMaskingRule>['columns'] = [
    {
      title: '规则', dataIndex: 'name', width: 300,
      render: (value: string, rule) => {
        const strategyLabel = maskingStrategyLabels[rule.strategy];
        const visual = maskingStrategyVisuals[rule.strategy];
        return (
          <ManagementListCell
            icon={<Tooltip title={strategyLabel}>{visual.icon}</Tooltip>}
            iconLabel={`脱敏策略：${strategyLabel}`}
            iconTone={visual.tone}
            primary={value}
            secondary={<ManagementCode value={rule.code} />}
          />
        );
      },
    },
    {
      title: '说明',
      dataIndex: 'description',
      render: (value: string | null) => (
        <Tooltip title={value || undefined} placement="topLeft">
          <span className="masking-rule-description-text">{value || '—'}</span>
        </Tooltip>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 160,
      render: (value: string) => <ManagementDateTime value={value} />,
    },
    {
      title: '操作',
      key: 'actions',
      width: 112,
      render: (_: unknown, rule: DataMaskingRule) => (
        <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">{canManage && <Tooltip title="修改规则"><Button type="text" icon={<EditOutlined />} aria-label={`修改脱敏规则${rule.name}`} onClick={() => setDrawerState({ rule, readOnly: false })} /></Tooltip>}</div>
          <Dropdown menu={{ items: [
            { key: 'view', label: '查看规则', onClick: () => setDrawerState({ rule, readOnly: true }) },
            ...(canManage ? [{ key: 'edit', icon: <EditOutlined />, label: '修改', onClick: () => setDrawerState({ rule, readOnly: false }) }, { type: 'divider' as const }, { key: 'delete', icon: <DeleteOutlined />, label: '删除', danger: true, onClick: () => Modal.confirm({ rootClassName: 'business-overlay business-modal-overlay', title: '删除脱敏规则', content: `确认删除“${rule.name}”吗？已有 Canvas 节点中的配置不会变化。`, okText: '删除', okButtonProps: { danger: true }, cancelText: '取消', onOk: () => remove(rule) }) }] : []),
          ] satisfies MenuProps['items'] }}><Tooltip title="更多操作"><Button className="management-row-actions-more" type="text" icon={<MoreOutlined />} aria-label={`${rule.name}的更多操作`} /></Tooltip></Dropdown>
        </div>
      ),
    },
  ];

  return (
    <div className="management-page masking-rule-page">
      {contextHolder}
      <section className="management-workbench">
        <div className="management-filter-strip">
          <Form<MaskingRuleFilters>
            autoComplete="off"
            form={form}
            layout="inline"
            onFinish={(values) => {
              setFilters(values);
              setPage(0);
            }}
          >
            <Form.Item name="code"><ManagementSearchInput allowClear placeholder="搜索规则编码" /></Form.Item>
            <Form.Item name="name"><Input allowClear placeholder="搜索规则名称" /></Form.Item>
            <Form.Item name="strategy">
              <Select
                allowClear
                placeholder="全部策略"
                style={{ width: 140 }}
                options={(Object.entries(maskingStrategyLabels) as [MaskingStrategy, string][])
                  .map(([value, label]) => ({ value, label }))}
              />
            </Form.Item>
          </Form>
          <ManagementFilterActions form={form} appliedFilters={filters} loading={rulesQuery.isFetching} onReset={reset} />
        </div>
        <div className="management-results-surface">
          <div className="management-result-toolbar">
          <div className="management-result-title">脱敏规则 <span className="management-result-count">共 {rulesQuery.data?.totalElements ?? 0} 项</span></div>
          <Space size={4} className="management-result-actions">
            <Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新脱敏规则" onClick={() => void rulesQuery.refetch()} /></Tooltip>
            {canManage && (
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => setDrawerState({ rule: null, readOnly: false })}
              >
                新建规则
              </Button>
            )}
          </Space>
          </div>
          {rulesQuery.error && (
            <Alert
              type="error"
              showIcon
              title="脱敏规则加载失败"
              description={rulesQuery.error instanceof Error ? rulesQuery.error.message : undefined}
              action={<Button onClick={() => void rulesQuery.refetch()}>重试</Button>}
            />
          )}
          <Table<DataMaskingRule>
          className="management-table"
          size="small"
          rowKey="id"
          columns={columns}
          dataSource={rulesQuery.data?.content ?? []}
          loading={rulesQuery.isFetching}
          scroll={{ y: '100%' }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: rulesQuery.data?.totalElements ?? 0,
            showSizeChanger: true,
            hideOnSinglePage: false,
            showTotal: (total) => `共 ${total} 条`,
            onChange: (nextPage, nextSize) => {
              setPage(nextSize !== size ? 0 : nextPage - 1);
              setSize(nextSize);
            },
          }}
          />
        </div>
      </section>
      <MaskingRuleDrawer
        open={drawerState !== null}
        rule={drawerState?.rule ?? null}
        readOnly={drawerState?.readOnly}
        onClose={() => setDrawerState(null)}
      />
    </div>
  );
};
