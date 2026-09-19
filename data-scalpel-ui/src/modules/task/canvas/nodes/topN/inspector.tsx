import { CanvasNodeType } from '../../canvasTypes';
import { TopNProcessorInspector } from '../../components/processors/TopNProcessorInspector';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const TopNCanvasNodeInspector = adaptCanvasNodeInspector(
  CanvasNodeType.TopN,
  TopNProcessorInspector,
);

export default TopNCanvasNodeInspector;
