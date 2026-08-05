import { CanvasNodeType } from '../../canvasTypes';
import { FilterProcessorInspector } from '../../components/processors/FilterProcessorInspector';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const FilterCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.Filter, FilterProcessorInspector);

export default FilterCanvasNodeInspector;

