import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createTdEngineTmqInputConfiguration } from '../nodeDefaults';
import { collectTdEngineTmqInputMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeTdEngineTmqInput } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { tdEngineTmqInputCanvasView } from './canvasView';

export const tdEngineTmqInputSpec = createCanvasNodeSpec({
  type: CanvasNodeType.TdEngineTmqInput,
  category: CanvasNodeCategory.Input,
  group: CanvasNodeGroup.InputStream,
  label: 'TDengine TMQ 输入',
  description: '持续订阅完整超级表 TMQ Topic',
  searchKeywords: ['tdengine', 'tmq', 'topic', '超级表', '流式'],
  iconKey: CanvasNodeIconKey.Stream,
  order: 20,
  canvasView: tdEngineTmqInputCanvasView,
  supportedModes: ['STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 0, maxInputs: 0, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createTdEngineTmqInputConfiguration,
  summarize: summarizeTdEngineTmqInput,
  collectMetadataReferences: collectTdEngineTmqInputMetadataReferences,
  loadInspector: () => import('./inspector'),
});
