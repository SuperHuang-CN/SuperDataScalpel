import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Input, Modal, Space, Spin, Typography } from 'antd';
import { fetchMetricDraft, publishMetric } from '../api/metricApi';
import { invalidateMetrics } from '../hooks/useMetrics';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { MetricDefinitionPanel } from './MetricDefinitionPanel';
export const MetricPublishModal = ({ id, name, onClose }: { id: string; name: string; onClose: () => void }) => {
 const [note,setNote]=useState(''); const client=useQueryClient();
 const query=useQuery({queryKey:['metrics','publish-draft',id],queryFn:()=>fetchMetricDraft(id),refetchOnWindowFocus:false});
 const mutation=useMutation({mutationFn:()=>publishMetric(id,query.data!.fingerprint,note),onSuccess:async()=>{await invalidateMetrics(client);onClose();}});
 return <Modal open title={`发布口径 · ${name}`} width={900} rootClassName="business-overlay business-modal-overlay" onCancel={onClose} onOk={()=>mutation.mutate()} okText="确认发布" confirmLoading={mutation.isPending} okButtonProps={{disabled:!query.data?.health.canPublish||query.isError}}>
  {query.isError?<InlineFeedback tone="error" label={query.error.message} action={<Button onClick={()=>void query.refetch()}>重试</Button>} />:query.data?<Space orientation="vertical" className="metric-full-width"><Typography.Text type="secondary">发布保存口径与结果绑定快照；不会修改或运行计算任务。</Typography.Text><label className="metric-full-width">变更说明<Input.TextArea value={note} onChange={e=>setNote(e.target.value)} rows={2} maxLength={2000} autoComplete="off" /></label><MetricDefinitionPanel definition={query.data.definition} references={query.data.references} health={query.data.health} /></Space>:<Spin />}
  {mutation.isError&&<InlineFeedback tone="error" label={mutation.error.message} />}
 </Modal>;
};
