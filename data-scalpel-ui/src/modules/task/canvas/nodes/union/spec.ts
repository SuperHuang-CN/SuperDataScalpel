import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createUnionConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeUnion } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const unionSpec = createCanvasNodeSpec({
  type: CanvasNodeType.Union,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorRelational,
  label: '合并数据',
  description: '按字段名纵向合并多张结构一致的表',
  searchKeywords: ['union', '合并', '纵向', '追加', 'all', 'distinct'],
  iconKey: CanvasNodeIconKey.Union,
  order: 20,
  defaultSize: { width: 240, height: 120 },
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 12,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createUnionConfiguration,
  summarize: summarizeUnion,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
