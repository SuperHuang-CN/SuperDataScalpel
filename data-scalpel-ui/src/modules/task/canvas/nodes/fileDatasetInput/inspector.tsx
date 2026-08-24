import { DeleteOutlined, DownOutlined, PlusOutlined, UpOutlined } from '@ant-design/icons';
import { Button, Empty, Form, Modal, Select, Space, Table, Tag, Tooltip, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useImperativeHandle, useMemo, useState } from 'react';
import { fileDatasetParseStatusLabels, useFileDatasetCanvasMetadata, useFileDatasets, useFileDatasetTables, type FileDatasetTable } from '../../../../filedataset';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { configurationFingerprint } from '../../components/CanvasInspectorUtils';
import { CanvasNodeType, type FileDatasetInputConfiguration, type FileDatasetInputTableSelection } from '../../canvasTypes';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';

interface Values { fileDatasetId: string; }
const ready = (table: FileDatasetTable) => table.parseStatus === 'READY' || table.parseStatus === 'SCHEMA_READY';

const TablePicker = ({ open, datasetId, value, onCancel, onConfirm }: { open: boolean; datasetId: string; value: readonly FileDatasetInputTableSelection[]; onCancel: () => void; onConfirm: (value: FileDatasetInputTableSelection[]) => void; }) => {
  const query = useFileDatasetTables(datasetId || undefined, open && Boolean(datasetId));
  const [selected, setSelected] = useState<FileDatasetInputTableSelection[]>([]);
  const candidates = query.data?.content ?? [];
  const ids = useMemo(() => new Set(selected.map((item) => item.fileDatasetTableId)), [selected]);
  const toggle = (table: FileDatasetTable, checked: boolean) => setSelected((current) => checked ? current.some((item) => item.fileDatasetTableId === table.id) ? current : [...current, { fileDatasetTableId: table.id }] : current.filter((item) => item.fileDatasetTableId !== table.id));
  const columns: TableColumnsType<FileDatasetTable> = [
    { title: '逻辑表', key: 'name', ellipsis: true, render: (_, table) => <><Typography.Text>{table.name}</Typography.Text><Typography.Text type="secondary" className="canvas-resource-row-meta">{table.code}</Typography.Text></> },
    { title: '格式', key: 'format', width: 90, render: () => '文件表' },
    { title: '状态', key: 'status', width: 110, render: (_, table) => <Tag color={ready(table) ? 'success' : 'default'}>{fileDatasetParseStatusLabels[table.parseStatus]}</Tag> },
  ];
  return <Modal open={open} width={860} title="管理文件数据集逻辑表" afterOpenChange={(visible) => { if (visible) setSelected(value.map((item) => ({ ...item }))); }} onCancel={onCancel} footer={<Space><Button onClick={onCancel}>取消</Button><Button type="primary" onClick={() => onConfirm(selected)}>确定 · {selected.length} 张表</Button></Space>}>
    <div className="canvas-jdbc-input-picker-grid"><section className="canvas-jdbc-input-picker-pane"><div className="canvas-jdbc-input-picker-heading"><strong>候选逻辑表</strong><Button type="link" size="small" onClick={() => setSelected((current) => {
      const existing = new Set(current.map((item) => item.fileDatasetTableId));
      return [...current, ...candidates.filter(ready).flatMap((table) => (
        existing.has(table.id) ? [] : [{ fileDatasetTableId: table.id }]
      ))];
    })}>选择当前结果</Button></div><Table<FileDatasetTable> size="small" pagination={false} loading={query.isFetching} dataSource={candidates} rowKey="id" columns={columns} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有逻辑表" /> }} rowSelection={{ selectedRowKeys: [...ids], getCheckboxProps: (table) => ({ disabled: !ready(table) }), onSelect: toggle }} /></section><section className="canvas-jdbc-input-picker-pane"><div className="canvas-jdbc-input-picker-heading"><strong>已选表</strong><Typography.Text type="secondary">完整保留</Typography.Text></div><Table<FileDatasetInputTableSelection> size="small" pagination={false} dataSource={selected} rowKey="fileDatasetTableId" columns={[{ title: '表 ID', dataIndex: 'fileDatasetTableId', ellipsis: true }, { title: '操作', width: 64, render: (_, item) => <Button type="text" danger icon={<DeleteOutlined />} onClick={() => setSelected((current) => current.filter((candidate) => candidate.fileDatasetTableId !== item.fileDatasetTableId))} /> }]} /></section></div>
  </Modal>;
};

const Inspector = ({ node, validation, validationUnavailableMessage, onApply, onDirtyChange, inspectorRef }: CanvasNodeInspectorComponentProps<typeof CanvasNodeType.FileDatasetInput>) => {
  const [form] = Form.useForm<Values>();
  const [tables, setTables] = useState<FileDatasetInputTableSelection[]>(node.configuration.tables.map((item) => ({ ...item })));
  const [pickerOpen, setPickerOpen] = useState(false);
  const datasetId = Form.useWatch('fileDatasetId', form) ?? '';
  const datasetsQuery = useFileDatasets({ page: 0, size: 100, sort: 'name' });
  const metadataQuery = useFileDatasetCanvasMetadata(tables.map((item) => item.fileDatasetTableId), tables.length > 0);
  const metadata = metadataQuery.data?.tables ?? [];
  const configuration = (values: Partial<Values>, selected: FileDatasetInputTableSelection[]): FileDatasetInputConfiguration => ({ fileDatasetId: values.fileDatasetId ?? '', tables: selected.map((item) => ({ ...item })) });
  const markDirty = (values: Partial<Values>, selected: FileDatasetInputTableSelection[]) => onDirtyChange(configurationFingerprint(configuration(values, selected)) !== configurationFingerprint(node.configuration));
  const update = (next: FileDatasetInputTableSelection[]) => { setTables(next); markDirty(form.getFieldsValue(true), next); };
  const apply = () => { onApply({ id: node.id, type: node.type, configuration: configuration(form.getFieldsValue(true), tables) }); onDirtyChange(false); };
  useImperativeHandle<CanvasNodeInspectorHandle, CanvasNodeInspectorHandle>(inspectorRef, () => ({ apply: async () => { void form.validateFields().catch(() => undefined); apply(); return true; } }));
  const move = (index: number, direction: -1 | 1) => { const target = index + direction; if (target < 0 || target >= tables.length) return; const next = [...tables]; [next[index], next[target]] = [next[target], next[index]]; update(next); };
  const options = (datasetsQuery.data?.content ?? []).map((dataset) => ({ value: dataset.id, label: `${dataset.name} · ${dataset.type}` }));
  return <Space orientation="vertical" size={12} className="canvas-inspector-content"><CanvasNodeValidationIssues validation={validation} unavailableMessage={validationUnavailableMessage} /><Form<Values> autoComplete="off" form={form} layout="vertical" initialValues={{ fileDatasetId: node.configuration.fileDatasetId }} onValuesChange={(_changed, values) => markDirty(values, tables)}><Form.Item name="fileDatasetId" label="文件数据集" rules={[{ required: true, message: '请选择文件数据集' }]}><Select showSearch optionFilterProp="label" placeholder="选择文件数据集" loading={datasetsQuery.isFetching} options={options} /></Form.Item></Form>
    <div className="canvas-jdbc-input-table-section-heading"><div><Typography.Text strong>逻辑表</Typography.Text><Typography.Text type="secondary"> 一个节点可读取同一数据集中的多张表</Typography.Text></div><Button disabled={!datasetId} icon={<PlusOutlined />} onClick={() => setPickerOpen(true)}>管理表</Button></div>
    {tables.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未选择逻辑表" /> : tables.map((item, index) => { const detail = metadata.find((candidate) => candidate.fileDatasetTableId === item.fileDatasetTableId); return <div className="canvas-jdbc-input-selected-table" key={item.fileDatasetTableId}><div className="canvas-jdbc-input-selected-table-main"><span className="canvas-jdbc-input-selected-table-index">{index + 1}</span><div className="canvas-jdbc-input-selected-table-identity"><Typography.Text ellipsis title={item.fileDatasetTableId}>{detail ? `${detail.name} · ${detail.code}` : item.fileDatasetTableId}</Typography.Text><div className="canvas-jdbc-input-selected-table-meta"><Tag>{detail ? `${detail.fields.length} 个字段` : '等待解析'}</Tag>{detail && <Tag>{fileDatasetParseStatusLabels[detail.parseStatus]}</Tag>}</div></div><Space size={0} className="canvas-jdbc-input-selected-table-actions"><Tooltip title="上移"><Button type="text" size="small" disabled={index === 0} icon={<UpOutlined />} onClick={() => move(index, -1)} /></Tooltip><Tooltip title="下移"><Button type="text" size="small" disabled={index === tables.length - 1} icon={<DownOutlined />} onClick={() => move(index, 1)} /></Tooltip><Tooltip title="移除"><Button type="text" size="small" danger icon={<DeleteOutlined />} onClick={() => update(tables.filter((candidate) => candidate.fileDatasetTableId !== item.fileDatasetTableId))} /></Tooltip></Space></div></div>; })}
    <TablePicker open={pickerOpen} datasetId={datasetId} value={tables} onCancel={() => setPickerOpen(false)} onConfirm={(next) => { update(next); setPickerOpen(false); }} />
  </Space>;
};

export default Inspector;
