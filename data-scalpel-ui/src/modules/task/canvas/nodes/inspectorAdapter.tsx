import type { ComponentType } from 'react';
import type {
  CanvasExecutionMode,
  CanvasNodeByType,
  CanvasNodeConfigurationUpdate,
  CanvasNodeType,
  CanvasNodeValidationResult,
} from '../canvasTypes';
import type {
  CanvasNodeInspectorComponent,
  CanvasNodeInspectorComponentProps,
  CanvasNodeInspectorHandle,
} from './nodeSpec';
import type { Ref } from 'react';

interface LegacyInspectorProps<T extends CanvasNodeType> {
  node: CanvasNodeByType<T>;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  executionMode: CanvasExecutionMode;
  onApply: (update: CanvasNodeConfigurationUpdate) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

export const adaptCanvasNodeInspector = <T extends CanvasNodeType>(
  type: T,
  Inspector: ComponentType<LegacyInspectorProps<T>>,
): CanvasNodeInspectorComponent<T> => {
  const AdaptedInspector = (props: CanvasNodeInspectorComponentProps<T>) => (
    <Inspector
      {...props}
      onApply={(update) => {
        if (update.type !== type) {
          throw new Error(`Inspector ${type} 返回了错误节点类型 ${update.type}`);
        }
        props.onApply(update as Parameters<typeof props.onApply>[0]);
      }}
    />
  );
  AdaptedInspector.displayName = `${type}CanvasNodeInspector`;
  return AdaptedInspector;
};
