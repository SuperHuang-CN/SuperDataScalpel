import { AimOutlined, ZoomInOutlined, ZoomOutOutlined } from '@ant-design/icons';
import { Graph } from '@antv/x6';
import { Button, Descriptions, Select, Space, Tag } from 'antd';
import { useEffect, useRef, useState } from 'react';
import type { DataModel } from '../model/dataModel';
import {
  buildMockLineage,
  type MockLineageDirection,
  type MockLineageNode,
  type MockLineageNodeKind,
} from '../model/modelDetailMock';

interface DataModelLineagePanelProps {
  model: DataModel;
}

const nodeKindLabels: Record<MockLineageNodeKind, string> = {
  MODEL: '数据模型',
  TASK: '数据任务',
  SERVICE: '数据服务',
};

const nodeTheme: Record<MockLineageNodeKind, { fill: string; stroke: string }> = {
  MODEL: { fill: '#e6f4ff', stroke: '#1677ff' },
  TASK: { fill: '#f6ffed', stroke: '#52c41a' },
  SERVICE: { fill: '#f9f0ff', stroke: '#722ed1' },
};

export const DataModelLineagePanel = ({ model }: DataModelLineagePanelProps) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);
  const [direction, setDirection] = useState<MockLineageDirection>('BOTH');
  const [depth, setDepth] = useState<1 | 2>(2);
  const [selectedNode, setSelectedNode] = useState<MockLineageNode>();

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return undefined;

    const definition = buildMockLineage(direction, depth);
    const nodes = definition.nodes.map((node) => node.id === 'current-model'
      ? { ...node, label: model.name, subtitle: model.code }
      : node);
    const currentNode = nodes.find((node) => node.id === 'current-model');
    const graph = new Graph({
      container,
      autoResize: true,
      background: { color: '#fbfcfe' },
      grid: { visible: true, size: 10 },
      interacting: false,
      panning: true,
      mousewheel: { enabled: true, minScale: 0.6, maxScale: 1.5 },
    });

    graph.addNodes(nodes.map((node) => {
      const theme = node.id === 'current-model'
        ? { fill: '#fff1f0', stroke: '#ff4d4f' }
        : nodeTheme[node.kind];
      return {
        id: node.id,
        shape: 'rect',
        x: node.x,
        y: node.y,
        width: 190,
        height: 66,
        data: node,
        attrs: {
          body: {
            fill: theme.fill,
            stroke: theme.stroke,
            strokeWidth: node.id === 'current-model' ? 2 : 1,
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
      };
    }));
    graph.addEdges(definition.edges.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      router: { name: 'manhattan' },
      connector: { name: 'rounded' },
      attrs: {
        line: {
          stroke: '#8c8c8c',
          strokeWidth: 1.5,
          targetMarker: { name: 'block', width: 8, height: 6 },
        },
      },
    })));
    graph.on('node:click', ({ node }) => setSelectedNode(node.getData<MockLineageNode>()));
    graph.on('blank:click', () => setSelectedNode(currentNode));
    graph.zoomToFit({ padding: 30, maxScale: 1 });
    graphRef.current = graph;
    setSelectedNode(currentNode);

    return () => {
      graphRef.current = null;
      graph.dispose();
    };
  }, [depth, direction, model.code, model.name]);

  return (
    <div className="model-detail-tab-panel">
      <div className="model-lineage-toolbar">
        <Space size={8}>
          <Tag color="gold">前端 Mock</Tag>
          <span>方向</span>
          <Select<MockLineageDirection>
            value={direction}
            className="model-lineage-direction-select"
            onChange={setDirection}
            options={[
              { value: 'UPSTREAM', label: '仅上游' },
              { value: 'DOWNSTREAM', label: '仅下游' },
              { value: 'BOTH', label: '上下游' },
            ]}
          />
          <span>层级</span>
          <Select<1 | 2>
            value={depth}
            className="model-lineage-depth-select"
            onChange={setDepth}
            options={[{ value: 1, label: '1 层' }, { value: 2, label: '2 层' }]}
          />
        </Space>
        <Space size={4}>
          <Button icon={<ZoomInOutlined />} aria-label="放大血缘图" onClick={() => graphRef.current?.zoom(0.1)} />
          <Button icon={<ZoomOutOutlined />} aria-label="缩小血缘图" onClick={() => graphRef.current?.zoom(-0.1)} />
          <Button icon={<AimOutlined />} onClick={() => graphRef.current?.zoomToFit({ padding: 30, maxScale: 1 })}>适应画布</Button>
        </Space>
      </div>
      <div className="model-lineage-workspace">
        <div className="model-lineage-canvas-shell">
          <div ref={containerRef} className="model-lineage-canvas" aria-label="模型血缘关系图" />
        </div>
        <aside className="model-lineage-inspector">
          <div className="model-lineage-inspector-title">节点信息</div>
          {selectedNode ? (
            <Descriptions size="small" column={1}>
              <Descriptions.Item label="名称">{selectedNode.label}</Descriptions.Item>
              <Descriptions.Item label="类型">{nodeKindLabels[selectedNode.kind]}</Descriptions.Item>
              <Descriptions.Item label="说明">{selectedNode.subtitle}</Descriptions.Item>
              <Descriptions.Item label="方向">
                {selectedNode.side === 'CURRENT' ? '当前模型' : selectedNode.side === 'UPSTREAM' ? '上游' : '下游'}
              </Descriptions.Item>
              <Descriptions.Item label="层级">{selectedNode.depth}</Descriptions.Item>
            </Descriptions>
          ) : <span className="model-lineage-empty">点击图中节点查看信息</span>}
        </aside>
      </div>
    </div>
  );
};
