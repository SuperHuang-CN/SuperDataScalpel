import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Modal, Result, Space, Spin, Tabs, Tag } from 'antd';
import { ArrowLeftOutlined, BarChartOutlined, EditOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { useCurrentUser } from '../../system';
import { useMetric, invalidateMetrics } from '../hooks/useMetrics';
import { deleteMetric, disableMetric } from '../api/metricApi';
import { metricStatusLabels } from '../model/metric';
import { MetricBasicsDrawer } from '../components/MetricBasicsDrawer';
import { MetricDefinitionDrawer } from '../components/MetricDefinitionDrawer';
import { MetricDefinitionPanel } from '../components/MetricDefinitionPanel';
import { MetricPublishModal } from '../components/MetricPublishModal';
import { MetricVersionsPanel } from '../components/MetricVersionsPanel';
import { MetricRelatedTasks } from '../components/MetricRelatedTasks';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import '../metric.css';
export const MetricDetailPage = () => {
 const {id=''}=useParams();const navigate=useNavigate();const query=useMetric(id);const client=useQueryClient();const user=useCurrentUser();const permissions=new Set(user.data?.permissions??[]);
 const [edit,setEdit]=useState(false);const [basic,setBasic]=useState(false);const [publishing,setPublishing]=useState(false);const [modal,modalContext]=Modal.useModal();
 const command=useMutation({mutationFn:async(action:'disable'|'delete')=>{if(action==='delete')await deleteMetric(id);else await disableMetric(id);return action;},onSuccess:async action=>{await invalidateMetrics(client);if(action==='delete')navigate('/metrics');}});
 if(query.isError)return <Result status="error" title="指标加载失败" subTitle={query.error.message} extra={<Button onClick={()=>void query.refetch()}>重试</Button>} />;
 if(!query.data)return <Spin />;
 const m=query.data;const confirm=(action:'disable'|'delete')=>modal.confirm({title:`${action==='delete'?'删除':'停用'}指标“${m.name}”`,content:action==='delete'?'仅删除尚未发布的指标登记，不删除模型或任务。':'保留口径与发布历史，已有计算任务继续运行。',okText:action==='delete'?'删除':'停用',okButtonProps:{danger:true},onOk:()=>command.mutateAsync(action).then(()=>undefined)});
 return <div className="model-detail-page metric-detail-page">{modalContext}<div className="model-detail-header business-detail-header"><div className="model-detail-identity"><div className="model-detail-title-row"><Button type="text" icon={<ArrowLeftOutlined />} onClick={()=>navigate('/metrics')}>返回列表</Button><span className="business-detail-resource-icon business-detail-resource-icon-purple"><BarChartOutlined /></span><span className="model-detail-title">{m.name}</span><code>{m.code}</code><Tag color={m.status==='PUBLISHED'?'success':'default'}>{metricStatusLabels[m.status]}</Tag>{m.publishedVersion&&<Tag>V{m.publishedVersion}</Tag>}</div><div className="model-detail-subtitle">负责人：{m.ownerName??'未填写'}{m.hasDraftChanges?' · 有未发布草稿':''}</div></div>
 <Space wrap><Button icon={<ReloadOutlined />} aria-label="刷新指标" onClick={()=>void query.refetch()} />{permissions.has('metric.manage')&&<><Button onClick={()=>setBasic(true)}>修改资料</Button><Button icon={<EditOutlined />} onClick={()=>setEdit(true)}>编辑草稿</Button></>}{permissions.has('metric.publish')&&<><Button type="primary" onClick={()=>setPublishing(true)}>{m.status==='DISABLED'?'发布并启用':m.publishedVersion?'发布变更':'发布'}</Button>{m.status==='PUBLISHED'&&<Button loading={command.isPending} onClick={()=>confirm('disable')}>停用</Button>}</>}{permissions.has('metric.manage')&&!m.publishedVersion&&<Button danger onClick={()=>confirm('delete')}>删除</Button>}</Space></div>
 {command.isError&&<InlineFeedback tone="error" label={command.error.message} />}
 <Tabs className="model-detail-tabs business-detail-tabs" destroyOnHidden items={[{key:'definition',label:'指标口径',children:<MetricDefinitionPanel definition={m.definition} references={m.references} health={m.health} />},{key:'relations',label:'相关任务与服务',children:<MetricRelatedTasks metric={m} />},{key:'versions',label:'发布版本',children:<MetricVersionsPanel id={id} current={m.publishedVersion} />}]} />
 {basic&&<MetricBasicsDrawer metric={m} onClose={()=>setBasic(false)} onCreated={()=>setBasic(false)} />}{edit&&<MetricDefinitionDrawer id={id} name={m.name} onClose={()=>setEdit(false)} onSaved={()=>setEdit(false)} />}{publishing&&<MetricPublishModal id={id} name={m.name} onClose={()=>setPublishing(false)} />}
 </div>;
};
