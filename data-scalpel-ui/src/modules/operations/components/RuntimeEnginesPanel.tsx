import { Form, Tag, Tooltip } from 'antd';
import type { TableProps } from 'antd';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { searchContains } from '../../../shared/search';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { ManagementDateTime, ManagementListCell } from '../../../shared/components/ManagementListCells';
import { computeBackendTypeLabels } from '../../computeengine';
import { useRuntimeEngines } from '../hooks/useOperations';
import type { RuntimeEngine } from '../model/operations';
import { OperationsTable } from './OperationsTable';
const registrationLabels: Record<string, string> = { CREATED: '待注册', REGISTERING: '注册中', ACTIVE: '已激活', DRAINING: '排空中', INACTIVE: '已停用', DETACHED: '已解绑', ERROR: '异常' };
export const RuntimeEnginesPanel = () => {
  const [form] = Form.useForm<{ keyword?: string }>(); const [keyword, setKeyword] = useState<string>(); const [page, setPage] = useState(0); const [size, setSize] = useState(20);
  const query = useRuntimeEngines({ page, size, sort: 'name', search: searchContains('name', keyword) });
  const columns: TableProps<RuntimeEngine>['columns'] = [
    { title: '计算引擎', width: 245, render: (_, e) => <ManagementListCell primary={<Link to={`/compute-engine/${e.id}`}>{e.name}</Link>} secondary={computeBackendTypeLabels[e.backendType]} /> },
    { title: '注册 / 观测', width: 175, render: (_, e) => <ManagementListCell primary={registrationLabels[e.registrationState] ?? e.registrationState}
      secondary={<Tag color={e.observationState === 'UNREACHABLE' ? 'error' : e.observationState === 'UNKNOWN' ? 'default' : e.dependenciesReady ? 'success' : 'warning'}>{e.observationState === 'UNREACHABLE' ? '不可达' : e.observationState === 'UNKNOWN' ? '观测不可用' : e.dependenciesReady === null ? '依赖状态未知' : e.dependenciesReady ? '正常' : '依赖未就绪'}</Tag>} /> },
    { title: '排队 / 队列容量', width: 160, align: 'right', render: (_, e) => e.stale || e.observationState !== 'REACHABLE' ? '—' : `${e.snapshot?.admissionUsage?.queued ?? '—'} / ${e.snapshot?.admissionCapacity?.maxQueuedExecutions ?? '—'}` },
    { title: '在途 / 容量', width: 150, align: 'right', render: (_, e) => e.stale || e.observationState !== 'REACHABLE' ? '—' : `${e.snapshot?.admissionUsage?.inFlight ?? '—'} / ${e.snapshot?.admissionCapacity?.maxInFlightApplications ?? '—'}` },
    { title: '依赖 / 摘要', width: 270, render: (_, e) => <ManagementListCell primary={<Tooltip title={e.summary}>{e.summary}</Tooltip>}
      secondary={e.stale || e.observationState !== 'REACHABLE' ? '—' : e.snapshot?.dependencies.map(d => `${d.name}: ${d.state}`).join(' · ')} /> },
    { title: '最近观测', dataIndex: 'observedAt', width: 160, render: value => <ManagementDateTime value={value} /> },
  ];
  return <section className="management-workbench">
    <div className="management-filter-strip"><Form form={form} autoComplete="off" layout="inline" className="management-filter-form" onFinish={v => { setKeyword(v.keyword); setPage(0); }}>
      <Form.Item name="keyword"><ManagementSearchInput placeholder="搜索计算引擎名称" allowClear /></Form.Item>
    </Form><ManagementFilterActions form={form} appliedFilters={{ keyword }} onReset={() => { form.resetFields(); setKeyword(undefined); setPage(0); }} /></div>
    <OperationsTable title="计算引擎观测" query={query} columns={columns} page={page} size={size} onPage={(p, s) => { setPage(p); setSize(s); }} />
  </section>;
};
