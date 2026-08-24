import { DeleteOutlined, DownOutlined, PlusOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Empty, Input, Modal, Space, Table, Tag, Tooltip, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react';
import { buildDataModelSearch, useDataModel, useDataModels, type DataModel } from '../../../../model';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { configurationFingerprint } from '../../components/CanvasInspectorUtils';
import { CanvasNodeType, type ModelInputConfiguration, type ModelInputSelection } from '../../canvasTypes';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';

const CANDIDATE_LIMIT = 100;
const SEARCH_DELAY_MS = 300;

const ModelPickerModal = ({ open, value, onCancel, onConfirm }: {
  open: boolean;
  value: readonly ModelInputSelection[];
  onCancel: () => void;
  onConfirm: (value: ModelInputSelection[]) => void;
}) => {
  const [search, setSearch] = useState('');
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<ModelInputSelection[]>(() => value.map((item) => ({ ...item })));
  const timerRef = useRef<number | null>(null);
  const request = useMemo(() => ({ search: buildDataModelSearch({ keyword, status: 'PUBLISHED' }), page: 0, size: CANDIDATE_LIMIT, sort: 'code' }), [keyword]);
  const query = useDataModels(request, open);
  const models = query.data?.content ?? [];
  const selectedIds = useMemo(() => new Set(selected.map((item) => item.modelId)), [selected]);

  useEffect(() => () => { if (timerRef.current !== null) window.clearTimeout(timerRef.current); }, []);
  const updateSearch = (next: string) => {
    setSearch(next);
    if (timerRef.current !== null) window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => { setKeyword(next.trim()); timerRef.current = null; }, SEARCH_DELAY_MS);
  };
  const addVisible = () => setSelected((current) => {
    const ids = new Set(current.map((item) => item.modelId));
    return [...current, ...models.flatMap((model) => ids.has(model.id) ? [] : [{ modelId: model.id }])];
  });
  const toggle = (model: DataModel, checked: boolean) => setSelected((current) => (
    checked ? current.some((item) => item.modelId === model.id) ? current : [...current, { modelId: model.id }]
      : current.filter((item) => item.modelId !== model.id)
  ));
  const columns: TableColumnsType<DataModel> = [
    { title: '模型', key: 'model', ellipsis: true, render: (_, model) => <><Typography.Text>{model.name}</Typography.Text><Typography.Text type="secondary" className="canvas-resource-row-meta">{model.code}</Typography.Text></> },
    { title: '数据源', dataIndex: 'storageDataSourceName', ellipsis: true },
    { title: 'Schema', key: 'schema', width: 100, render: (_, model) => `v${model.schemaVersion}` },
  ];
  return <Modal open={open} width={860} title="管理输入模型" onCancel={onCancel} footer={<Space><Button onClick={onCancel}>取消</Button><Button type="primary" onClick={() => onConfirm(selected)}>确定 · {selected.length} 个模型</Button></Space>}>
    <div className="canvas-jdbc-input-picker-grid">
      <section className="canvas-jdbc-input-picker-pane"><div className="canvas-jdbc-input-picker-heading"><strong>已发布模型</strong><Button type="link" size="small" disabled={models.length === 0} onClick={addVisible}>选择当前结果</Button></div><Input autoFocus allowClear autoComplete="off" name="model-input-search" placeholder="按名称或编码搜索模型" value={search} onChange={(event) => updateSearch(event.target.value)} /><Table<DataModel> size="small" pagination={false} loading={query.isFetching} dataSource={models} rowKey="id" columns={columns} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有已发布模型" /> }} rowSelection={{ selectedRowKeys: [...selectedIds], onSelect: toggle }} /></section>
      <section className="canvas-jdbc-input-picker-pane"><div className="canvas-jdbc-input-picker-heading"><strong>已选模型</strong><Typography.Text type="secondary">完整保留，不受搜索影响</Typography.Text></div><Table<ModelInputSelection> size="small" pagination={false} dataSource={selected} rowKey="modelId" columns={[{ title: '模型 ID', dataIndex: 'modelId', ellipsis: true }, { title: '操作', width: 64, render: (_, item) => <Button type="text" danger icon={<DeleteOutlined />} aria-label={`移除模型 ${item.modelId}`} onClick={() => setSelected((current) => current.filter((candidate) => candidate.modelId !== item.modelId))} /> }]} /></section>
    </div>
  </Modal>;
};

const SelectedModelRow = ({ selection, index, total, onMove, onRemove }: {
  selection: ModelInputSelection; index: number; total: number;
  onMove: (direction: -1 | 1) => void; onRemove: () => void;
}) => {
  const query = useDataModel(selection.modelId, Boolean(selection.modelId));
  const model = query.data?.model;
  return <div className={`canvas-jdbc-input-selected-table${query.isError ? ' is-invalid' : ''}`}><div className="canvas-jdbc-input-selected-table-main"><span className="canvas-jdbc-input-selected-table-index">{index + 1}</span><div className="canvas-jdbc-input-selected-table-identity"><Typography.Text ellipsis title={selection.modelId}>{model ? `${model.name} · ${model.code}` : selection.modelId}</Typography.Text><div className="canvas-jdbc-input-selected-table-meta"><Tag>{model ? `Schema v${model.schemaVersion}` : query.isFetching ? '正在读取模型' : '模型不可用'}</Tag>{model && <Tag>{model.storageDataSourceName}</Tag>}</div></div><Space size={0} className="canvas-jdbc-input-selected-table-actions"><Tooltip title="上移"><Button type="text" size="small" disabled={index === 0} icon={<UpOutlined />} onClick={() => onMove(-1)} /></Tooltip><Tooltip title="下移"><Button type="text" size="small" disabled={index === total - 1} icon={<DownOutlined />} onClick={() => onMove(1)} /></Tooltip><Tooltip title="移除"><Button type="text" size="small" danger icon={<DeleteOutlined />} onClick={onRemove} /></Tooltip></Space></div></div>;
};

const Inspector = ({ node, validation, validationUnavailableMessage, onApply, onDirtyChange, inspectorRef }: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.ModelInput>) => {
  const [models, setModels] = useState<ModelInputSelection[]>(node.configuration.models.map((item) => ({ ...item })));
  const [pickerOpen, setPickerOpen] = useState(false);
  const configuration = (next: ModelInputSelection[]): ModelInputConfiguration => ({ models: next.map((item) => ({ ...item })) });
  const update = (next: ModelInputSelection[]) => { setModels(next); onDirtyChange(configurationFingerprint(configuration(next)) !== configurationFingerprint(node.configuration)); };
  const apply = () => { onApply({ id: node.id, type: node.type, configuration: configuration(models) }); onDirtyChange(false); };
  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(inspectorRef, () => ({ apply: async () => { apply(); return true; } }));
  const move = (index: number, direction: -1 | 1) => { const target = index + direction; if (target < 0 || target >= models.length) return; const next = [...models]; [next[index], next[target]] = [next[target], next[index]]; update(next); };
  return <Space orientation="vertical" size={12} className="canvas-inspector-content">
    <CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} />
    <div className="canvas-jdbc-input-table-section-heading"><div><Typography.Text strong>输入模型</Typography.Text><Typography.Text type="secondary"> 一个节点可读取多个已发布模型</Typography.Text></div><Button icon={<PlusOutlined />} onClick={() => setPickerOpen(true)}>管理模型</Button></div>
    {models.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未选择输入模型" /> : models.map((item, index) => <SelectedModelRow key={item.modelId} selection={item} index={index} total={models.length} onMove={(direction) => move(index, direction)} onRemove={() => update(models.filter((candidate) => candidate.modelId !== item.modelId))} />)}
    {pickerOpen && <ModelPickerModal key={JSON.stringify(models)} open value={models} onCancel={() => setPickerOpen(false)} onConfirm={(next) => { update(next); setPickerOpen(false); }} />}
  </Space>;
};

export default Inspector;
