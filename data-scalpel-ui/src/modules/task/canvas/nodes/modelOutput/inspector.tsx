import { CanvasNodeType } from '../../canvasTypes';
import { ModelOutputInspector } from '../../components/CanvasLegacyInspectors';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const ModelOutputCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.ModelOutput, ModelOutputInspector);

export default ModelOutputCanvasNodeInspector;

