import { useRef, useState } from 'react';
import { Button, Form, Input, Select, Table, Tag, Tooltip } from 'antd';
import { BarChartOutlined, EditOutlined, FormOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useNavigate } from 'react-router-dom';
import { DirectoryTreePanel, findDirectoryDescendantIds, useDirectoryTree, type DirectorySelection } from '../../directory';
import { useCurrentUser } from '../../system';
import { andSearch, orSearch, searchContains, searchEquals } from '../../../shared/search';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { ManagementListCell, ManagementCode, ManagementName } from '../../../shared/components/ManagementListCells';
import { ManagementAdaptiveMoreFilters, ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { useMetrics } from '../hooks/useMetrics';
import { bindingLabels, metricKindLabels, metricStatusLabels, periodLabels, type Metric, type MetricKind, type MetricStatus } from '../model/metric';
import { MetricBasicsDrawer } from '../components/MetricBasicsDrawer';
import { MetricDefinitionDrawer } from '../components/MetricDefinitionDrawer';
import { MetricExcelActions } from '../components/MetricExcelActions';
import '../metric.css';
interface Filters { keyword?: string; kind?: MetricKind; status?: MetricStatus; ownerName?: string }
export const MetricListPage = () => {
 const navigate=useNavigate(); const user=useCurrentUser(); const permissions=new Set(user.data?.permissions ?? []); const canManage=permissions.has('metric.manage');
 const directories=useDirectoryTree('METRIC',permissions.has('directory.view')); const [directory,setDirectory]=useState<DirectorySelection>(undefined);
 const [form]=Form.useForm<Filters>(); const [filters,setFilters]=useState<Filters>({}); const [page,setPage]=useState(0); const [size,setSize]=useState(20);
 const [advancedForm] = Form.useForm<Filters>();
 const [advancedOpen, setAdvancedOpen] = useState(false);
 const advancedSnapshot = useRef<Filters>({});
 const advancedDraft = Form.useWatch((values: Filters) => values, { form: advancedForm, preserve: true });
 const advancedCount = [advancedDraft?.kind, advancedDraft?.status, advancedDraft?.ownerName].filter(value => value !== undefined && value !== '').length;
 const closeAdvanced = () => { if (advancedOpen) advancedForm.setFieldsValue({ kind: undefined, status: undefined, ownerName: undefined, ...advancedSnapshot.current }); setAdvancedOpen(false); };
 const [selectedIds, setSelectedIds] = useState<string[]>([]);
 const [basics,setBasics]=useState<Metric | 'new' | null>(null); const [editing,setEditing]=useState<Metric | null>(null);
 const directoryFilter=directory===undefined?undefined:directory===null?'directoryId:null':orSearch(...findDirectoryDescendantIds(directories.data ?? [],directory).map(id=>searchEquals('directoryId',id)));
 const appliedSearch=andSearch(orSearch(searchContains('name',filters.keyword),searchContains('code',filters.keyword)),searchEquals('kind',filters.kind),searchEquals('status',filters.status),searchContains('ownerName',filters.ownerName),directoryFilter);
 const query=useMetrics({page,size,sort:'-updatedAt',search:appliedSearch});
 const columns: ColumnsType<Metric>=[
  {title:'指标名称 / 编码',key:'identity',render:(_,m)=><ManagementListCell icon={<BarChartOutlined />} primary={<ManagementName name={m.name} code={m.code} description={m.summary}><Button type="link" size="small" onClick={()=>navigate(`/metrics/${m.id}`)}>{m.name}</Button></ManagementName>} secondary={m.code !== m.name ? <ManagementCode value={m.code} /> : undefined} />},
  {title:'类型 / 周期',key:'kind',width:130,render:(_,m)=><ManagementListCell primary={metricKindLabels[m.kind]} secondary={`${periodLabels[m.definition.statisticalPeriod]} · ${m.publishedVersion?`V${m.publishedVersion}`:'未发布'}`} />},
  {title:'结果模型 / 数值字段',key:'binding',render:(_,m)=><ManagementListCell primary={m.references.find(r=>r.path==='binding.modelId')?.name ?? '未绑定结果'} secondary={<code>{m.references.find(r=>r.path==='binding.valueFieldId')?.code ?? '—'}</code>} />},
  {title:'状态 / 绑定',key:'status',width:145,render:(_,m)=><ManagementListCell primary={<Tag color={m.status==='PUBLISHED'?'success':m.status==='DISABLED'?'default':'warning'}>{metricStatusLabels[m.status]}</Tag>} secondary={bindingLabels[m.health.bindingStatus]} />},
  {title:'负责人',dataIndex:'ownerName',width:115,render:value=>value ?? '—'},
  {title:'操作',key:'actions',align:'center',fixed:'right',width:88,render:(_,m)=>canManage?<div className="modeling-row-actions">
   <Tooltip title="编辑口径"><Button type="text" size="small" icon={<EditOutlined />} aria-label={`编辑${m.name}口径`} onClick={()=>setEditing(m)} /></Tooltip>
   <Tooltip title="修改资料"><Button type="text" size="small" icon={<FormOutlined />} aria-label={`修改${m.name}基础资料`} onClick={()=>setBasics(m)} /></Tooltip>
  </div>:null}
 ];
 return <><div className={permissions.has('directory.view')?'directory-management-layout metric-management modeling-workspace':'page-stack metric-management modeling-workspace'}>
 {permissions.has('directory.view')&&<DirectoryTreePanel scope="METRIC" label="指标目录" tree={directories.data ?? []} loading={directories.isFetching} selection={directory} canManage={permissions.has('directory.manage')} onSelectionChange={id=>{setDirectory(id);setPage(0);setSelectedIds([]);}} />}
 <section className="management-workbench"><div className="management-filter-strip modeling-list-controls"><Form id="metric-list-filters" name="metric-list-filters" form={form} autoComplete="off" layout="inline" className="management-filter-form" onFinish={value=>{setFilters({...value,...advancedForm.getFieldsValue(true)});setPage(0);setSelectedIds([]);}}>
 <Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索指标名称或编码" /></Form.Item></Form><ManagementAdaptiveMoreFilters count={advancedCount} open={advancedOpen}
 onOpenChange={open => { if (open) { advancedSnapshot.current = advancedForm.getFieldsValue(true); setAdvancedOpen(true); } else closeAdvanced(); }}
 onClear={() => advancedForm.setFieldsValue({ kind: undefined, status: undefined, ownerName: undefined })}
 onCancel={closeAdvanced} onConfirm={() => setAdvancedOpen(false)}>
 <Form form={advancedForm} name="metric-advanced-filters" className="modeling-advanced-filters" autoComplete="off" layout="vertical" onFinish={() => { setAdvancedOpen(false); form.submit(); }}>
 <Form.Item name="kind" label="类型"><Select allowClear placeholder="全部类型" options={Object.entries(metricKindLabels).map(([value,label])=>({value,label}))} className="advanced-filter-select" /></Form.Item>
 <Form.Item name="status" label="状态"><Select allowClear placeholder="全部状态" options={Object.entries(metricStatusLabels).map(([value,label])=>({value,label}))} className="advanced-filter-select" /></Form.Item>
 <Form.Item name="ownerName" label="业务负责人"><Input allowClear placeholder="业务负责人" className="advanced-filter-select" /></Form.Item>
 </Form></ManagementAdaptiveMoreFilters><div className="modeling-page-actions management-filter-actions"><ManagementFilterActions form={form} formId="metric-list-filters" appliedFilters={filters} additionalActive={advancedCount > 0 || directory !== undefined} loading={query.isFetching} onReset={()=>{form.resetFields();advancedForm.resetFields();setAdvancedOpen(false);setFilters({});setDirectory(undefined);setPage(0);setSelectedIds([]);}} /><div className="modeling-page-commands"><MetricExcelActions search={appliedSearch} selectedIds={selectedIds} onImported={() => { setSelectedIds([]); setPage(0); }} /><Button icon={<ReloadOutlined />} aria-label="刷新指标列表" onClick={()=>void query.refetch()} />{canManage&&<Button type="primary" icon={<PlusOutlined />} onClick={()=>setBasics('new')}>新建指标</Button>}</div></div></div><div className="management-results-surface"><div className="management-result-toolbar"><div className="management-result-title"><BarChartOutlined aria-hidden />指标列表 <span className="management-result-count">共 {query.data?.totalElements ?? 0} 项</span></div>{selectedIds.length > 0 && <Button type="text" onClick={() => setSelectedIds([])}>清空勾选</Button>}</div>
 {query.isError&&<InlineFeedback tone="error" label={query.error.message} action={<Button onClick={()=>void query.refetch()}>重试</Button>} />}
 {directories.isError&&<InlineFeedback tone="error" label="指标目录加载失败" action={<Button onClick={()=>void directories.refetch()}>重试</Button>} />}
 <Table className="management-table" size="small" rowKey="id" rowSelection={{ selectedRowKeys: selectedIds, preserveSelectedRowKeys: true, onChange: keys => setSelectedIds(keys.map(String)) }} columns={columns} dataSource={query.data?.content ?? []} loading={query.isFetching} scroll={{x:960,y:'100%'}} pagination={{current:page+1,pageSize:size,total:query.data?.totalElements ?? 0,showSizeChanger:true,placement:['bottomEnd'],onChange:(p,s)=>{setPage(s===size?p-1:0);setSize(s);}}} />
 </div></section></div>
 {basics&&<MetricBasicsDrawer metric={basics==='new'?undefined:basics} directoryId={directory} onClose={()=>setBasics(null)} onCreated={m=>{setBasics(null);if(basics==='new')setEditing(m);}} />}
 {editing&&<MetricDefinitionDrawer id={editing.id} name={editing.name} onClose={()=>setEditing(null)} onSaved={()=>{setEditing(null);navigate(`/metrics/${editing.id}`);}} />}
 </>;
};
