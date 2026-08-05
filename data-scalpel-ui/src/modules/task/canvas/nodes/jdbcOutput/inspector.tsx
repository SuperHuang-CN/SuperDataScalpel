import { CanvasNodeType } from '../../canvasTypes';
import { JdbcOutputInspector } from '../../components/CanvasLegacyInspectors';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const JdbcOutputCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.JdbcOutput, JdbcOutputInspector);

export default JdbcOutputCanvasNodeInspector;

