import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Select, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate } from 'react-router-dom';
import { DetailTableToolbar } from '../../../shared/components/DetailTableToolbar';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { fetchModelMetrics, fetchTaskMetrics } from '../api/metricApi';
import { metricLocationLabel, metricStatusLabels, type Metric, type MetricStatus } from '../model/metric';
import { useCurrentUser } from '../../system';
export const MetricRelationsPanel = ({ modelId, taskId }: { modelId?: string; taskId?: string }) => {
 const [page,setPage] = useState(0); const [size,setSize] = useState(20); const [status,setStatus] = useState<MetricStatus>('PUBLISHED'); const navigate = useNavigate(); const user = useCurrentUser();
 const query = useQuery({ queryKey:['metrics','relations',modelId,taskId,status,page,size],queryFn:async () => {
  const request={page,size,sort:'name'}; if(taskId){const r=await fetchTaskMetrics(taskId,request,status);return {data:r.metrics,relation:r};}
  return {data:await fetchModelMetrics(modelId!,request,status),relation:null};
 } });
 const columns: ColumnsType<Metric> = [
  {title:'指标 / 版本',key:'name',render:(_,m) => <><Button type="link" size="small" onClick={() => navigate(`/metrics/${m.id}`)}>{m.name}</Button><div><Typography.Text type="secondary">{m.publishedVersion ? `V${m.publishedVersion}` : '未发布'} · {metricStatusLabels[m.status]}</Typography.Text></div></>},
  {title:'输出模型 / 数值字段',key:'binding',render:(_,m) => <>{m.references.find(r=>r.path==='binding.modelId')?.name ?? '—'}<div><code>{m.references.find(r=>r.path==='binding.valueFieldId')?.code ?? '—'}</code></div></>},
  {title:'粒度 / 关联依据',key:'evidence',render:(_,m) => {const relation=query.data?.relation;const match=relation?.fieldEvidence.some(e=>e.currentDefinition&&m.definition.binding?.valueFieldId&&e.fieldIds.includes(m.definition.binding.valueFieldId));return <>{m.definition.grainDescription ?? '—'}<div>{taskId ? <Space wrap><Tag>{match ? '模型关联 · 字段命中' : '模型级关联'}</Tag><Typography.Text type="secondary">定义 V{relation?.definitionVersion ?? '—'}</Typography.Text></Space> : <Tag>结果绑定</Tag>}</div></>;}}
 ];
 return <div className="metric-relation-panel"><DetailTableToolbar title={<Space>关联指标<ContextHelp ariaLabel="关联精度说明" content="通过输出模型查询关联指标。字段命中只补充说明，不证明整张表或所有月份已更新；Spark JAR 的写权限声明不等于实际写入。" /></Space>} total={query.data?.data.totalElements ?? 0} current={page+1} pageSize={size} onChange={(p,s)=>{setPage(p-1);setSize(s);}} onRefresh={()=>void query.refetch()} refreshing={query.isFetching} extra={user.data?.permissions.includes('metric.manage') ? <Select size="small" value={status} options={Object.entries(metricStatusLabels).map(([value,label])=>({value,label}))} onChange={value=>{setStatus(value);setPage(0);}} /> : null} />
 {query.isError ? <InlineFeedback tone="error" label={query.error.message} action={<Button onClick={()=>void query.refetch()}>重试</Button>} /> : <>
 {query.data?.relation && query.data.relation.resolution !== 'MODEL_RELATIONS' && <InlineFeedback label={query.data.relation.resolution === 'UNCONFIGURED' ? '任务尚无已保存定义' : '暂无可解析的输出模型关联'} />}
 {query.data?.relation?.outputModels.map(m=><div key={m.modelId}><Button type="link" size="small" onClick={()=>navigate(`/model/${m.modelId}`)}>{m.modelName}</Button><Typography.Text type="secondary">{[...new Set(m.locations.map(metricLocationLabel))].join('；')}</Typography.Text></div>)}
 {query.data?.relation?.fieldEvidence.filter(e=>!e.currentDefinition).map((e,i)=><InlineFeedback key={i} label={`现有字段血缘来自旧定义 V${e.definitionVersion}，未作为当前字段命中依据`} />)}
 <Table size="small" rowKey="id" columns={columns} dataSource={query.data?.data.content ?? []} loading={query.isFetching} pagination={false} scroll={{x:600}} />
 </>}
 </div>;
};
