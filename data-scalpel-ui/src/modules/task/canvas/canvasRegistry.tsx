/* eslint-disable react-refresh/only-export-components -- X6 consumes this module as a runtime node registry. */
import {
  CheckCircleFilled,
  ClockCircleOutlined,
  CloseCircleFilled,
  DeleteOutlined,
  LockOutlined,
  SettingOutlined,
  TableOutlined,
  WarningFilled,
} from '@ant-design/icons';
import type { Node } from '@antv/x6';
import { register } from '@antv/x6-react-shape';
import { Button, Tooltip } from 'antd';
import { createElement, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { requestCanvasNodeDeletion } from './canvasNodeDeletion';
import { CanvasNodeIcon } from './components/CanvasNodeIcons';
import { CanvasTableSchemaModal } from './components/CanvasTableSchemaModal';
import {
  CanvasNodeCategory,
  type CanvasExecutionMode,
  type CanvasNodeRuntimeData,
  type CanvasNodeValidationStatus,
  type CanvasTableSchema,
} from './canvasTypes';
import { canvasNodeRegistry } from './nodes/nodeRegistry';
import {
  CANVAS_RUNTIME_NODE_SHAPE,
  type CanvasNodeIconKey as CanvasNodeIconKeyValue,
} from './nodes/nodeSpec';
import type { CanvasNodeGroup } from './nodes/nodeGroups';

export interface CanvasNodeTemplate {
  type: CanvasNodeRuntimeData['type'];
  shape: typeof CANVAS_RUNTIME_NODE_SHAPE;
  label: string;
  description: string;
  searchKeywords: readonly string[];
  category: CanvasNodeCategory;
  group: CanvasNodeGroup;
  iconKey: CanvasNodeIconKeyValue;
  order: number;
  width: number;
  height: number;
  supportedModes: readonly CanvasExecutionMode[];
}

type CanvasNodeTemplateSource = Omit<
  CanvasNodeTemplate,
  'shape' | 'width' | 'height'
>;

const canvasTemplateFromSpec = (spec: CanvasNodeTemplateSource): CanvasNodeTemplate => {
  const size = canvasNodeRegistry.resolveSize(canvasNodeRegistry.createRuntimeData(spec.type));
  return {
    type: spec.type,
    shape: CANVAS_RUNTIME_NODE_SHAPE,
    label: spec.label,
    description: spec.description,
    searchKeywords: spec.searchKeywords,
    category: spec.category,
    group: spec.group,
    iconKey: spec.iconKey,
    order: spec.order,
    width: size.width,
    height: size.height,
    supportedModes: spec.supportedModes,
  };
};

export const canvasNodeTemplates: readonly CanvasNodeTemplate[] = canvasNodeRegistry.all().map(canvasTemplateFromSpec);

export const canvasNodeTemplate = (type: CanvasNodeRuntimeData['type']): CanvasNodeTemplate => {
  const spec = canvasNodeRegistry.require(type);
  return canvasTemplateFromSpec(spec);
};

const validationLabel: Record<CanvasNodeValidationStatus, { color: string; label: string }> = {
  UNCHECKED: { color: 'default', label: '待校验' },
  UNCONFIGURED: { color: 'default', label: '未配置' },
  VALID: { color: 'success', label: '有效' },
  WARNING: { color: 'warning', label: '有警告' },
  ERROR: { color: 'error', label: '有错误' },
};

const validationIcon: Record<CanvasNodeValidationStatus, ReactNode> = {
  UNCHECKED: <ClockCircleOutlined />,
  UNCONFIGURED: <SettingOutlined />,
  VALID: <CheckCircleFilled />,
  WARNING: <WarningFilled />,
  ERROR: <CloseCircleFilled />,
};

interface CanvasNodeViewProps {
  node: Node;
}

const CanvasNodeOutputSchemaTrigger = ({
  nodeName,
  tables,
}: {
  nodeName: string;
  tables: readonly CanvasTableSchema[];
}) => {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Tooltip title={`查看输出结构 · ${tables.length} 张表`}>
        <Button
          type="text"
          size="small"
          className="canvas-node-output-schema-trigger"
          aria-label={`查看节点 ${nodeName} 的输出结构，共 ${tables.length} 张表`}
          icon={<TableOutlined />}
          onMouseDown={(event) => event.stopPropagation()}
          onPointerDown={(event) => event.stopPropagation()}
          onDoubleClick={(event) => event.stopPropagation()}
          onKeyDown={(event) => event.stopPropagation()}
          onClick={(event) => {
            event.stopPropagation();
            setOpen(true);
          }}
        >
          {tables.length}
        </Button>
      </Tooltip>
      <CanvasTableSchemaModal
        open={open}
        title={`节点输出结构 · ${nodeName}`}
        tables={tables}
        onClose={() => setOpen(false)}
      />
    </>
  );
};

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
    const refresh = () => setSize(node.getSize());
    node.on('change:size', refresh);
    return () => {
      node.off('change:size', refresh);
    };
  }, [node]);

  useEffect(() => {
    if (!editingName) return;
    nameInputRef.current?.focus();
    nameInputRef.current?.select();
  }, [editingName]);

  const beginNameEdit = () => {
    if (data.readOnly) return;
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
    if (nextName !== data.name) node.setData({ name: nextName }, { canvasNodeRename: true });
  };

  const cancelNameEdit = () => {
    cancelBlurRef.current = true;
    setDraftName(data.name);
    setEditingName(false);
  };

  const template = canvasNodeTemplate(data.type);
  const validation = data.validation ?? validationLabel.UNCHECKED;
  const validationPresentation = 'status' in validation
    ? validationLabel[validation.status]
    : validation;
  const validationStatus = 'status' in validation ? validation.status : 'UNCHECKED';
  const hasIssueBar = validationStatus === 'WARNING' || validationStatus === 'ERROR';
  const outputTables = data.compilation?.outputTables ?? [];
  const canViewOutputSchema = template.category !== CanvasNodeCategory.Output
    && (validationStatus === 'VALID' || validationStatus === 'WARNING')
    && outputTables.length > 0;
  const resolvedSize = useMemo(() => {
    const base = canvasNodeRegistry.resolveSize(data);
    return { width: base.width, height: base.height + (hasIssueBar ? 28 : 0) };
  }, [data, hasIssueBar]);
  const body = useMemo(
    () => createElement(canvasNodeRegistry.canvasBody(data.type), { data }),
    [data],
  );

  useEffect(() => {
    const current = node.getSize();
    if (current.width === resolvedSize.width && current.height === resolvedSize.height) return;
    node.resize(resolvedSize.width, resolvedSize.height, { canvasPresentationUpdate: true });
  }, [node, resolvedSize.height, resolvedSize.width]);

  return (
    <div
      className={`canvas-node canvas-node-category-${template.category.toLowerCase()} canvas-node-type-${data.type.toLowerCase().replaceAll('_', '-')} canvas-node-${validationPresentation.color}${data.readOnly ? ' canvas-node-readonly' : ''}`}
      style={{ width: size.width, height: size.height }}
    >
      <div className={`canvas-node-header canvas-node-header-${template.category.toLowerCase()}`}>
        <span className="canvas-node-category-icon">
          <CanvasNodeIcon iconKey={template.iconKey} />
        </span>
        {editingName && !data.readOnly ? (
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
        ) : data.readOnly ? (
          <span className="canvas-node-title" title={template.label}>{data.name}</span>
        ) : (
          <span
            className="canvas-node-title"
            role="button"
            tabIndex={0}
            aria-label={`重命名节点 ${data.name}`}
            title={`${template.label} · 双击重命名`}
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
        {canViewOutputSchema && <CanvasNodeOutputSchemaTrigger nodeName={data.name} tables={outputTables} />}
        <Tooltip title={`${data.readOnly ? '只读 · ' : ''}${validationPresentation.label}${data.validation?.message ? `：${data.validation.message}` : ''}`}>
          <span
            className={`canvas-node-validation-icon is-${validationPresentation.color}${data.readOnly ? ' is-readonly' : ''}`}
            aria-label={`${data.readOnly ? '只读，' : ''}${validationPresentation.label}`}
          >
            {data.readOnly && <LockOutlined className="canvas-node-readonly-icon" />}
            {validationIcon[validationStatus]}
          </span>
        </Tooltip>
        {!data.readOnly && (
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
        )}
      </div>
      <div className="canvas-node-body">
        {body}
      </div>
      {hasIssueBar && <div className={`canvas-node-issue is-${validationStatus?.toLowerCase()}`} title={data.validation?.message}>{data.validation?.message || validationPresentation.label}</div>}
    </div>
  );
};

let didRegisterCanvasNodes = false;

export const registerCanvasNodes = () => {
  if (didRegisterCanvasNodes) return;
  register({
    shape: CANVAS_RUNTIME_NODE_SHAPE,
    width: 240,
    height: 120,
    component: CanvasNodeView,
    effect: ['data'],
  });
  didRegisterCanvasNodes = true;
};
