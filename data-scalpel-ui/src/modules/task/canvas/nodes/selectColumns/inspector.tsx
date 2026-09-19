import { CanvasNodeType } from '../../canvasTypes';
import { SelectColumnsProcessorInspector } from '../../components/processors/SelectColumnsProcessorInspector';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const SelectColumnsCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.SelectColumns, SelectColumnsProcessorInspector);

export default SelectColumnsCanvasNodeInspector;

