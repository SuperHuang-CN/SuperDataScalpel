import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Drawer, Select, Space, Spin, Table, Tag, Typography } from 'antd';
import { fetchMetricVersion, fetchMetricVersions } from '../api/metricApi';
import { metricDefinitionLabels, type MetricSnapshot, type MetricVersion } from '../model/metric';
import { DetailTableToolbar } from '../../../shared/components/DetailTableToolbar';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { MetricDefinitionPanel } from './MetricDefinitionPanel';

const contractDescription = (reference?: MetricSnapshot): string => {
  if (!reference) return '未引用';
  if (!reference.contract) return '引用已失效';
  const parts = reference.contract.split('|');
  if (reference.resourceKind === 'MODEL') {
    const [source, catalog, schema, table, mode] = parts;
    return `${[catalog, schema, table].filter(Boolean).join('.')}（${mode}；数据源 ${source}）`;
  }
  if (reference.resourceKind === 'MODEL_FIELD') {
    const [code, type, length, precision, scale] = parts;
    const detail = [['长度', length], ['精度', precision], ['小数位', scale]]
      .filter(([, value]) => value && value !== 'null')
      .map(([label, value]) => `${label} ${value}`);
    return `${code} · ${type}${detail.length ? `（${detail.join('，')}）` : ''}`;
  }
  return reference.targetVersion ? `V${reference.targetVersion}` : '当前资源';
};

const Snapshot = ({ id, version, current }: { id: string; version: number; current: number }) => {
  const [compare, setCompare] = useState<number | undefined>(version > 1 ? version - 1 : undefined);
  const query = useQuery({
    queryKey: ['metrics', 'version', id, version],
    queryFn: () => fetchMetricVersion(id, version),
  });
  const other = useQuery({
    queryKey: ['metrics', 'version', id, compare],
    queryFn: () => fetchMetricVersion(id, compare!),
    enabled: Boolean(compare),
  });
  if (query.isError) {
    return <InlineFeedback tone="error" label={query.error.message} action={<Button onClick={() => void query.refetch()}>重试</Button>} />;
  }
  if (!query.data) return <Spin />;

  const selected = query.data;
  const compared = other.data;
  const changed = compared
    ? (Object.keys(metricDefinitionLabels) as (keyof MetricVersion['definition'])[])
      .filter(key => JSON.stringify(selected.definition[key]) !== JSON.stringify(compared.definition[key]))
      .map(key => metricDefinitionLabels[key])
    : [];
  const paths = [...new Set([...selected.references, ...(compared?.references ?? [])].map(r => r.path))];
  const contracts = paths.map(path => {
    const left = selected.references.find(r => r.path === path);
    const right = compared?.references.find(r => r.path === path);
    return {
      path,
      name: left?.name ?? right?.name ?? left?.code ?? right?.code ?? path,
      left,
      right,
      changed: left?.resourceId !== right?.resourceId || left?.contract !== right?.contract,
    };
  }).filter(row => !compared || row.changed);
  const changes = [...changed, ...(compared && contracts.length ? ['引用资源结构'] : [])];

  return <>
    <Space wrap>
      <Typography.Text>查看 V{version}；对比版本</Typography.Text>
      <Select
        allowClear value={compare} placeholder="选择版本"
        options={Array.from({ length: current }, (_, i) => i + 1).filter(v => v !== version).map(v => ({ value: v, label: `V${v}` }))}
        onChange={setCompare}
      />
    </Space>
    {other.isFetching && <Spin size="small" />}
    {other.isError && <InlineFeedback tone="error" label={other.error.message} action={<Button onClick={() => void other.refetch()}>重试</Button>} />}
    {compared && <InlineFeedback label={changes.length ? `差异：${changes.join('、')}` : '口径与绑定结构相同'} />}
    {contracts.length > 0 && <details>
      <summary>{compared ? '查看资源结构差异' : '查看发布时的资源结构'}</summary>
      <Table
        size="small" rowKey="path" pagination={false} dataSource={contracts}
        columns={[
          { title: '引用资源', dataIndex: 'name' },
          { title: `V${version}`, key: 'left', render: (_, row) => contractDescription(row.left) },
          ...(compared ? [{ title: `V${compare}`, key: 'right', render: (_: unknown, row: typeof contracts[number]) => contractDescription(row.right) }] : []),
        ]}
      />
    </details>}
    <div className={compared ? 'metric-version-comparison' : ''}>
      <section><h3>V{version}</h3><MetricDefinitionPanel definition={selected.definition} references={selected.references} /></section>
      {compared && <section><h3>V{compare}</h3><MetricDefinitionPanel definition={compared.definition} references={compared.references} /></section>}
    </div>
  </>;
};

export const MetricVersionsPanel = ({ id, current }: { id: string; current: number | null }) => {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [version, setVersion] = useState<number | null>(null);
  const query = useQuery({
    queryKey: ['metrics', 'versions', id, page, size],
    queryFn: () => fetchMetricVersions(id, { page, size, sort: '-version' }),
  });
  return <>
    <DetailTableToolbar
      title="发布版本" total={query.data?.totalElements ?? 0} current={page + 1} pageSize={size}
      onChange={(p, s) => { setPage(p - 1); setSize(s); }} onRefresh={() => void query.refetch()}
    />
    {query.isError
      ? <InlineFeedback tone="error" label={query.error.message} action={<Button onClick={() => void query.refetch()}>重试</Button>} />
      : <Table<MetricVersion>
        rowKey="id" size="small" loading={query.isFetching} pagination={false} dataSource={query.data?.content ?? []}
        columns={[
          { title: '版本', dataIndex: 'version', render: (v: number) => <Space><Button type="link" size="small" onClick={() => setVersion(v)}>V{v}</Button>{v === current && <Tag color="success">当前版本</Tag>}</Space> },
          { title: '变更说明', dataIndex: 'changeNote', render: value => value ?? '—' },
          { title: '发布人', dataIndex: 'publishedBy' },
          { title: '发布时间', dataIndex: 'publishedAt', render: (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false }) },
        ]}
      />}
    {version && <Drawer open title={`指标口径快照 V${version}`} width={1100} rootClassName="business-overlay business-drawer-overlay" onClose={() => setVersion(null)}>
      <Typography.Text type="secondary">快照仅保存口径与绑定，不包含历史业务数据。</Typography.Text>
      <Snapshot key={version} id={id} version={version} current={current ?? version} />
    </Drawer>}
  </>;
};
