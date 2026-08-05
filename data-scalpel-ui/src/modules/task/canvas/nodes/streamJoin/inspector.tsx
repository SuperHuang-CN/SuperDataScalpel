import { CanvasNodeType } from '../../canvasTypes';
import { StreamJoinInspector } from '../../components/CanvasLegacyInspectors';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const StreamJoinCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.StreamJoin, StreamJoinInspector);

export default StreamJoinCanvasNodeInspector;

