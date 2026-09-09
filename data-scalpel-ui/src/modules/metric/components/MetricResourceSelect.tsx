import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Select, Space } from 'antd';
import { fetchDataModels } from '../../model';
import { fetchDataServices } from '../../dataservice';
import { andSearch, orSearch, searchContains, searchEquals } from '../../../shared/search';
import { fetchMetricFields, fetchMetrics } from '../api/metricApi';
import type { MetricSnapshot, ResourceKind } from '../model/metric';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
interface Props { kind: ResourceKind; modelId?: string | null; value?: string | null; onChange?: (value: string | undefined) => void; onBlur?: () => void; snapshots?: MetricSnapshot[]; numeric?: boolean; disabled?: boolean; publishedOnly?: boolean }
export const MetricResourceSelect = ({ kind, modelId, value, onChange, onBlur, snapshots = [], numeric, disabled, publishedOnly }: Props) => {
 const [keyword, setKeyword] = useState(''); const [page, setPage] = useState(0);
 const request = { page, size: 30, sort: 'code', search: andSearch(orSearch(searchContains('name', keyword), searchContains('code', keyword)), publishedOnly ? searchEquals('status', 'PUBLISHED') : undefined) };
 const query = useQuery({ queryKey: ['metrics', 'candidates', kind, modelId, request], enabled: !disabled && (kind !== 'MODEL_FIELD' || Boolean(modelId)), queryFn: async () => {
  if (kind === 'MODEL') return fetchDataModels(request);
  if (kind === 'MODEL_FIELD') return fetchMetricFields(modelId!, request);
  if (kind === 'METRIC') return fetchMetrics(request);
  return fetchDataServices(request);
 } });
 const options = (query.data?.content ?? []).filter(item => !numeric || !('fieldType' in item) || ['BYTE','SHORT','INTEGER','LONG','FLOAT','DOUBLE','DECIMAL'].includes(String(item.fieldType))).map(item => ({ value: item.id, label: `${item.name} · ${item.code}${'fieldType' in item ? ` · ${item.fieldType}` : ''}` }));
 if (value && !options.some(o => o.value === value)) { const saved = snapshots.find(s => s.resourceId === value); options.push({ value, label: saved ? `${saved.name ?? value}${saved.contract === null ? '（已失效）' : ''}` : value }); }
 return <Select onBlur={onBlur} value={value ?? undefined} onChange={onChange} allowClear showSearch={{ filterOption: false, onSearch: text => { setKeyword(text); setPage(0); } }} loading={query.isFetching} disabled={disabled || (kind === 'MODEL_FIELD' && !modelId)} options={options} placeholder="搜索并选择资源" popupRender={menu => <>{query.isError && <InlineFeedback tone="error" label="候选资源加载失败" action={<Button size="small" onClick={() => void query.refetch()}>重试</Button>} />}{menu}<Space className="metric-picker-pagination"><Button size="small" disabled={!page} onClick={() => setPage(p => p - 1)}>上一页</Button><span>第 {page + 1} 页</span><Button size="small" disabled={!query.data || page + 1 >= query.data.totalPages} onClick={() => setPage(p => p + 1)}>下一页</Button></Space></>} />;
};
