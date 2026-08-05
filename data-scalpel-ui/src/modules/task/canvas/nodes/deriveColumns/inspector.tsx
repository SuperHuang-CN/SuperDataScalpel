import { CanvasNodeType } from '../../canvasTypes';
import { DeriveColumnsProcessorInspector } from '../../components/processors/DeriveColumnsProcessorInspector';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const DeriveColumnsCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.DeriveColumns, DeriveColumnsProcessorInspector);

export default DeriveColumnsCanvasNodeInspector;

