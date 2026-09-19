import { CanvasNodeType } from '../../canvasTypes';
import { MaskFieldsProcessorInspector } from '../../components/processors/MaskFieldsProcessorInspector';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const MaskFieldsCanvasNodeInspector = adaptCanvasNodeInspector(
  CanvasNodeType.MaskFields,
  MaskFieldsProcessorInspector,
);

export default MaskFieldsCanvasNodeInspector;
