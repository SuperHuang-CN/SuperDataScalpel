import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createTypeCastConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeTypeCast } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const typeCastSpec = createCanvasNodeSpec({
  type: CanvasNodeType.TypeCast,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorColumn,
  label: '类型转换',
  description: '将字段显式转换为平台数据类型',
  searchKeywords: ['cast', 'convert', '类型转换', '字段类型', 'try cast'],
  iconKey: CanvasNodeIconKey.Cast,
  order: 40,
  defaultSize: { width: 240, height: 120 },
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 10,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createTypeCastConfiguration,
  summarize: summarizeTypeCast,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
