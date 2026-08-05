import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createTopNConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeTopN } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const topNSpec = createCanvasNodeSpec({
  type: CanvasNodeType.TopN,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorRow,
  label: 'Top N',
  description: '按排序选择全局或分组内的前 N 行',
  searchKeywords: ['top n', '排名', '前几名', '分组', '并列'],
  iconKey: CanvasNodeIconKey.TopN,
  order: 30,
  defaultSize: { width: 240, height: 120 },
  supportedModes: ['BATCH'],
  introducedInMinor: 17,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createTopNConfiguration,
  summarize: summarizeTopN,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
