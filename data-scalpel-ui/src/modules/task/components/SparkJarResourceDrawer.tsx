import { OverlayTitle } from '../../../shared/components/OverlayTitle';
import { DatabaseOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Drawer, Form, Input, Radio, Select, Space, Table, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { CompactAlert, ContextHelp } from '../../../shared/components/ContextualFeedback';
import { buildDataModelSearch, useDataModel, useDataModels } from '../../model';
import {
  buildDataSourceSearch, dataSourcePurposeLabels, jdbcTableIdentifierDisplayName, jdbcTableIdentifierKey,
  useDataSource, useDataSources, useDataSourceTables, type DataSource, type TableIdentifier,
} from '../../datasource';
import { CanvasKafkaTopicSelect } from '../canvas/components/CanvasKafkaSelectors';
import type { SparkJarResourceType } from '../model/task';
import type { SparkJarBindingDraft, SparkJarResourceSelection } from '../model/sparkJarResourceConfiguration';

export type { SparkJarBindingDraft, SparkJarResourceSelection } from '../model/sparkJarResourceConfiguration';

interface Props {
  initial?: SparkJarBindingDraft;
  initialTable?: TableIdentifier;
  bindingNames: string[];
  streaming: boolean;
  onClose: () => void;
  onConfirm: (selection: SparkJarResourceSelection) => void;
  saving?: boolean;
  error?: string;
}

const canUseSource = (source: DataSource, type: SparkJarResourceType, access: SparkJarBindingDraft['accessMode']) => {
  if (!source.enabled) return false;
  if (type === 'MODEL') return source.connectionKind === 'JDBC' && (source.purposes.includes('STORAGE')
    || access === 'READ' && source.purposes.includes('SOURCE'));
  if (source.connectionKind !== (type === 'KAFKA_TOPIC' ? 'KAFKA' : 'JDBC')) return false;
  return (access === 'WRITE' || source.purposes.includes('SOURCE'))
    && (access === 'READ' || source.purposes.includes('DISTRIBUTION'));
};

/** A local draft: cancelling never changes the task's binding or sample configuration. */
export const SparkJarResourceDrawer = ({ initial, initialTable, bindingNames, streaming, onClose, onConfirm, saving = false, error }: Props) => {
  const [form] = Form.useForm<SparkJarBindingDraft>();
  const resourceType = Form.useWatch('resourceType', form) ?? initial?.resourceType ?? 'MODEL';
  const accessMode = Form.useWatch('accessMode', form) ?? initial?.accessMode ?? 'READ';
  const resourceId = Form.useWatch('resourceId', form);
  const selectedModel = useDataModel(resourceId, resourceType === 'MODEL' && Boolean(resourceId));
  const [storageId, setStorageId] = useState<string>();
  const effectiveSourceId = resourceType === 'MODEL'
    ? storageId ?? selectedModel.data?.model.storageDataSourceId : resourceId;
  const selectedSource = useDataSource(effectiveSourceId, Boolean(effectiveSourceId));
  const [sourceSearch, setSourceSearch] = useState('');
  const [sourceKeyword, setSourceKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [keyword, setKeyword] = useState('');
  const [table, setTable] = useState<TableIdentifier | null>(initialTable ?? null);
  const [nameEdited, setNameEdited] = useState(Boolean(initial?.bindingName));
  const [selectionError, setSelectionError] = useState('');
  useEffect(() => { const timer = setTimeout(() => setKeyword(search.trim()), 300); return () => clearTimeout(timer); }, [search]);
  useEffect(() => { const timer = setTimeout(() => setSourceKeyword(sourceSearch.trim()), 300); return () => clearTimeout(timer); }, [sourceSearch]);
  const sources = useDataSources({
    search: buildDataSourceSearch({ keyword: sourceKeyword, enabled: true,
      purposesAny: resourceType === 'MODEL' ? accessMode === 'READ' ? ['SOURCE', 'STORAGE'] : ['STORAGE'] : undefined }),
    hasPublishedModels: resourceType === 'MODEL', page: 0, size: 100, sort: 'name',
  });
  const modelMatchesSource = selectedModel.data?.model.status === 'PUBLISHED'
    && selectedModel.data.model.storageDataSourceId === effectiveSourceId;
  const sourceOptions = useMemo(() => [...new Map([
    ...(selectedSource.data && (resourceType !== 'MODEL' || modelMatchesSource) ? [selectedSource.data] : []), ...(sources.data?.content ?? []),
  ].filter((item) => canUseSource(item, resourceType, accessMode)).map((item) => [item.id, item])).values()]
    .map((item) => ({ value: item.id, label: `${item.name} · ${item.type}`, name: item.name, purposes: item.purposes })),
  [selectedSource.data, sources.data, resourceType, accessMode, modelMatchesSource]);
  const models = useDataModels({ search: buildDataModelSearch({ status: 'PUBLISHED', storageDataSourceId: effectiveSourceId, keyword }),
    page: 0, size: 100, sort: 'code' }, resourceType === 'MODEL' && Boolean(effectiveSourceId));
  const needsTable = resourceType === 'JDBC_DATA_SOURCE' && accessMode !== 'WRITE';
  const tables = useDataSourceTables(effectiveSourceId, { keyword: keyword || undefined, includeViews: false, limit: 100 },
    needsTable && Boolean(effectiveSourceId));
  const sourceValid = Boolean(selectedSource.data && canUseSource(selectedSource.data, resourceType, accessMode));
  const suggestName = (code: string) => {
    if (nameEdited) return;
    const base = `${accessMode === 'WRITE' ? 'target' : 'source'}_${code.replace(/[^A-Za-z0-9_]/g, '_')}`.slice(0, 90);
    let name = base;
    for (let suffix = 2; bindingNames.includes(name); suffix++) name = `${base}_${suffix}`;
    form.setFieldValue('bindingName', name);
  };
  const clearSelection = () => {
    setSelectionError('');
    form.setFieldsValue({ resourceId: undefined, topicName: undefined });
    setTable(null); setSearch('');
    if (!nameEdited) form.setFieldValue('bindingName', '');
  };
  const submit = async () => {
    if (saving) return;
    if (!sourceValid) { setSelectionError('请选择当前用途下可用的数据源'); return; }
    if (resourceType === 'MODEL' && !resourceId) { setSelectionError('请选择已发布模型'); return; }
    if (resourceType === 'MODEL' && !modelMatchesSource) { setSelectionError('所选模型尚未确认可用，请检查模型状态或重新选择'); return; }
    if (needsTable && !table) { setSelectionError('请选择本地开发使用的表'); return; }
    const binding = await form.validateFields();
    onConfirm({ binding: { ...binding, bindingName: binding.bindingName.trim(),
      topicName: resourceType === 'KAFKA_TOPIC' ? binding.topicName : null }, table: needsTable ? table : null });
  };
  return <Drawer open placement="right" size={780} rootClassName="business-overlay business-drawer-overlay resource-workspace-overlay"
    className="spark-jar-resource-drawer" title={<OverlayTitle icon={<DatabaseOutlined />} title={initial ? '编辑任务资源' : '添加任务资源'} description="选择用途与资源，建立代码引用" />}
    closable={saving ? false : { placement: 'end' }} maskClosable={!saving} keyboard={!saving} onClose={saving ? undefined : onClose}
    footer={<div className="spark-jar-resource-footer"><Typography.Text type="secondary">只建立代码引用，不执行读写</Typography.Text>
      <Space><Button disabled={saving} onClick={onClose}>取消</Button><Button type="primary" loading={saving} onClick={() => void submit().catch(() => undefined)}>确认绑定</Button></Space></div>}>
    {error && <CompactAlert type="error" message={error} />}
    <Form form={form} layout="vertical" autoComplete="off" disabled={saving} initialValues={initial ?? { resourceType: 'MODEL', accessMode: 'READ' }}>
      <div className="spark-jar-resource-form-top">
        <Form.Item name="accessMode" label="资源用途" rules={[{ required: true }]}>
          <Radio.Group className="spark-jar-purpose-options" options={[
            { value: 'READ', label: <span>输入<small>读取数据</small></span> },
            { value: 'WRITE', label: <span>输出<small>写入结果</small></span> },
            { value: 'READ_WRITE', label: <span>输入及输出<small>读取与写入</small></span> },
          ]} />
        </Form.Item>
        <Form.Item name="resourceType" label="资源类型" rules={[{ required: true }]}>
          <Select options={[{ value: 'MODEL', label: '数据模型' }, { value: 'JDBC_DATA_SOURCE', label: 'JDBC 数据源' },
            ...(streaming ? [{ value: 'KAFKA_TOPIC', label: 'Kafka Topic' }] : [])]}
            onChange={() => { clearSelection(); setStorageId(''); setSourceSearch(''); }} />
        </Form.Item>
      </div>
      <Form.Item label={resourceType === 'MODEL' ? <Space>模型所属数据源<ContextHelp ariaLabel="模型所属数据源说明"
        content="仅显示有可选已发布模型的 JDBC 连接。输入支持数据源或数据存储；输出要求数据存储用途。" /></Space> : '数据源'} required>
        <Select value={effectiveSourceId || undefined} showSearch filterOption={false} onSearch={setSourceSearch}
          loading={sources.isFetching || selectedSource.isFetching} options={sourceOptions} placeholder="按名称或编码搜索"
          labelRender={({ value, label }) => sourceOptions.some((option) => option.value === value) ? label
            : selectedSource.data?.id === value ? `${selectedSource.data.name}（请检查可用性）` : label}
          optionRender={(option) => <Space wrap><span>{option.label}</span>{option.data.purposes.map((purpose) =>
            <Tag key={purpose}>{dataSourcePurposeLabels[purpose]}</Tag>)}</Space>}
          notFoundContent={sources.isFetching ? '正在加载…' : sources.isError ? '加载失败，请重试'
            : resourceType === 'MODEL' ? '没有符合条件的已发布模型所属连接' : '没有符合条件的数据源'}
          onChange={(id: string) => { clearSelection(); setStorageId(id);
            if (resourceType !== 'MODEL') { form.setFieldValue('resourceId', id); suggestName(sourceOptions.find((item) => item.value === id)?.name ?? 'data'); }
          }} />
      </Form.Item>
      {sources.isError && <CompactAlert type="error" message="数据源加载失败" action={<Button onClick={() => void sources.refetch()}>重试</Button>} />}
      {(sources.data?.totalElements ?? 0) > 100 && <Typography.Text type="secondary">数据源较多，请搜索名称或编码缩小范围</Typography.Text>}
      {resourceType === 'MODEL' && selectedModel.isError && <CompactAlert type="error" message="已选模型加载失败" action={<Button onClick={() => void selectedModel.refetch()}>重试</Button>} />}
      {resourceType === 'MODEL' && resourceId && selectedModel.isSuccess && !modelMatchesSource
        && <CompactAlert type="error" message="已选模型未发布或不属于当前连接，请重新选择" />}
      {effectiveSourceId && selectedSource.isError && <CompactAlert type="error" message="当前数据源不可用" action={<Button onClick={() => void selectedSource.refetch()}>重试</Button>} />}
      {effectiveSourceId && selectedSource.isSuccess && !sourceValid && <CompactAlert type="error" message="当前数据源不支持此用途，请重新选择" />}
      {selectionError && <CompactAlert type="error" message={selectionError} />}
      <Form.Item name="resourceId" rules={[{ required: true, message: '请选择资源' }]} hidden><Input /></Form.Item>
      {(resourceType === 'MODEL' || needsTable) && <div className="spark-jar-resource-candidates">
        <Input autoComplete="off" prefix={<SearchOutlined />} allowClear value={search} disabled={saving || !effectiveSourceId}
          placeholder={resourceType === 'MODEL' ? '搜索已发布模型' : '搜索表名'} onChange={(event) => setSearch(event.target.value)} />
        {resourceType === 'MODEL' ? <Table size="small" rowKey="id" pagination={false} scroll={{ y: 180 }} loading={models.isFetching}
          dataSource={effectiveSourceId ? models.data?.content ?? [] : []}
          locale={{ emptyText: effectiveSourceId ? '没有匹配的已发布模型' : '请先选择模型所属数据源' }}
          columns={[{ title: '模型', dataIndex: 'name', ellipsis: true }, { title: '编码', dataIndex: 'code', ellipsis: true }]}
          rowSelection={{ type: 'radio', getCheckboxProps: () => ({ disabled: saving || !sourceValid }), selectedRowKeys: resourceId ? [resourceId] : [], onChange: (_, rows) => {
            if (rows[0]) { form.setFieldValue('resourceId', rows[0].id); suggestName(rows[0].code); setSelectionError(''); }
          } }} />
          : <Table size="small" rowKey={(item) => jdbcTableIdentifierKey(item.identifier)} pagination={false} scroll={{ y: 180 }}
            dataSource={effectiveSourceId ? tables.data?.tables ?? [] : []} loading={tables.isFetching}
            locale={{ emptyText: effectiveSourceId ? '没有匹配的数据表' : '请先选择数据源' }}
            columns={[{ title: '数据表', render: (_, item) => jdbcTableIdentifierDisplayName(item.identifier) }, { title: '说明', dataIndex: 'comment', ellipsis: true }]}
            rowSelection={{ type: 'radio', getCheckboxProps: () => ({ disabled: saving }), selectedRowKeys: table ? [jdbcTableIdentifierKey(table)] : [], onChange: (_, rows) => {
              if (rows[0]) { setTable(rows[0].identifier); suggestName(rows[0].identifier.table); setSelectionError(''); }
            } }} />}
        {(resourceType === 'MODEL' ? models.isError : tables.isError) && <CompactAlert type="error" message="资源列表加载失败"
          action={<Button onClick={() => void (resourceType === 'MODEL' ? models.refetch() : tables.refetch())}>重试</Button>} />}
        {(resourceType === 'MODEL' ? (models.data?.totalElements ?? 0) > 100 : tables.data?.truncated) && <Typography.Text type="secondary">仅展示前 100 项，请搜索缩小范围</Typography.Text>}
      </div>}
      {resourceType === 'KAFKA_TOPIC' && <Form.Item name="topicName" label="Topic" rules={[{ required: true, message: '请选择 Topic' }]}>
        <CanvasKafkaTopicSelect dataSourceId={resourceId ?? ''} placeholder="搜索 Topic" /></Form.Item>}
      <Form.Item name="bindingName" label={<Space>代码引用名<ContextHelp ariaLabel="资源绑定说明" content="代码通过此名称访问资源。输入允许读取，输出允许写入；选择资源不会自动生成数据处理流程。JDBC 表仅用于本地样例，不限制该连接在生产作业中的访问范围。" /></Space>}
        rules={[{ required: true, whitespace: true, message: '请输入代码引用名' }, { max: 100 }, { validator: async (_, value: string) => {
          if (bindingNames.some((name) => name === value?.trim() && name !== initial?.bindingName)) throw new Error('代码引用名已存在');
        } }]}>
        <Input autoComplete="off" placeholder="选中资源后自动生成，可修改" onChange={() => setNameEdited(true)} />
      </Form.Item>
      {initial && <Typography.Text type="secondary">修改引用名后，已有源码需同步修改。</Typography.Text>}
    </Form>
  </Drawer>;
};
