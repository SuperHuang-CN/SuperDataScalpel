import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createStreamJoinConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeStreamJoin } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const streamJoinSpec = createCanvasNodeSpec({
  type: CanvasNodeType.StreamJoin,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorStream,
  label: '流-维 Join',
  description: '将实时数据流与静态维表关联',
  searchKeywords: ['stream', 'join', '流', '维表', '关联'],
  iconKey: CanvasNodeIconKey.StreamJoin,
  order: 10,
  defaultSize: { width: 240, height: 120 },
  supportedModes: ['STREAMING'],
  introducedInMinor: 3,
  graph: { minInputs: 2, maxInputs: 2, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createStreamJoinConfiguration,
  summarize: summarizeStreamJoin,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
