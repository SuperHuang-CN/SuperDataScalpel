import { BellOutlined } from '@ant-design/icons';
import { Button, Drawer, Form, Input, Modal, Pagination, Select, Space, Spin, Tabs, Tag, Timeline, message } from 'antd';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useCurrentUser } from '../../system';
import { InlineFeedback, ContextHelp } from '../../../shared/components/ContextualFeedback';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';
import { useAlert, useAlertHistory, useOperationsMutation } from '../hooks/useOperations';
import { commandAlert, type AlertCommand } from '../api/operationsApi';
import { isContinuousRule, isEngineRule, ruleLabels, severityLabels, statusLabels } from '../model/operations';
import { AlertDeliveriesPanel } from './AlertDeliveriesPanel';
import { RuntimeRunDrawer } from './RuntimeRunDrawer';

const actionLabels: Record<string, string> = { TRIGGERED: '异常触发', ACKNOWLEDGED: '确认接手', CLOSED: '人工关闭', RECOVERED: '条件恢复', ENDED: '条件结束', SILENCED: '设置静默', UNSILENCED: '取消静默', SILENCE_EXPIRED: '静默到期提醒', NOTIFICATION_SUPPRESSED: '通知抑制' };
export const AlertIncidentDrawer = ({ incidentId, onClose }: { incidentId: string | null; onClose: () => void }) => {
  const query = useAlert(incidentId); const user = useCurrentUser(); const command = useOperationsMutation(commandAlert);
  const [historyPage, setHistoryPage] = useState(0); const history = useAlertHistory(incidentId, { page: historyPage, size: 20, sort: '-createdAt' });
  const [modal, setModal] = useState<'close' | 'silence' | null>(null); const [runId, setRunId] = useState<string | null>(null);
  const [form] = Form.useForm<{ reason: string; minutes: number; until?: string }>(); const [notice, context] = message.useMessage();
  const i = query.data; const minutes = Form.useWatch('minutes', form); const canHandle = user.data?.permissions.includes('alert.handle') ?? false;
  const execute = async (input: AlertCommand) => {
    try { await command.mutateAsync(input); notice.success('告警已更新'); setModal(null); return true; }
    catch (error) { notice.error(error instanceof Error ? error.message : '操作失败'); return false; }
  };
  const openForm = (kind: 'close' | 'silence') => { form.resetFields(); setModal(kind); };
  return <>{context}<Drawer rootClassName="business-overlay business-drawer-overlay" width={920} title={<Space><BellOutlined />告警详情</Space>} open={Boolean(incidentId)} onClose={onClose} destroyOnHidden
    extra={i && <Tag color={i.severity === 'CRITICAL' ? 'error' : 'warning'}>{severityLabels[i.severity]}</Tag>}>
    {query.isPending && <Spin />}{query.error && <InlineFeedback tone="error" label="告警详情加载失败" detail={query.error.message} action={<Button type="link" onClick={() => void query.refetch()}>重试</Button>} />}
    {i && <>
      <Space wrap><strong>{i.subjectName} · {ruleLabels[i.ruleType]}</strong><Tag>{statusLabels[i.status]}</Tag>
        {isContinuousRule(i.ruleType) && <Tag>{i.conditionState === 'UNKNOWN' ? '观测待确认' : i.conditionState === 'CLEARED' ? '条件已结束' : '条件持续触发'}</Tag>}
      </Space>
      <p>{i.summary}</p>
      <Space wrap>
        {i.sourceExists && <Link to={isEngineRule(i.ruleType) ? `/compute-engine/${i.subjectId}` : `/task/${i.subjectId}`}>查看来源</Link>}
        {i.runId && <Button type="link" onClick={() => setRunId(i.runId)}>查看运行、日志与结果</Button>}
        {!i.sourceExists && <span>来源已删除，历史证据仍保留</span>}
        {canHandle && i.status !== 'CLOSED' && <>
          <Button loading={command.isPending} disabled={i.status !== 'OPEN'} onClick={() => void execute({ id: i.id, action: 'acknowledge' })}>确认接手</Button>
          {!isContinuousRule(i.ruleType) && <Button onClick={() => openForm('close')}>关闭告警</Button>}
          {i.silencedUntil ? <Button loading={command.isPending} onClick={() => void execute({ id: i.id, action: 'unsilence' })}>取消静默</Button> : <Button onClick={() => openForm('silence')}>静默</Button>}
        </>}
        <ContextHelp ariaLabel="告警处理规则" content="确认表示已接手，个人已读不会确认告警。运行失败和质检不通过需人工说明后关闭；后续运行成功不会自动关闭原失败。持续异常依据有效恢复证据自动关闭。" />
      </Space>
      <div className="ops-detail-grid">
        {[
          ['发生时间', formatManagementDateTime(i.occurredAt)], ['检测时间', formatManagementDateTime(i.detectedAt)],
          ['最近观测', formatManagementDateTime(i.lastObservedAt)], ['静默截止', formatManagementDateTime(i.silencedUntil)],
          ['错误码', i.errorCode ?? '—'], ['诊断 ID', i.diagnosticId ?? '—'], ['关闭时间', formatManagementDateTime(i.closedAt)], ['处理结果', i.closeReason ?? '—'],
        ].map(([label, value]) => <div className="ops-detail-field" key={label}><span>{label}</span><div>{value}</div></div>)}
      </div>
      <Tabs items={[
        { key: 'history', label: '处理时间线', children: <><div className="ops-panel-toolbar"><span>共 {history.data?.totalElements ?? '—'} 条</span><Pagination size="small" current={historyPage + 1} pageSize={20} total={history.data?.totalElements ?? 0} showSizeChanger={false} onChange={p => setHistoryPage(p - 1)} /></div>
          {history.error && <InlineFeedback tone="error" label="时间线加载失败" detail={history.error.message} action={<Button type="link" onClick={() => void history.refetch()}>重试</Button>} />}
          <Timeline items={history.data?.content.map(a => ({ key: a.id, content: <><strong>{actionLabels[a.action] ?? a.action}</strong> · {a.actorName} · {formatManagementDateTime(a.createdAt)}<p>{a.reason}{a.untilAt && ` · 截止 ${formatManagementDateTime(a.untilAt)}`}</p></> }))} /></> },
        { key: 'deliveries', label: 'Webhook 投递', children: <AlertDeliveriesPanel key={i.id} incidentId={i.id} embedded /> },
      ]} />
    </>}
  </Drawer>
  <Modal rootClassName="business-overlay business-modal-overlay" title={modal === 'close' ? `关闭告警 · ${i?.subjectName ?? ''}` : `静默同类告警 · ${i?.subjectName ?? ''}`} open={modal !== null}
    okText={modal === 'close' ? '确认关闭' : '设置静默'} cancelText="返回" confirmLoading={command.isPending} onCancel={() => setModal(null)} onOk={() => form.submit()} destroyOnHidden>
    <Form form={form} layout="vertical" autoComplete="off" initialValues={{ minutes: 30 }} onFinish={values => {
      if (!i) return;
      if (modal === 'close') void execute({ id: i.id, action: 'close', reason: values.reason });
      else void execute({ id: i.id, action: 'silence', reason: values.reason, untilAt: values.minutes === 0 ? new Date(values.until!).toISOString() : new Date(Date.now() + values.minutes * 60_000).toISOString() });
    }}>
      {modal === 'silence' && <><Form.Item name="minutes" label="静默时长"><Select options={[{ value: 30, label: '30 分钟' }, { value: 120, label: '2 小时' }, { value: 0, label: '自定义截止时间' }]} /></Form.Item>
        {minutes === 0 && <Form.Item name="until" label="截止时间" rules={[{ required: true, message: '请选择未来的截止时间' }, { validator: async (_, value: string) => { if (value && Date.parse(value) <= Date.now()) throw new Error('截止时间必须晚于当前时间'); } }]}><Input type="datetime-local" /></Form.Item>}
        <p>静默期间仍记录告警，暂停此对象同类规则的站内提醒和 Webhook 通知。</p></>}
      <Form.Item name="reason" label={modal === 'close' ? '处理说明' : '静默原因'} rules={[{ required: true, whitespace: true, message: '请填写说明' }, { max: 1000 }]}><Input.TextArea rows={3} maxLength={1000} /></Form.Item>
    </Form>
  </Modal>
  <RuntimeRunDrawer runId={runId} onClose={() => setRunId(null)} />
  </>;
};
