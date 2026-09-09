import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Drawer, Empty, Form, Input, InputNumber, Select, Space, Spin, Switch, Tabs, Typography, message } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { fetchMetricDraft, fetchMetricFields, saveMetricDraft } from '../api/metricApi';
import { invalidateMetrics } from '../hooks/useMetrics';
import { metricDefinitionLabels, periodLabels, resourceLabels, type MetricDefinition, type MetricDraft, type ResourceKind } from '../model/metric';
import { MetricResourceSelect } from './MetricResourceSelect';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { useCurrentUser } from '../../system';
import { searchEquals } from '../../../shared/search';
const textGuidance = {
 businessMeaning: '说明这个指标衡量什么，例如：某测站一个统计日内的累计降雨量。',
 calculation: '说明计算方法及例外处理，例如：累计统计日内各时段降雨量；缺测时段按空值规则处理。',
 statisticalScope: '说明纳入和排除的范围，例如：纳入正常运行测站，排除测试记录和异常读数。',
 sourceGrain: '选填，例如：来源数据每行代表一个测站的一次观测。',
 nullHandling: '说明缺失与零值的区别，例如：未降雨记为 0，数据缺测记为空，不用 0 代替缺测。',
 aggregationDescription: '说明哪些情况可以汇总，例如：同一测站可按日累计；不同测站的降雨量不直接相加。',
 updateDescription: '选填，例如：次日更新前一天数据；补录后由实施人员安排重新计算。',
} as const;
const textPlaceholders = {
 businessMeaning: '描述指标的业务含义',
 calculation: '描述计算方法和处理规则',
 statisticalScope: '描述统计对象及纳入、排除条件',
 sourceGrain: '描述来源数据每行代表什么（选填）',
 nullHandling: '描述缺失数据与零值的处理方式',
 aggregationDescription: '描述可汇总的范围及限制',
 updateDescription: '描述更新频率及补录处理（选填）',
} as const;
const requiredTextFields = new Set(['businessMeaning', 'calculation', 'statisticalScope', 'nullHandling', 'aggregationDescription']);
const readDefinitionPath = (definition: MetricDefinition, path: string): unknown =>
 path.replace(/\[(\d+)\]/g, '.$1').split('.').reduce<unknown>((value, key) =>
  value && typeof value === 'object' ? (value as Record<string, unknown>)[key] : undefined, definition);
const textFields = ['businessMeaning','calculation','statisticalScope','sourceGrain','nullHandling','aggregationDescription','updateDescription'] as const;
const ReferenceRow = ({ index, remove, snapshots, feedback }: { index: number; remove: () => void; snapshots: MetricDraft['references']; feedback: { help?: string; validateStatus?: 'warning' } }) => {
 const form = Form.useFormInstance<MetricDefinition>();
 const kind = Form.useWatch(['references', index, 'resourceKind'], form) as ResourceKind | undefined;
 const parent = Form.useWatch(['references', index, 'parentModelId'], form) as string | undefined;
 const user = useCurrentUser(); const permissions = new Set(user.data?.permissions ?? []);
 const allowed = (value: string) => permissions.has(value === 'METRIC' ? 'metric.view' : value === 'DATA_SERVICE' ? 'service.view' : 'model.view');
 return <div className="metric-reference-row"><div className="metric-form-grid">
  <Form.Item name={[index,'resourceKind']} label="参考类型"><Select options={Object.entries(resourceLabels).map(([value,label]) => ({ value,label,disabled: !allowed(value) }))} /></Form.Item>
  {kind === 'MODEL_FIELD' && <Form.Item name={[index,'parentModelId']} label="所属模型"><MetricResourceSelect kind="MODEL" snapshots={snapshots} disabled={!allowed('MODEL')} /></Form.Item>}
  <Form.Item name={[index,'resourceId']} label="参考资源" {...feedback}><MetricResourceSelect key={`${kind}-${parent}`} kind={kind ?? 'MODEL'} modelId={parent} snapshots={snapshots} disabled={!allowed(kind ?? 'MODEL')} /></Form.Item>
  {kind === 'METRIC' && <Form.Item name={[index,'targetVersion']} label="参考版本（留空为当前定义）"><InputNumber min={1} precision={0} /></Form.Item>}
  <Form.Item name={[index,'role']} label="用途"><Input placeholder="例如：分子、分母、来源" maxLength={100} /></Form.Item>
  <Form.Item name={[index,'note']} label="参考说明"><Input.TextArea rows={2} maxLength={2000} /></Form.Item>
 </div><Button danger type="text" icon={<MinusCircleOutlined />} onClick={remove}>移除参考</Button></div>;
};
const Editor = ({ draft, onClose, onSaved }: { draft: MetricDraft; onClose: () => void; onSaved: (draft: MetricDraft) => void }) => {
 const [form] = Form.useForm<MetricDefinition>(); const client = useQueryClient(); const [msg, context] = message.useMessage();
 const [blurredFields, setBlurredFields] = useState<Set<string>>(() => new Set());
 const markBlurred = (path: string) => {
  const name = path.replace(/\[(\d+)\]/g, '.$1').split('.').map(part => /^\d+$/.test(part) ? Number(part) : part);
  if (form.isFieldTouched(name as Parameters<typeof form.isFieldTouched>[0])) setBlurredFields(previous => previous.has(path) ? previous : new Set([...previous, path]));
 };
 const [bindingEnabled, setBindingEnabled] = useState(Boolean(draft.definition.binding));
 const current = Form.useWatch((values: MetricDefinition) => values, { form, preserve: true }) ?? draft.definition;
 const modelId = current.binding?.modelId;
 const periodFieldId = current.binding?.periodFieldId;
 const timeRequired = current.statisticalPeriod !== 'NONE';
 const user = useCurrentUser(); const canViewModels = user.data?.permissions.includes('model.view') ?? false;
 const periodField = useQuery({
  queryKey: ['metrics', 'time-field', modelId, periodFieldId],
  queryFn: () => fetchMetricFields(modelId!, { page: 0, size: 1, search: searchEquals('id', periodFieldId) }),
  enabled: bindingEnabled && canViewModels && Boolean(modelId && periodFieldId),
 });
 const savedPeriod = draft.references.find(r => r.path === 'binding.periodFieldId' && r.resourceId === periodFieldId);
 const stringTime = bindingEnabled && (periodField.data?.content[0]?.fieldType ?? savedPeriod?.contract?.split('|')[1]) === 'STRING';
 // Publish requirements are inline guidance, not save-draft validation rules.
 const feedback = (path: string, required = false, label?: string, diagnosticPath = path) => {
  if (path === 'periodFormat' && !bindingEnabled) return { required: false };
  const value = readDefinitionPath(current, path);
  const missing = required && (value == null || (typeof value === 'string' && !value.trim()));
  if (missing) return blurredFields.has(path)
   ? { required, validateStatus: 'warning' as const, help: `请${path.endsWith('Id') ? '选择' : '填写'}${label ?? metricDefinitionLabels[path as keyof MetricDefinition] ?? '此项'}` }
   : { required };
  const issues = draft.health.issues.filter(issue => issue.path === diagnosticPath && !['METRIC_DEFINITION_INCOMPLETE', 'METRIC_BINDING_INCOMPLETE'].includes(issue.code));
  let unchanged = JSON.stringify(readDefinitionPath(current, diagnosticPath)) === JSON.stringify(readDefinitionPath(draft.definition, diagnosticPath));
  if (path.startsWith('binding.')) unchanged &&= modelId === draft.definition.binding?.modelId;
  if (path === 'periodFormat') unchanged &&= periodFieldId === draft.definition.binding?.periodFieldId && modelId === draft.definition.binding?.modelId;
  const help = issues.length ? unchanged ? issues.map(issue => issue.message).join('；') : '已修改，保存草稿后将重新校验。' : undefined;
  return { required, help, validateStatus: issues.length && unchanged ? 'warning' as const : undefined };
 };
 const mutation = useMutation({ mutationFn: (value: MetricDefinition) => {
  const binding = bindingEnabled ? { modelId: null, valueFieldId: null, periodFieldId: null, dimensions: [], supportingFieldIds: [], fixedFilters: [], ...value.binding } : null;
  return saveMetricDraft(draft.metricId, { ...draft.definition, ...value, binding, references: value.references ?? [] }, draft.fingerprint);
 }, onSuccess: async saved => { await invalidateMetrics(client); void msg.success('草稿已保存'); onSaved(saved); } });
 const fieldPicker = (path: string, numeric = false) => <MetricResourceSelect onBlur={() => markBlurred(path)} kind="MODEL_FIELD" modelId={modelId} snapshots={draft.references} numeric={numeric} disabled={!canViewModels} />;
 const sections = [
  { key:'business', label:'业务口径', children:<div className="metric-form-grid">{textFields.map(key => <Form.Item key={key} className="metric-span" name={key} label={<Space size={4}>{metricDefinitionLabels[key]}<ContextHelp ariaLabel={`${metricDefinitionLabels[key]}填写说明`} content={textGuidance[key]} /></Space>} {...feedback(key, requiredTextFields.has(key))}><Input.TextArea onBlur={() => markBlurred(key)} placeholder={textPlaceholders[key]} rows={key === 'calculation' ? 5 : 2} maxLength={key === 'calculation' ? 20000 : key === 'sourceGrain' || key === 'updateDescription' ? 2000 : 10000} /></Form.Item>)}<Form.Item name="unit" label="单位" {...feedback("unit", true)}><Input onBlur={() => markBlurred("unit")} placeholder="例如：毫米（mm）、件、元、%" maxLength={32} /></Form.Item><Form.Item name="decimalPlaces" label="显示小数位"><InputNumber min={0} max={10} precision={0} /></Form.Item><Form.Item name="valueFormat" label="数值显示"><Select options={[{value:'NUMBER',label:'普通数值'},{value:'RATIO',label:'0～1 比值显示为百分比'},{value:'PERCENT_VALUE',label:'百分数值显示为百分比'}]} /></Form.Item></div> },
  { key:'time', label:'时间与维度', children:<><div className="metric-form-grid"><Form.Item name="statisticalPeriod" label="统计周期"><Select options={Object.entries(periodLabels).map(([value,label]) => ({ value,label }))} /></Form.Item><Form.Item name="periodFormat" label="字符串时间格式" {...feedback("periodFormat", stringTime)} tooltip="绑定 STRING 类型的时间字段时，发布前必填；日期或时间戳字段无需填写。"><Input onBlur={() => markBlurred("periodFormat")} placeholder="例如：yyyy-MM-dd" maxLength={100} /></Form.Item><Form.Item className="metric-span" name="timeDescription" label={<Space size={4}>时间口径<ContextHelp ariaLabel="时间口径填写说明" content="说明采用的业务时间、统计起止时间和时区，例如：以北京时间每日 0 时至次日 0 时为一个统计日。" /></Space>} {...feedback("timeDescription", timeRequired)}><Input.TextArea onBlur={() => markBlurred("timeDescription")} rows={3} placeholder="描述统计时间及周期归属" maxLength={10000} /></Form.Item><Form.Item className="metric-span" name="grainDescription" label={<Space size={4}>结果粒度<ContextHelp ariaLabel="结果粒度填写说明" content="说明每行结果代表什么，例如：每行代表一个统计日、一个测站的降雨量。" /></Space>} {...feedback("grainDescription", true)}><Input.TextArea onBlur={() => markBlurred("grainDescription")} rows={3} placeholder="描述每行结果的业务粒度" maxLength={2000} /></Form.Item></div><Typography.Text type="secondary">维度的字段映射在“结果绑定”中配置。</Typography.Text></> },
  { key:'binding', label:'结果绑定', children:<><Space className="metric-section-toolbar"><Switch checked={bindingEnabled} onChange={setBindingEnabled} /><Typography.Text>绑定结果模型</Typography.Text><ContextHelp ariaLabel="结果绑定用途" content="仅登记计算结果的位置。可以先发布业务口径，稍后绑定结果；此处不执行查询或计算。" /></Space>{bindingEnabled ? <><div className="metric-form-grid"><Form.Item className="metric-span" name={['binding','modelId']} label="结果模型" {...feedback("binding.modelId", true, "结果模型")}><MetricResourceSelect onBlur={() => markBlurred("binding.modelId")} kind="MODEL" snapshots={draft.references} publishedOnly disabled={!canViewModels} /></Form.Item><Form.Item name={['binding','valueFieldId']} label="指标值字段" {...feedback("binding.valueFieldId", true, "指标值字段")}>{fieldPicker("binding.valueFieldId", true)}</Form.Item><Form.Item name={['binding','periodFieldId']} label="时间字段" {...feedback("binding.periodFieldId", timeRequired, "时间字段")}>{fieldPicker("binding.periodFieldId")}</Form.Item></div>
  <Form.List name={['binding','dimensions']}>{(items,{add,remove}) => <><h4>维度映射</h4>{items.map(item => <div className="metric-form-grid metric-reference-row" key={item.key}><Form.Item name={[item.name,'key']} label="维度编码" {...feedback(`binding.dimensions[${item.name}].key`, true, "维度编码")}><Input onBlur={() => markBlurred(`binding.dimensions[${item.name}].key`)} placeholder="district" maxLength={64} /></Form.Item><Form.Item name={[item.name,'name']} label="维度名称" {...feedback(`binding.dimensions[${item.name}].name`, true, "维度名称")}><Input onBlur={() => markBlurred(`binding.dimensions[${item.name}].name`)} placeholder="区县" maxLength={100} /></Form.Item><Form.Item name={[item.name,'fieldId']} label="结果字段" {...feedback(`binding.dimensions[${item.name}].fieldId`, true, "结果字段", `binding.dimensions[${item.name}]`)}>{fieldPicker(`binding.dimensions[${item.name}].fieldId`)}</Form.Item><Form.Item name={[item.name,'description']} label="维度含义"><Input maxLength={1000} /></Form.Item><Button type="text" danger onClick={() => remove(item.name)}>移除维度</Button></div>)}<Button icon={<PlusOutlined />} disabled={items.length >= 20} onClick={() => add({})}>添加维度</Button></>}</Form.List>
  <Form.List name={['binding','supportingFieldIds']}>{(items,{add,remove}) => <><h4>辅助解释字段（最多 4 个）</h4>{items.map(item => <Space.Compact block key={item.key}><Form.Item name={item.name} className="metric-grow" {...feedback(`binding.supportingFieldIds[${item.name}]`)}>{fieldPicker(`binding.supportingFieldIds[${item.name}]`)}</Form.Item><Button danger onClick={() => remove(item.name)}>移除</Button></Space.Compact>)}<Button disabled={items.length >= 4} onClick={() => add(null)}>添加辅助字段</Button></>}</Form.List>
  <Form.List name={['binding','fixedFilters']}>{(items,{add,remove}) => <><h4>固定范围 <ContextHelp ariaLabel="固定范围说明" content="条件之间均为 AND。长表可使用 metric_code 等于指定编码区分指标；当前仅登记条件。" /></h4>{items.map(item => <div className="metric-form-grid metric-reference-row" key={item.key}><Form.Item name={[item.name,'fieldId']} label="字段" {...feedback(`binding.fixedFilters[${item.name}].fieldId`, true, "条件字段")}>{fieldPicker(`binding.fixedFilters[${item.name}].fieldId`)}</Form.Item><Form.Item name={[item.name,'operator']} label="运算符" {...feedback(`binding.fixedFilters[${item.name}].operator`, true, "运算符")}><Select onBlur={() => markBlurred(`binding.fixedFilters[${item.name}].operator`)} options={[['EQ','等于'],['NE','不等于'],['GT','大于'],['GE','大于等于'],['LT','小于'],['LE','小于等于'],['IS_NULL','为空'],['IS_NOT_NULL','非空']].map(([value,label]) => ({value,label}))} /></Form.Item><Form.Item name={[item.name,'value']} label="条件值（空值运算符不填写）" {...feedback(`binding.fixedFilters[${item.name}]`)} required={Boolean(current.binding?.fixedFilters[item.name]?.operator && !["IS_NULL", "IS_NOT_NULL"].includes(current.binding.fixedFilters[item.name].operator))}><Input maxLength={2000} /></Form.Item><Button danger type="text" onClick={() => remove(item.name)}>移除条件</Button></div>)}<Button icon={<PlusOutlined />} disabled={items.length >= 8} onClick={() => add({operator:'EQ'})}>添加固定条件</Button></>}</Form.List>
  </> : <Empty description="未绑定结果；仍可登记和发布完整口径" />}</> },
  { key:'references', label:'参考资料', children:<><Space><Typography.Text>来源、相关指标与已有服务</Typography.Text><ContextHelp ariaLabel="参考资料用途" content="选填，仅用于实施说明和导航，不构成任务执行依赖。来源字段先选择所属模型。" /></Space><Form.List name="references">{(items,{add,remove}) => <>{items.map(item => <ReferenceRow key={item.key} index={item.name} remove={() => remove(item.name)} snapshots={draft.references} feedback={feedback(`references[${item.name}]`)} />)}<Button icon={<PlusOutlined />} disabled={items.length >= 50} onClick={() => add({resourceKind:'MODEL'})}>添加参考</Button></>}</Form.List></> },
 ];
 return <>{context}<Form form={form} layout="vertical" autoComplete="off" initialValues={draft.definition} onFinish={() => mutation.mutate(form.getFieldsValue(true))}><div className="metric-form-guidance"><Typography.Text type="secondary"><Typography.Text type="danger">*</Typography.Text> 发布必填，草稿可留空。</Typography.Text></div>
 {draft.health.issues.some(issue => issue.path === 'ownerName') && <Typography.Text type="secondary">业务负责人尚未登记，可在“修改资料”中补充。</Typography.Text>}
 <Tabs items={sections} />{mutation.isError && <InlineFeedback tone="error" label={mutation.error.message} />}<div className="metric-editor-footer"><Typography.Text type="secondary">保存草稿不会替换已发布口径</Typography.Text><Space><Button onClick={onClose}>取消</Button><Button type="primary" htmlType="submit" loading={mutation.isPending}>保存草稿</Button></Space></div></Form></>;
};
export const MetricDefinitionDrawer = ({ id, name, onClose, onSaved }: { id: string; name: string; onClose: () => void; onSaved: (draft: MetricDraft) => void }) => {
 const query = useQuery({ queryKey:['metrics','draft',id],queryFn:() => fetchMetricDraft(id),refetchOnWindowFocus:false });
 return <Drawer open title={`编辑口径 · ${name}`} width={880} rootClassName="business-overlay business-drawer-overlay" onClose={onClose}>{query.isError ? <InlineFeedback tone="error" label={query.error.message} action={<Button onClick={() => void query.refetch()}>重试</Button>} /> : query.data ? <Editor key={query.data.fingerprint} draft={query.data} onClose={onClose} onSaved={onSaved} /> : <Spin />}</Drawer>;
};
