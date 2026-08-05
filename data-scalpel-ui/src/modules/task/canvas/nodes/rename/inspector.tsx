import { CanvasNodeType } from '../../canvasTypes';
import { RenameInspector } from '../../components/CanvasLegacyInspectors';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const RenameCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.Rename, RenameInspector);

export default RenameCanvasNodeInspector;

