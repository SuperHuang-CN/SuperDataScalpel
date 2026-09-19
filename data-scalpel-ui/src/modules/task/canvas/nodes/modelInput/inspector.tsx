import { DeleteOutlined, DownOutlined, PlusOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Empty, Space, Tag, Tooltip, Typography } from 'antd';
import { useImperativeHandle, useState } from 'react';
import { DataModelPickerModal, useDataModel } from '../../../../model';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { configurationFingerprint } from '../../components/CanvasInspectorUtils';
import { CanvasNodeType, type ModelInputConfiguration, type ModelInputSelection } from '../../canvasTypes';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';

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
    {pickerOpen && <DataModelPickerModal open value={models.map((item) => item.modelId)} selectionMode="multiple" title="管理输入模型" onCancel={() => setPickerOpen(false)} onConfirm={(modelIds) => { update(modelIds.map((modelId) => ({ modelId }))); setPickerOpen(false); }} />}
  </Space>;
};

export default Inspector;
