import { ApartmentOutlined } from '@ant-design/icons';
import { Alert, Space, Typography, message } from 'antd';
import { useState } from 'react';
import { CanvasDesigner } from '../canvas/CanvasDesigner';
import { defaultCanvasDefinition } from '../canvas/defaultCanvas';
import type { CanvasDefinition } from '../canvas/canvasTypes';

export const TaskOrchestrationPage = () => {
  const [savedDefinition, setSavedDefinition] = useState<CanvasDefinition>(defaultCanvasDefinition);

  const saveDefinition = (definition: CanvasDefinition) => {
    setSavedDefinition(definition);
    message.success(`编排已暂存：${definition.nodes.length} 个节点，${definition.edges.length} 条连线`);
  };

  return (
    <Space orientation="vertical" size={16} className="page-stack">
      <div>
        <Typography.Title level={2}><ApartmentOutlined /> 任务编排</Typography.Title>
        <Typography.Paragraph type="secondary">
          Canvas 定义与 X6 图实例解耦。当前保存动作仅暂存于前端，后端任务 API 完成后会接入统一保存和预运行校验。
        </Typography.Paragraph>
      </div>
      <Alert
        showIcon
        type="info"
        title="第一版 Canvas 骨架"
        description={`当前内存中的定义包含 ${savedDefinition.nodes.length} 个节点和 ${savedDefinition.edges.length} 条连线。`}
      />
      <CanvasDesigner initialDefinition={savedDefinition} onSave={saveDefinition} />
    </Space>
  );
};
