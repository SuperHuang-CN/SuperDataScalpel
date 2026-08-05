import { CanvasNodeType } from '../../canvasTypes';
import { NullHandlingProcessorInspector } from '../../components/processors/NullHandlingProcessorInspector';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const NullHandlingCanvasNodeInspector = adaptCanvasNodeInspector(
  CanvasNodeType.NullHandling,
  NullHandlingProcessorInspector,
);

export default NullHandlingCanvasNodeInspector;
