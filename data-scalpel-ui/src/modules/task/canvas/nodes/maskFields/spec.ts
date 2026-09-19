import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createMaskFieldsConfiguration } from './defaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeMaskFields } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { maskFieldsCanvasView } from './canvasView';

export const maskFieldsSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.MaskFields,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorColumn,
  label: '字段脱敏',
  description: '按全局或自定义规则对字段执行不可逆脱敏',
  searchKeywords: ['mask', 'masking', '脱敏', '掩码', '隐私'],
  iconKey: CanvasNodeIconKey.Masking,
  order: 50,
  canvasView: maskFieldsCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createMaskFieldsConfiguration,
  summarize: summarizeMaskFields,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
