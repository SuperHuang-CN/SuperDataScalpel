import { CanvasNodeType } from '../../canvasTypes';
import { JsonExtractProcessorInspector } from '../../components/processors/JsonExtractProcessorInspector';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const JsonExtractCanvasNodeInspector = adaptCanvasNodeInspector(
  CanvasNodeType.JsonExtract,
  JsonExtractProcessorInspector,
);

export default JsonExtractCanvasNodeInspector;
