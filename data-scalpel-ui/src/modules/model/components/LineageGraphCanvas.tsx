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
  LineageOutputFieldEffect,
  LineageWriteMode,
} from '../model/dataModel';

interface PositionedLineageNode extends LineageGraphNode { x: number; y: number }

interface LineageGraphCanvasProps {
  graph: LineageGraph | undefined;
  loading: boolean;
  errorMessage?: string;
  emptyDescription: string;
  ariaLabel: string;
  onRetry: () => void;
  sideLabels?: Record<'UPSTREAM' | 'CURRENT' | 'DOWNSTREAM', string>;
  legend?: string[];
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

const layoutNodes = (graph: LineageGraph): PositionedLineageNode[] => {
  const columns = { UPSTREAM: 70, CURRENT: 490, DOWNSTREAM: 910 } as const;
  return (['UPSTREAM', 'CURRENT', 'DOWNSTREAM'] as const).flatMap((side) => {
    const nodes = graph.nodes.filter((node) => node.side === side)
      .sort((left, right) => left.depth - right.depth
        || left.kind.localeCompare(right.kind)
        || left.label.localeCompare(right.label)
        || left.id.localeCompare(right.id));
    const totalHeight = nodes.length * 72 + Math.max(0, nodes.length - 1) * 18;
    const startY = Math.max(36, (480 - totalHeight) / 2);
    return nodes.map((node, index) => ({ ...node, x: columns[side], y: startY + index * 90 }));
  });
};

const edgeLabel = (edge: LineageGraphEdge) => {
  if (edge.type === 'EXPOSES') return '对外暴露';
  if (edge.usages.length > 0) return edge.usages.map((usage) => usageLabels[usage]).join('、');
  if (edge.outputEffect && edge.outputEffect !== 'DERIVED') return effectLabels[edge.outputEffect];
  if (edge.derivationType && edge.derivationType !== 'DIRECT') return derivationLabels[edge.derivationType];
  return undefined;
};

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
}: LineageGraphCanvasProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);
  const [selectedNode, setSelectedNode] = useState<LineageGraphNode>();
  const selectedEdges = useMemo(() => graph?.edges.filter(
    (edge) => edge.source === selectedNode?.id || edge.target === selectedNode?.id,
  ) ?? [], [graph?.edges, selectedNode?.id]);

  useEffect(() => {
    const container = containerRef.current;
    graphRef.current?.dispose();
    graphRef.current = null;
    if (!container || !graph || graph.nodes.length <= 1) {
      setSelectedNode(graph?.nodes.find((node) => node.id === graph.rootNodeId));
      return undefined;
    }
    const nodes = layoutNodes(graph);
    const rootNode = nodes.find((node) => node.id === graph.rootNodeId) ?? nodes[0];
    const canvas = new Graph({
      container, autoResize: true, background: { color: '#fafbff' },
      grid: { visible: true, size: 10 }, interacting: false, panning: true,
      mousewheel: { enabled: true, minScale: 0.5, maxScale: 1.6 },
    });
    canvas.addNodes(nodes.map((node) => {
      const theme = nodeThemes[node.kind];
      const root = node.id === graph.rootNodeId;
      return {
        id: node.id, shape: 'rect', x: node.x, y: node.y, width: 220, height: 72, data: node,
        attrs: {
          body: {
            fill: theme.fill, stroke: node.stale ? '#fa8c16' : theme.stroke,
            strokeWidth: root ? 2.5 : node.stale ? 2 : 1.2,
            strokeDasharray: node.stale ? '5 3' : undefined, rx: 10, ry: 10,
          },
          label: {
            text: `${node.label}\n${node.subtitle || lineageNodeKindLabels[node.kind]}`,
            fill: '#1f2937', fontSize: 12, lineHeight: 19,
            textWrap: { width: 196, height: 50, ellipsis: true },
          },
        },
      };
    }));
    canvas.addEdges(graph.edges.map((edge) => ({
      id: edge.id, source: edge.source, target: edge.target,
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
    canvas.on('node:click', ({ node }) => setSelectedNode(node.getData<LineageGraphNode>()));
    canvas.on('blank:click', () => setSelectedNode(rootNode));
    canvas.zoomToFit({ padding: 36, maxScale: 1 });
    graphRef.current = canvas;
    setSelectedNode(rootNode);
    return () => { graphRef.current = null; canvas.dispose(); };
  }, [graph]);

  const showCanvas = Boolean(graph && graph.nodes.length > 1);
  return (
    <>
      <div className="model-lineage-canvas-actions">
        <Tooltip title="重新加载"><Button icon={<ReloadOutlined />} aria-label="重新加载血缘" onClick={onRetry} /></Tooltip>
        <Button icon={<ZoomInOutlined />} aria-label="放大血缘图" disabled={!showCanvas} onClick={() => graphRef.current?.zoom(0.1)} />
        <Button icon={<ZoomOutOutlined />} aria-label="缩小血缘图" disabled={!showCanvas} onClick={() => graphRef.current?.zoom(-0.1)} />
        <Button icon={<AimOutlined />} disabled={!showCanvas} onClick={() => graphRef.current?.zoomToFit({ padding: 36, maxScale: 1 })}>适应画布</Button>
      </div>
      <div className="model-lineage-workspace">
        <div className="model-lineage-canvas-shell">
          {loading && <div className="model-lineage-state"><Spin tip="正在加载血缘" /></div>}
          {!loading && errorMessage && <div className="model-lineage-state"><Empty description={errorMessage}><Button type="primary" onClick={onRetry}>重试</Button></Empty></div>}
          {!loading && !errorMessage && (!graph || graph.nodes.length <= 1) && <div className="model-lineage-state"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyDescription} /></div>}
          <div ref={containerRef} className="model-lineage-canvas" aria-label={ariaLabel} />
        </div>
        <aside className="model-lineage-inspector">
          <div className="model-lineage-inspector-title">节点信息</div>
          {selectedNode ? (
            <Descriptions size="small" column={1}>
              <Descriptions.Item label="名称">{selectedNode.label}</Descriptions.Item>
              <Descriptions.Item label="类型">{lineageNodeKindLabels[selectedNode.kind]}</Descriptions.Item>
              <Descriptions.Item label="说明">{selectedNode.subtitle || '—'}</Descriptions.Item>
              <Descriptions.Item label="方向">{sideLabels[selectedNode.side]}</Descriptions.Item>
              <Descriptions.Item label="任务跳数">{selectedNode.depth}</Descriptions.Item>
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
    </>
  );
};
