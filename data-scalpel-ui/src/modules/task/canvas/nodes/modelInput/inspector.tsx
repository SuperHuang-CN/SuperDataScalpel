import { CanvasNodeType } from '../../canvasTypes';
import { ModelInputInspector } from '../../components/CanvasLegacyInspectors';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const ModelInputCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.ModelInput, ModelInputInspector);

export default ModelInputCanvasNodeInspector;

