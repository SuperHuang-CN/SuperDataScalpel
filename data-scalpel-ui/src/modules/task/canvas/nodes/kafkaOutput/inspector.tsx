import { CanvasNodeType } from '../../canvasTypes';
import { KafkaOutputInspector } from '../../components/CanvasLegacyInspectors';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const KafkaOutputCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.KafkaOutput, KafkaOutputInspector);

export default KafkaOutputCanvasNodeInspector;

