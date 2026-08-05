import { CanvasNodeType } from '../../canvasTypes';
import { UnionProcessorInspector } from '../../components/processors/UnionProcessorInspector';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const UnionCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.Union, UnionProcessorInspector);

export default UnionCanvasNodeInspector;

