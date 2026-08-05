import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createFilterConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeFilter } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const filterSpec = createCanvasNodeSpec({
  type: CanvasNodeType.Filter,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorRow,
  label: '筛选',
  description: '按组合条件筛选一张表的数据行',
  searchKeywords: ['filter', 'where', '筛选', '过滤', '条件'],
  iconKey: CanvasNodeIconKey.Filter,
  order: 10,
  defaultSize: { width: 240, height: 120 },
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 7,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createFilterConfiguration,
  summarize: summarizeFilter,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
