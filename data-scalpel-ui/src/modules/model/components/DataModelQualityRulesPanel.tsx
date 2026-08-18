import {
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  HistoryOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Collapse,
  Dropdown,
  Empty,
  Modal,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { QualityFailureSampleDrawer } from '../../task';
import {
  useAcceptModelQualityRuleSuggestions,
  useCreateModelQualityRule,
  useDeleteModelQualityRule,
  useModelQualityOverview,
  useModelQualityRuleCommand,
  useModelQualityRules,
  useModelQualityRuleSuggestions,
  useUpdateModelQualityRule,
} from '../hooks/useModelQualityRules';
import type { DataModelField } from '../model/dataModel';
import type {
  ModelQualityOverviewMetric,
  ModelQualityOverviewRuleResult,
} from '../model/modelQualityOverview';
import {
  formatPatternPresetLabels,
  modelQualityRuleSeverityLabels,
  modelQualityRuleTypeLabels,
  qualityConditionOperatorLabels,
  qualityFieldComparisonOperatorLabels,
  type CreateModelQualityRuleRequest,
  type ModelQualityRule,
  type ModelQualityRuleDefinition,
  type ModelQualityRuleSuggestion,
} from '../model/modelQualityRule';
import { ModelQualityRuleDrawer } from './ModelQualityRuleDrawer';
import { ModelQualityRuleSuggestionsModal } from './ModelQualityRuleSuggestionsModal';

interface DataModelQualityRulesPanelProps {
  modelId: string;
  fields: DataModelField[];
  canUpdate: boolean;
  canViewTasks: boolean;
}

interface SelectedSample {
  runId: string;
  ruleId: string;
  ruleName: string;
}

const severityColors = { CRITICAL: 'error', MAJOR: 'warning', MINOR: 'default' } as const;

const runStatusLabels: Record<string, string> = {
  QUEUED: '排队中', RUNNING: '运行中', CANCEL_REQUESTED: '取消中', STOP_REQUESTED: '停止中',
  STOPPED: '已停止', SUCCESS: '成功', FAILED: '技术失败', TIMED_OUT: '超时',
  CANCELLED: '已取消', SKIPPED: '已跳过',
};

const runTriggerLabels = { MANUAL: '手动触发', SCHEDULED: '定时触发' } as const;

const toleranceText = (definition: ModelQualityRuleDefinition) => {
  if (definition.type === 'ROW_COUNT') return `至少 ${definition.minimumRowCount} 行`;
  if (definition.type === 'FRESHNESS') return `最大延迟 ${definition.maximumDelayMinutes} 分钟`;
  if (definition.type === 'VALUE_RANGE') {
    const left = definition.minimum ? `${definition.minimumInclusive ? '[' : '('}${definition.minimum}` : '(-∞';
    const right = definition.maximum ? `${definition.maximum}${definition.maximumInclusive ? ']' : ')'}` : '+∞)';
    return `${left}, ${right}`;
  }
  if (definition.type === 'STRING_LENGTH') return `${definition.minimumLength ?? 0} ～ ${definition.maximumLength ?? '不限'} 字符`;
  if (definition.type === 'FORMAT_PATTERN') {
    return definition.patternKind === 'PRESET'
      ? formatPatternPresetLabels[definition.preset as keyof typeof formatPatternPresetLabels]
      : `正则：${definition.regex}`;
  }
  const tolerance = definition.tolerance;
  return `允许 ≤ ${tolerance.value}${tolerance.metric === 'PERCENT' ? '%' : ' 条'}`;
};

const fieldCode = (rule: ModelQualityRule, fieldId: string) => (
  rule.fields.find((field) => field.id === fieldId)?.code ?? `已删除字段 ${fieldId}`
);

const checkTargetText = (rule: ModelQualityRule) => {
  const definition = rule.definition;
  if (definition.type === 'ROW_COUNT') return '模型行数';
  if (definition.type === 'CONDITIONAL_NOT_NULL') {
    const values = definition.condition.values.length ? ` ${definition.condition.values.join('、')}` : '';
    return `${fieldCode(rule, definition.condition.fieldId)} ${qualityConditionOperatorLabels[definition.condition.operator]}${values} → ${fieldCode(rule, definition.targetFieldId)} 必填`;
  }
  if (definition.type === 'FIELD_COMPARISON') {
    return `${fieldCode(rule, definition.leftFieldId)} ${qualityFieldComparisonOperatorLabels[definition.operator]} ${fieldCode(rule, definition.rightFieldId)}`;
  }
  if (definition.type === 'REFERENCE_EXISTS') {
    const targetName = rule.referenceTarget
      ? `${rule.referenceTarget.code}`
      : `已删除模型 ${definition.targetModelId}`;
    const sourceIds = definition.mappings.map((mapping) => fieldCode(rule, mapping.sourceFieldId)).join(' + ');
    const targetIds = definition.mappings.map((mapping) => (
      rule.referenceTarget?.fields.find((field) => field.id === mapping.targetFieldId)?.code
        ?? `已删除字段 ${mapping.targetFieldId}`
    )).join(' + ');
    return `${sourceIds} → ${targetName}.${targetIds}`;
  }
  return rule.fields.map((field) => field.code).join(' + ');
};

const dateTime = (value: string | null | undefined) => value
  ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'medium', hour12: false }).format(new Date(value))
  : '—';

const relativeTime = (value: string) => {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 1000));
  if (seconds < 60) return '刚刚';
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分钟前`;
  if (seconds < 86_400) return `${Math.floor(seconds / 3600)} 小时前`;
  return `${Math.floor(seconds / 86_400)} 天前`;
};

const metricText = (metric: ModelQualityOverviewMetric | null) => {
  if (!metric) return '—';
  if (metric.kind === 'VIOLATION') {
    const tolerance = metric.toleranceMetric === 'COUNT'
      ? `${metric.toleranceValue} 条`
      : `${metric.toleranceValue}%`;
    return `异常 ${metric.violationCount} 条（${metric.violationPercent}%），允许 ${tolerance}`;
  }
  if (metric.kind === 'ROW_COUNT') return `实际 ${metric.actualRows} 行，至少 ${metric.minimumRows} 行`;
  return metric.maximumValue
    ? `最大时间 ${dateTime(metric.maximumValue)}，延迟 ${metric.actualDelayMinutes ?? '—'} 分钟，允许 ${metric.maximumDelayMinutes} 分钟`
    : `没有有效时间值，允许延迟 ${metric.maximumDelayMinutes} 分钟`;
};

const resultTag = (result: ModelQualityOverviewRuleResult) => {
  if (result.state === 'PASSED') return <Tag color="success">通过</Tag>;
  if (result.state === 'FAILED') return <Tag color="error">不通过</Tag>;
  return <Tag color="default">已跳过</Tag>;
};

const errorText = (error: unknown, fallback: string) => error instanceof ApiError ? error.message : fallback;

export const DataModelQualityRulesPanel = ({
  modelId,
  fields,
  canUpdate,
  canViewTasks,
}: DataModelQualityRulesPanelProps) => {
  const navigate = useNavigate();
  const [editingRule, setEditingRule] = useState<ModelQualityRule | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [suggestionsOpen, setSuggestionsOpen] = useState(false);
  const [selectedSample, setSelectedSample] = useState<SelectedSample | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const rulesQuery = useModelQualityRules(modelId);
  const overviewQuery = useModelQualityOverview(modelId);
  const suggestionsQuery = useModelQualityRuleSuggestions(modelId, suggestionsOpen);
  const createMutation = useCreateModelQualityRule();
  const updateMutation = useUpdateModelQualityRule();
  const enableMutation = useModelQualityRuleCommand('enable');
  const disableMutation = useModelQualityRuleCommand('disable');
  const deleteMutation = useDeleteModelQualityRule(modelId);
  const acceptMutation = useAcceptModelQualityRuleSuggestions();
  const commandLoading = enableMutation.isPending || disableMutation.isPending;
  const overview = overviewQuery.data;
  const effective = overview?.latestEffectiveResult;
  const currentRuleIds = useMemo(
    () => new Set((rulesQuery.data ?? []).map((rule) => rule.id)),
    [rulesQuery.data],
  );
  const resultByRuleId = useMemo(
    () => new Map((overview?.ruleResults ?? []).map((result) => [result.ruleId, result])),
    [overview?.ruleResults],
  );
  const historicalResults = rulesQuery.data && overview?.resultDetailStatus === 'AVAILABLE'
    ? overview.ruleResults.filter((result) => !currentRuleIds.has(result.ruleId))
    : [];

  const save = async (request: CreateModelQualityRuleRequest) => {
    try {
      if (editingRule) {
        await updateMutation.mutateAsync({
          id: editingRule.id,
          request: {
            name: request.name,
            description: request.description,
            severity: request.severity,
            definition: request.definition,
          },
        });
      } else {
        await createMutation.mutateAsync({ modelId, request });
      }
      messageApi.success(editingRule ? '质量规则已修改' : '质量规则已新增');
      setDrawerOpen(false);
      setEditingRule(null);
    } catch (error) {
      messageApi.error(errorText(error, '质量规则保存失败'));
    }
  };

  const toggle = async (rule: ModelQualityRule, enabled: boolean) => {
    try {
      await (enabled ? enableMutation : disableMutation).mutateAsync(rule.id);
      messageApi.success(enabled ? '质量规则已启用' : '质量规则已停用');
    } catch (error) {
      messageApi.error(errorText(error, '质量规则状态修改失败'));
    }
  };

  const remove = (rule: ModelQualityRule) => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: '删除质量规则',
    content: `确认删除“${rule.name}”吗？`,
    okText: '删除',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteMutation.mutateAsync(rule.id);
        messageApi.success('质量规则已删除');
      } catch (error) {
        messageApi.error(errorText(error, '质量规则删除失败'));
        throw error;
      }
    },
  });

  const accept = async (suggestions: ModelQualityRuleSuggestion[]) => {
    try {
      await acceptMutation.mutateAsync({
        modelId,
        suggestionKeys: suggestions.map((item) => item.key),
      });
      messageApi.success(`已采纳 ${suggestions.length} 条建议，规则默认停用`);
      setSuggestionsOpen(false);
    } catch (error) {
      messageApi.error(errorText(error, '规则建议采纳失败'));
    }
  };

  const openRun = (taskId: string, runId: string) => {
    navigate(`/task/${taskId}?tab=runs&runId=${runId}`);
  };

  const resultContent = (result: ModelQualityOverviewRuleResult | undefined, changed: boolean) => {
    if (!effective) return <Typography.Text type="secondary">未检查</Typography.Text>;
    if (overview?.resultDetailStatus !== 'AVAILABLE') {
      return <Typography.Text type="secondary">明细暂不可用</Typography.Text>;
    }
    if (!result) return <Typography.Text type="secondary">未检查</Typography.Text>;
    return (
      <Space orientation="vertical" size={2} className="quality-rule-result-cell">
        <Space size={4} wrap>
          {resultTag(result)}
          {changed && <Tooltip title="当前规则在本次质检快照形成后发生过修改，结果仅代表当时配置。"><Tag color="warning">配置已变更</Tag></Tooltip>}
          {canViewTasks && result.sample?.status === 'AVAILABLE' && (
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => setSelectedSample({ runId: effective.runId, ruleId: result.ruleId, ruleName: result.ruleName })}
            >
              {result.sample.sampledRows} 条样本
            </Button>
          )}
        </Space>
        <Typography.Text type="secondary" ellipsis={{ tooltip: result.skipReason ?? metricText(result.metric) }}>
          {result.skipReason ?? metricText(result.metric)}
        </Typography.Text>
        {result.durationMs !== null && <Typography.Text type="secondary">耗时 {result.durationMs} ms</Typography.Text>}
      </Space>
    );
  };

  const historicalColumns: TableProps<ModelQualityOverviewRuleResult>['columns'] = [
    {
      title: '历史规则', dataIndex: 'ruleName', width: 220,
      render: (name: string, result) => (
        <Space orientation="vertical" size={1}>
          <Typography.Text>{name}</Typography.Text>
          <Typography.Text type="secondary">{modelQualityRuleTypeLabels[result.ruleType]}</Typography.Text>
        </Space>
      ),
    },
    { title: '最近结果', width: 120, render: (_, result) => resultTag(result) },
    {
      title: '指标', render: (_, result) => (
        <Typography.Text ellipsis={{ tooltip: result.skipReason ?? metricText(result.metric) }}>
          {result.skipReason ?? metricText(result.metric)}
        </Typography.Text>
      ),
    },
    { title: '耗时', dataIndex: 'durationMs', width: 90, align: 'right', render: (value: number | null) => value === null ? '—' : `${value} ms` },
    ...(canViewTasks ? [{
      title: '样本', width: 100,
      render: (_: unknown, result: ModelQualityOverviewRuleResult) => result.sample?.status === 'AVAILABLE' && effective
        ? <Button type="link" size="small" onClick={() => setSelectedSample({ runId: effective.runId, ruleId: result.ruleId, ruleName: result.ruleName })}>查看</Button>
        : '—',
    }] : []),
  ];

  const latestRunNeedsNotice = overview?.latestRun
    && overview.latestRun.runId !== overview.latestEffectiveResult?.runId;
  const latestRunActive = overview?.latestRun
    && ['QUEUED', 'RUNNING', 'CANCEL_REQUESTED', 'STOP_REQUESTED'].includes(overview.latestRun.status);
  const latestRunMessage = overview?.latestRun
    ? overview.latestRun.executionError?.message ?? overview.latestRun.message
    : null;

  return (
    <div className="model-detail-tab-panel model-quality-rules-panel">
      {messageContext}
      {modalContext}
      {!canUpdate && <Alert banner showIcon type="info" title="当前账号可以查看质量规则，但没有管理权限。" />}
      {overviewQuery.error && (
        <Alert type="error" showIcon title="质量概览加载失败" action={<Button onClick={() => void overviewQuery.refetch()}>重试</Button>} />
      )}
      {rulesQuery.error && (
        <Alert type="error" showIcon title="质量规则加载失败" action={<Button onClick={() => void rulesQuery.refetch()}>重试</Button>} />
      )}
      <div className={`model-quality-overview${overviewQuery.isPending ? ' is-loading' : ''}`}>
        <div className="model-quality-overview-main">
          <div className={`model-quality-conclusion is-${effective?.conclusion?.toLowerCase() ?? 'empty'}`}>
            <span className="model-quality-overview-label">最近质量结论</span>
            <strong>{overviewQuery.isPending
              ? '加载中'
              : effective ? (effective.conclusion === 'PASSED' ? '通过' : '不通过') : '尚未检查'}</strong>
            <span>{overviewQuery.isPending
              ? '正在读取最近质检结果'
              : effective ? `${relativeTime(effective.endedAt)} · ${dateTime(effective.endedAt)}` : '首次成功质检后将在此展示结果'}</span>
          </div>
          <div className="model-quality-overview-metrics">
            <div><span>检查行数</span><strong>{effective?.checkedRows?.toLocaleString('zh-CN') ?? '—'}</strong></div>
            <div><span>通过规则</span><strong>{effective?.passedRules ?? '—'}</strong></div>
            <div><span>失败规则</span><strong className={effective?.failedRules ? 'is-danger' : undefined}>{effective?.failedRules ?? '—'}</strong></div>
            <div><span>跳过规则</span><strong>{effective?.skippedRules ?? '—'}</strong></div>
          </div>
          {effective && (
            <div className="model-quality-overview-source">
              <Typography.Text type="secondary">来源：{effective.taskName}</Typography.Text>
              {canViewTasks && <Button type="link" size="small" onClick={() => openRun(effective.taskId, effective.runId)}>查看运行</Button>}
            </div>
          )}
        </div>
        {latestRunNeedsNotice && overview?.latestRun && (
          <Alert
            showIcon
            type={latestRunActive ? 'info' : overview.latestRun.status === 'FAILED' ? 'error' : 'warning'}
            title={`最近一次质检${runStatusLabels[overview.latestRun.status] ?? overview.latestRun.status}`}
            description={`${overview.latestRun.taskName} · ${runTriggerLabels[overview.latestRun.triggerType]} · ${dateTime(overview.latestRun.endedAt ?? overview.latestRun.queuedAt)}${latestRunMessage ? ` · ${latestRunMessage}` : ''}`}
            action={canViewTasks ? <Button size="small" onClick={() => openRun(overview.latestRun!.taskId, overview.latestRun!.runId)}>查看运行</Button> : undefined}
          />
        )}
        {effective && overview?.resultDetailStatus !== 'AVAILABLE' && (
          <Alert
            showIcon
            type={overview?.resultDetailStatus === 'INVALID' ? 'error' : 'warning'}
            title="最近质量汇总可用，但规则明细暂不可用"
            description={overview?.resultDetailMessage}
            action={<Button size="small" onClick={() => void overviewQuery.refetch()}>重试</Button>}
          />
        )}
        {historicalResults.length > 0 && (
          <Collapse
            size="small"
            items={[{
              key: 'history',
              label: <Space><HistoryOutlined /><span>本次运行中已删除的历史规则</span><Tag>{historicalResults.length}</Tag></Space>,
              children: (
                <Table<ModelQualityOverviewRuleResult>
                  size="small"
                  rowKey="ruleId"
                  pagination={false}
                  columns={historicalColumns}
                  dataSource={historicalResults}
                  scroll={{ y: 240 }}
                />
              ),
            }]}
          />
        )}
      </div>
      <div className="model-tab-toolbar">
        <Space><Typography.Text strong>质量规则</Typography.Text><Tag>{rulesQuery.data?.length ?? 0} 条</Tag></Space>
        <Space size={4}>
          <Tooltip title="刷新质量概览和规则">
            <Button
              icon={<ReloadOutlined />}
              aria-label="刷新质量概览和规则"
              loading={rulesQuery.isFetching || overviewQuery.isFetching}
              onClick={() => { void rulesQuery.refetch(); void overviewQuery.refetch(); }}
            />
          </Tooltip>
          {canUpdate && <Button icon={<ThunderboltOutlined />} onClick={() => setSuggestionsOpen(true)}>规则建议</Button>}
          {canUpdate && <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingRule(null); setDrawerOpen(true); }}>新增规则</Button>}
        </Space>
      </div>
      <Table<ModelQualityRule>
        className="management-table model-quality-rules-table"
        size="small"
        rowKey="id"
        dataSource={rulesQuery.data ?? []}
        loading={rulesQuery.isFetching}
        locale={{ emptyText: <Empty description="暂未配置质量规则" /> }}
        scroll={{ x: 1_150, y: '100%' }}
        pagination={false}
        columns={[
          {
            title: '规则', dataIndex: 'name', width: 210,
            render: (value, rule) => (
              <Space orientation="vertical" size={1}>
                <Typography.Text strong>{value}</Typography.Text>
                <Space size={4} wrap>
                  <Tag>{modelQualityRuleTypeLabels[rule.ruleType]}</Tag>
                  <Tag color={severityColors[rule.severity]}>{modelQualityRuleSeverityLabels[rule.severity]}</Tag>
                </Space>
                {rule.description && <Typography.Text type="secondary" ellipsis={{ tooltip: rule.description }}>{rule.description}</Typography.Text>}
              </Space>
            ),
          },
          {
            title: '检查对象', width: 220,
            render: (_, rule) => <Typography.Text ellipsis={{ tooltip: checkTargetText(rule) }}>{checkTargetText(rule)}</Typography.Text>,
          },
          {
            title: '参数', width: 170,
            render: (_, rule) => <Typography.Text ellipsis={{ tooltip: toleranceText(rule.definition) }}>{toleranceText(rule.definition)}</Typography.Text>,
          },
          {
            title: '当前状态', width: 145,
            render: (_, rule) => (
              <Space size={6}>
                <Switch size="small" disabled={!canUpdate || commandLoading || Boolean(rule.invalidReason)} checked={rule.enabled} onChange={(checked) => void toggle(rule, checked)} />
                {rule.invalidReason
                  ? <Tooltip title={rule.invalidReason}><Tag color="error">已失效</Tag></Tooltip>
                  : (rule.enabled ? <Tag color="success">已启用</Tag> : <Tag>已停用</Tag>)}
              </Space>
            ),
          },
          {
            title: effective ? `最近结果 · ${dateTime(effective.endedAt)}` : '最近结果',
            width: 320,
            render: (_, rule) => resultContent(
              resultByRuleId.get(rule.id),
              Boolean(effective && new Date(rule.updatedAt).getTime() > new Date(effective.ruleSnapshotAt).getTime()),
            ),
          },
          ...(canUpdate ? [{
            title: '操作', width: 64, fixed: 'right' as const,
            render: (_: unknown, rule: ModelQualityRule) => (
              <Dropdown
                trigger={['click']}
                menu={{
                  items: [
                    { key: 'edit', label: '修改', icon: <EditOutlined /> },
                    { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true },
                  ],
                  onClick: ({ key }) => {
                    if (key === 'edit') { setEditingRule(rule); setDrawerOpen(true); }
                    if (key === 'delete') remove(rule);
                  },
                }}
              >
                <Button type="text" icon={<MoreOutlined />} aria-label={`管理质量规则 ${rule.name}`} />
              </Dropdown>
            ),
          }] : []),
        ]}
      />
      <ModelQualityRuleDrawer
        open={drawerOpen}
        rule={editingRule}
        fields={fields}
        submitting={createMutation.isPending || updateMutation.isPending}
        onClose={() => { setDrawerOpen(false); setEditingRule(null); }}
        onSubmit={(request) => void save(request)}
      />
      <ModelQualityRuleSuggestionsModal
        open={suggestionsOpen}
        suggestions={suggestionsQuery.data ?? []}
        loading={suggestionsQuery.isFetching}
        submitting={acceptMutation.isPending}
        onClose={() => setSuggestionsOpen(false)}
        onAccept={(suggestions) => void accept(suggestions)}
      />
      <QualityFailureSampleDrawer
        open={Boolean(selectedSample)}
        runId={selectedSample?.runId ?? null}
        ruleId={selectedSample?.ruleId ?? null}
        ruleName={selectedSample?.ruleName ?? null}
        onClose={() => setSelectedSample(null)}
      />
    </div>
  );
};
