import { SettingOutlined } from '@ant-design/icons';
import { Button, Drawer, Form, InputNumber, Select, Space, Switch, message } from 'antd';
import { useState } from 'react';
import { useTasks } from '../../task';
import { useComputeEngines } from '../../computeengine';
import { useCurrentUser } from '../../system';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { searchContains } from '../../../shared/search';
import { useChannels, useOperationsMutation, useRecipients } from '../hooks/useOperations';
import { applyOverrides, saveRule } from '../api/operationsApi';
import { isContinuousRule, isEngineRule, ruleLabels, severityLabels, type AlertRule, type AlertRuleType, type AlertRuleWrite } from '../model/operations';

type RuleForm = Omit<AlertRuleWrite, 'subjectId'> & { subjectIds: string[] };
export const AlertRuleDrawer = ({ rule, onClose }: { rule: AlertRule | null; onClose: () => void }) => {
  const [form] = Form.useForm<RuleForm>(); const [subjectKeyword, setSubjectKeyword] = useState(''); const [recipientKeyword, setRecipientKeyword] = useState('');
  const [notice, context] = message.useMessage(); const save = useOperationsMutation(saveRule); const batch = useOperationsMutation(applyOverrides);
  const user = useCurrentUser(); const canTask = user.data?.permissions.includes('task.view') ?? false; const canEngine = user.data?.permissions.includes('compute.engine.view') ?? false;
  const defaultType: AlertRuleType = canTask ? 'RUN_FAILED' : 'ENGINE_UNREACHABLE';
  const type: AlertRuleType = Form.useWatch('ruleType', form) ?? rule?.ruleType ?? defaultType; const enabled = Form.useWatch('enabled', form) ?? true;
  const tasks = useTasks({ size: 100, sort: 'name', search: searchContains('name', subjectKeyword) }, !isEngineRule(type) && canTask);
  const engines = useComputeEngines({ size: 100, sort: 'name', search: searchContains('name', subjectKeyword) }, isEngineRule(type) && canEngine);
  const recipients = useRecipients(type, recipientKeyword, true); const channels = useChannels({ size: 500, sort: 'name' });
  const options = isEngineRule(type) ? engines.data?.content.map(e => ({ value: e.id, label: e.name })) ?? [] : tasks.data?.content.map(t => ({ value: t.id, label: t.name })) ?? [];
  if (rule?.subjectId && !options.some(o => o.value === rule.subjectId)) options.push({ value: rule.subjectId, label: rule.subjectName });
  const busy = save.isPending || batch.isPending;
  const submit = async (values: RuleForm) => {
    const body: AlertRuleWrite = { ruleType: values.ruleType, subjectId: rule?.subjectId ?? null, enabled: values.enabled,
      severity: values.severity, thresholdSeconds: values.thresholdSeconds ?? 0, cooldownSeconds: values.cooldownSeconds,
      userIds: values.userIds ?? [], channelIds: values.channelIds ?? [] };
    try {
      if (rule) await save.mutateAsync({ id: rule.id, body });
      else await batch.mutateAsync({ subjectIds: values.subjectIds, configuration: body });
      notice.success('告警规则已保存'); onClose();
    } catch (error) { notice.error(error instanceof Error ? error.message : '保存失败'); }
  };
  const defaults: RuleForm = rule ? { ...rule, subjectIds: rule.subjectId ? [rule.subjectId] : [] }
    : { ruleType: defaultType, subjectIds: [], enabled: true, severity: 'CRITICAL', thresholdSeconds: isEngineRule(defaultType) ? 90 : 0, cooldownSeconds: 600, userIds: [], channelIds: [] };
  return <>{context}<Drawer rootClassName="business-overlay business-drawer-overlay" open onClose={onClose} width={720}
    title={<Space><SettingOutlined />{rule ? '编辑告警规则' : '配置对象覆盖'}</Space>} extra={rule?.subjectName}
    footer={<div className="ops-panel-toolbar" style={{ margin: 0 }}><span>{rule?.subjectId === null ? '全局默认配置' : '所选对象优先使用本配置'}</span><Space><Button onClick={onClose}>取消</Button><Button type="primary" loading={busy} onClick={() => form.submit()}>保存</Button></Space></div>}>
    <Form form={form} layout="vertical" autoComplete="off" initialValues={defaults} onFinish={values => void submit(values)}>
      <div className="ops-rule-form-columns">
        <Form.Item name="ruleType" label="规则类型" rules={[{ required: true }]}><Select disabled={Boolean(rule)} options={Object.entries(ruleLabels).filter(([value]) => isEngineRule(value as AlertRuleType) ? canEngine : canTask).map(([value, label]) => ({ value, label }))}
          onChange={(value: AlertRuleType) => { if (isEngineRule(value) !== isEngineRule(type)) form.setFieldsValue({ subjectIds: [], userIds: [] }); form.setFieldsValue({ thresholdSeconds: value === 'QUEUE_TOO_LONG' ? 600 : isEngineRule(value) ? 90 : value === 'RUN_TOO_LONG' ? 3600 : 0, severity: value === 'RUN_FAILED' || value === 'ENGINE_UNREACHABLE' ? 'CRITICAL' : 'WARNING' }); }} /></Form.Item>
        <Form.Item name="enabled" label="启用规则" valuePropName="checked"><Switch /></Form.Item>
      </div>
      {(!rule || rule.subjectId !== null) && <Form.Item name="subjectIds" label={isEngineRule(type) ? '覆盖计算引擎' : '覆盖任务'} rules={[{ required: true, type: 'array', min: 1, max: 100, message: '请选择 1 至 100 个对象' }]}>
        <Select mode="multiple" disabled={Boolean(rule)} showSearch filterOption={false} onSearch={setSubjectKeyword} options={options} loading={tasks.isFetching || engines.isFetching}
          placeholder="搜索并选择对象；已有同类覆盖将更新" />
      </Form.Item>}
      <div className="ops-rule-form-columns">
        <Form.Item name="severity" label="告警级别" rules={[{ required: true }]}><Select options={Object.entries(severityLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
        {isContinuousRule(type) && <Form.Item name="thresholdSeconds" label="异常持续阈值（秒）" rules={[{ required: true }, { type: 'number', min: enabled ? 1 : 0, max: 2592000 }]}><InputNumber min={enabled ? 1 : 0} max={2592000} style={{ width: '100%' }} /></Form.Item>}
        <Form.Item name="cooldownSeconds" label={<Space>通知冷却（秒）<ContextHelp ariaLabel="通知冷却" content="同类规则、同一对象和接收目标在冷却期内最多发送一次触发提醒。告警记录始终保留；0 表示不限制。" /></Space>} rules={[{ required: true }]}><InputNumber min={0} max={86400} style={{ width: '100%' }} /></Form.Item>
      </div>
      <Form.Item name="userIds" label="站内通知接收人"><Select mode="multiple" showSearch filterOption={false} onSearch={setRecipientKeyword} loading={recipients.isFetching}
        options={recipients.data?.content.map(u => ({ value: u.id, label: `${u.displayName}（${u.username}）` }))} placeholder="选择有来源查看权限的用户" /></Form.Item>
      {recipients.error && <InlineFeedback tone="error" label="接收人加载失败" detail={recipients.error.message} action={<Button type="link" onClick={() => void recipients.refetch()}>重试</Button>} />}
      <Form.Item name="channelIds" label="Webhook 通知渠道"><Select mode="multiple" options={channels.data?.content.map(c => ({ value: c.id, label: `${c.name}${c.enabled ? '' : '（已停用）'}` }))} placeholder="选择已配置的渠道" /></Form.Item>
      {channels.error && <InlineFeedback tone="error" label="渠道加载失败" detail={channels.error.message} action={<Button type="link" onClick={() => void channels.refetch()}>重试</Button>} />}
      <ContextHelp ariaLabel="规则覆盖与默认接收人" presentation="popover" content="未指定个人接收人或 Webhook 的告警仍进入共享告警中心。对象覆盖会替代该对象的全局默认配置；关闭覆盖不会回退到全局。恢复默认会移除对象覆盖。保存规则不会回放已结束的历史运行。" />
    </Form>
  </Drawer></>;
};
