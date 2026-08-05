import { CanvasNodeType } from '../../canvasTypes';
import { JoinInspector } from '../../components/CanvasLegacyInspectors';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const JoinCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.Join, JoinInspector);

export default JoinCanvasNodeInspector;

