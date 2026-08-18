import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createKafkaInputConfiguration } from '../nodeDefaults';
import { collectKafkaInputMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeKafkaInput } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { kafkaInputCanvasView } from './canvasView';

export const kafkaInputSpec = createCanvasNodeSpec({
  type: CanvasNodeType.KafkaInput,
  category: CanvasNodeCategory.Input,
  group: CanvasNodeGroup.InputStream,
  label: 'Kafka 输入',
  description: '持续消费 Kafka Topic 数据',
  searchKeywords: ['kafka', 'topic', '消息', '流式'],
  iconKey: CanvasNodeIconKey.Stream,
  order: 10,
  canvasView: kafkaInputCanvasView,
  supportedModes: ['STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 0, maxInputs: 0, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createKafkaInputConfiguration,
  summarize: summarizeKafkaInput,
  collectMetadataReferences: collectKafkaInputMetadataReferences,
  loadInspector: () => import('./inspector'),
});
