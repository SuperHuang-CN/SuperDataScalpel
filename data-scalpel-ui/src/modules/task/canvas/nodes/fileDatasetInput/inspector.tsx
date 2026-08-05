import { CanvasNodeType } from '../../canvasTypes';
import { FileDatasetInputInspector } from '../../components/CanvasLegacyInspectors';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const FileDatasetInputCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.FileDatasetInput, FileDatasetInputInspector);

export default FileDatasetInputCanvasNodeInspector;

