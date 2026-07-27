/* eslint-disable react-refresh/only-export-components -- X6 consumes this module as a runtime node registry. */
import {
  BranchesOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  FileTextOutlined,
  SaveOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import type { Node } from '@antv/x6';
import { register } from '@antv/x6-react-shape';
import { Button, Tag, Tooltip } from 'antd';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { requestCanvasNodeDeletion } from './canvasNodeDeletion';
import {
  CanvasNodeCategory,
  CanvasNodeType,
  type CanvasExecutionMode,
  type CanvasNodeRuntimeData,
  type CanvasNodeValidationStatus,
} from './canvasTypes';

export interface CanvasNodeTemplate {
  type: CanvasNodeType;
  shape: string;
  label: string;
  category: CanvasNodeCategory;
  width: number;
  height: number;
  supportedModes: readonly CanvasExecutionMode[];
}

export const canvasNodeTemplates: readonly CanvasNodeTemplate[] = [
  {
    type: CanvasNodeType.ModelInput,
    shape: 'datascalpel-model-input',
    label: '模型输入',
    category: CanvasNodeCategory.Input,
    width: 240,
    height: 120,
    supportedModes: ['BATCH'],
  },
  {
    type: CanvasNodeType.JdbcInput,
    shape: 'datascalpel-jdbc-input',
    label: 'JDBC 输入',
    category: CanvasNodeCategory.Input,
    width: 240,
    height: 120,
    supportedModes: ['BATCH', 'STREAMING'],
  },
  {
    type: CanvasNodeType.FileDatasetInput,
    shape: 'datascalpel-file-dataset-input',
    label: '文件数据集输入',
    category: CanvasNodeCategory.Input,
    width: 240,
    height: 120,
    supportedModes: ['BATCH'],
  },
  {
    type: CanvasNodeType.HttpApiInput,
    shape: 'datascalpel-http-api-input',
    label: 'HTTP API 输入',
    category: CanvasNodeCategory.Input,
    width: 240,
    height: 120,
    supportedModes: ['BATCH'],
  },
  {
    type: CanvasNodeType.KafkaInput,
    shape: 'datascalpel-kafka-input',
    label: 'Kafka 输入',
    category: CanvasNodeCategory.Input,
    width: 240,
    height: 120,
    supportedModes: ['STREAMING'],
  },
  {
    type: CanvasNodeType.Join,
    shape: 'datascalpel-join',
    label: 'Join 处理器',
    category: CanvasNodeCategory.Processor,
    width: 240,
    height: 120,
    supportedModes: ['BATCH'],
  },
  {
    type: CanvasNodeType.StreamJoin,
    shape: 'datascalpel-stream-join',
    label: '流-维 Join',
    category: CanvasNodeCategory.Processor,
    width: 240,
    height: 120,
    supportedModes: ['STREAMING'],
  },
  {
    type: CanvasNodeType.Rename,
    shape: 'datascalpel-rename',
    label: '重命名',
    category: CanvasNodeCategory.Processor,
    width: 240,
    height: 120,
    supportedModes: ['BATCH', 'STREAMING'],
  },
  {
    type: CanvasNodeType.ModelOutput,
    shape: 'datascalpel-model-output',
    label: '模型输出',
    category: CanvasNodeCategory.Output,
    width: 240,
    height: 120,
    supportedModes: ['BATCH'],
  },
  {
    type: CanvasNodeType.JdbcOutput,
    shape: 'datascalpel-jdbc-output',
    label: 'JDBC 输出',
    category: CanvasNodeCategory.Output,
    width: 240,
    height: 120,
    supportedModes: ['BATCH', 'STREAMING'],
  },
  {
    type: CanvasNodeType.KafkaOutput,
    shape: 'datascalpel-kafka-output',
    label: 'Kafka 输出',
    category: CanvasNodeCategory.Output,
    width: 240,
    height: 120,
    supportedModes: ['STREAMING'],
  },
];

export const canvasNodeTemplate = (type: CanvasNodeType): CanvasNodeTemplate => {
  const template = canvasNodeTemplates.find((candidate) => candidate.type === type);
  if (!template) throw new Error(`未知 Canvas 节点类型：${type}`);
  return template;
};

const categoryIcon: Record<CanvasNodeCategory, ReactNode> = {
  [CanvasNodeCategory.Input]: <DatabaseOutlined />,
  [CanvasNodeCategory.Processor]: <BranchesOutlined />,
  [CanvasNodeCategory.Output]: <SaveOutlined />,
};

const validationLabel: Record<CanvasNodeValidationStatus, { color: string; label: string }> = {
  UNCHECKED: { color: 'default', label: '待校验' },
  UNCONFIGURED: { color: 'default', label: '未配置' },
  VALID: { color: 'success', label: '有效' },
  WARNING: { color: 'warning', label: '有警告' },
  ERROR: { color: 'error', label: '有错误' },
};

const nodeSummary = (data: CanvasNodeRuntimeData): string => {
  switch (data.type) {
    case CanvasNodeType.ModelInput:
      if (!data.configuration.modelId) return '请选择来源模型';
      return data.summary?.kind === 'MODEL'
        ? `${data.summary.modelName} · ${data.summary.modelCode} · v${data.summary.modelSchemaVersion}`
        : `模型 ${data.configuration.modelId}`;
    case CanvasNodeType.JdbcInput: {
      if (!data.configuration.tableName) return '请选择来源表';
      return data.summary?.kind === 'JDBC'
        ? `${data.summary.dataSourceName} · ${data.summary.qualifiedTableName}`
        : `未知数据源 · ${data.configuration.tableName}`;
    }
    case CanvasNodeType.FileDatasetInput: {
      if (!data.configuration.fileDatasetTableId) return '请选择文件数据集表';
      return data.summary?.kind === 'FILE_DATASET'
        ? `${data.summary.fileDatasetName} · ${data.summary.tableName} (${data.summary.tableCode}) · ${data.summary.status}`
        : `文件表 ${data.configuration.fileDatasetTableId}`;
    }
    case CanvasNodeType.HttpApiInput: {
      const { dataSourceId, resourceId, outputTableName } = data.configuration;
      if (!dataSourceId || !resourceId || !outputTableName) return '请选择 API 资源并设置输出表';
      return data.summary?.kind === 'HTTP_API'
        ? `${data.summary.dataSourceName} · ${data.summary.qualifiedTableName} → ${outputTableName}`
        : `${resourceId} → ${outputTableName}`;
    }
    case CanvasNodeType.KafkaInput: {
      const { dataSourceId, topic, valueSchema, outputTableName } = data.configuration;
      if (!dataSourceId || !topic || valueSchema.columns.length === 0 || !outputTableName) {
        return '请配置 Kafka 输入';
      }
      return data.summary?.kind === 'KAFKA'
        ? `${data.summary.dataSourceName} · ${topic} → ${outputTableName} · ${valueSchema.columns.length} 字段`
        : `${topic} → ${outputTableName} · ${valueSchema.columns.length} 字段`;
    }
    case CanvasNodeType.Join: {
      const { leftTableName, rightTableName, outputTableName, joinType } = data.configuration;
      if (!leftTableName || !rightTableName || !outputTableName || !joinType) return '请配置 Join';
      return `${leftTableName} ${joinType} ${rightTableName} → ${outputTableName}`;
    }
    case CanvasNodeType.StreamJoin: {
      const { leftTableName, rightTableName, outputTableName, joinType } = data.configuration;
      if (!leftTableName || !rightTableName || !outputTableName || !joinType) return '请配置流-维 Join';
      return `${leftTableName} ${joinType} ${rightTableName} → ${outputTableName}`;
    }
    case CanvasNodeType.Rename: {
      const { sourceTableName, outputTableName, columnMappings } = data.configuration;
      if (!sourceTableName || !outputTableName) return '请选择来源表并设置输出表名';
      const tableSummary = sourceTableName === outputTableName
        ? sourceTableName
        : `${sourceTableName} → ${outputTableName}`;
      return columnMappings.length > 0
        ? `${tableSummary} · ${columnMappings.length} 个字段`
        : tableSummary;
    }
    case CanvasNodeType.ModelOutput: {
      const { sourceTableName, targetModelId, writeMode } = data.configuration;
      if (!sourceTableName || !targetModelId || !writeMode) return '请选择输出模型';
      const target = data.summary?.kind === 'MODEL'
        ? `${data.summary.modelName} · ${data.summary.modelCode}`
        : `模型 ${targetModelId}`;
      return `${sourceTableName} → ${target} (${writeMode})`;
    }
    case CanvasNodeType.JdbcOutput: {
      const { sourceTableName, dataSourceId, targetTableName, writeMode } = data.configuration;
      if (!sourceTableName || !targetTableName || !writeMode) return '请选择输出目标';
      const target = data.summary?.kind === 'JDBC'
        ? data.summary.qualifiedTableName
        : `${dataSourceId || '未知数据源'}.${targetTableName}`;
      return `${sourceTableName} → ${target} (${writeMode})`;
    }
    case CanvasNodeType.KafkaOutput: {
      const { sourceTableName, dataSourceId, topic, valueSchema } = data.configuration;
      if (!sourceTableName || !dataSourceId || !topic || valueSchema.columns.length === 0) {
        return '请配置 Kafka 输出';
      }
      return data.summary?.kind === 'KAFKA'
        ? `${sourceTableName} → ${data.summary.dataSourceName} · ${topic} · ${valueSchema.columns.length} 字段`
        : `${sourceTableName} → ${topic} · ${valueSchema.columns.length} 字段`;
    }
  }
};

interface CanvasNodeViewProps {
  node: Node;
}

export const CanvasNodeView = ({ node }: CanvasNodeViewProps) => {
  const [data, setData] = useState<CanvasNodeRuntimeData>(() => node.getData<CanvasNodeRuntimeData>());
  const [size, setSize] = useState(() => node.getSize());
  const [editingName, setEditingName] = useState(false);
  const [draftName, setDraftName] = useState(data.name);
  const nameInputRef = useRef<HTMLInputElement>(null);
  const cancelBlurRef = useRef(false);

  useEffect(() => {
    const refresh = () => setData(node.getData<CanvasNodeRuntimeData>());
    node.on('change:data', refresh);
    return () => {
      node.off('change:data', refresh);
    };
  }, [node]);

  useEffect(() => {
    if (!editingName) return;
    nameInputRef.current?.focus();
    nameInputRef.current?.select();
  }, [editingName]);

  const beginNameEdit = () => {
    cancelBlurRef.current = false;
    setDraftName(data.name);
    setEditingName(true);
  };

  const commitNameEdit = () => {
    const nextName = draftName.trim();
    setEditingName(false);
    if (!nextName) {
      setDraftName(data.name);
      return;
    }
    if (nextName !== data.name) {
      node.setData({ name: nextName }, { canvasNodeRename: true });
    }
  };

  const cancelNameEdit = () => {
    cancelBlurRef.current = true;
    setDraftName(data.name);
    setEditingName(false);
  };

  useEffect(() => {
    const refresh = () => setSize(node.getSize());
    node.on('change:size', refresh);
    return () => {
      node.off('change:size', refresh);
    };
  }, [node]);

  const template = canvasNodeTemplate(data.type);
  const validation = data.validation ?? validationLabel.UNCHECKED;
  const validationPresentation = 'status' in validation ? validationLabel[validation.status] : validation;

  return (
    <div
      className={`canvas-node canvas-node-${validationPresentation.color}`}
      style={{ width: size.width, height: size.height }}
    >
      <div className={`canvas-node-header canvas-node-header-${template.category.toLowerCase()}`}>
        <span>{categoryIcon[template.category]}</span>
        {editingName ? (
          <input
            ref={nameInputRef}
            className="canvas-node-title-input"
            aria-label="节点名称"
            value={draftName}
            maxLength={100}
            onChange={(event) => setDraftName(event.target.value)}
            onMouseDown={(event) => event.stopPropagation()}
            onClick={(event) => event.stopPropagation()}
            onDoubleClick={(event) => event.stopPropagation()}
            onBlur={() => {
              if (cancelBlurRef.current) {
                cancelBlurRef.current = false;
                return;
              }
              commitNameEdit();
            }}
            onKeyDown={(event) => {
              event.stopPropagation();
              if (event.key === 'Enter') {
                event.preventDefault();
                commitNameEdit();
              } else if (event.key === 'Escape') {
                event.preventDefault();
                cancelNameEdit();
              }
            }}
          />
        ) : (
          <span
            className="canvas-node-title"
            role="button"
            tabIndex={0}
            aria-label={`重命名节点 ${data.name}`}
            title={`${canvasNodeTemplate(data.type).label} · 双击重命名`}
            onDoubleClick={(event) => {
              event.stopPropagation();
              beginNameEdit();
            }}
            onKeyDown={(event) => {
              if (event.key !== 'Enter' && event.key !== 'F2') return;
              event.preventDefault();
              event.stopPropagation();
              beginNameEdit();
            }}
          >
            {data.name}
          </span>
        )}
        <Tooltip title="删除节点">
          <Button
            type="text"
            danger
            size="small"
            className="canvas-node-delete"
            aria-label={`删除节点 ${data.name}`}
            icon={<DeleteOutlined />}
            onMouseDown={(event) => event.stopPropagation()}
            onClick={(event) => {
              event.stopPropagation();
              const graph = node.model?.graph;
              if (graph) requestCanvasNodeDeletion(graph, [node]);
            }}
          />
        </Tooltip>
      </div>
      <div className="canvas-node-body">
        {data.type === CanvasNodeType.KafkaInput || data.type === CanvasNodeType.KafkaOutput
          ? <CloudServerOutlined />
          : data.type === CanvasNodeType.FileDatasetInput
            ? <FileTextOutlined />
            : <SwapOutlined />}
        <span className="canvas-node-summary" title={nodeSummary(data)}>{nodeSummary(data)}</span>
      </div>
      <div className="canvas-node-status">
        <Tag color={validationPresentation.color}>{validationPresentation.label}</Tag>
        {data.validation?.message && <span title={data.validation.message}>{data.validation.message}</span>}
      </div>
    </div>
  );
};

let didRegisterCanvasNodes = false;

export const registerCanvasNodes = () => {
  if (didRegisterCanvasNodes) return;

  canvasNodeTemplates.forEach((template) => {
    register({
      shape: template.shape,
      width: template.width,
      height: template.height,
      component: CanvasNodeView,
      effect: ['data'],
    });
  });

  didRegisterCanvasNodes = true;
};
