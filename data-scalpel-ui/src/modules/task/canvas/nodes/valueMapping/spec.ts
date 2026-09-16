import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createValueMappingConfiguration } from './defaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeValueMapping } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { valueMappingCanvasView } from './canvasView';

export const valueMappingSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.ValueMapping,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorColumn,
  label: '值映射',
  description: '把少量原值精确映射为标准值',
  searchKeywords: ['mapping', 'map', '值映射', '枚举', '标准化', '字典'],
  iconKey: CanvasNodeIconKey.ValueMapping,
  order: 40,
  canvasView: valueMappingCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createValueMappingConfiguration,
  summarize: summarizeValueMapping,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
