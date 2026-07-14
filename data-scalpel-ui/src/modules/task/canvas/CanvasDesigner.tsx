import {
  AimOutlined,
  DeleteOutlined,
  RedoOutlined,
  SaveOutlined,
  UndoOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
} from '@ant-design/icons';
import { Dnd, Graph, History, Keyboard, Selection, Snapline, Transform } from '@antv/x6';
import type { Node } from '@antv/x6';
import { Button, Card, Divider, Form, Input, Space, Typography } from 'antd';
import { useEffect, useRef, useState, type MouseEvent } from 'react';
import { loadCanvasDefinition, toCanvasDefinition } from './canvasSerialization';
import { canvasNodePorts } from './canvasPorts';
import { canvasNodeTemplates, registerCanvasNodes, type CanvasNodeTemplate } from './canvasRegistry';
import { canConnectCategories, createsCycle, type CanvasDefinition, type CanvasNodeData } from './canvasTypes';
import { defaultCanvasDefinition } from './defaultCanvas';
import './canvasDesigner.css';

interface CanvasDesignerProps {
  initialDefinition?: CanvasDefinition;
  onSave: (definition: CanvasDefinition) => void;
}

interface NodeInspectorValues {
  label: string;
}

const createNode = (graph: Graph, template: CanvasNodeTemplate) => graph.createNode({
  shape: template.shape,
  width: template.width,
  height: template.height,
  ports: canvasNodePorts,
  data: {
    label: template.label,
    category: template.category,
    configuration: {},
  } satisfies CanvasNodeData,
});

export const CanvasDesigner = ({ initialDefinition = defaultCanvasDefinition, onSave }: CanvasDesignerProps) => {
  const graphContainerRef = useRef<HTMLDivElement>(null);
  const paletteRef = useRef<HTMLDivElement>(null);
  const graphRef = useRef<Graph | null>(null);
  const dndRef = useRef<Dnd | null>(null);
  const initialDefinitionRef = useRef(initialDefinition);
  const [selectedNode, setSelectedNode] = useState<Node>();
  const [form] = Form.useForm<NodeInspectorValues>();

  useEffect(() => {
    const container = graphContainerRef.current;
    if (!container) return undefined;

    registerCanvasNodes();
    const graph: Graph = new Graph({
      container,
      autoResize: true,
      grid: {
        visible: true,
        type: 'doubleMesh',
        args: [{ color: '#f0f0f0', thickness: 1 }, { color: '#fafafa', thickness: 1, factor: 4 }],
      },
      panning: true,
      mousewheel: {
        enabled: true,
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
        validateConnection({ sourceCell, targetCell }) {
          if (!sourceCell?.isNode() || !targetCell?.isNode()) return false;
          const source = sourceCell as Node;
          const target = targetCell as Node;
          const sourceData = source.getData<CanvasNodeData>();
          const targetData = target.getData<CanvasNodeData>();

          if (!canConnectCategories(sourceData.category, targetData.category)) return false;
          const currentDefinition = toCanvasDefinition(graph);
          if (currentDefinition.edges.some((edge) => edge.source.nodeId === source.id && edge.target.nodeId === target.id)) {
            return false;
          }
          return !createsCycle(currentDefinition.edges, source.id, target.id);
        },
      },
    });

    graph.use(new History({ enabled: true }));
    graph.use(new Keyboard({ enabled: true }));
    graph.use(new Selection({ enabled: true, multiple: true, rubberband: true }));
    graph.use(new Snapline());
    graph.use(new Transform({ resizing: { enabled: true, minWidth: 180, minHeight: 96 } }));
    graph.bindKey(['ctrl+z', 'meta+z'], () => graph.undo());
    graph.bindKey(['ctrl+shift+z', 'meta+shift+z'], () => graph.redo());
    graph.bindKey(['backspace', 'delete'], () => {
      graph.getSelectedCells().forEach((cell) => cell.remove());
    });
    graph.on('edge:added', ({ edge }) => {
      edge.attr('line', {
        stroke: '#1677ff',
        strokeWidth: 2,
        targetMarker: 'block',
      });
    });

    loadCanvasDefinition(graph, initialDefinitionRef.current);
    graphRef.current = graph;
    dndRef.current = new Dnd({
      target: graph,
      dndContainer: paletteRef.current ?? undefined,
      scaled: false,
    });

    graph.on('node:click', ({ node }) => {
      setSelectedNode(node);
      const data = node.getData<CanvasNodeData>();
      form.setFieldsValue({ label: data.label });
    });
    graph.on('blank:click', () => setSelectedNode(undefined));

    return () => {
      dndRef.current = null;
      graphRef.current = null;
      graph.dispose();
    };
  }, [form]);

  const startNodeDrag = (event: MouseEvent<HTMLElement>, template: CanvasNodeTemplate) => {
    const graph = graphRef.current;
    const dnd = dndRef.current;
    if (!graph || !dnd) return;

    event.preventDefault();
    dnd.start(createNode(graph, template), event.nativeEvent);
  };

  const saveDefinition = () => {
    const graph = graphRef.current;
    if (!graph) return;
    onSave(toCanvasDefinition(graph));
  };

  const updateSelectedNode = ({ label }: NodeInspectorValues) => {
    if (!selectedNode) return;
    selectedNode.setData({ label }, { merge: true });
  };

  return (
    <div className="canvas-designer">
      <div className="canvas-toolbar">
        <Space wrap>
          <Button icon={<UndoOutlined />} onClick={() => graphRef.current?.undo()}>撤销</Button>
          <Button icon={<RedoOutlined />} onClick={() => graphRef.current?.redo()}>重做</Button>
          <Button icon={<ZoomInOutlined />} onClick={() => graphRef.current?.zoom(0.1)}>放大</Button>
          <Button icon={<ZoomOutOutlined />} onClick={() => graphRef.current?.zoom(-0.1)}>缩小</Button>
          <Button icon={<AimOutlined />} onClick={() => graphRef.current?.centerContent()}>居中</Button>
          <Button danger icon={<DeleteOutlined />} disabled={!selectedNode} onClick={() => selectedNode?.remove()}>删除节点</Button>
        </Space>
        <Button type="primary" icon={<SaveOutlined />} onClick={saveDefinition}>保存编排</Button>
      </div>

      <div className="canvas-workspace">
        <aside className="canvas-palette" ref={paletteRef} aria-label="可用节点">
          <Typography.Text strong>可用节点</Typography.Text>
          <Divider />
          <Space orientation="vertical" size={8} className="canvas-palette-list">
            {canvasNodeTemplates.map((template) => (
              <Button
                key={template.shape}
                block
                className="canvas-palette-item"
                onMouseDown={(event) => startNodeDrag(event, template)}
              >
                {template.label}
              </Button>
            ))}
          </Space>
        </aside>

        <div className="canvas-stage" ref={graphContainerRef} aria-label="任务编排画布" />

        <aside className="canvas-inspector" aria-label="节点配置">
          <Typography.Text strong>节点配置</Typography.Text>
          <Divider />
          {selectedNode ? (
            <Form form={form} layout="vertical" onFinish={updateSelectedNode}>
              <Form.Item label="节点名称" name="label" rules={[{ required: true, message: '请输入节点名称' }]}>
                <Input placeholder="节点名称" />
              </Form.Item>
              <Button htmlType="submit" type="primary">应用修改</Button>
            </Form>
          ) : (
            <Card size="small" className="canvas-inspector-empty">
              选中一个节点后，可以在此处编辑其配置。具体节点的业务配置面板将在对应任务能力落地时接入。
            </Card>
          )}
        </aside>
      </div>
    </div>
  );
};
