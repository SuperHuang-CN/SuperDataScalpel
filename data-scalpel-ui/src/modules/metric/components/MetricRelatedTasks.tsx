import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Empty, Space, Table, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { fetchMetricTasks } from '../api/metricApi';
import { metricLocationLabel, referenceHref, type Metric } from '../model/metric';
import { DetailTableToolbar } from '../../../shared/components/DetailTableToolbar';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { useCurrentUser } from '../../system';
export const MetricRelatedTasks = ({ metric }: { metric:Metric }) => {
 const user=useCurrentUser(); const permissions=new Set(user.data?.permissions??[]);const navigate=useNavigate();
 const [page,setPage]=useState(0);const [size,setSize]=useState(20);const allowed=permissions.has('task.view')&&permissions.has('model.view');
 const query=useQuery({queryKey:['metrics','tasks',metric.id,page,size],queryFn:()=>fetchMetricTasks(metric.id,{page,size,sort:'name'}),enabled:allowed&&Boolean(metric.definition.binding?.modelId)});
 const bindingMissing = Boolean(metric.definition.binding) && !metric.references.find(r => r.path === 'binding.modelId')?.contract;
 const services=metric.references.filter(r=>r.resourceKind==='DATA_SERVICE');
 return <div className="metric-detail-content"><DetailTableToolbar title={<Space>相关任务<ContextHelp ariaLabel="任务关联说明" content="以该结果模型当前的输出任务关系进行反查。只说明可能影响该指标，不能证明任务已按口径完成全部计算。" /></Space>} total={query.data?.totalElements??0} current={page+1} pageSize={size} onChange={(p,s)=>{setPage(p-1);setSize(s);}} onRefresh={allowed?()=>void query.refetch():undefined} />
 {!allowed?<InlineFeedback label="查看相关任务需要任务和模型查看权限" />:!metric.definition.binding?<Empty description="绑定结果模型后可查找相关任务" />:bindingMissing?<Empty description="结果模型引用已失效，请修订绑定" />:query.isError?<InlineFeedback tone="error" label={query.error.message} action={<Button onClick={()=>void query.refetch()}>重试</Button>} />:<Table size="small" rowKey="taskId" dataSource={query.data?.content??[]} loading={query.isFetching} pagination={false} columns={[{title:'任务',dataIndex:'taskName',render:(name:string,t)=><Button type="link" size="small" onClick={()=>navigate(`/task/${t.taskId}`)}>{name}</Button>},{title:'类型 / 定义版本',key:'version',render:(_,t)=><>{t.taskType}<div>定义 V{t.definitionVersion}</div></>},{title:'状态',dataIndex:'taskStatus'},{title:'关联依据',key:'source',render:(_,t)=><Tag>{[...new Set(t.locations.map(metricLocationLabel))].join('；')}</Tag>}]} />}
 <h4>参考数据服务</h4>{services.length?services.map(s=><Space key={s.path}>{s.contract&&permissions.has('service.view')?<Button type="link" onClick={()=>navigate(referenceHref(s))}>{s.name}</Button>:<span>{s.name??s.resourceId}</span>}<Tag>{s.status??'引用已失效'}</Tag></Space>):<Empty description="未关联数据服务，可在参考资料中添加" />}
 </div>;
};
