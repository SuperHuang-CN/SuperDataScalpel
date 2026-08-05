import { CanvasNodeType } from '../../canvasTypes';
import { HttpApiInputInspector } from '../../components/CanvasLegacyInspectors';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const HttpApiInputCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.HttpApiInput, HttpApiInputInspector);

export default HttpApiInputCanvasNodeInspector;

