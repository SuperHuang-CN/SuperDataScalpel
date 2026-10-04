import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { LineageGraph, LineageGraphNode } from '../model/dataModel';

const canvas = vi.hoisted(() => ({
  nodes: [] as Array<{ id: string; shape: string; data: Record<string, unknown> }>,
  handlers: {} as Record<string, (event?: unknown) => void>,
  edgeAttr: vi.fn(),
  dispose: vi.fn(),
}));
vi.mock('@antv/x6', () => ({ Graph: class {
  addNodes(nodes: typeof canvas.nodes) { canvas.nodes.push(...nodes); }
  addEdges() {}
  on(name: string, handler: (event?: unknown) => void) { canvas.handlers[name] = handler; }
  getEdges() { return [{ id: 'edge', attr: canvas.edgeAttr, getData: () => ({ type: 'DERIVES' }) }]; }
  zoomToFit() {}
  dispose() { canvas.dispose(); }
} }));
vi.mock('./LineageNodeResourceDetails', () => ({ LineageNodeResourceDetails: () => null }));
vi.mock('@antv/x6-react-shape', () => ({ register: vi.fn() }));

import { LineageGraphCanvas } from './LineageGraphCanvas';

const node = (overrides: Partial<LineageGraphNode> = {}): LineageGraphNode => ({
  id: 'model', kind: 'MODEL', label: '当前模型', subtitle: 'current_model', side: 'CURRENT', depth: 0,
  modelId: 'model', modelFieldId: null, taskId: null, dataSourceId: null, taskStatus: null,
  definitionVersion: null, writeMode: null, stale: false, externalResourceType: null,
  resourceId: null, dataServiceId: null, dataServiceType: null, dataServiceStatus: null,
  routePath: null, fieldOwner: null, focusRoot: false, focusFieldKeys: [], ...overrides,
});
const graph: LineageGraph = {
  rootNodeId: 'model', granularity: 'TABLE', coverage: 'FIELD_COMPLETE', warnings: [], truncated: false,
  nodes: [node(), node({ id: 'task', kind: 'TASK', label: '同步任务', taskStatus: 'PUBLISHED', definitionVersion: 3, writeMode: 'UPSERT' })],
  edges: [],
};
const renderGraph = (data = graph) => render(<LineageGraphCanvas graph={data} loading={false} emptyDescription="暂无血缘关系" ariaLabel="模型血缘" onRetry={vi.fn()} />);

describe('lineage workspace interactions', () => {
  afterEach(cleanup);
  beforeEach(() => { canvas.nodes = []; canvas.handlers = {}; vi.clearAllMocks(); });

  it('opens node properties on selection, closes and reopens without recreating the graph', () => {
    renderGraph();
    expect(screen.queryByLabelText('血缘节点属性')).not.toBeInTheDocument();
    expect(screen.getByText('节点与连线图例')).toBeInTheDocument();
    const task = canvas.nodes.find((item) => item.id === 'task')!;
    act(() => (task.data.onSelect as (value: LineageGraphNode) => void)(graph.nodes[1]));
    expect(screen.getByLabelText('血缘节点属性')).toHaveTextContent('同步任务');
    expect(screen.getByLabelText('血缘节点属性')).toHaveTextContent('更新或插入');
    fireEvent.click(screen.getByLabelText('关闭节点属性'));
    expect(screen.queryByLabelText('血缘节点属性')).not.toBeInTheDocument();
    expect(canvas.dispose).not.toHaveBeenCalled();
    act(() => canvas.handlers['node:click']({ node: { getData: () => graph.nodes[1] } }));
    expect(screen.getByLabelText('血缘节点属性')).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByLabelText('血缘节点属性')).not.toBeInTheDocument();
  });

  it('keeps per-field selection and locked path focus until blank click', () => {
    const field = node({ id: 'field', kind: 'FIELD', label: '订单金额', focusFieldKeys: ['amount'], fieldOwner: { key: 'owner', kind: 'MODEL', label: '订单', subtitle: 'orders', fieldOrder: 0 } });
    renderGraph({ ...graph, granularity: 'FIELD', nodes: [field], rootNodeId: 'field' });
    const card = canvas.nodes.find((item) => item.shape === 'data-scalpel-lineage-field-card')!;
    act(() => (card.data.onFieldClick as (value: LineageGraphNode) => void)(field));
    expect(screen.getByLabelText('血缘节点属性')).toHaveTextContent('订单金额');
    expect(canvas.edgeAttr).toHaveBeenCalledWith('line/opacity', 0.08);
    act(() => (card.data.onFieldLeave as () => void)());
    expect(canvas.edgeAttr).not.toHaveBeenCalledWith('line/opacity', 1);
    act(() => canvas.handlers['blank:click']());
    expect(canvas.edgeAttr).toHaveBeenCalledWith('line/opacity', 1);
    expect(screen.queryByLabelText('血缘节点属性')).not.toBeInTheDocument();
  });

  it('renders the root and keeps the no-relations explanation when no edges exist', () => {
    renderGraph({ ...graph, nodes: [node()] });
    expect(canvas.nodes).toHaveLength(1);
    expect(screen.getByText('暂无血缘关系')).toBeInTheDocument();
    expect(screen.getByLabelText('放大血缘图')).not.toBeDisabled();
  });
});
