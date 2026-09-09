import { DatabaseOutlined, FileTextOutlined, LinkOutlined } from '@ant-design/icons';
import { Button, Empty, Space, Tag, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { BusinessDetailSection } from '../../../shared/components/BusinessDetailSection';
import { BusinessDetailDescriptions } from '../../../shared/components/BusinessDetailDescriptions';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { useCurrentUser } from '../../system';
import { metricDefinitionLabels, periodLabels, referenceHref, resourceLabels, type MetricDefinition, type MetricHealth, type MetricSnapshot } from '../model/metric';
export const MetricDefinitionPanel = ({ definition: d, references, health }: { definition: MetricDefinition; references: MetricSnapshot[]; health?: MetricHealth }) => {
 const navigate = useNavigate(); const user = useCurrentUser(); const permissions = new Set(user.data?.permissions ?? []);
 const canNavigate = (r: MetricSnapshot) => permissions.has(r.resourceKind === 'METRIC' ? 'metric.view' : r.resourceKind === 'DATA_SERVICE' ? 'service.view' : 'model.view');
 const resource = (id?: string | null) => { const r = references.find(ref => ref.resourceId === id); return r ? <Space wrap>{r.contract && canNavigate(r) ? <Button size="small" type="link" onClick={() => navigate(referenceHref(r))}>{r.name}</Button> : <span>{r.name ?? r.resourceId}</span>}<Typography.Text type="secondary">{r.code}</Typography.Text>{!r.contract && <Tag color="warning">已失效</Tag>}</Space> : id ?? '—'; };
 const keys: (keyof MetricDefinition)[] = ['businessMeaning','calculation','statisticalScope','sourceGrain','timeDescription','grainDescription','unit','statisticalPeriod','periodFormat','nullHandling','aggregationDescription','updateDescription'];
 return <div className="metric-detail-content">
  {health?.issues.length ? <InlineFeedback tone={health.issues.some(i => i.blocking) ? 'warning' : 'info'} label={`口径与绑定诊断：${health.issues.length} 项`} detail={<ul>{health.issues.map((i,n) => <li key={n}>{i.message}</li>)}</ul>} /> : null}
  <BusinessDetailSection title="指标口径" description="业务定义及实施说明" icon={<FileTextOutlined />}><BusinessDetailDescriptions column={3} items={keys.map(key => ({ key, label:metricDefinitionLabels[key], span:['businessMeaning','calculation','statisticalScope','timeDescription','nullHandling','aggregationDescription'].includes(key) ? 3 : 1, children:<span className="metric-long-text">{key === 'statisticalPeriod' ? periodLabels[d.statisticalPeriod] : String(d[key] ?? '—')}</span> }))} /></BusinessDetailSection>
  <BusinessDetailSection title="结果绑定" description="指标结果所在的模型及字段" icon={<DatabaseOutlined />} extra={<ContextHelp ariaLabel="结果绑定说明" content="仅登记结果位置，本期不读取业务数据。时间、维度和固定条件共同描述每行含义。" />}>
   {!d.binding ? <Empty description="未绑定结果模型" /> : <><BusinessDetailDescriptions column={3} items={[{key:'model',label:'结果模型',children:resource(d.binding.modelId)},{key:'value',label:'数值字段',children:resource(d.binding.valueFieldId)},{key:'period',label:'时间字段',children:resource(d.binding.periodFieldId)},{key:'format',label:'数值显示',children:d.valueFormat === 'RATIO' ? '0～1 比值 → 百分比' : d.valueFormat === 'PERCENT_VALUE' ? '百分数值 → 百分比' : '普通数值'},{key:'places',label:'小数位数',children:d.decimalPlaces},{key:'grain',label:'结果粒度',children:d.grainDescription ?? '—'}]} />
   <h4>维度</h4>{d.binding.dimensions.length ? d.binding.dimensions.map(dim => <div key={dim.key}>{dim.name}（{dim.key}）：{resource(dim.fieldId)}</div>) : <Typography.Text type="secondary">未设置维度</Typography.Text>}
   <h4>固定范围</h4>{d.binding.fixedFilters.length ? d.binding.fixedFilters.map((f,i) => <div key={i}>{resource(f.fieldId)} <Tag>{f.operator}</Tag> {f.value ?? ''}{i < d.binding!.fixedFilters.length - 1 ? ' AND' : ''}</div>) : <Typography.Text type="secondary">无固定条件</Typography.Text>}
   <h4>辅助解释字段</h4><Space wrap>{d.binding.supportingFieldIds.length ? d.binding.supportingFieldIds.map(id => <span key={id}>{resource(id)}</span>) : '—'}</Space></>}
  </BusinessDetailSection>
  <BusinessDetailSection title="参考资料" description="选填的来源、相关指标与服务导航" icon={<LinkOutlined />}>
   {d.references.length ? d.references.map((r,i) => <div className="metric-reference-row" key={i}><Space wrap><Tag>{resourceLabels[r.resourceKind]}</Tag>{resource(r.resourceId)}{r.targetVersion && <Tag>V{r.targetVersion}</Tag>}<span>{r.role}</span></Space><div className="metric-long-text">{r.note}</div></div>) : <Empty description="未登记参考资料" />}
  </BusinessDetailSection>
 </div>;
};
