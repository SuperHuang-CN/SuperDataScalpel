import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createFilterConfiguration } from './defaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeFilter } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { filterCanvasView } from './canvasView';

export const filterSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.Filter,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorRow,
  label: '筛选',
  description: '按可视化条件或 SQL 表达式逐表筛选数据行',
  searchKeywords: ['filter', 'where', '筛选', '过滤', '条件'],
  iconKey: CanvasNodeIconKey.Filter,
  order: 10,
  canvasView: filterCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createFilterConfiguration,
  summarize: summarizeFilter,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
