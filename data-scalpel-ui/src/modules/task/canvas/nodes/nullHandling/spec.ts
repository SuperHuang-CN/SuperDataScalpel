import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createNullHandlingConfiguration } from './defaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeNullHandling } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { nullHandlingCanvasView } from './canvasView';

export const nullHandlingSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.NullHandling,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorQuality,
  label: '空值处理',
  description: '删除含空值的行，或使用固定值填充空字段',
  searchKeywords: ['null', '空值', '缺失值', '填充', '删除行'],
  iconKey: CanvasNodeIconKey.NullHandling,
  order: 10,
  canvasView: nullHandlingCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createNullHandlingConfiguration,
  summarize: summarizeNullHandling,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
