import { ApartmentOutlined } from '@ant-design/icons';
import { Button, Form, Input, Select, Tag, TreeSelect } from 'antd';
import type { TableProps } from 'antd';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ManagementAdaptiveMoreFilters, ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { ManagementDateTime, ManagementListCell } from '../../../shared/components/ManagementListCells';
import { ContextHelp } from '../../../shared/components/ContextualFeedback';
import { useCurrentUser } from '../../system';
import { directoryTreeSelectData, useDirectoryTree } from '../../directory';
import { useComputeEngines } from '../../computeengine';
import { taskRunStatusColors, taskRunStatusLabels, taskTypeLabels, taskRunTriggerTypeLabels, taskRunExecutionModeLabels } from '../../task';
import { useRuntimeRuns } from '../hooks/useOperations';
import { buildRuntimeRunSearch } from '../model/operationsSearch';
import type { RuntimeRun, RuntimeRunFilters } from '../model/operations';
import { OperationsTable } from './OperationsTable';
import { durationLabel } from '../model/runtimePresentation';
import { RuntimeRunDrawer } from './RuntimeRunDrawer';

type RunForm = RuntimeRunFilters & { fromLocal?: string; toLocal?: string };
const emptyAdvanced: RunForm = { mode: 'REAL', batchOnly: false, qualityConclusion: undefined, computeEngineId: undefined, directoryId: undefined, triggerType: undefined, timeField: 'queuedAt', from: undefined, to: undefined, fromLocal: undefined, toLocal: undefined };
const localTime = (iso?: string) => { if (!iso || !Number.isFinite(Date.parse(iso))) return undefined; const date = new Date(iso); return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16); };
export const RuntimeRunsPanel = ({ initial = {} }: { initial?: RuntimeRunFilters }) => {
  const defaults: RunForm = { ...emptyAdvanced, activeOnly: false, ...initial, fromLocal: localTime(initial.from), toLocal: localTime(initial.to) };
  const [form] = Form.useForm<RunForm>(); const [advancedForm] = Form.useForm<RunForm>();
  const [filters, setFilters] = useState<RunForm>(defaults); const [advanced, setAdvanced] = useState<RunForm>(defaults);
  const [advancedOpen, setAdvancedOpen] = useState(false); const [page, setPage] = useState(0); const [size, setSize] = useState(20);
  const [runId, setRunId] = useState<string | null>(null); const user = useCurrentUser();
  const permissions = new Set(user.data?.permissions ?? []);
  const engines = useComputeEngines({ size: 500, sort: 'name' }, permissions.has('compute.engine.view'));
  const directories = useDirectoryTree('TASK', permissions.has('directory.view'));
  const applied: RuntimeRunFilters = { ...filters, from: filters.fromLocal ? filters.fromLocal === localTime(filters.from) ? filters.from : new Date(filters.fromLocal).toISOString() : undefined, to: filters.toLocal ? filters.toLocal === localTime(filters.to) ? filters.to : new Date(filters.toLocal).toISOString() : undefined };
  const query = useRuntimeRuns({ page, size, sort: '-queuedAt', search: buildRuntimeRunSearch(applied) }, applied);
  const advancedCount = [advanced.batchOnly, advanced.qualityConclusion, advanced.computeEngineId, advanced.directoryId, advanced.triggerType, advanced.fromLocal, advanced.toLocal, advanced.mode !== 'REAL' ? advanced.mode : undefined].filter(Boolean).length;
  const columns: TableProps<RuntimeRun>['columns'] = [
    { title: '任务 / 运行', width: 270, render: (_, r) => <ManagementListCell icon={<ApartmentOutlined />} primary={<Button type="link" className="ops-name-button" onClick={() => setRunId(r.id)}>{r.taskName}</Button>}
      secondary={<span>{taskTypeLabels[r.taskType]} · <span title={r.id}>{r.id.slice(0, 8)}</span>{r.sourceExists && <> · <Link to={`/task/${r.taskId}`}>任务详情</Link></>}</span>} /> },
    { title: '运行 / 质量', width: 140, render: (_, r) => <ManagementListCell primary={<Tag color={taskRunStatusColors[r.status]}>{taskRunStatusLabels[r.status]}</Tag>}
      secondary={r.qualityConclusion ? <Tag color={r.qualityConclusion === 'FAILED' ? 'warning' : 'success'}>{r.qualityConclusion === 'FAILED' ? `质检不通过 · ${r.qualityFailedRules ?? '—'} 项` : '质检通过'}</Tag> : null} /> },
    { title: '来源 / 引擎', width: 180, render: (_, r) => <ManagementListCell primary={`${taskRunTriggerTypeLabels[r.triggerType]} · ${taskRunExecutionModeLabels[r.executionMode]}`} secondary={r.engineName ?? (r.computeEngineId ? '已删除引擎' : r.taskType === 'LOCAL_SQL' ? '本地 SQL' : r.taskType === 'WORKFLOW' ? '依赖工作流' : '—')} /> },
    { title: '提交时间', dataIndex: 'queuedAt', width: 150, render: value => <ManagementDateTime value={value} /> },
    { title: '开始 / 结束', width: 190, render: (_, r) => <ManagementListCell primary={r.startedAt ? new Date(r.startedAt).toLocaleString() : '尚未开始'} secondary={r.endedAt ? new Date(r.endedAt).toLocaleString() : '尚未结束'} /> },
    { title: '排队 / 执行耗时', width: 150, align: 'right', render: (_, r) => <ManagementListCell primary={durationLabel(r.queuedAt, r.startedAt ?? r.endedAt)} secondary={durationLabel(r.startedAt, r.endedAt)} /> },
  ];
  const reset = () => { const next = { activeOnly: false, mode: 'REAL' as const }; form.resetFields(); form.setFieldsValue({ ...next, taskName: undefined, status: undefined, taskType: undefined }); advancedForm.setFieldsValue(emptyAdvanced); setAdvanced(emptyAdvanced); setAdvancedOpen(false); setFilters(next); setPage(0); };
  return <section className="management-workbench">
    <div className="management-filter-strip">
      <Form<RunForm> form={form} initialValues={{ taskName: defaults.taskName, activeOnly: defaults.activeOnly, status: defaults.status, taskType: defaults.taskType }} autoComplete="off" layout="inline" className="management-filter-form" onFinish={values => { const a = advancedOpen ? advanced : advancedForm.getFieldsValue(true); setAdvanced(a); setFilters({ ...a, ...values }); setPage(0); }}>
        <Form.Item name="taskName"><ManagementSearchInput placeholder="搜索任务名称" allowClear /></Form.Item>
        <Form.Item name="activeOnly" getValueProps={(value: boolean) => ({ value: value ? 'active' : 'history' })} normalize={(value: string) => value === 'active'}><Select style={{ width: 125 }} options={[{ value: 'history', label: '历史运行' }, { value: 'active', label: '全部活动实例' }]} /></Form.Item>
        <Form.Item name="status"><Select style={{ width: 125 }} placeholder="全部状态" allowClear options={[{ value: 'FAILED_OR_TIMED_OUT', label: '失败及超时' }, ...Object.entries(taskRunStatusLabels).map(([value, label]) => ({ value, label }))]} /></Form.Item>
        <Form.Item name="taskType"><Select style={{ width: 145 }} placeholder="全部任务类型" allowClear options={Object.entries(taskTypeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      </Form>
      <ManagementAdaptiveMoreFilters count={advancedCount} open={advancedOpen} onOpenChange={open => { if (open) setAdvanced(advancedForm.getFieldsValue(true)); else advancedForm.setFieldsValue(advanced); setAdvancedOpen(open); }}
        onClear={() => advancedForm.setFieldsValue(emptyAdvanced)} onCancel={() => { advancedForm.setFieldsValue(advanced); setAdvancedOpen(false); }} onConfirm={() => { setAdvanced(advancedForm.getFieldsValue()); setAdvancedOpen(false); }}>
        <Form<RunForm> form={advancedForm} initialValues={defaults} autoComplete="off" layout="vertical">
          <Form.Item name="batchOnly" label="任务范围" getValueProps={(value: boolean) => ({ value: value ? 'batch' : 'all' })} normalize={(value: string) => value === 'batch'}><Select style={{ width: 130 }} options={[{ value: 'all', label: '全部任务' }, { value: 'batch', label: '仅批任务' }]} /></Form.Item>
          <Form.Item name="qualityConclusion" label="质检结论"><Select allowClear placeholder="全部结论" style={{ width: 130 }} options={[{ value: 'PASSED', label: '通过' }, { value: 'FAILED', label: '不通过' }]} /></Form.Item>
          <Form.Item name="mode" label="运行模式"><Select style={{ width: 130 }} options={Object.entries(taskRunExecutionModeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
          <Form.Item name="computeEngineId" label="计算引擎"><Select style={{ width: 150 }} allowClear placeholder="全部引擎" options={engines.data?.content.map(e => ({ value: e.id, label: e.name }))} /></Form.Item>
          <Form.Item name="directoryId" label="任务目录"><TreeSelect style={{ width: 150 }} allowClear placeholder="全部目录" treeData={directoryTreeSelectData(directories.data ?? [])} /></Form.Item>
          <Form.Item name="triggerType" label="触发方式"><Select style={{ width: 130 }} allowClear placeholder="全部触发方式" options={Object.entries(taskRunTriggerTypeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
          <Form.Item name="timeField" label="历史时间口径"><Select style={{ width: 130 }} placeholder="提交时间" options={[{ value: 'queuedAt', label: '提交时间' }, { value: 'endedAt', label: '结束时间' }]} /></Form.Item>
          <Form.Item name="fromLocal" label="开始时间"><Input type="datetime-local" aria-label="运行查询开始时间" style={{ width: 190 }} /></Form.Item>
          <Form.Item name="toLocal" label="结束时间"><Input type="datetime-local" aria-label="运行查询结束时间" style={{ width: 190 }} /></Form.Item>
        </Form>
      </ManagementAdaptiveMoreFilters>
      <ManagementFilterActions form={form} appliedFilters={filters} additionalActive={advancedCount > 0} loading={query.isFetching} onReset={reset} />
    </div>
    <OperationsTable title={filters.activeOnly ? '活动运行实例' : '运行历史'} query={query} columns={columns} page={page} size={size}
      onPage={(p, s) => { setPage(p); setSize(s); }} extra={<ContextHelp ariaLabel="运行列表时间范围" content="活动实例包含所有尚未结束的运行，不受历史时间范围限制。历史查询默认最近 24 小时，单次最多 90 天。" />} />
    <RuntimeRunDrawer runId={runId} onClose={() => setRunId(null)} />
  </section>;
};
