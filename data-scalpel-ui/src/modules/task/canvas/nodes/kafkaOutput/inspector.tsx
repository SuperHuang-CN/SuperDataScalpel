import { createUuid } from '../../../../../shared/browser/createUuid';
import {
  ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, PlusOutlined, SettingOutlined,
} from '@ant-design/icons';
import { Button, Empty, Modal, Space, Tag, Tooltip, Typography } from 'antd';
import { useImperativeHandle, useRef, useState } from 'react';
import {
  CanvasNodeType, type KafkaOutputConfiguration, type KafkaOutputWrite,
} from '../../canvasTypes';
import { KafkaOutputInspector } from './settingsInspector';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import { CanvasKafkaDataSourceSelect } from '../../components/CanvasKafkaSelectors';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';

type Props = CanvasNodeInspectorComponentProps<typeof CanvasNodeType.KafkaOutput>;
const MAX_STREAMING_WRITES = 32;

const configuredWrites = (configuration: KafkaOutputConfiguration): KafkaOutputWrite[] => {
  if (configuration.writes?.length) return configuration.writes;
  if (!configuration.sourceTableName && !configuration.topic) return [];
  return [{
    writeId: createUuid(),
    sourceTableName: configuration.sourceTableName ?? '',
    topic: configuration.topic ?? '',
    valueFormat: null,
    valueColumnNames: [],
    keyColumnName: configuration.keyColumnName ?? '',
    valueSchema: configuration.valueSchema ?? { columns: [] },
    columnMappings: configuration.columnMappings ?? [],
  }];
};

const editorConfiguration = (
  dataSourceId: string,
  write: KafkaOutputWrite,
): KafkaOutputConfiguration => ({
  dataSourceId,
  writes: [write],
  sourceTableName: write.sourceTableName,
  topic: write.topic,
  valueSchema: write.valueSchema ?? { columns: [] },
  keyColumnName: write.keyColumnName,
  columnMappings: write.columnMappings,
});

const persistentConfiguration = (
  dataSourceId: string,
  writes: KafkaOutputWrite[],
): KafkaOutputConfiguration => ({ dataSourceId, writes } as KafkaOutputConfiguration);

const KafkaOutputCanvasNodeInspector = ({
  node, validation, validationUnavailableMessage, onApply, onDirtyChange, inspectorRef,
}: Props) => {
  const [initialState] = useState(() => ({
    dataSourceId: node.configuration.dataSourceId ?? '',
    writes: configuredWrites(node.configuration),
  }));
  const [dataSourceId, setDataSourceId] = useState(initialState.dataSourceId);
  const [writes, setWrites] = useState<KafkaOutputWrite[]>(initialState.writes);
  const [editingWriteId, setEditingWriteId] = useState<string | null>(null);
  const settingsRef = useRef<CanvasNodeInspectorHandle>(null);
  const editingWrite = writes.find((write) => write.writeId === editingWriteId) ?? null;

  const markDirty = (nextDataSourceId: string, nextWrites: KafkaOutputWrite[]) => {
    onDirtyChange(JSON.stringify({ dataSourceId: nextDataSourceId, writes: nextWrites })
      !== JSON.stringify(initialState));
  };
  const updateWrites = (next: KafkaOutputWrite[]) => {
    setWrites(next);
    markDirty(dataSourceId, next);
  };
  const updateDataSourceId = (next: string) => {
    setDataSourceId(next);
    markDirty(next, writes);
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      onApply({
        id: node.id,
        type: node.type,
        configuration: persistentConfiguration(dataSourceId, writes),
      });
      onDirtyChange(false);
      return true;
    },
  }), [dataSourceId, node.id, node.type, onApply, onDirtyChange, writes]);

  const addWrite = () => {
    if (writes.length >= MAX_STREAMING_WRITES) return;
    const source = validation?.inputTables.find((table) => table.datasetKind === 'UNBOUNDED');
    const write: KafkaOutputWrite = {
      writeId: createUuid(),
      sourceTableName: source?.name ?? '',
      topic: '',
      valueFormat: 'JSON',
      valueColumnNames: source?.columns.map((column) => column.name) ?? [],
      keyColumnName: '',
      valueSchema: null,
      columnMappings: [],
    };
    updateWrites([...writes, write]);
    setEditingWriteId(write.writeId);
  };
  const replaceWrite = (write: KafkaOutputWrite) => {
    updateWrites(writes.map((item) => item.writeId === write.writeId ? write : item));
  };
  const moveWrite = (index: number, offset: -1 | 1) => {
    const target = index + offset;
    if (target < 0 || target >= writes.length) return;
    const next = [...writes];
    [next[index], next[target]] = [next[target], next[index]];
    updateWrites(next);
  };
  const removeWrite = (write: KafkaOutputWrite) => {
    Modal.confirm({
      title: '删除这条 Kafka 写入？',
      content: write.topic
        ? `Topic ${write.topic} 的 Value 格式、字段选择和 Key 会一并删除。`
        : '未完成的写入配置会一并删除。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => updateWrites(writes.filter((item) => item.writeId !== write.writeId)),
    });
  };

  const limitReached = writes.length >= MAX_STREAMING_WRITES;
  return <div className="canvas-inspector-content">
    <Space orientation="vertical" size={12} style={{ width: '100%' }}>
      <CanvasNodeValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <div>
        <Typography.Text strong>Kafka 数据源</Typography.Text>
        <div style={{ marginTop: 6 }}>
          <CanvasKafkaDataSourceSelect
            purpose="DISTRIBUTION"
            value={dataSourceId || undefined}
            placeholder="选择 Kafka 输出数据源"
            onChange={(value) => updateDataSourceId(value ?? '')}
          />
        </div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div><Typography.Text strong>Kafka 写入</Typography.Text><Typography.Text type="secondary"> · {writes.length} 个 StreamingQuery</Typography.Text></div>
        <Tooltip title={limitReached ? '单个节点最多配置 32 条实时写入' : undefined}>
          <Button type="primary" size="small" icon={<PlusOutlined />} disabled={limitReached} onClick={addWrite}>添加输出</Button>
        </Tooltip>
      </div>
      {writes.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未配置 Kafka 输出">
        <Button icon={<PlusOutlined />} onClick={addWrite}>添加第一条输出</Button>
      </Empty> : writes.map((write, index) => <div key={write.writeId}
        style={{ border: '1px solid #e7e9f5', borderRadius: 10, padding: '10px 12px', background: '#fff' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{ minWidth: 0, flex: 1 }}>
            <Typography.Text ellipsis style={{ display: 'block' }}>
              {write.sourceTableName || '待选择来源流表'} → {write.topic || '待选择 Topic'}
            </Typography.Text>
            <Space size={4} wrap style={{ marginTop: 5 }}>
              <Tag color={write.keyColumnName ? 'blue' : 'default'}>{write.keyColumnName ? `Key · ${write.keyColumnName}` : '无 Key'}</Tag>
              <Tag>{write.valueFormat ?? '旧版 JSON 映射'}</Tag>
              <Tag>{write.valueFormat == null
                ? `${write.valueSchema?.columns.length ?? 0} 个 Schema 字段`
                : write.valueFormat === 'JSON'
                  ? `${write.valueColumnNames.length} 个 Value 字段`
                  : write.valueColumnNames[0] || '待选择 Value 字段'}</Tag>
            </Space>
          </div>
          <Tooltip title="上移"><Button type="text" size="small" icon={<ArrowUpOutlined />} disabled={index === 0}
            aria-label={`上移第 ${index + 1} 条 Kafka 写入`} onClick={() => moveWrite(index, -1)} /></Tooltip>
          <Tooltip title="下移"><Button type="text" size="small" icon={<ArrowDownOutlined />} disabled={index === writes.length - 1}
            aria-label={`下移第 ${index + 1} 条 Kafka 写入`} onClick={() => moveWrite(index, 1)} /></Tooltip>
          <Tooltip title="设置"><Button type="text" size="small" icon={<SettingOutlined />}
            aria-label={`设置第 ${index + 1} 条 Kafka 写入`} onClick={() => setEditingWriteId(write.writeId)} /></Tooltip>
          <Tooltip title="删除"><Button danger type="text" size="small" icon={<DeleteOutlined />}
            aria-label={`删除第 ${index + 1} 条 Kafka 写入`} onClick={() => removeWrite(write)} /></Tooltip>
        </div>
      </div>)}
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        Kafka Output 只负责序列化和投递；字段改名、类型转换与自定义消息结构请在前置 Processor 完成。
      </Typography.Text>
    </Space>
    <Modal className="canvas-output-write-modal" open={Boolean(editingWrite)} width={1080} destroyOnHidden styles={{ body: { overflow: 'hidden' } }}
      title={editingWrite ? `设置 Kafka 写入 · ${editingWrite.sourceTableName || '未选择来源流表'}` : '设置 Kafka 写入'}
      onCancel={() => setEditingWriteId(null)}
      onOk={async () => { const applied = await settingsRef.current?.apply(); if (applied) setEditingWriteId(null); }}
      okText="保存此项" cancelText="取消">
      {editingWrite && <KafkaOutputInspector
        node={{ ...node, configuration: editorConfiguration(dataSourceId, editingWrite) }}
        validation={validation}
        validationUnavailableMessage={validationUnavailableMessage}
        inspectorRef={settingsRef} splitLayout hideDataSource hideNodeValidation
        onDirtyChange={() => undefined}
        onApply={(update) => {
          const updatedConfiguration = update.configuration as KafkaOutputConfiguration;
          const updated = updatedConfiguration.writes?.[0];
          if (updated) replaceWrite({ ...updated, writeId: editingWrite.writeId });
          if (updatedConfiguration.dataSourceId !== dataSourceId) {
            updateDataSourceId(updatedConfiguration.dataSourceId);
          }
        }} />}
    </Modal>
  </div>;
};

export default KafkaOutputCanvasNodeInspector;
