import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createDeduplicateConfiguration } from './defaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeDeduplicate } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { deduplicateCanvasView } from './canvasView';

export const deduplicateSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.Deduplicate,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorRow,
  label: '去重',
  description: '按业务键保留任意、第一条或最后一条记录',
  searchKeywords: ['deduplicate', 'distinct', '去重', '重复', '第一条', '最后一条'],
  iconKey: CanvasNodeIconKey.Deduplicate,
  order: 20,
  canvasView: deduplicateCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createDeduplicateConfiguration,
  summarize: summarizeDeduplicate,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
