import {
  AimOutlined,
  CloseOutlined,
  EyeOutlined,
  ImportOutlined,
  ReadOutlined,
  RedoOutlined,
  UndoOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
} from '@ant-design/icons';
import { Dnd, Graph, History, Keyboard, MiniMap, Selection, Shape, Snapline, Transform } from '@antv/x6';
import type { Node } from '@antv/x6';
import { Button, List, Modal, Space, Tag, Tooltip, Typography, message } from 'antd';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type MouseEvent,
  type ReactNode,
} from 'react';
import { parseCanvasDefinitionJson } from './canvasDefinitionIO';
import {
  emptyCanvasDefinition,
  exampleCanvasTopologyDefinition,
  exampleStreamingCanvasTopologyDefinition,
} from './defaultCanvas';
import { canvasResizeOptions, isCanvasZoomWheel } from './canvasInteraction';
import {
  confirmCanvasNodeDeletion,
  registerCanvasNodeDeletionRequestHandler,
  requestCanvasNodeDeletion,
} from './canvasNodeDeletion';
import { canvasNodePorts } from './canvasPorts';
import { canvasNodeCenterPlacement } from './canvasNodePlacement';
import { canvasNodeTemplate, canvasNodeTemplates, registerCanvasNodes, type CanvasNodeTemplate } from './canvasRegistry';
import {
  loadCanvasDefinition,
  replaceCanvasDefinition,
  styleCanvasEdge,
  toCanvasDefinition,
} from './canvasSerialization';
import { canvasNodeCompilationBadge } from './canvasCompilationPresentation';
import {
  CanvasNodeCategory,
  CanvasNodeType,
  createsCycle,
  type CanvasDefinition,
  type CanvasExecutionMode,
  type CanvasNodeConfigurationUpdate,
  type CanvasNodeRuntimeData,
} from './canvasTypes';
import { taskCompilationValidation } from './taskCompilationTypes';
import { CanvasCompilationMask } from './components/CanvasCompilationMask';
import { CanvasDefinitionModal } from './components/CanvasDefinitionModal';
import { CanvasNodeInspector, type CanvasNodeInspectorHandle } from './components/CanvasNodeInspector';
import { CanvasNodePalette } from './components/CanvasNodePalette';
import { useCanvasMetadataSnapshot } from './useCanvasMetadataSnapshot';
import {
  isCanvasTaskCompilationBlocking,
  useCanvasTaskCompilation,
  type CanvasTaskCompilationState,
} from './useCanvasTaskCompilation';
import './canvasDesigner.css';

export interface CanvasDesignerProps {
  initialDefinition?: CanvasDefinition;
  onDefinitionChange?: (definition: CanvasDefinition) => void;
  onInspectorDirtyChange?: (dirty: boolean) => void;
  toolbarLeading?: ReactNode;
  toolbarTrailing?: ReactNode;
  executionMode?: CanvasExecutionMode;
}

interface PendingInspectorAction {
  execute: () => void;
}

const allowCanvasKeyboardEvent = (event: KeyboardEvent) => {
  const target = event.target;
  if (!(target instanceof HTMLElement)) return true;
  return !target.closest('input, textarea, select, [contenteditable="true"], [role="textbox"], .ant-select');
};

const emptyRuntimeData = (type: CanvasNodeType): CanvasNodeRuntimeData => {
  const name = canvasNodeTemplate(type).label;
  switch (type) {
    case CanvasNodeType.ModelInput:
      return { type, name, configuration: { modelId: '' } };
    case CanvasNodeType.JdbcInput:
      return { type, name, configuration: { dataSourceId: '', tableName: '' } };
    case CanvasNodeType.FileDatasetInput:
      return { type, name, configuration: { fileDatasetTableId: '' } };
    case CanvasNodeType.HttpApiInput:
      return {
        type,
        name,
        configuration: { dataSourceId: '', resourceId: '', outputTableName: '', runtimeParameters: [] },
      };
    case CanvasNodeType.KafkaInput:
      return {
        type,
        name,
        configuration: {
          dataSourceId: '',
          topic: '',
          valueSchema: { columns: [] },
          outputTableName: '',
          startingOffsets: null,
        },
      };
    case CanvasNodeType.Join:
      return {
        type,
        name,
        configuration: {
          leftTableName: '',
          rightTableName: '',
          outputTableName: '',
          joinType: null,
          conditions: [],
        },
      };
    case CanvasNodeType.StreamJoin:
      return {
        type,
        name,
        configuration: {
          leftTableName: '',
          rightTableName: '',
          outputTableName: '',
          joinType: null,
          conditions: [],
        },
      };
    case CanvasNodeType.Rename:
      return {
        type,
        name,
        configuration: {
          sourceTableName: '',
          outputTableName: '',
          columnMappings: [],
        },
      };
    case CanvasNodeType.ModelOutput:
      return {
        type,
        name,
        configuration: {
          sourceTableName: '',
          targetModelId: '',
          writeMode: null,
          columnMappingMode: null,
          columnMappings: [],
        },
      };
    case CanvasNodeType.JdbcOutput:
      return {
        type,
        name,
        configuration: {
          sourceTableName: '',
          dataSourceId: '',
          targetTableName: '',
          writeMode: null,
          columnMappingMode: null,
          columnMappings: [],
        },
      };
    case CanvasNodeType.KafkaOutput:
      return {
        type,
        name,
        configuration: {
          sourceTableName: '',
          dataSourceId: '',
          topic: '',
          valueSchema: { columns: [] },
          keyColumnName: '',
          columnMappingMode: null,
          columnMappings: [],
        },
      };
  }
};

const createNode = (graph: Graph, template: CanvasNodeTemplate) => graph.createNode({
  id: crypto.randomUUID(),
  shape: template.shape,
  width: template.width,
  height: template.height,
  ports: canvasNodePorts(template.type),
  data: emptyRuntimeData(template.type),
});

const canConnectNodeTypes = (sourceType: CanvasNodeType, targetType: CanvasNodeType) => {
  const sourceCategory = canvasNodeTemplate(sourceType).category;
  const targetCategory = canvasNodeTemplate(targetType).category;
  if (sourceCategory === CanvasNodeCategory.Input) {
    return targetCategory === CanvasNodeCategory.Processor || targetCategory === CanvasNodeCategory.Output;
  }
  return sourceCategory === CanvasNodeCategory.Processor
    && (targetCategory === CanvasNodeCategory.Processor || targetCategory === CanvasNodeCategory.Output);
};

const taskCompilationStatus = (
  compilation: CanvasTaskCompilationState,
  metadataIssues: ReturnType<typeof useCanvasMetadataSnapshot>['issues'],
) => {
  switch (compilation.status) {
    case 'IDLE':
      return { color: 'default', text: '等待校验', detail: '画布发生配置变化后将自动调用 Task Engine' };
    case 'WAITING_METADATA':
      return { color: 'processing', text: '等待元数据', detail: '正在读取 Engine 编译所需的数据源和字段信息' };
    case 'METADATA_ERROR':
      return {
        color: 'error',
        text: '元数据读取失败',
        detail: metadataIssues.length > 1
          ? `${metadataIssues[0]?.message ?? '无法生成完整的编译元数据快照'}（另有 ${metadataIssues.length - 1} 个问题）`
          : metadataIssues[0]?.message ?? '无法生成完整的编译元数据快照',
      };
    case 'WAITING':
      return { color: 'default', text: '等待校验', detail: '配置变化已记录，即将调用 Task Engine' };
    case 'COMPILING':
      return { color: 'processing', text: '引擎校验中', detail: 'Task Engine 正在编译当前 Canvas 定义' };
    case 'UNAVAILABLE':
      return {
        color: 'warning',
        text: 'Task Engine 不可用',
        detail: compilation.error instanceof Error ? compilation.error.message : 'Task Engine 校验请求失败',
      };
    case 'SUCCESS': {
      const response = compilation.response;
      if (!response) return { color: 'default', text: '等待校验', detail: '' };
      const issues = [response.canvasIssues, ...response.nodeResults.map((result) => result.issues)].flat();
      const errors = issues.filter((issue) => issue.severity === 'ERROR').length;
      const warnings = issues.filter((issue) => issue.severity === 'WARNING').length;
      if (errors > 0) return { color: 'error', text: `引擎发现 ${errors} 个错误`, detail: '当前定义未通过 Task Engine 校验' };
      if (warnings > 0) return { color: 'warning', text: `引擎校验通过，${warnings} 个警告`, detail: '定义可编译，但存在需要关注的提示' };
      return { color: 'success', text: '引擎校验通过', detail: `Spark ${response.sparkApplicationId}` };
    }
  }
  return { color: 'default', text: '等待校验', detail: '' };
};

export const CanvasDesigner = ({
  initialDefinition = emptyCanvasDefinition(),
  onDefinitionChange,
  onInspectorDirtyChange,
  toolbarLeading,
  toolbarTrailing,
  executionMode = 'BATCH',
}: CanvasDesignerProps) => {
  const graphContainerRef = useRef<HTMLDivElement>(null);
  const minimapContainerRef = useRef<HTMLDivElement>(null);
  const paletteRef = useRef<HTMLDivElement>(null);
  const importInputRef = useRef<HTMLInputElement>(null);
  const graphRef = useRef<Graph | null>(null);
  const dndRef = useRef<Dnd | null>(null);
  const inspectorRef = useRef<CanvasNodeInspectorHandle>(null);
  const suspendedRef = useRef(false);
  const initialDefinitionRef = useRef(initialDefinition);
  const refreshDefinitionRef = useRef<() => void>(() => undefined);
  const selectedNodeIdRef = useRef<string | undefined>(undefined);
  const inspectorDirtyRef = useRef(false);
  const inspectorDirtyChangeRef = useRef(onInspectorDirtyChange);
  const definitionChangeRef = useRef(onDefinitionChange);
  const pendingInspectorActionRef = useRef<PendingInspectorAction | null>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<string>();
  const [inspectorDirty, setInspectorDirty] = useState(false);
  const [inspectorResetVersion, setInspectorResetVersion] = useState(0);
  const [pendingInspectorAction, setPendingInspectorAction] = useState<PendingInspectorAction | null>(null);
  const [applyingInspector, setApplyingInspector] = useState(false);
  const [validationRequestVersion, setValidationRequestVersion] = useState(0);
  const [definition, setDefinition] = useState<CanvasDefinition>(initialDefinition);
  const [activePaletteCategory, setActivePaletteCategory] = useState<CanvasNodeCategory | null>(null);
  const metadata = useCanvasMetadataSnapshot(definition);
  const taskCompilation = useCanvasTaskCompilation({
    definition,
    metadataSnapshot: metadata.metadataSnapshot,
    metadataLoading: metadata.loading,
    metadataError: metadata.error,
    validationRequestVersion,
    executionMode,
  });
  const engineValidation = useMemo(
    () => taskCompilation.response ? taskCompilationValidation(taskCompilation.response) : null,
    [taskCompilation.response],
  );
  const [definitionOpen, setDefinitionOpen] = useState(false);

  const selectedNode = useMemo(
    () => definition.nodes.find((node) => node.id === selectedNodeId) ?? null,
    [definition.nodes, selectedNodeId],
  );
  const selectedValidation = selectedNodeId ? engineValidation?.nodeResults.get(selectedNodeId) : undefined;
  const compilationStatus = taskCompilationStatus(taskCompilation, metadata.issues);
  const compilationBlocking = isCanvasTaskCompilationBlocking(taskCompilation.status);
  const selectedMetadataIssue = selectedNodeId
    ? metadata.issues.find((issue) => issue.nodeIds.includes(selectedNodeId))
    : undefined;
  const validationUnavailableMessage = engineValidation
    ? null
    : selectedMetadataIssue?.message ?? (compilationStatus.detail || compilationStatus.text);
  const canRetryCompilation = taskCompilation.status === 'METADATA_ERROR'
    || taskCompilation.status === 'UNAVAILABLE';

  const retryCompilation = () => {
    if (taskCompilation.status === 'METADATA_ERROR') {
      void metadata.retry();
      return;
    }
    taskCompilation.retry();
  };

  const updateInspectorDirty = useCallback((dirty: boolean) => {
    inspectorDirtyRef.current = dirty;
    setInspectorDirty(dirty);
  }, []);

  const restoreInspectorSelection = useCallback(() => {
    const graph = graphRef.current;
    const currentNodeId = selectedNodeIdRef.current;
    if (!graph || !currentNodeId || !graph.getCellById(currentNodeId)?.isNode()) return;
    graph.cleanSelection();
    graph.select(currentNodeId);
  }, []);

  const selectInspectorNode = useCallback((nodeId: string) => {
    selectedNodeIdRef.current = nodeId;
    setSelectedNodeId(nodeId);
    const graph = graphRef.current;
    if (!graph || !graph.getCellById(nodeId)?.isNode()) return;
    graph.cleanSelection();
    graph.select(nodeId);
  }, []);

  const closeInspector = useCallback(() => {
    selectedNodeIdRef.current = undefined;
    setSelectedNodeId(undefined);
    graphRef.current?.cleanSelection();
  }, []);

  const requestInspectorExit = useCallback((action: () => void) => {
    if (!inspectorDirtyRef.current) {
      action();
      return;
    }
    if (pendingInspectorActionRef.current) return;
    const pending = { execute: action };
    pendingInspectorActionRef.current = pending;
    setPendingInspectorAction(pending);
    restoreInspectorSelection();
  }, [restoreInspectorSelection]);

  useEffect(() => {
    inspectorDirtyChangeRef.current = onInspectorDirtyChange;
  }, [onInspectorDirtyChange]);

  useEffect(() => {
    definitionChangeRef.current = onDefinitionChange;
  }, [onDefinitionChange]);

  useEffect(() => {
    onInspectorDirtyChange?.(inspectorDirty);
  }, [inspectorDirty, onInspectorDirtyChange]);

  useEffect(() => () => {
    inspectorDirtyChangeRef.current?.(false);
  }, []);

  useEffect(() => {
    const container = graphContainerRef.current;
    const minimapContainer = minimapContainerRef.current;
    if (!container || !minimapContainer) return undefined;

    registerCanvasNodes();
    const graph = new Graph({
      container,
      autoResize: true,
      grid: {
        visible: true,
        type: 'doubleMesh',
        args: [{ color: '#f0f0f0', thickness: 1 }, { color: '#fafafa', thickness: 1, factor: 4 }],
      },
      panning: {
        enabled: true,
        eventTypes: ['leftMouseDown', 'mouseWheelDown'],
      },
      mousewheel: {
        enabled: true,
        guard: isCanvasZoomWheel,
        minScale: 0.5,
        maxScale: 2,
      },
      connecting: {
        allowBlank: false,
        allowLoop: false,
        allowEdge: false,
        allowNode: false,
        snap: { radius: 20 },
        connector: { name: 'rounded' },
        createEdge: () => new Shape.Edge({ id: crypto.randomUUID() }),
        validateConnection({ sourceCell, targetCell }) {
          if (!sourceCell?.isNode() || !targetCell?.isNode()) return false;
          const source = sourceCell as Node;
          const target = targetCell as Node;
          const sourceData = source.getData<CanvasNodeRuntimeData>();
          const targetData = target.getData<CanvasNodeRuntimeData>();
          if (!canConnectNodeTypes(sourceData.type, targetData.type)) return false;

          const current = toCanvasDefinition(graph);
          if (current.edges.some((edge) => edge.sourceNodeId === source.id && edge.targetNodeId === target.id)) {
            return false;
          }
          const targetIncomingCount = current.edges.filter((edge) => edge.targetNodeId === target.id).length;
          if ((targetData.type === CanvasNodeType.Join
            || targetData.type === CanvasNodeType.StreamJoin) && targetIncomingCount >= 2) return false;
          if (targetData.type === CanvasNodeType.Rename && targetIncomingCount >= 1) return false;
          if ((targetData.type === CanvasNodeType.ModelOutput
            || targetData.type === CanvasNodeType.JdbcOutput
            || targetData.type === CanvasNodeType.KafkaOutput) && targetIncomingCount >= 1) return false;
          return !createsCycle(current.edges, source.id, target.id);
        },
      },
    });

    const panCanvasWithWheel = (event: WheelEvent) => {
      if (isCanvasZoomWheel(event) || event.deltaX === 0) return;
      event.preventDefault();
      graph.translateBy(-event.deltaX, 0);
    };
    container.addEventListener('wheel', panCanvasWithWheel, { passive: false });

    const refresh = () => {
      if (suspendedRef.current) return;
      const nextDefinition = toCanvasDefinition(graph);
      setDefinition(nextDefinition);
      definitionChangeRef.current?.(nextDefinition);
    };
    refreshDefinitionRef.current = refresh;

    graph.use(new History({
      enabled: true,
      beforeAddCommand: (_event, args) => !(
        args !== null && 'options' in args && args.options?.canvasPresentationUpdate === true
      ),
    }));
    graph.use(new Keyboard({ enabled: true, guard: allowCanvasKeyboardEvent }));
    graph.use(new Selection({
      enabled: true,
      multiple: true,
      rubberband: true,
      modifiers: 'shift',
    }));
    graph.use(new Snapline());
    graph.use(new Transform({ resizing: canvasResizeOptions }));
    graph.use(new MiniMap({
      container: minimapContainer,
      width: 160,
      height: 110,
      padding: 8,
      scalable: false,
    }));
    graph.bindKey(['ctrl+z', 'meta+z'], () => {
      requestInspectorExit(() => {
        graph.undo();
        queueMicrotask(refresh);
      });
    });
    graph.bindKey(['ctrl+shift+z', 'meta+shift+z'], () => {
      requestInspectorExit(() => {
        graph.redo();
        queueMicrotask(refresh);
      });
    });
    graph.bindKey(['backspace', 'delete'], () => {
      const selectedCells = graph.getSelectedCells();
      const selectedNodes = selectedCells.filter((cell): cell is Node => cell.isNode());
      if (selectedNodes.length > 0) {
        requestCanvasNodeDeletion(graph, selectedNodes);
      } else {
        selectedCells.forEach((cell) => cell.remove());
        queueMicrotask(refresh);
      }
    });
    graph.on('edge:added', ({ edge }) => styleCanvasEdge(edge));
    graph.on('edge:connected', refresh);
    graph.on('edge:removed', refresh);
    graph.on('node:added', ({ node, options }) => {
      refresh();
      if (suspendedRef.current || !options.stencil) return;
      queueMicrotask(() => {
        setActivePaletteCategory(null);
        selectInspectorNode(node.id);
      });
    });
    graph.on('node:change:position', refresh);
    graph.on('node:change:size', refresh);
    graph.on('node:change:data', ({ options }) => {
      if (options.canvasPresentationUpdate || options.canvasConfigurationCommit) return;
      refresh();
    });
    graph.on('node:removed', ({ node }) => {
      if (selectedNodeIdRef.current === node.id) {
        selectedNodeIdRef.current = undefined;
        setSelectedNodeId(undefined);
        updateInspectorDirty(false);
      }
      refresh();
    });
    graph.on('node:click', ({ node }) => {
      setActivePaletteCategory(null);
      if (selectedNodeIdRef.current === node.id) return;
      requestInspectorExit(() => selectInspectorNode(node.id));
    });
    graph.on('blank:click', () => {
      setActivePaletteCategory(null);
      requestInspectorExit(closeInspector);
    });

    suspendedRef.current = true;
    loadCanvasDefinition(graph, initialDefinitionRef.current);
    suspendedRef.current = false;
    graphRef.current = graph;
    dndRef.current = new Dnd({ target: graph, dndContainer: paletteRef.current ?? undefined, scaled: false });
    const unregisterDeletionRequest = registerCanvasNodeDeletionRequestHandler(graph, (nodes) => {
      const action = () => confirmCanvasNodeDeletion(graph, nodes);
      const currentNodeId = selectedNodeIdRef.current;
      if (currentNodeId && nodes.some((node) => node.id === currentNodeId)) {
        requestInspectorExit(action);
        return;
      }
      action();
    });
    refresh();

    return () => {
      unregisterDeletionRequest();
      container.removeEventListener('wheel', panCanvasWithWheel);
      refreshDefinitionRef.current = () => undefined;
      dndRef.current = null;
      graphRef.current = null;
      graph.dispose();
    };
  }, [closeInspector, requestInspectorExit, selectInspectorNode, updateInspectorDirty]);

  useEffect(() => {
    const graph = graphRef.current;
    if (!graph) return;
    definition.nodes.forEach((nodeDefinition) => {
      const graphNode = graph.getCellById(nodeDefinition.id);
      if (!graphNode?.isNode()) return;
      graphNode.setData({
        validation: canvasNodeCompilationBadge(engineValidation?.nodeResults.get(nodeDefinition.id)),
        summary: metadata.nodeSummaries.get(nodeDefinition.id),
      }, { canvasPresentationUpdate: true, deep: false });
    });
  }, [definition.nodes, engineValidation, metadata.nodeSummaries]);

  const startNodeDrag = (event: MouseEvent<HTMLElement>, template: CanvasNodeTemplate) => {
    const graph = graphRef.current;
    const dnd = dndRef.current;
    if (!graph || !dnd) return;
    event.preventDefault();
    dnd.start(createNode(graph, template), event.nativeEvent);
  };

  const addNodeAtViewportCenter = (template: CanvasNodeTemplate) => {
    requestInspectorExit(() => {
      const graph = graphRef.current;
      const container = graphContainerRef.current;
      if (!graph || !container) return;

      const node = createNode(graph, template);
      const containerBounds = container.getBoundingClientRect();
      const viewportCenter = graph.clientToLocal(
        containerBounds.left + containerBounds.width / 2,
        containerBounds.top + containerBounds.height / 2,
      );
      const position = canvasNodeCenterPlacement({
        viewportCenter,
        nodeSize: { width: template.width, height: template.height },
        gridSize: graph.getGridSize(),
        occupiedBounds: graph.getNodes().map((existingNode) => existingNode.getBBox()),
      });
      node.position(position.x, position.y);
      graph.addNode(node, { canvasPaletteAdd: true });
      setActivePaletteCategory(null);
      selectInspectorNode(node.id);
    });
  };

  const handleBlockedNodeDrag = () => {
    requestInspectorExit(() => undefined);
  };

  const applyNodeConfiguration = (update: CanvasNodeConfigurationUpdate) => {
    const graphNode = graphRef.current?.getCellById(update.id);
    if (!graphNode?.isNode()) return;
    const current = graphNode.getData<CanvasNodeRuntimeData>();
    if (current.type !== update.type) return;
    graphNode.setData(
      { ...current, configuration: structuredClone(update.configuration) },
      { canvasConfigurationCommit: true },
    );
    refreshDefinitionRef.current();
    setValidationRequestVersion((currentVersion) => currentVersion + 1);
    message.success(`${current.name} 配置已应用`);
  };

  const replaceDefinition = (nextDefinition: CanvasDefinition) => {
    const graph = graphRef.current;
    if (!graph) return;
    suspendedRef.current = true;
    selectedNodeIdRef.current = undefined;
    setSelectedNodeId(undefined);
    setActivePaletteCategory(null);
    updateInspectorDirty(false);
    setInspectorResetVersion((current) => current + 1);
    replaceCanvasDefinition(graph, nextDefinition);
    suspendedRef.current = false;
    refreshDefinitionRef.current();
    if (nextDefinition.nodes.length > 0) graph.zoomToFit({ padding: 32, maxScale: 1 });
  };

  const confirmReplacement = (title: string, content: string, action: () => void) => {
    const graph = graphRef.current;
    if (!graph || graph.getNodes().length === 0) {
      action();
      return;
    }
    Modal.confirm({ title, content, okText: '替换画布', cancelText: '取消', onOk: action });
  };

  const loadExample = () => requestInspectorExit(() => {
    confirmReplacement(
      '加载示例定义？',
      '当前画布将被完整替换，尚未导出的内容会丢失。',
      () => {
        replaceDefinition(executionMode === 'STREAMING'
          ? exampleStreamingCanvasTopologyDefinition()
          : exampleCanvasTopologyDefinition());
        message.success(executionMode === 'STREAMING'
          ? '已加载实时节点连线示例，请配置 Kafka、模型和静态维表'
          : '已加载节点连线示例，请选择真实数据源和物理表');
      },
    );
  });

  const importDefinition = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    const parsed = parseCanvasDefinitionJson(await file.text());
    if (!parsed.success) {
      Modal.error({
        title: '无法导入 Canvas 定义',
        content: <List size="small" dataSource={parsed.errors} renderItem={(error) => <List.Item>{error}</List.Item>} />,
      });
      return;
    }
    requestInspectorExit(() => {
      confirmReplacement(
        '导入 Canvas 定义？',
        '当前画布将被完整替换。业务配置无效的草稿仍可导入并在画布中显示错误。',
        () => {
          replaceDefinition(parsed.definition);
          message.success('Canvas 定义已导入');
        },
      );
    });
  };

  const showDefinition = () => {
    refreshDefinitionRef.current();
    setDefinitionOpen(true);
  };

  const undo = () => requestInspectorExit(() => {
    graphRef.current?.undo();
    queueMicrotask(refreshDefinitionRef.current);
  });

  const redo = () => requestInspectorExit(() => {
    graphRef.current?.redo();
    queueMicrotask(refreshDefinitionRef.current);
  });

  const closePendingInspectorAction = () => {
    pendingInspectorActionRef.current = null;
    setPendingInspectorAction(null);
  };

  const continueEditingInspector = () => {
    closePendingInspectorAction();
    restoreInspectorSelection();
  };

  const discardInspectorAndContinue = () => {
    const action = pendingInspectorActionRef.current;
    closePendingInspectorAction();
    updateInspectorDirty(false);
    setInspectorResetVersion((current) => current + 1);
    action?.execute();
  };

  const applyInspector = async () => {
    setApplyingInspector(true);
    try {
      return await inspectorRef.current?.apply() ?? false;
    } finally {
      setApplyingInspector(false);
    }
  };

  const applyInspectorAndContinue = async () => {
    const action = pendingInspectorActionRef.current;
    if (!action) return;
    const applied = await applyInspector();
    if (!applied) {
      closePendingInspectorAction();
      restoreInspectorSelection();
      return;
    }
    closePendingInspectorAction();
    updateInspectorDirty(false);
    action.execute();
  };

  return (
    <div className="canvas-designer">
      <div className="canvas-toolbar">
        {toolbarLeading && <div className="canvas-toolbar-leading">{toolbarLeading}</div>}
        <Space wrap className="canvas-toolbar-actions">
          <Tooltip title={compilationStatus.detail}>
            <Tag color={compilationStatus.color}>{compilationStatus.text}</Tag>
          </Tooltip>
          {canRetryCompilation && <Button onClick={retryCompilation}>重新校验</Button>}
          <Button icon={<ReadOutlined />} onClick={loadExample}>加载示例</Button>
          <Button icon={<ImportOutlined />} onClick={() => importInputRef.current?.click()}>导入 JSON</Button>
          <Button type="primary" icon={<EyeOutlined />} onClick={showDefinition}>查看定义</Button>
          {toolbarTrailing}
          <input ref={importInputRef} hidden type="file" accept="application/json,.json" onChange={(event) => void importDefinition(event)} />
        </Space>
      </div>

      <div className="canvas-workspace">
        <div className={`canvas-stage${definition.nodes.length === 0 ? ' canvas-stage-empty' : ''}`}>
          <div className="canvas-graph" ref={graphContainerRef} aria-label="任务编排画布" />
          <CanvasNodePalette
            ref={paletteRef}
            templates={canvasNodeTemplates}
            executionMode={executionMode}
            activeCategory={activePaletteCategory}
            inspectorDirty={inspectorDirty}
            onActiveCategoryChange={setActivePaletteCategory}
            onAddNode={addNodeAtViewportCenter}
            onStartNodeDrag={startNodeDrag}
            onBlockedDrag={handleBlockedNodeDrag}
          />
          <div className="canvas-navigation">
            <div className="canvas-minimap" ref={minimapContainerRef} aria-label="画布鹰眼图" />
            <div className="canvas-navigation-toolbar" role="toolbar" aria-label="画布视图控制">
              <Tooltip title="撤销">
                <Button type="text" icon={<UndoOutlined />} aria-label="撤销" onClick={undo} />
              </Tooltip>
              <Tooltip title="重做">
                <Button type="text" icon={<RedoOutlined />} aria-label="重做" onClick={redo} />
              </Tooltip>
              <Tooltip title="放大">
                <Button
                  type="text"
                  icon={<ZoomInOutlined />}
                  aria-label="放大画布"
                  onClick={() => graphRef.current?.zoom(0.1)}
                />
              </Tooltip>
              <Tooltip title="缩小">
                <Button
                  type="text"
                  icon={<ZoomOutOutlined />}
                  aria-label="缩小画布"
                  onClick={() => graphRef.current?.zoom(-0.1)}
                />
              </Tooltip>
              <Tooltip title="居中">
                <Button
                  type="text"
                  icon={<AimOutlined />}
                  aria-label="居中画布"
                  onClick={() => graphRef.current?.centerContent()}
                />
              </Tooltip>
            </div>
          </div>
          {selectedNode && (
            <aside className="canvas-inspector" aria-label="节点配置">
              <header className="canvas-inspector-header">
                <div className="canvas-inspector-heading">
                  <Typography.Text strong>{canvasNodeTemplate(selectedNode.type).label}配置</Typography.Text>
                  <Space size={6} wrap>
                    {inspectorDirty && <Tag color="warning">未应用</Tag>}
                  </Space>
                </div>
                <Button
                  type="text"
                  icon={<CloseOutlined />}
                  aria-label="关闭节点配置"
                  onClick={() => requestInspectorExit(closeInspector)}
                />
              </header>
              <div className="canvas-inspector-body">
                <CanvasNodeInspector
                  key={`${selectedNode.id}:${inspectorResetVersion}`}
                  ref={inspectorRef}
                  node={selectedNode}
                  validation={selectedValidation}
                  validationUnavailableMessage={validationUnavailableMessage}
                  executionMode={executionMode}
                  onApply={applyNodeConfiguration}
                  onDirtyChange={updateInspectorDirty}
                />
              </div>
              <footer className="canvas-inspector-footer">
                <Button onClick={() => requestInspectorExit(closeInspector)}>关闭</Button>
                <Button type="primary" loading={applyingInspector} onClick={() => void applyInspector()}>
                  应用配置
                </Button>
              </footer>
            </aside>
          )}
        </div>
        <CanvasCompilationMask
          visible={compilationBlocking}
          title={compilationStatus.text}
          detail={compilationStatus.detail}
        />
      </div>

      <CanvasDefinitionModal
        open={definitionOpen}
        definition={definition}
        validation={engineValidation}
        validationStatus={[compilationStatus.text, compilationStatus.detail].filter(Boolean).join('：')}
        onClose={() => setDefinitionOpen(false)}
      />
      <Modal
        open={pendingInspectorAction !== null}
        title="节点配置尚未应用"
        closable={false}
        maskClosable={false}
        onCancel={continueEditingInspector}
        footer={(
          <Space>
            <Button onClick={continueEditingInspector}>继续编辑</Button>
            <Button danger onClick={discardInspectorAndContinue}>放弃修改</Button>
            <Button type="primary" loading={applyingInspector} onClick={() => void applyInspectorAndContinue()}>
              应用并继续
            </Button>
          </Space>
        )}
      >
        当前节点的配置已修改，离开后这些修改会丢失。
      </Modal>
    </div>
  );
};
