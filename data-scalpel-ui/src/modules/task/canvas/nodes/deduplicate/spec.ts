import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createDeduplicateConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeDeduplicate } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const deduplicateSpec = createCanvasNodeSpec({
  type: CanvasNodeType.Deduplicate,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorRow,
  label: '去重',
  description: '按业务键保留任意、第一条或最后一条记录',
  searchKeywords: ['deduplicate', 'distinct', '去重', '重复', '第一条', '最后一条'],
  iconKey: CanvasNodeIconKey.Deduplicate,
  order: 20,
  defaultSize: { width: 240, height: 120 },
  supportedModes: ['BATCH'],
  introducedInMinor: 13,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createDeduplicateConfiguration,
  summarize: summarizeDeduplicate,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
