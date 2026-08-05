import { CanvasNodeType } from '../../canvasTypes';
import { JdbcInputInspector } from '../../components/CanvasLegacyInspectors';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const JdbcInputCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.JdbcInput, JdbcInputInspector);

export default JdbcInputCanvasNodeInspector;

