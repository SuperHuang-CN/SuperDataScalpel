import { CanvasNodeType } from '../../canvasTypes';
import { ValueMappingProcessorInspector } from '../../components/processors/ValueMappingProcessorInspector';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const ValueMappingCanvasNodeInspector = adaptCanvasNodeInspector(
  CanvasNodeType.ValueMapping,
  ValueMappingProcessorInspector,
);

export default ValueMappingCanvasNodeInspector;
