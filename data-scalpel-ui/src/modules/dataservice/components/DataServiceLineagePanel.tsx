import { AimOutlined, ZoomInOutlined, ZoomOutOutlined } from '@ant-design/icons';
import { Graph } from '@antv/x6';
import { Button, Descriptions, Space, Tag } from 'antd';
import { useEffect, useRef, useState } from 'react';
import type { DataServiceRelatedModelView } from '../hooks/useDataServiceRelatedModels';
import { gatewayProviderLabels } from '../model/apiConsumer';
import type { DataServiceDetail } from '../model/dataService';

interface DataServiceLineagePanelProps {
  dataService: DataServiceDetail;
  sourceName?: string;
  relatedModels: DataServiceRelatedModelView[];
}

type LineageNodeKind = 'DATASOURCE' | 'MODEL' | 'SERVICE' | 'GATEWAY' | 'CONSUMER';

interface LineageNode {
  id: string;
  label: string;
  subtitle: string;
  kind: LineageNodeKind;
  direction: 'UPSTREAM' | 'CURRENT' | 'DOWNSTREAM';
  mock: boolean;
  x: number;
  y: number;
}

interface LineageEdge {
  id: string;
  source: string;
  target: string;
  mock: boolean;
}

const nodeKindLabels: Record<LineageNodeKind, string> = {
  DATASOURCE: '数据源',
  MODEL: '数据模型',
  SERVICE: '数据服务',
  GATEWAY: 'API 网关',
  CONSUMER: '调用方',
};

const nodeThemes: Record<LineageNodeKind, { fill: string; stroke: string }> = {
  DATASOURCE: { fill: '#fff7e6', stroke: '#fa8c16' },
  MODEL: { fill: '#e6f4ff', stroke: '#1677ff' },
  SERVICE: { fill: '#f9f0ff', stroke: '#722ed1' },
  GATEWAY: { fill: '#f6ffed', stroke: '#52c41a' },
  CONSUMER: { fill: '#fff1f0', stroke: '#ff4d4f' },
};

const buildLineage = (
  dataService: DataServiceDetail,
  sourceName: string | undefined,
  relatedModels: DataServiceRelatedModelView[],
): { nodes: LineageNode[]; edges: LineageEdge[] } => {
  const displayedModels = relatedModels.slice(0, 5);
  const modelNodeCount = Math.max(displayedModels.length, 1);
  const rowGap = 86;
  const top = 32;
  const centerY = top + ((modelNodeCount - 1) * rowGap) / 2;
  const sourceId = dataService.sqlDefinition?.dataSourceId ?? dataService.scriptDefinition?.dataSourceId;
  const sourceLabel = sourceName
    ?? displayedModels[0]?.model?.storageDataSourceName
    ?? (sourceId ? `数据源 ${sourceId.slice(0, 8)}` : '上游数据源');
  const gatewayBinding = dataService.gatewayBindings[0];

  const nodes: LineageNode[] = [
    {
      id: 'source',
      label: sourceLabel,
      subtitle: sourceId ? '服务配置数据源' : '模型存储数据源',
      kind: 'DATASOURCE',
      direction: 'UPSTREAM',
      mock: false,
      x: 24,
      y: centerY,
    },
    {
      id: 'current-service',
      label: dataService.name,
      subtitle: dataService.code,
      kind: 'SERVICE',
      direction: 'CURRENT',
      mock: false,
      x: 560,
      y: centerY,
    },
    {
      id: 'gateway',
      label: gatewayBinding ? gatewayProviderLabels[gatewayBinding.provider] : '内网 API 网关',
      subtitle: gatewayBinding ? '当前网关绑定' : '下游发布节点（示例）',
      kind: 'GATEWAY',
      direction: 'DOWNSTREAM',
      mock: !gatewayBinding,
      x: 820,
      y: centerY,
    },
    {
      id: 'consumer',
      label: '政务业务应用',
      subtitle: '订阅消费者（示例）',
      kind: 'CONSUMER',
      direction: 'DOWNSTREAM',
      mock: true,
      x: 1080,
      y: centerY,
    },
  ];
  const edges: LineageEdge[] = [
    { id: 'service-gateway', source: 'current-service', target: 'gateway', mock: !gatewayBinding },
    { id: 'gateway-consumer', source: 'gateway', target: 'consumer', mock: true },
  ];

  if (displayedModels.length > 0) {
    displayedModels.forEach((row, index) => {
      const nodeId = `model-${index}`;
      nodes.push({
        id: nodeId,
        label: row.model?.name ?? `模型引用 ${row.order}`,
        subtitle: row.model?.code ?? row.modelId,
        kind: 'MODEL',
        direction: 'UPSTREAM',
        mock: false,
        x: 286,
        y: top + index * rowGap,
      });
      edges.push(
        { id: `source-${nodeId}`, source: 'source', target: nodeId, mock: false },
        { id: `${nodeId}-service`, source: nodeId, target: 'current-service', mock: false },
      );
    });
    if (relatedModels.length > displayedModels.length) {
      nodes.push({
        id: 'more-models',
        label: `其余 ${relatedModels.length - displayedModels.length} 个模型`,
        subtitle: '关联模型汇总',
        kind: 'MODEL',
        direction: 'UPSTREAM',
        mock: false,
        x: 286,
        y: top + displayedModels.length * rowGap,
      });
      edges.push({ id: 'more-models-service', source: 'more-models', target: 'current-service', mock: false });
    }
  } else {
    edges.push({ id: 'source-service', source: 'source', target: 'current-service', mock: dataService.type !== 'SCRIPT_API' });
  }

  return { nodes, edges };
};

export const DataServiceLineagePanel = ({
  dataService,
  sourceName,
  relatedModels,
}: DataServiceLineagePanelProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);
  const [selectedNode, setSelectedNode] = useState<LineageNode>();

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return undefined;
    const definition = buildLineage(dataService, sourceName, relatedModels);
    const currentNode = definition.nodes.find((node) => node.id === 'current-service');
    const graph = new Graph({
      container,
      autoResize: true,
      background: { color: '#fbfcfe' },
      grid: { visible: true, size: 10 },
      interacting: false,
      panning: true,
      mousewheel: { enabled: true, minScale: 0.5, maxScale: 1.5 },
    });

    graph.addNodes(definition.nodes.map((node) => ({
      id: node.id,
      shape: 'rect',
      x: node.x,
      y: node.y,
      width: 200,
      height: 64,
      data: node,
      attrs: {
        body: {
          fill: nodeThemes[node.kind].fill,
          stroke: nodeThemes[node.kind].stroke,
          strokeWidth: node.id === 'current-service' ? 2 : 1,
          strokeDasharray: node.mock ? '5 3' : undefined,
          rx: 5,
          ry: 5,
        },
        label: {
          text: `${node.label}\n${node.subtitle}`,
          fill: '#262626',
          fontSize: 12,
          lineHeight: 18,
        },
      },
    })));
    graph.addEdges(definition.edges.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      router: { name: 'manhattan' },
      connector: { name: 'rounded' },
      attrs: {
        line: {
          stroke: edge.mock ? '#bfbfbf' : '#8c8c8c',
          strokeWidth: 1.5,
          strokeDasharray: edge.mock ? '5 3' : undefined,
          targetMarker: { name: 'block', width: 8, height: 6 },
        },
      },
    })));
    graph.on('node:click', ({ node }) => setSelectedNode(node.getData<LineageNode>()));
    graph.on('blank:click', () => setSelectedNode(currentNode));
    graph.zoomToFit({ padding: 28, maxScale: 1 });
    graphRef.current = graph;
    setSelectedNode(currentNode);

    return () => {
      graphRef.current = null;
      graph.dispose();
    };
  }, [dataService, relatedModels, sourceName]);

  return (
    <div className="data-service-detail-tab-panel">
      <div className="data-service-lineage-toolbar">
        <Space size={8} wrap>
          <Tag color="gold">前端 Mock</Tag>
          <span>实线表示当前配置引用，虚线表示示例或推演关系</span>
        </Space>
        <Space size={4}>
          <Button icon={<ZoomInOutlined />} aria-label="放大血缘图" onClick={() => graphRef.current?.zoom(0.1)} />
          <Button icon={<ZoomOutOutlined />} aria-label="缩小血缘图" onClick={() => graphRef.current?.zoom(-0.1)} />
          <Button icon={<AimOutlined />} onClick={() => graphRef.current?.zoomToFit({ padding: 28, maxScale: 1 })}>适应画布</Button>
        </Space>
      </div>
      <div className="data-service-lineage-workspace">
        <div className="data-service-lineage-canvas-shell">
          <div ref={containerRef} className="data-service-lineage-canvas" aria-label="数据服务血缘关系图" />
        </div>
        <aside className="data-service-lineage-inspector">
          <div className="data-service-lineage-inspector-title">节点信息</div>
          {selectedNode ? (
            <Descriptions size="small" column={1}>
              <Descriptions.Item label="名称">{selectedNode.label}</Descriptions.Item>
              <Descriptions.Item label="类型">{nodeKindLabels[selectedNode.kind]}</Descriptions.Item>
              <Descriptions.Item label="说明">{selectedNode.subtitle}</Descriptions.Item>
              <Descriptions.Item label="方向">
                {selectedNode.direction === 'CURRENT' ? '当前服务' : selectedNode.direction === 'UPSTREAM' ? '上游' : '下游'}
              </Descriptions.Item>
              <Descriptions.Item label="数据性质">
                <Tag color={selectedNode.mock ? 'gold' : 'blue'}>{selectedNode.mock ? '示例数据' : '当前配置'}</Tag>
              </Descriptions.Item>
            </Descriptions>
          ) : <span className="data-service-lineage-empty">点击图中节点查看信息</span>}
        </aside>
      </div>
    </div>
  );
};
