import { CanvasNodeType } from '../../canvasTypes';
import { DeduplicateProcessorInspector } from '../../components/processors/DeduplicateProcessorInspector';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const DeduplicateCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.Deduplicate, DeduplicateProcessorInspector);

export default DeduplicateCanvasNodeInspector;

