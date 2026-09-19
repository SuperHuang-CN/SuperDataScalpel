import { CanvasNodeType } from '../../canvasTypes';
import { TypeCastProcessorInspector } from '../../components/processors/TypeCastProcessorInspector';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const TypeCastCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.TypeCast, TypeCastProcessorInspector);

export default TypeCastCanvasNodeInspector;

