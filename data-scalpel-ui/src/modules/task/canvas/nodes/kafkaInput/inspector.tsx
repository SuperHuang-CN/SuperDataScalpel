import { CanvasNodeType } from '../../canvasTypes';
import { KafkaInputInspector } from '../../components/CanvasLegacyInspectors';
import { adaptCanvasNodeInspector } from '../inspectorAdapter';

const KafkaInputCanvasNodeInspector = adaptCanvasNodeInspector(CanvasNodeType.KafkaInput, KafkaInputInspector);

export default KafkaInputCanvasNodeInspector;

