import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createTopNConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeTopN } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { topNCanvasView } from './canvasView';

export const topNSpec = createCanvasNodeSpec({
  type: CanvasNodeType.TopN,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorRow,
  label: 'Top N',
  description: '按排序选择全局或分组内的前 N 行',
  searchKeywords: ['top n', '排名', '前几名', '分组', '并列'],
  iconKey: CanvasNodeIconKey.TopN,
  order: 30,
  canvasView: topNCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createTopNConfiguration,
  summarize: summarizeTopN,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
