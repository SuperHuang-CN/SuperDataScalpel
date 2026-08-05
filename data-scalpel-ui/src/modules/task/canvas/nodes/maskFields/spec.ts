import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createMaskFieldsConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeMaskFields } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const maskFieldsSpec = createCanvasNodeSpec({
  type: CanvasNodeType.MaskFields,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorColumn,
  label: '字段脱敏',
  description: '按全局或自定义规则对字段执行不可逆脱敏',
  searchKeywords: ['mask', 'masking', '脱敏', '掩码', '隐私'],
  iconKey: CanvasNodeIconKey.Masking,
  order: 50,
  defaultSize: { width: 240, height: 120 },
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 18,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 1, maxOutputs: 1 },
  createDefaultConfiguration: createMaskFieldsConfiguration,
  summarize: summarizeMaskFields,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
