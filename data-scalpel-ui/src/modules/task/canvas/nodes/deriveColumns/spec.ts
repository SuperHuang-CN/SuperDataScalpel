import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createDeriveColumnsConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeDeriveColumns } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { deriveColumnsCanvasView } from './canvasView';

export const deriveColumnsSpec = createCanvasNodeSpec({
  type: CanvasNodeType.DeriveColumns,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorColumn,
  label: '派生字段',
  description: '通过表达式新增或覆盖字段',
  searchKeywords: ['derive', 'expression', '计算字段', '派生字段', '新增字段', '覆盖字段'],
  iconKey: CanvasNodeIconKey.Derive,
  order: 30,
  canvasView: deriveColumnsCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createDeriveColumnsConfiguration,
  summarize: summarizeDeriveColumns,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
