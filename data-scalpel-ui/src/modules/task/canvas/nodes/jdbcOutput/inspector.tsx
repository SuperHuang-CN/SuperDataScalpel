import {
  ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined, PlusOutlined, SettingOutlined,
} from '@ant-design/icons';
import { Button, Empty, Modal, Space, Tag, Tooltip, Typography } from 'antd';
import { useImperativeHandle, useRef, useState } from 'react';
import {
  CanvasNodeType, type JdbcOutputConfiguration, type JdbcOutputWrite,
} from '../../canvasTypes';
import { CanvasJdbcDataSourceSelect } from '../../components/CanvasJdbcSelectors';
import { JdbcOutputInspector } from '../../components/CanvasLegacyInspectors';
import { CanvasNodeValidationIssues } from '../../components/common/CanvasNodeValidationIssues';
import type { CanvasNodeInspectorComponentProps, CanvasNodeInspectorHandle } from '../nodeSpec';

type Props = CanvasNodeInspectorComponentProps<typeof CanvasNodeType.JdbcOutput>;

const configuredWrites = (configuration: JdbcOutputConfiguration): JdbcOutputWrite[] => {
  if (configuration.writes?.length) return configuration.writes;
  if (!configuration.sourceTableName && !configuration.targetTableName) return [];
  return [{
    writeId: crypto.randomUUID(),
    sourceTableName: configuration.sourceTableName ?? '',
    targetTableName: configuration.targetTableName ?? '',
    writeMode: configuration.writeMode ?? null,
    columnMappings: configuration.columnMappings ?? [],
    upsertKeyColumns: configuration.upsertKeyColumns ?? [],
  }];
};

const editorConfiguration = (
  dataSourceId: string,
  write: JdbcOutputWrite,
): JdbcOutputConfiguration => ({
  dataSourceId,
  writes: [write],
  sourceTableName: write.sourceTableName,
  targetTableName: write.targetTableName,
  writeMode: write.writeMode,
  columnMappings: write.columnMappings,
  upsertKeyColumns: write.upsertKeyColumns,
});

const persistentConfiguration = (
  dataSourceId: string,
  writes: JdbcOutputWrite[],
): JdbcOutputConfiguration => ({ dataSourceId, writes } as JdbcOutputConfiguration);

const JdbcOutputCanvasNodeInspector = ({
  node, executionMode, validation, validationUnavailableMessage, onApply, onDirtyChange, inspectorRef,
}: Props) => {
  const [initialState] = useState(() => ({
    dataSourceId: node.configuration.dataSourceId ?? '',
    writes: configuredWrites(node.configuration),
  }));
  const [dataSourceId, setDataSourceId] = useState(initialState.dataSourceId);
  const [writes, setWrites] = useState<JdbcOutputWrite[]>(initialState.writes);
  const [editingWriteId, setEditingWriteId] = useState<string | null>(null);
  const settingsRef = useRef<CanvasNodeInspectorHandle>(null);
  const editingWrite = writes.find((write) => write.writeId === editingWriteId) ?? null;

  const markDirty = (nextDataSourceId: string, nextWrites: JdbcOutputWrite[]) => {
    onDirtyChange(JSON.stringify({ dataSourceId: nextDataSourceId, writes: nextWrites })
      !== JSON.stringify(initialState));
  };
  const updateWrites = (next: JdbcOutputWrite[]) => {
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
    const write: JdbcOutputWrite = {
      writeId: crypto.randomUUID(),
      sourceTableName: validation?.inputTables[0]?.name ?? '',
      targetTableName: '',
      writeMode: 'OVERWRITE',
      columnMappings: [],
      upsertKeyColumns: [],
    };
    updateWrites([...writes, write]);
    setEditingWriteId(write.writeId);
  };
  const replaceWrite = (write: JdbcOutputWrite) => {
    updateWrites(writes.map((item) => item.writeId === write.writeId ? write : item));
  };
  const moveWrite = (index: number, offset: -1 | 1) => {
    const target = index + offset;
    if (target < 0 || target >= writes.length) return;
    const next = [...writes];
    [next[index], next[target]] = [next[target], next[index]];
    updateWrites(next);
  };
  const removeWrite = (write: JdbcOutputWrite) => {
    Modal.confirm({
      title: '删除这条 JDBC 写入？',
      content: write.targetTableName
        ? `目标表 ${write.targetTableName} 的字段映射和写入设置会一并删除。`
        : '未完成的写入配置会一并删除。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => updateWrites(writes.filter((item) => item.writeId !== write.writeId)),
    });
  };

  return <div className="canvas-inspector-content">
    <Space orientation="vertical" size={12} style={{ width: '100%' }}>
      <CanvasNodeValidationIssues
        validation={validation}
        unavailableMessage={validationUnavailableMessage}
      />
      <div>
        <Typography.Text strong>目标数据源</Typography.Text>
        <div style={{ marginTop: 6 }}>
          <CanvasJdbcDataSourceSelect
            purpose="DISTRIBUTION"
            value={dataSourceId || undefined}
            placeholder="选择 JDBC 数据分发数据源"
            onChange={(value) => updateDataSourceId(value ?? '')}
          />
        </div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div><Typography.Text strong>JDBC 写入</Typography.Text><Typography.Text type="secondary"> · {writes.length} 项</Typography.Text></div>
        <Button type="primary" size="small" icon={<PlusOutlined />} onClick={addWrite}>添加输出</Button>
      </div>
      {writes.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未配置 JDBC 输出">
        <Button icon={<PlusOutlined />} onClick={addWrite}>添加第一条输出</Button>
      </Empty> : writes.map((write, index) => <div key={write.writeId}
        style={{ border: '1px solid #e7e9f5', borderRadius: 10, padding: '10px 12px', background: '#fff' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{ minWidth: 0, flex: 1 }}>
            <Typography.Text ellipsis style={{ display: 'block' }}>
              {write.sourceTableName || '待选择来源表'} → {write.targetTableName || '待选择目标表'}
            </Typography.Text>
            <Space size={4} wrap style={{ marginTop: 5 }}>
              <Tag color={write.writeMode === 'OVERWRITE' ? 'orange' : write.writeMode === 'UPSERT' ? 'purple' : 'blue'}>
                {write.writeMode ?? '待设置模式'}
              </Tag>
              <Tag>{write.columnMappings.length} 个映射</Tag>
              {write.writeMode === 'UPSERT' && <Tag>{write.upsertKeyColumns.length} 个 Key</Tag>}
            </Space>
          </div>
          <Tooltip title="上移"><Button type="text" size="small" icon={<ArrowUpOutlined />} disabled={index === 0}
            aria-label={`上移第 ${index + 1} 条 JDBC 写入`} onClick={() => moveWrite(index, -1)} /></Tooltip>
          <Tooltip title="下移"><Button type="text" size="small" icon={<ArrowDownOutlined />} disabled={index === writes.length - 1}
            aria-label={`下移第 ${index + 1} 条 JDBC 写入`} onClick={() => moveWrite(index, 1)} /></Tooltip>
          <Tooltip title="设置"><Button type="text" size="small" icon={<SettingOutlined />}
            aria-label={`设置第 ${index + 1} 条 JDBC 写入`} onClick={() => setEditingWriteId(write.writeId)} /></Tooltip>
          <Tooltip title="删除"><Button danger type="text" size="small" icon={<DeleteOutlined />}
            aria-label={`删除第 ${index + 1} 条 JDBC 写入`} onClick={() => removeWrite(write)} /></Tooltip>
        </div>
      </div>)}
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        每条写入独立配置来源和目标；批任务按列表顺序执行，不提供跨目标事务。
      </Typography.Text>
    </Space>
    <Modal className="canvas-output-write-modal" open={Boolean(editingWrite)} width={1080} destroyOnHidden styles={{ body: { overflow: 'hidden' } }}
      title={editingWrite ? `设置 JDBC 写入 · ${editingWrite.sourceTableName || '未选择来源表'}` : '设置 JDBC 写入'}
      onCancel={() => setEditingWriteId(null)}
      onOk={async () => { const applied = await settingsRef.current?.apply(); if (applied) setEditingWriteId(null); }}
      okText="保存此项" cancelText="取消">
      {editingWrite && <JdbcOutputInspector
        node={{ ...node, configuration: editorConfiguration(dataSourceId, editingWrite) }}
        executionMode={executionMode}
        validation={validation}
        validationUnavailableMessage={validationUnavailableMessage}
        inspectorRef={settingsRef} splitLayout hideDataSource hideNodeValidation
        onDirtyChange={() => undefined}
        onApply={(update) => {
          const updatedConfiguration = update.configuration as JdbcOutputConfiguration;
          const updated = updatedConfiguration.writes?.[0];
          if (updated) replaceWrite({ ...updated, writeId: editingWrite.writeId });
          if (updatedConfiguration.dataSourceId !== dataSourceId) {
            updateDataSourceId(updatedConfiguration.dataSourceId);
          }
        }} />}
    </Modal>
  </div>;
};

export default JdbcOutputCanvasNodeInspector;
