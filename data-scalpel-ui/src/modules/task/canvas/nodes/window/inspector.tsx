import { CanvasNodeType } from '../../canvasTypes';
import { WindowProcessorInspector } from '../../components/processors/WindowProcessorInspector';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const WindowCanvasNodeInspector = adaptCanvasNodeInspector(
  CanvasNodeType.Window,
  WindowProcessorInspector,
);

export default WindowCanvasNodeInspector;
