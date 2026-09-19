import { CanvasNodeType } from '../../canvasTypes';
import { AggregateProcessorInspector } from '../../components/processors/AggregateProcessorInspector';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const AggregateCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.Aggregate, AggregateProcessorInspector);

export default AggregateCanvasNodeInspector;

