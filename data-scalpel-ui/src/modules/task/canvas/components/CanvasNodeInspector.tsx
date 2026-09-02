import { CompactAlert as Alert } from '../../../../shared/components/ContextualFeedback';
import { ReloadOutlined } from '@ant-design/icons';
import { Button, Card, Spin } from 'antd';
import {
  forwardRef,
  useEffect,
  useState,
  type ComponentType,
} from 'react';
import {
  type CanvasNodeConfigurationUpdate,
  type CanvasNodeDefinition,
  type CanvasExecutionMode,
  type CanvasNodeValidationResult,
  type CanvasNodeType,
} from '../canvasTypes';
import { canvasNodeRegistry } from '../nodes/nodeRegistry';
import type {
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from '../nodes/nodeSpec';

export type { CanvasNodeInspectorHandle } from '../nodes/nodeSpec';

interface CanvasNodeInspectorProps {
  node: CanvasNodeDefinition | null;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage?: string | null;
  executionMode?: CanvasExecutionMode;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
}

type LoadedInspector = ComponentType<CanvasNodeInspectorComponentProps<CanvasNodeType>>;

const configurationFingerprint = (node: CanvasNodeDefinition) => (
  `${node.id}:${JSON.stringify(node.configuration)}`
);

export const CanvasNodeInspector = forwardRef<CanvasNodeInspectorHandle, CanvasNodeInspectorProps>(({
  node,
  validation,
  validationUnavailableMessage = null,
  executionMode = 'BATCH',
  onApply,
  onDirtyChange,
}, ref) => {
  const [Inspector, setInspector] = useState<LoadedInspector | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const nodeType = node?.type ?? null;

  useEffect(() => {
    let active = true;
    setInspector(null);
    setLoadError(null);
    if (!nodeType) return () => { active = false; };

    canvasNodeRegistry.require(nodeType).loadInspector()
      .then((module) => {
        if (active) setInspector(() => module.default as LoadedInspector);
      })
      .catch((error: unknown) => {
        if (!active) return;
        setLoadError(error instanceof Error ? error.message : '节点配置面板加载失败');
      });

    return () => {
      active = false;
    };
  }, [loadAttempt, nodeType]);

  if (!node) {
    return <Card size="small" className="canvas-inspector-empty">选中一个节点后配置其数据源、表和字段规则。</Card>;
  }

  if (loadError) {
    return (
      <Alert
        showIcon
        type="error"
        title="节点配置面板加载失败"
        description={loadError}
        action={(
          <Button
            size="small"
            icon={<ReloadOutlined />}
            onClick={() => setLoadAttempt((attempt) => attempt + 1)}
          >
            重试
          </Button>
        )}
      />
    );
  }

  if (!Inspector) {
    return (
      <div className="canvas-inspector-loading">
        <Spin size="small" />
        <span>正在加载节点配置…</span>
      </div>
    );
  }

  return (
    <Inspector
      key={configurationFingerprint(node)}
      inspectorRef={ref}
      node={node}
      executionMode={executionMode}
      validation={validation}
      validationUnavailableMessage={validationUnavailableMessage}
      onApply={onApply}
      onDirtyChange={onDirtyChange}
    />
  );
});

CanvasNodeInspector.displayName = 'CanvasNodeInspector';
