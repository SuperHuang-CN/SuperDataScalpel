import { AimOutlined, ApartmentOutlined, ReloadOutlined, ZoomInOutlined, ZoomOutOutlined } from '@ant-design/icons';
import { Graph } from '@antv/x6';
import { Button, Descriptions, Empty, Space, Spin, Tag, Tooltip } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import type {
  LineageFieldDerivationType,
  LineageFieldUsageType,
  LineageGraph,
  LineageGraphEdge,
  LineageGraphNode,
  LineageGraphNodeKind,
  LineageFocusField,
  LineageOutputFieldEffect,
  LineageWriteMode,
} from '../model/dataModel';
import {
  LINEAGE_FIELD_CARD_SHAPE,
  registerLineageFieldCardNode,
  type LineageFieldCardData,
} from './LineageFieldCardNode';

interface PositionedLineageNode extends LineageGraphNode { x: number; y: number; width: number; height: number }

interface PositionedFieldCard {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  fields: LineageGraphNode[];
  owner: NonNullable<LineageGraphNode['fieldOwner']>;
  stale: boolean;
}

interface VisualEndpoint {
  cell: string;
  port?: string;
}

interface PositionedVisualGraph {
  ordinaryNodes: PositionedLineageNode[];
  fieldCards: PositionedFieldCard[];
  edges: Array<{ edge: LineageGraphEdge; source: VisualEndpoint; target: VisualEndpoint }>;
  fieldCardByNodeId: Map<string, string>;
}

interface LineageGraphCanvasProps {
  graph: LineageGraph | undefined;
  loading: boolean;
  errorMessage?: string;
  emptyDescription: string;
  ariaLabel: string;
  onRetry: () => void;
  sideLabels?: Record<'UPSTREAM' | 'CURRENT' | 'DOWNSTREAM', string>;
  legend?: string[];
  focusFields?: LineageFocusField[];
}

const lineageNodeKindLabels: Record<LineageGraphNodeKind, string> = {
  MODEL: '数据模型',
  JDBC_TABLE: 'JDBC 物理表',
  EXTERNAL_RESOURCE: '外部资源',
  TASK: '数据任务',
  FIELD: '字段',
  DATA_SERVICE: '数据服务',
};

const nodeThemes: Record<LineageGraphNodeKind, { fill: string; stroke: string }> = {
  MODEL: { fill: '#eef2ff', stroke: '#6366f1' },
  JDBC_TABLE: { fill: '#ecfeff', stroke: '#0891b2' },
  EXTERNAL_RESOURCE: { fill: '#fdf4ff', stroke: '#c026d3' },
  TASK: { fill: '#f0fdf4', stroke: '#16a34a' },
  FIELD: { fill: '#fff7ed', stroke: '#ea580c' },
  DATA_SERVICE: { fill: '#f9f0ff', stroke: '#722ed1' },
};

const writeLabels: Record<LineageWriteMode, string> = {
  APPEND: '追加', FULL_OVERWRITE: '全量覆盖', UPSERT: '更新或插入',
  PARTITION_OVERWRITE: '分区覆盖', SNAPSHOT_SYNC: '快照同步', CREATE_NEW: '新建写入',
};

const derivationLabels: Record<LineageFieldDerivationType, string> = {
  DIRECT: '直接映射', CALCULATED: '计算派生', AGGREGATED: '聚合派生',
};

const effectLabels: Record<LineageOutputFieldEffect, string> = {
  DERIVED: '派生写入', WRITTEN_UNKNOWN_SOURCE: '来源未知', CONSTANT: '常量',
  DEFAULT_VALUE: '默认值', NULL_FILLED: '空值填充', PRESERVED: '保留原值', NOT_WRITTEN: '未写入',
};

const usageLabels: Record<LineageFieldUsageType, string> = {
  JOIN_KEY: '关联条件', FILTER_CONDITION: '过滤条件', GROUP_KEY: '分组字段',
  SORT_KEY: '排序字段', PARTITION_KEY: '分区字段',
};

const serviceTypeLabels = {
  STANDARD_TABLE: '标准单表', SQL_QUERY: 'SQL 查询', SCRIPT_API: 'Groovy 脚本',
} as const;

const serviceStatusLabels = { DRAFT: '草稿', ENABLED: '已启用', DISABLED: '已停用' } as const;

const defaultSideLabels = { UPSTREAM: '上游', CURRENT: '当前', DOWNSTREAM: '下游' } as const;

const FIELD_CARD_WIDTH = 280;
const FIELD_CARD_HEADER_HEIGHT = 58;
const FIELD_CARD_ROW_HEIGHT = 38;

const layoutVisualGraph = (graph: LineageGraph): PositionedVisualGraph => {
  const fieldGroups = new Map<string, LineageGraphNode[]>();
  graph.nodes.filter((node) => node.kind === 'FIELD' && node.fieldOwner).forEach((field) => {
    const owner = field.fieldOwner as NonNullable<LineageGraphNode['fieldOwner']>;
    const group = fieldGroups.get(owner.key) ?? [];
    group.push(field);
    fieldGroups.set(owner.key, group);
  });
  const fieldCardByNodeId = new Map<string, string>();
  const portByNodeId = new Map<string, { input: string; output: string }>();
  const fieldCards = Array.from(fieldGroups.entries()).map(([ownerKey, fields]) => {
    const sortedFields = [...fields].sort((left, right) => (
      (left.fieldOwner?.fieldOrder ?? 0) - (right.fieldOwner?.fieldOrder ?? 0)
      || left.label.localeCompare(right.label)
      || left.id.localeCompare(right.id)
    ));
    const cardId = `field-card:${ownerKey}`;
    sortedFields.forEach((field, index) => {
      fieldCardByNodeId.set(field.id, cardId);
      portByNodeId.set(field.id, { input: `input-${index}`, output: `output-${index}` });
    });
    return {
      id: cardId,
      x: 0,
      y: 0,
      width: FIELD_CARD_WIDTH,
      height: FIELD_CARD_HEADER_HEIGHT + sortedFields.length * FIELD_CARD_ROW_HEIGHT,
      fields: sortedFields,
      owner: sortedFields[0].fieldOwner as NonNullable<LineageGraphNode['fieldOwner']>,
      stale: sortedFields.some((field) => field.stale),
    };
  });
  const ordinary = graph.nodes.filter((node) => node.kind !== 'FIELD');
  const visualEdges = graph.edges.flatMap((edge) => {
    const sourceCard = fieldCardByNodeId.get(edge.source);
    const targetCard = fieldCardByNodeId.get(edge.target);
    const source = sourceCard
      ? { cell: sourceCard, port: portByNodeId.get(edge.source)?.output }
      : { cell: edge.source };
    const target = targetCard
      ? { cell: targetCard, port: portByNodeId.get(edge.target)?.input }
      : { cell: edge.target };
    if (source.cell === target.cell) return [];
    return [{ edge, source, target }];
  });
  const visualNodeIds = [...fieldCards.map((card) => card.id), ...ordinary.map((node) => node.id)];
  const indegree = new Map(visualNodeIds.map((id) => [id, 0]));
  const successors = new Map<string, Set<string>>();
  visualEdges.forEach(({ source, target }) => {
    if (source.cell === target.cell) return;
    const next = successors.get(source.cell) ?? new Set<string>();
    if (!next.has(target.cell)) indegree.set(target.cell, (indegree.get(target.cell) ?? 0) + 1);
    next.add(target.cell);
    successors.set(source.cell, next);
  });
  const ranks = new Map<string, number>();
  const queue = visualNodeIds.filter((id) => (indegree.get(id) ?? 0) === 0).sort();
  while (queue.length > 0) {
    const id = queue.shift() as string;
    const rank = ranks.get(id) ?? 0;
    Array.from(successors.get(id) ?? []).sort().forEach((target) => {
      ranks.set(target, Math.max(ranks.get(target) ?? 0, rank + 1));
      indegree.set(target, (indegree.get(target) ?? 1) - 1);
      if (indegree.get(target) === 0) queue.push(target);
    });
    queue.sort();
  }
  visualNodeIds.forEach((id) => {
    if (!ranks.has(id)) ranks.set(id, 0);
  });
  const byRank = new Map<number, Array<{ id: string; height: number; order: string }>>();
  fieldCards.forEach((card) => {
    const rank = ranks.get(card.id) ?? 0;
    const items = byRank.get(rank) ?? [];
    items.push({ id: card.id, height: card.height, order: `${card.owner.label}:${card.id}` });
    byRank.set(rank, items);
  });
  ordinary.forEach((node) => {
    const rank = ranks.get(node.id) ?? 0;
    const items = byRank.get(rank) ?? [];
    items.push({ id: node.id, height: node.kind === 'TASK' ? 56 : 72, order: `${node.kind}:${node.label}:${node.id}` });
    byRank.set(rank, items);
  });
  const positions = new Map<string, { x: number; y: number }>();
  Array.from(byRank.entries()).sort(([left], [right]) => left - right).forEach(([rank, items]) => {
    items.sort((left, right) => left.order.localeCompare(right.order));
    const totalHeight = items.reduce((sum, item) => sum + item.height, 0) + Math.max(0, items.length - 1) * 28;
    let y = Math.max(36, (620 - totalHeight) / 2);
    items.forEach((item) => {
      positions.set(item.id, { x: 70 + rank * 390, y });
      y += item.height + 28;
    });
  });
  return {
    ordinaryNodes: ordinary.map((node) => ({
      ...node,
      ...(positions.get(node.id) ?? { x: 70, y: 36 }),
      width: node.kind === 'TASK' ? 176 : 220,
      height: node.kind === 'TASK' ? 56 : 72,
    })),
    fieldCards: fieldCards.map((card) => ({ ...card, ...(positions.get(card.id) ?? { x: 70, y: 36 }) })),
    edges: visualEdges,
    fieldCardByNodeId,
  };
};

const edgeLabel = (edge: LineageGraphEdge) => {
  if (edge.type === 'EXPOSES') return '对外暴露';
  if (edge.usages.length > 0) return edge.usages.map((usage) => usageLabels[usage]).join('、');
  if (edge.outputEffect && edge.outputEffect !== 'DERIVED') return effectLabels[edge.outputEffect];
  if (edge.derivationType && edge.derivationType !== 'DIRECT') return derivationLabels[edge.derivationType];
  return undefined;
};

const nodeSubtitleForCanvas = (node: LineageGraphNode) => (
  node.kind === 'JDBC_TABLE' ? node.subtitle.split(' · ')[0] : node.subtitle
);

export const LineageGraphCanvas = ({
  graph,
  loading,
  errorMessage,
  emptyDescription,
  ariaLabel,
  onRetry,
  sideLabels = defaultSideLabels,
  legend = [
    '从左到右：上游、当前、下游',
    '橙色虚线节点表示血缘基于旧结构或部署快照不一致',
    '紫色虚线边表示字段参与处理逻辑，不代表值来源',
  ],
  focusFields,
}: LineageGraphCanvasProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);
  const [selectedNode, setSelectedNode] = useState<LineageGraphNode>();
  const selectedEdges = useMemo(() => graph?.edges.filter(
    (edge) => edge.source === selectedNode?.id || edge.target === selectedNode?.id,
  ) ?? [], [graph?.edges, selectedNode?.id]);
  const selectedFocusSummaries = useMemo(() => {
    const keys = new Set(selectedNode?.focusFieldKeys ?? []);
    return focusFields?.filter((field) => keys.has(field.fieldKey)) ?? [];
  }, [focusFields, selectedNode?.focusFieldKeys]);

  useEffect(() => {
    const container = containerRef.current;
    graphRef.current?.dispose();
    graphRef.current = null;
    const hasFieldCard = graph?.granularity === 'FIELD' && graph.nodes.some((node) => node.kind === 'FIELD');
    if (!container || !graph || (!hasFieldCard && graph.nodes.length <= 1)) {
      setSelectedNode(graph?.nodes.find((node) => node.id === graph.rootNodeId));
      return undefined;
    }
    registerLineageFieldCardNode();
    const visual = layoutVisualGraph(graph);
    const rootNode = graph.nodes.find((node) => node.id === graph.rootNodeId) ?? graph.nodes[0];
    const canvas = new Graph({
      container, autoResize: true, background: { color: '#fafbff' },
      grid: { visible: true, size: 10 }, interacting: false, panning: true,
      mousewheel: { enabled: true, minScale: 0.65, maxScale: 1.6 },
    });
    canvas.addNodes(visual.ordinaryNodes.map((node) => {
      const theme = nodeThemes[node.kind];
      const root = node.id === graph.rootNodeId;
      return {
        id: node.id, shape: 'rect', x: node.x, y: node.y, width: node.width,
        height: node.height, data: node,
        attrs: {
          body: {
            fill: theme.fill, stroke: node.stale ? '#fa8c16' : theme.stroke,
            strokeWidth: root ? 2.5 : node.stale ? 2 : 1.2,
            strokeDasharray: node.stale ? '5 3' : undefined, rx: 10, ry: 10,
          },
          label: {
            text: `${node.label}\n${nodeSubtitleForCanvas(node) || lineageNodeKindLabels[node.kind]}`,
            fill: '#1f2937', fontSize: 12, lineHeight: 19,
            textWrap: { width: node.width - 24, height: 50, ellipsis: true },
          },
        },
      };
    }));
    const focusIndex = new Map<string, { nodes: Set<string>; edges: Set<string> }>();
    graph.nodes.forEach((node) => (node.focusFieldKeys ?? []).forEach((key) => {
      const entry = focusIndex.get(key) ?? { nodes: new Set<string>(), edges: new Set<string>() };
      entry.nodes.add(node.id);
      focusIndex.set(key, entry);
    }));
    graph.edges.forEach((edge) => (edge.focusFieldKeys ?? []).forEach((key) => {
      const entry = focusIndex.get(key) ?? { nodes: new Set<string>(), edges: new Set<string>() };
      entry.edges.add(edge.id);
      entry.nodes.add(edge.source);
      entry.nodes.add(edge.target);
      focusIndex.set(key, entry);
    }));
    let lockedKeys: string[] = [];
    const applyFocus = (keys: string[]) => {
      const activeNodes = new Set<string>();
      const activeEdges = new Set<string>();
      keys.forEach((key) => {
        const entry = focusIndex.get(key);
        entry?.nodes.forEach((id) => activeNodes.add(id));
        entry?.edges.forEach((id) => activeEdges.add(id));
      });
      const focused = keys.length > 0;
      canvas.getNodes().forEach((node) => {
        const data = node.getData<LineageGraphNode | LineageFieldCardData>();
        if ('visualKind' in data) return;
        const active = !focused || activeNodes.has(node.id);
        node.attr('body/opacity', active ? 1 : 0.16);
        node.attr('label/opacity', active ? 1 : 0.22);
        node.attr('body/strokeWidth', active && focused ? 2.8
          : data.id === graph.rootNodeId ? 2.5 : data.stale ? 2 : 1.2);
      });
      container.querySelectorAll<HTMLElement>('.lineage-field-card').forEach((cardElement) => {
        const card = visual.fieldCards.find((item) => item.id === cardElement.dataset.lineageCardId);
        const active = !focused || Boolean(card?.fields.some((field) => activeNodes.has(field.id)));
        cardElement.style.opacity = active ? '1' : '0.18';
        cardElement.classList.toggle('is-path-active', focused && active);
      });
      container.querySelectorAll<HTMLElement>('.lineage-field-card-row').forEach((rowElement) => {
        const fieldNodeId = rowElement.dataset.lineageFieldNodeId;
        const active = !focused || Boolean(fieldNodeId && activeNodes.has(fieldNodeId));
        rowElement.style.opacity = active ? '1' : '0.18';
        rowElement.classList.toggle('is-path-active', focused && active);
      });
      canvas.getEdges().forEach((edge) => {
        const active = !focused || activeEdges.has(edge.id);
        const data = edge.getData<LineageGraphEdge>();
        edge.attr('line/opacity', active ? 1 : 0.08);
        edge.attr('line/strokeWidth', active && focused ? 3
          : data.type === 'DERIVES' || data.type === 'EXPOSES' ? 1.8 : 1.4);
        edge.attr('label/opacity', active ? 1 : 0.1);
      });
    };
    const onFieldEnter = (field: LineageGraphNode) => {
      if (lockedKeys.length === 0) applyFocus(field.focusFieldKeys ?? []);
    };
    const onFieldLeave = () => {
      if (lockedKeys.length === 0) applyFocus([]);
    };
    const onFieldClick = (field: LineageGraphNode) => {
      setSelectedNode(field);
      lockedKeys = [...(field.focusFieldKeys ?? [])];
      applyFocus(lockedKeys);
    };
    canvas.addNodes(visual.fieldCards.map((card) => ({
      id: card.id,
      shape: LINEAGE_FIELD_CARD_SHAPE,
      x: card.x,
      y: card.y,
      width: card.width,
      height: card.height,
      data: {
        visualKind: 'FIELD_CARD',
        cardId: card.id,
        ownerKind: card.owner.kind,
        ownerLabel: card.owner.label,
        ownerSubtitle: card.owner.subtitle,
        stale: card.stale,
        fields: card.fields,
        onFieldEnter,
        onFieldLeave,
        onFieldClick,
      } satisfies LineageFieldCardData,
      ports: {
        groups: {
          input: {
            position: 'absolute',
            attrs: { circle: { r: 4, magnet: true, stroke: '#64748b', fill: '#ffffff' } },
          },
          output: {
            position: 'absolute',
            attrs: { circle: { r: 4, magnet: true, stroke: '#64748b', fill: '#ffffff' } },
          },
        },
        items: card.fields.flatMap((_, index) => {
          const y = FIELD_CARD_HEADER_HEIGHT + index * FIELD_CARD_ROW_HEIGHT + FIELD_CARD_ROW_HEIGHT / 2;
          return [
            { id: 'input-' + index, group: 'input', args: { x: 0, y } },
            { id: 'output-' + index, group: 'output', args: { x: '100%', y } },
          ];
        }),
      },
    })));
    canvas.addEdges(visual.edges.map(({ edge, source, target }) => ({
      id: edge.id, source, target, data: edge,
      router: { name: 'manhattan', args: { padding: 16 } }, connector: { name: 'rounded' },
      labels: edgeLabel(edge) ? [{ attrs: {
        label: { text: edgeLabel(edge), fill: '#64748b', fontSize: 10 },
        body: { fill: '#fff', stroke: '#e2e8f0', rx: 4, ry: 4 },
      } }] : [],
      attrs: { line: {
        stroke: edge.type === 'EXPOSES' ? '#722ed1' : edge.type === 'FIELD_EFFECT' ? '#a855f7' : '#94a3b8',
        strokeWidth: edge.type === 'DERIVES' || edge.type === 'EXPOSES' ? 1.8 : 1.4,
        strokeDasharray: edge.type === 'FIELD_EFFECT' ? '5 3' : undefined,
        targetMarker: { name: 'block', width: 8, height: 6 },
      } },
    })));
    canvas.on('node:mouseenter', ({ node }) => {
      const data = node.getData<LineageGraphNode | LineageFieldCardData>();
      if (!('visualKind' in data) && data.kind === 'FIELD' && lockedKeys.length === 0) {
        applyFocus(data.focusFieldKeys ?? []);
      }
    });
    canvas.on('node:mouseleave', () => {
      if (lockedKeys.length === 0) applyFocus([]);
    });
    canvas.on('node:click', ({ node }) => {
      const data = node.getData<LineageGraphNode | LineageFieldCardData>();
      if (!('visualKind' in data)) setSelectedNode(data);
      if (!('visualKind' in data) && data.kind === 'FIELD') {
        lockedKeys = [...(data.focusFieldKeys ?? [])];
        applyFocus(lockedKeys);
      }
    });
    canvas.on('blank:click', () => {
      lockedKeys = [];
      applyFocus([]);
      setSelectedNode(rootNode);
    });
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      lockedKeys = [];
      applyFocus([]);
    };
    window.addEventListener('keydown', onKeyDown);
    canvas.zoomToFit({ padding: 36, maxScale: 1 });
    if (canvas.zoom() < 0.65) canvas.zoomTo(0.65);
    graphRef.current = canvas;
    setSelectedNode(rootNode);
    return () => { window.removeEventListener('keydown', onKeyDown); graphRef.current = null; canvas.dispose(); };
  }, [graph]);

  const showCanvas = Boolean(graph && (graph.nodes.length > 1
    || (graph.granularity === 'FIELD' && graph.nodes.some((node) => node.kind === 'FIELD'))));
  return (
    <div className="model-lineage-workspace">
      <div className="model-lineage-canvas-shell">
        <div className="model-lineage-canvas-actions">
          <Tooltip title="重新加载"><Button icon={<ReloadOutlined />} aria-label="重新加载血缘" onClick={onRetry} /></Tooltip>
          <Button icon={<ZoomInOutlined />} aria-label="放大血缘图" disabled={!showCanvas} onClick={() => graphRef.current?.zoom(0.1)} />
          <Button icon={<ZoomOutOutlined />} aria-label="缩小血缘图" disabled={!showCanvas} onClick={() => graphRef.current?.zoom(-0.1)} />
          <Button icon={<AimOutlined />} disabled={!showCanvas} onClick={() => graphRef.current?.zoomToFit({ padding: 36, maxScale: 1 })}>适应画布</Button>
        </div>
        {loading && <div className="model-lineage-state"><Spin tip="正在加载血缘" /></div>}
        {!loading && errorMessage && <div className="model-lineage-state"><Empty description={errorMessage}><Button type="primary" onClick={onRetry}>重试</Button></Empty></div>}
        {!loading && !errorMessage && !showCanvas && <div className="model-lineage-state"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyDescription} /></div>}
        <div ref={containerRef} className="model-lineage-canvas" aria-label={ariaLabel} />
        <aside className="model-lineage-inspector">
          <div className="model-lineage-inspector-title">节点信息</div>
          {selectedNode ? (
            <Descriptions size="small" column={1}>
              <Descriptions.Item label="名称">{selectedNode.label}</Descriptions.Item>
              <Descriptions.Item label="类型">{lineageNodeKindLabels[selectedNode.kind]}</Descriptions.Item>
              <Descriptions.Item label="说明">{selectedNode.subtitle || '—'}</Descriptions.Item>
              {selectedNode.fieldOwner && <Descriptions.Item label="所属资产">
                {selectedNode.fieldOwner.label}{selectedNode.fieldOwner.subtitle ? ` · ${selectedNode.fieldOwner.subtitle}` : ''}
              </Descriptions.Item>}
              <Descriptions.Item label="方向">{sideLabels[selectedNode.side]}</Descriptions.Item>
              <Descriptions.Item label="任务跳数">{selectedNode.depth}</Descriptions.Item>
              {selectedNode.focusFieldKeys?.length > 0 && <Descriptions.Item label="关联路径">{selectedNode.focusFieldKeys.length} 个所选字段</Descriptions.Item>}
              {selectedFocusSummaries.length > 0 && <Descriptions.Item label="路径覆盖">
                {selectedFocusSummaries.map((field) => `${field.name}：${field.truncated ? '已截断' : field.hasLineage ? field.coverage === 'FIELD_COMPLETE' ? '完整' : '部分' : '无路径'}`).join('；')}
              </Descriptions.Item>}
              {selectedNode.dataServiceType && <Descriptions.Item label="服务类型">{serviceTypeLabels[selectedNode.dataServiceType]}</Descriptions.Item>}
              {selectedNode.dataServiceStatus && <Descriptions.Item label="服务状态"><Tag color={selectedNode.dataServiceStatus === 'ENABLED' ? 'success' : selectedNode.dataServiceStatus === 'DISABLED' ? 'warning' : 'default'}>{serviceStatusLabels[selectedNode.dataServiceStatus]}</Tag></Descriptions.Item>}
              {selectedNode.routePath && <Descriptions.Item label="服务路由"><code>{selectedNode.routePath}</code></Descriptions.Item>}
              {selectedNode.taskStatus && <Descriptions.Item label="任务状态"><Tag>{selectedNode.taskStatus}</Tag></Descriptions.Item>}
              {selectedNode.definitionVersion != null && <Descriptions.Item label="定义版本">v{selectedNode.definitionVersion}</Descriptions.Item>}
              {selectedNode.writeMode && <Descriptions.Item label="写入模式">{writeLabels[selectedNode.writeMode]}</Descriptions.Item>}
              <Descriptions.Item label="结构状态">{selectedNode.stale ? <Tag color="warning">陈旧</Tag> : <Tag color="success">当前</Tag>}</Descriptions.Item>
              {selectedEdges.some((edge) => edge.usages.length > 0) && <Descriptions.Item label="字段用途"><Space size={[4, 4]} wrap>{Array.from(new Set(selectedEdges.flatMap((edge) => edge.usages))).map((usage) => <Tag key={usage}>{usageLabels[usage]}</Tag>)}</Space></Descriptions.Item>}
              {selectedEdges.some((edge) => edge.derivationType) && <Descriptions.Item label="派生方式">{Array.from(new Set(selectedEdges.flatMap((edge) => edge.derivationType ? [edge.derivationType] : []))).map((type) => derivationLabels[type]).join('、')}</Descriptions.Item>}
              {selectedEdges.some((edge) => edge.outputEffect && edge.outputEffect !== 'DERIVED') && <Descriptions.Item label="输出行为">{Array.from(new Set(selectedEdges.flatMap((edge) => edge.outputEffect ? [edge.outputEffect] : []))).map((effect) => effectLabels[effect]).join('、')}</Descriptions.Item>}
            </Descriptions>
          ) : <div className="model-lineage-empty"><ApartmentOutlined /> 点击图中节点查看详情</div>}
          <div className="model-lineage-legend">{legend.map((item) => <span key={item}>{item}</span>)}</div>
        </aside>
      </div>
    </div>
  );
};
