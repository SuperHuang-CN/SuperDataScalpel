import { CanvasNodeType } from '../../canvasTypes';
import { TdEngineTmqInputInspector } from './tdEngineTmqInputInspector';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const Inspector = adaptCanvasNodeInspector(CanvasNodeType.TdEngineTmqInput, TdEngineTmqInputInspector);
export default Inspector;
