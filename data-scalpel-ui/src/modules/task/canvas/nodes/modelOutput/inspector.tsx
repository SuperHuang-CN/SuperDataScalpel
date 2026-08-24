import {
  ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, PlusOutlined, SettingOutlined,
} from '@ant-design/icons';
import { Button, Empty, Modal, Space, Tag, Tooltip, Typography } from 'antd';
import { useImperativeHandle, useRef, useState } from 'react';
import { useDataModel } from '../../../../model';
import {
  CanvasNodeType, type ModelOutputConfiguration, type ModelOutputWrite,
} from '../../canvasTypes';
import { ModelOutputInspector } from '../../components/CanvasLegacyInspectors';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';

type Props = CanvasNodeInspectorComponentProps<typeof CanvasNodeType.ModelOutput>;

const configuredWrites = (configuration: ModelOutputConfiguration): ModelOutputWrite[] => {
  if (configuration.writes?.length) return configuration.writes;
  if (!configuration.sourceTableName && !configuration.targetModelId) return [];
  return [{
    writeId: crypto.randomUUID(),
    sourceTableName: configuration.sourceTableName ?? '',
    targetModelId: configuration.targetModelId ?? '',
    writeMode: configuration.writeMode ?? null,
    columnMappings: configuration.columnMappings ?? [],
  }];
};

const editorConfiguration = (write: ModelOutputWrite): ModelOutputConfiguration => ({
  writes: [write], sourceTableName: write.sourceTableName, targetModelId: write.targetModelId,
  writeMode: write.writeMode, columnMappings: write.columnMappings,
});

const persistentConfiguration = (writes: ModelOutputWrite[]): ModelOutputConfiguration => (
  { writes } as ModelOutputConfiguration
);

const ModelWriteTarget = ({ modelId }: { modelId: string }) => {
  const query = useDataModel(modelId || undefined, Boolean(modelId));
  if (!modelId) return <>待选择目标模型</>;
  if (query.data) {
    return <>{query.data.model.name} · {query.data.model.code} · v{query.data.model.schemaVersion}</>;
  }
  return <>{query.isError ? `${modelId}（已失效）` : '正在读取目标模型…'}</>;
};

const ModelOutputCanvasNodeInspector = ({
  node, executionMode, validation, validationUnavailableMessage, onApply, onDirtyChange, inspectorRef,
}: Props) => {
  const [initialWrites] = useState(() => configuredWrites(node.configuration));
  const [writes, setWrites] = useState<ModelOutputWrite[]>(initialWrites);
  const [editingWriteId, setEditingWriteId] = useState<string | null>(null);
  const settingsRef = useRef<CanvasNodeInspectorHandle>(null);
  const editingWrite = writes.find((write) => write.writeId === editingWriteId) ?? null;

  const updateWrites = (next: ModelOutputWrite[]) => {
    setWrites(next);
    onDirtyChange(JSON.stringify(next) !== JSON.stringify(initialWrites));
  };

  useImperativeHandle(inspectorRef, () => ({
    apply: async () => {
      onApply({ id: node.id, type: node.type, configuration: persistentConfiguration(writes) });
      onDirtyChange(false);
      return true;
    },
  }), [node.id, node.type, onApply, onDirtyChange, writes]);

  const addWrite = () => {
    const write: ModelOutputWrite = {
      writeId: crypto.randomUUID(),
      sourceTableName: validation?.inputTables[0]?.name ?? '',
      targetModelId: '', writeMode: 'OVERWRITE', columnMappings: [],
    };
    updateWrites([...writes, write]);
    setEditingWriteId(write.writeId);
  };

  const replaceWrite = (write: ModelOutputWrite) => {
    updateWrites(writes.map((item) => item.writeId === write.writeId ? write : item));
  };

  const moveWrite = (index: number, offset: -1 | 1) => {
    const target = index + offset;
    if (target < 0 || target >= writes.length) return;
    const next = [...writes];
    [next[index], next[target]] = [next[target], next[index]];
    updateWrites(next);
  };

  const removeWrite = (write: ModelOutputWrite) => {
    Modal.confirm({
      title: '删除这条模型写入？',
      content: write.targetModelId ? `目标模型 ${write.targetModelId} 的字段映射会一并删除。` : '未完成的写入配置会一并删除。',
      okText: '删除', cancelText: '取消', okButtonProps: { danger: true },
      onOk: () => updateWrites(writes.filter((item) => item.writeId !== write.writeId)),
    });
  };

  return <div className="canvas-inspector-content">
    <Space orientation="vertical" size={12} style={{ width: '100%' }}>
      <CanvasNodeValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div><Typography.Text strong>模型写入</Typography.Text><Typography.Text type="secondary"> · {writes.length} 项</Typography.Text></div>
        <Button type="primary" size="small" icon={<PlusOutlined />} onClick={addWrite}>添加输出</Button>
      </div>
      {writes.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未配置模型输出">
        <Button icon={<PlusOutlined />} onClick={addWrite}>添加第一条输出</Button>
      </Empty> : writes.map((write, index) => <div key={write.writeId}
        style={{ border: '1px solid #e7e9f5', borderRadius: 10, padding: '10px 12px', background: '#fff' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{ minWidth: 0, flex: 1 }}>
            <Typography.Text ellipsis style={{ display: 'block' }}>
              {write.sourceTableName || '待选择来源表'} → <ModelWriteTarget modelId={write.targetModelId} />
            </Typography.Text>
            <Space size={4} wrap style={{ marginTop: 5 }}>
              <Tag color={write.writeMode === 'OVERWRITE' ? 'orange' : write.writeMode === 'UPSERT' ? 'purple' : 'blue'}>
                {write.writeMode ?? '待设置模式'}
              </Tag><Tag>{write.columnMappings.length} 个映射</Tag>
            </Space>
          </div>
          <Tooltip title="上移"><Button type="text" size="small" icon={<ArrowUpOutlined />} disabled={index === 0}
            aria-label={`上移第 ${index + 1} 条模型写入`} onClick={() => moveWrite(index, -1)} /></Tooltip>
          <Tooltip title="下移"><Button type="text" size="small" icon={<ArrowDownOutlined />} disabled={index === writes.length - 1}
            aria-label={`下移第 ${index + 1} 条模型写入`} onClick={() => moveWrite(index, 1)} /></Tooltip>
          <Tooltip title="设置"><Button type="text" size="small" icon={<SettingOutlined />}
            aria-label={`设置第 ${index + 1} 条模型写入`} onClick={() => setEditingWriteId(write.writeId)} /></Tooltip>
          <Tooltip title="删除"><Button danger type="text" size="small" icon={<DeleteOutlined />}
            aria-label={`删除第 ${index + 1} 条模型写入`} onClick={() => removeWrite(write)} /></Tooltip>
        </div>
      </div>)}
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        每条写入独立配置来源和目标；多目标按列表顺序执行，不提供跨目标事务。
      </Typography.Text>
    </Space>
    <Modal className="canvas-output-write-modal" open={Boolean(editingWrite)} width={1080} destroyOnHidden styles={{ body: { overflow: 'hidden' } }}
      title={editingWrite ? `设置模型写入 · ${editingWrite.sourceTableName || '未选择来源表'}` : '设置模型写入'}
      onCancel={() => setEditingWriteId(null)}
      onOk={async () => { const applied = await settingsRef.current?.apply(); if (applied) setEditingWriteId(null); }}
      okText="保存此项" cancelText="取消">
      {editingWrite && <ModelOutputInspector
        node={{ ...node, configuration: editorConfiguration(editingWrite) }} executionMode={executionMode}
        validation={validation} validationUnavailableMessage={validationUnavailableMessage}
        inspectorRef={settingsRef} splitLayout hideNodeValidation onDirtyChange={() => undefined}
        onApply={(update) => {
          const updated = (update.configuration as ModelOutputConfiguration).writes?.[0];
          if (updated) replaceWrite({ ...updated, writeId: editingWrite.writeId });
        }} />}
    </Modal>
  </div>;
};

export default ModelOutputCanvasNodeInspector;
