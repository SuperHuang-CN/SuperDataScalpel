/* eslint-disable react-refresh/only-export-components -- X6 consumes this module as a runtime node registry. */
import { DatabaseOutlined, FilterOutlined, SaveOutlined, SwapOutlined } from '@ant-design/icons';
import { Tag } from 'antd';
import { register } from '@antv/x6-react-shape';
import type { Node } from '@antv/x6';
import { useEffect, useState, type ReactNode } from 'react';
import { CanvasNodeCategory, type CanvasNodeData } from './canvasTypes';

export interface CanvasNodeTemplate {
  shape: string;
  label: string;
  category: CanvasNodeCategory;
  width: number;
  height: number;
}

export const canvasNodeTemplates: readonly CanvasNodeTemplate[] = [
  {
    shape: 'datascalpel-jdbc-input',
    label: '数据源输入',
    category: CanvasNodeCategory.Input,
    width: 240,
    height: 120,
  },
  {
    shape: 'datascalpel-data-filter',
    label: '数据过滤',
    category: CanvasNodeCategory.Processor,
    width: 240,
    height: 120,
  },
  {
    shape: 'datascalpel-model-output',
    label: '模型输出',
    category: CanvasNodeCategory.Output,
    width: 240,
    height: 120,
  },
];

const categoryLabel: Record<CanvasNodeCategory, string> = {
  [CanvasNodeCategory.Input]: '输入',
  [CanvasNodeCategory.Processor]: '处理',
  [CanvasNodeCategory.Output]: '输出',
};

const categoryIcon: Record<CanvasNodeCategory, ReactNode> = {
  [CanvasNodeCategory.Input]: <DatabaseOutlined />,
  [CanvasNodeCategory.Processor]: <FilterOutlined />,
  [CanvasNodeCategory.Output]: <SaveOutlined />,
};

const categoryColor: Record<CanvasNodeCategory, string> = {
  [CanvasNodeCategory.Input]: 'blue',
  [CanvasNodeCategory.Processor]: 'gold',
  [CanvasNodeCategory.Output]: 'green',
};

interface CanvasNodeViewProps {
  node: Node;
}

const CanvasNodeView = ({ node }: CanvasNodeViewProps) => {
  const [data, setData] = useState<CanvasNodeData>(() => node.getData<CanvasNodeData>());

  useEffect(() => {
    const refresh = () => setData(node.getData<CanvasNodeData>());
    node.on('change:data', refresh);
    return () => {
      node.off('change:data', refresh);
    };
  }, [node]);

  const description = Object.keys(data.configuration).length > 0
    ? '已配置'
    : '尚未配置';

  return (
    <div className="canvas-node">
      <div className="canvas-node-header">
        <span>{categoryIcon[data.category]}</span>
        <span className="canvas-node-title">{data.label}</span>
        <Tag color={categoryColor[data.category]}>{categoryLabel[data.category]}</Tag>
      </div>
      <div className="canvas-node-body">
        <SwapOutlined />
        <span>{description}</span>
      </div>
    </div>
  );
};

let didRegisterCanvasNodes = false;

export const registerCanvasNodes = () => {
  if (didRegisterCanvasNodes) return;

  canvasNodeTemplates.forEach((template) => {
    register({
      shape: template.shape,
      width: template.width,
      height: template.height,
      component: CanvasNodeView,
      effect: ['data'],
    });
  });

  didRegisterCanvasNodes = true;
};
