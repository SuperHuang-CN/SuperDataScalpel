import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createKafkaOutputConfiguration } from './defaults';
import { collectKafkaOutputMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeKafkaOutput } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { kafkaOutputCanvasView } from './canvasView';

export const kafkaOutputSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.KafkaOutput,
  category: CanvasNodeCategory.Output,
  group: CanvasNodeGroup.OutputStream,
  label: 'Kafka 输出',
  description: '将处理结果发送到 Kafka Topic',
  searchKeywords: ['kafka', 'topic', '消息', '发送'],
  iconKey: CanvasNodeIconKey.StreamOutput,
  order: 10,
  canvasView: kafkaOutputCanvasView,
  supportedModes: ['STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 0, maxOutputs: 0 },
  createDefaultConfiguration: createKafkaOutputConfiguration,
  summarize: summarizeKafkaOutput,
  collectMetadataReferences: collectKafkaOutputMetadataReferences,
  loadInspector: () => import('./inspector'),
});
