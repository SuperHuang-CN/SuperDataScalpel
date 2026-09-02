import {
  AimOutlined,
  CloseOutlined,
  EyeOutlined,
  FullscreenExitOutlined,
  FullscreenOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
} from '@ant-design/icons';
import { Graph, MiniMap, Selection } from '@antv/x6';
import { Button, Descriptions, Input, Space, Tag, Tooltip, Typography } from 'antd';
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { isCanvasZoomWheel } from '../canvasInteraction';
import { canvasNodeCompilationBadge } from '../canvasCompilationPresentation';
import {
  registerCanvasNodeOutputSchemaController,
  type CanvasNodeOutputSchemaTarget,
  type CanvasNodeTrialTarget,
} from '../canvasNodeTrial';
import { canvasNodeTemplate, registerCanvasNodes } from '../canvasRegistry';
import { loadCanvasDefinition, runtimeDataFromDefinition } from '../canvasSerialization';
import {
  CanvasNodeCategory,
  type CanvasDefinition,
  type CanvasExecutionMode,
  type CanvasNodeDefinition,
  type CanvasNodeRuntimeData,
} from '../canvasTypes';
import { canvasNodeRegistry } from '../nodes/nodeRegistry';
import { taskCompilationValidation } from '../taskCompilationTypes';
import { useCanvasMetadataSnapshot } from '../useCanvasMetadataSnapshot';
import { useCanvasTaskCompilation } from '../useCanvasTaskCompilation';
import { CanvasDefinitionModal } from './CanvasDefinitionModal';
import { CanvasTableSchemaModal } from './CanvasTableSchemaModal';
import type { TaskStatus } from '../../model/task';

interface CanvasDefinitionViewerProps {
  definition: CanvasDefinition;
  executionMode: CanvasExecutionMode;
  toolbarLeading?: ReactNode;
  toolbarTrailing?: ReactNode;
  fullscreen: boolean;
  onToggleFullscreen: () => void;
  trialContext?: {
    taskId: string;
    baseDefinitionVersion: number;
    taskStatus: TaskStatus;
  };
}

const categoryLabels: Record<CanvasNodeCategory, string> = {
  [CanvasNodeCategory.Input]: '输入',
  [CanvasNodeCategory.Processor]: '处理器',
  [CanvasNodeCategory.Output]: '输出',
};

export const CanvasDefinitionViewer = ({
  definition,
  executionMode,
  toolbarLeading,
  toolbarTrailing,
  fullscreen,
  onToggleFullscreen,
  trialContext,
}: CanvasDefinitionViewerProps) => {
  const graphContainerRef = useRef<HTMLDivElement>(null);
  const minimapContainerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<string>();
  const [definitionOpen, setDefinitionOpen] = useState(false);
  const [outputSchemaTarget, setOutputSchemaTarget] = useState<CanvasNodeOutputSchemaTarget | null>(null);
  const metadata = useCanvasMetadataSnapshot(definition);
  const compilation = useCanvasTaskCompilation({
    definition,
    metadataSnapshot: metadata.metadataSnapshot,
    metadataLoading: metadata.loading,
    metadataError: metadata.error,
    executionMode,
  });
  const engineValidation = useMemo(
    () => compilation.response ? taskCompilationValidation(compilation.response) : null,
    [compilation.response],
  );
  const selectedNode = useMemo(
    () => definition.nodes.find((node) => node.id === selectedNodeId) ?? null,
    [definition.nodes, selectedNodeId],
  );
  useEffect(() => {
    const container = graphContainerRef.current;
    const minimapContainer = minimapContainerRef.current;
    if (!container || !minimapContainer) return undefined;

    registerCanvasNodes();
    const graph = new Graph({
      container,
      autoResize: true,
      interacting: false,
      grid: {
        visible: true,
        type: 'doubleMesh',
        args: [
          { color: '#f0f0f0', thickness: 1 },
          { color: '#fafafa', thickness: 1, factor: 4 },
        ],
      },
      panning: { enabled: true, eventTypes: ['leftMouseDown', 'mouseWheelDown'] },
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
        validateConnection: () => false,
      },
    });

    const panCanvasWithWheel = (event: WheelEvent) => {
      if (isCanvasZoomWheel(event) || event.deltaX === 0) return;
      event.preventDefault();
      graph.translateBy(-event.deltaX, 0);
    };
    container.addEventListener('wheel', panCanvasWithWheel, { passive: false });

    graph.use(new Selection({ enabled: true, multiple: false, rubberband: false }));
    graph.use(new MiniMap({
      container: minimapContainer,
      width: 160,
      height: 110,
      padding: 8,
      scalable: false,
    }));
    graph.on('node:click', ({ node }) => {
      graph.cleanSelection();
      graph.select(node.id);
      setSelectedNodeId(node.id);
    });
    graph.on('blank:click', () => {
      graph.cleanSelection();
      setSelectedNodeId(undefined);
    });
    loadCanvasDefinition(graph, definition, { readOnly: true });
    const unregisterOutputSchemaController = registerCanvasNodeOutputSchemaController(graph, {
      openOutputSchema: (node) => {
        const runtime = node.getData<CanvasNodeRuntimeData>();
        const nodeCompilation = runtime.compilation;
        if (!nodeCompilation || nodeCompilation.outputTables.length === 0) return;
        setOutputSchemaTarget({
          nodeId: node.id,
          nodeName: runtime.name,
          inputTables: nodeCompilation.inputTables,
          outputTables: nodeCompilation.outputTables,
        });
      },
    });
    graphRef.current = graph;
    window.requestAnimationFrame(() => {
      if (definition.nodes.length > 0) graph.zoomToFit({ padding: 40, maxScale: 1 });
    });

    return () => {
      container.removeEventListener('wheel', panCanvasWithWheel);
      unregisterOutputSchemaController();
      graphRef.current = null;
      graph.dispose();
    };
  }, [definition, executionMode]);

  useEffect(() => {
    const graph = graphRef.current;
    if (!graph) return;
    definition.nodes.forEach((nodeDefinition) => {
      const graphNode = graph.getCellById(nodeDefinition.id);
      if (!graphNode?.isNode()) return;
      const result = engineValidation?.nodeResults.get(nodeDefinition.id);
      graphNode.setData({
        validation: canvasNodeCompilationBadge(result),
        summary: metadata.nodeSummaries.get(nodeDefinition.id),
        compilation: result ? {
          inputTables: result.inputTables,
          outputTables: result.outputTables,
        } : undefined,
      }, { canvasPresentationUpdate: true, deep: false });
    });
  }, [definition.nodes, engineValidation, metadata.nodeSummaries]);

  const closeNodeDetails = () => {
    graphRef.current?.cleanSelection();
    setSelectedNodeId(undefined);
  };

  const trialActionVisible = Boolean(trialContext && executionMode === 'BATCH');
  const trialActionDisabledReason = !trialActionVisible
    ? undefined
    : trialContext?.taskStatus === 'PUBLISHED'
      ? '已发布任务不能发起 Canvas 试运行，请先停用任务'
      : (trialContext?.baseDefinitionVersion ?? 0) < 1
        ? '请先保存一次 Canvas 定义，再发起试运行'
        : !engineValidation
          ? '正在等待最新的 Task Engine 编译结果'
          : undefined;
  const createTrialTarget = outputSchemaTarget && trialContext && trialActionVisible
    ? (tableName: string): CanvasNodeTrialTarget => ({
      taskId: trialContext.taskId,
      baseDefinitionVersion: trialContext.baseDefinitionVersion,
      definition,
      nodeId: outputSchemaTarget.nodeId,
      nodeName: outputSchemaTarget.nodeName,
      inputTables: outputSchemaTarget.inputTables,
      outputTables: outputSchemaTarget.outputTables,
      initialTableName: tableName,
    })
    : undefined;

  return (
    <div className={`canvas-designer canvas-designer-view${fullscreen ? ' canvas-designer-fullscreen' : ''}`}>
      <div className="canvas-toolbar">
        {toolbarLeading && <div className="canvas-toolbar-leading">{toolbarLeading}</div>}
        <Space wrap className="canvas-toolbar-actions">
          <Tag>只读预览</Tag>
          <Button type="primary" icon={<EyeOutlined />} onClick={() => setDefinitionOpen(true)}>
            查看定义
          </Button>
          <Tooltip title={fullscreen ? '退出全屏' : '全屏'}>
            <Button
              icon={fullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
              aria-label={fullscreen ? '退出 Canvas 全屏' : 'Canvas 全屏'}
              onClick={onToggleFullscreen}
            />
          </Tooltip>
          {toolbarTrailing}
        </Space>
      </div>

      <div className="canvas-workspace">
        <div className={`canvas-stage${definition.nodes.length === 0 ? ' canvas-stage-empty' : ''}`}>
          <div className="canvas-graph" ref={graphContainerRef} aria-label="只读任务编排画布" />
          <div className="canvas-navigation">
            <div className="canvas-minimap" ref={minimapContainerRef} aria-label="画布鹰眼图" />
            <div className="canvas-navigation-toolbar" role="toolbar" aria-label="画布视图控制">
              <Tooltip title="放大">
                <Button type="text" icon={<ZoomInOutlined />} aria-label="放大画布" onClick={() => graphRef.current?.zoom(0.1)} />
              </Tooltip>
              <Tooltip title="缩小">
                <Button type="text" icon={<ZoomOutOutlined />} aria-label="缩小画布" onClick={() => graphRef.current?.zoom(-0.1)} />
              </Tooltip>
              <Tooltip title="适应画布">
                <Button
                  type="text"
                  icon={<AimOutlined />}
                  aria-label="适应画布"
                  onClick={() => graphRef.current?.zoomToFit({ padding: 40, maxScale: 1 })}
                />
              </Tooltip>
            </div>
          </div>
          {selectedNode && (
            <CanvasReadOnlyNodeDetails
              node={selectedNode}
              executionMode={executionMode}
              onClose={closeNodeDetails}
            />
          )}
        </div>
      </div>

      <CanvasDefinitionModal
        open={definitionOpen}
        definition={definition}
        validation={null}
        validationStatus="只读预览不调用 Task Engine 校验"
        onClose={() => setDefinitionOpen(false)}
      />
      {outputSchemaTarget && (
        <CanvasTableSchemaModal
          key={outputSchemaTarget.nodeId}
          open
          title={`节点输出结构 · ${outputSchemaTarget.nodeName}`}
          tables={outputSchemaTarget.outputTables}
          createTrialTarget={createTrialTarget}
          trialRunDisabled={trialActionDisabledReason !== undefined}
          trialRunDisabledReason={trialActionDisabledReason}
          onClose={() => setOutputSchemaTarget(null)}
        />
      )}
    </div>
  );
};

const CanvasReadOnlyNodeDetails = ({
  node,
  executionMode,
  onClose,
}: {
  node: CanvasNodeDefinition;
  executionMode: CanvasExecutionMode;
  onClose: () => void;
}) => {
  const template = canvasNodeTemplate(node.type);
  const summary = canvasNodeRegistry.summarize(runtimeDataFromDefinition(node, { readOnly: true }));
  return (
    <aside className="canvas-inspector canvas-readonly-inspector" aria-label="节点详情">
      <header className="canvas-inspector-header">
        <div className="canvas-inspector-heading">
          <Typography.Text strong>{node.name}</Typography.Text>
          <Tag>{template.label}</Tag>
        </div>
        <Button type="text" icon={<CloseOutlined />} aria-label="关闭节点详情" onClick={onClose} />
      </header>
      <div className="canvas-inspector-body">
        <Descriptions bordered size="small" column={1}>
          <Descriptions.Item label="节点类型">{node.type}</Descriptions.Item>
          <Descriptions.Item label="节点分类">{categoryLabels[template.category]}</Descriptions.Item>
          <Descriptions.Item label="执行模式">{executionMode === 'STREAMING' ? '实时' : '批处理'}</Descriptions.Item>
          <Descriptions.Item label="配置摘要">{summary || '—'}</Descriptions.Item>
        </Descriptions>
        <Typography.Text strong className="canvas-readonly-configuration-title">节点配置</Typography.Text>
        <Input.TextArea
          readOnly
          value={JSON.stringify(node.configuration, null, 2)}
          autoSize={{ minRows: 10, maxRows: 24 }}
          aria-label={`${node.name}节点配置 JSON`}
          className="canvas-readonly-configuration-json"
        />
      </div>
      <footer className="canvas-inspector-footer">
        <Button onClick={onClose}>关闭</Button>
      </footer>
    </aside>
  );
};
