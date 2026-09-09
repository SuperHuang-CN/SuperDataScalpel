import { Graph } from '@antv/x6';
import { useEffect, useRef } from 'react';
import type { WorkflowDefinition } from './workflowTypes';
import './workflow.css';

export interface WorkflowSelection { nodeId?: string; edgeIndex?: number }
interface Props {
  definition: WorkflowDefinition;
  names: Record<string, string>;
  states?: Record<string, string>;
  selected?: WorkflowSelection | null;
  editable?: boolean;
  onChange?: (definition: WorkflowDefinition) => void;
  onSelect?: (selection: WorkflowSelection) => void;
  onOpenNode?: (id: string) => void;
}
const colors: Record<string, string> = {
  SUCCESS: '#389e0d', FAILED: '#cf1322', TIMED_OUT: '#cf1322', RUNNING: '#1677ff',
  CANCEL_REQUESTED: '#d48806', CANCELLED: '#8c8c8c', BLOCKED: '#8c8c8c', SKIPPED: '#8c8c8c',
};
const labels: Record<string, string> = {
  SUCCESS: '成功', FAILED: '失败', TIMED_OUT: '超时', RUNNING: '运行中', QUEUED: '排队中',
  CANCEL_REQUESTED: '正在停止', CANCELLED: '已取消', BLOCKED: '已阻断', SKIPPED: '已跳过', WAITING: '等待依赖',
};
export const WorkflowGraph = (props: Props) => {
  const container = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);
  const applying = useRef(false);
  const latest = useRef(props);
  useEffect(() => { latest.current = props; });
  useEffect(() => {
    if (!container.current) return;
    const graph: Graph = new Graph({
      container: container.current, autoResize: true, grid: true, background: { color: '#fafbff' },
      panning: true, mousewheel: { enabled: true, modifiers: ['ctrl', 'meta'], minScale: 0.25, maxScale: 2 },
      interacting: props.editable ? true : false,
      connecting: { allowBlank: false, allowLoop: false, allowMulti: false, allowNode: false, snap: true,
        createEdge: () => graph.createEdge({ attrs: { line: { stroke: '#879bd8', targetMarker: 'classic' } } }),
        validateConnection: ({ sourcePort, targetPort }) => sourcePort === 'out' && targetPort === 'in',
      },
    });
    graphRef.current = graph;
    const changed = () => {
      if (applying.current) return;
      const current = latest.current.definition;
      const layout = Object.fromEntries(graph.getNodes().map(node => [node.id, node.position()]));
      const edges = graph.getEdges().filter(edge => edge.getSourceCellId() && edge.getTargetCellId())
        .map(edge => ({ source: edge.getSourceCellId(), target: edge.getTargetCellId() }));
      latest.current.onChange?.({ ...current, layout, edges });
    };
    graph.on('node:moved', changed);
    graph.on('edge:connected', changed);
    graph.on('edge:removed', changed);
    graph.on('node:click', ({ node }) => latest.current.onSelect?.({ nodeId: node.id }));
    graph.on('node:dblclick', ({ node }) => latest.current.onOpenNode?.(node.id));
    graph.on('edge:click', ({ edge }) => {
      const index = latest.current.definition.edges.findIndex(value => value.source === edge.getSourceCellId() && value.target === edge.getTargetCellId());
      if (index >= 0) latest.current.onSelect?.({ edgeIndex: index });
    });
    return () => { graph.dispose(); graphRef.current = null; };
  }, [props.editable]);
  useEffect(() => {
    const graph = graphRef.current;
    if (!graph) return;
    applying.current = true;
    try {
      graph.clearCells();
      props.definition.nodes.forEach((node, index) => {
        const position = props.definition.layout[node.id] ?? { x: 32 + (index % 3) * 240, y: 36 + Math.floor(index / 3) * 110 };
        const state = props.states?.[node.id];
        const name = props.names[node.taskId] ?? (node.taskId ? '引用任务不可用' : '请选择任务');
        graph.addNode({ id: node.id, shape: 'rect', ...position, width: 200, height: 64,
          label: `${name.length > 20 ? `${name.slice(0, 19)}…` : name}${state ? `\n${labels[state] ?? state}` : ''}`,
          attrs: { body: { rx: 9, ry: 9, fill: '#fff', strokeWidth: props.selected?.nodeId === node.id ? 3 : 1.5,
            stroke: state ? colors[state] ?? '#879bd8' : '#879bd8' }, label: { fill: '#26324f', fontSize: 12 } },
          ports: { groups: {
            in: { position: 'left', attrs: { circle: { r: 5, magnet: props.editable === true, stroke: '#879bd8', fill: '#fff' } } },
            out: { position: 'right', attrs: { circle: { r: 5, magnet: props.editable === true, stroke: '#879bd8', fill: '#fff' } } },
          }, items: [{ id: 'in', group: 'in' }, { id: 'out', group: 'out' }] },
        });
      });
      props.definition.edges.forEach((edge, index) => {
        if (!graph.getCellById(edge.source) || !graph.getCellById(edge.target)) return;
        graph.addEdge({ id: `edge-${index}`, source: { cell: edge.source, port: 'out' }, target: { cell: edge.target, port: 'in' },
          attrs: { line: { stroke: props.selected?.edgeIndex === index ? '#1677ff' : '#879bd8', strokeWidth: 2, targetMarker: 'classic' } } });
      });
    } finally { applying.current = false; }
  }, [props.definition, props.names, props.states, props.selected, props.editable]);
  return <div className="workflow-graph" ref={container} aria-label={props.editable ? '工作流依赖编辑画布' : '工作流依赖图'} />;
};
