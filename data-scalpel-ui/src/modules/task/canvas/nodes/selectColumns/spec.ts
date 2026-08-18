import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createSelectColumnsConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSelectColumns } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { selectColumnsCanvasView } from './canvasView';

export const selectColumnsSpec = createCanvasNodeSpec({
  type: CanvasNodeType.SelectColumns,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorColumn,
  label: '选择字段',
  description: '裁剪字段并定义输出字段顺序',
  searchKeywords: ['select', 'columns', 'project', '选择字段', '字段裁剪', '投影'],
  iconKey: CanvasNodeIconKey.Columns,
  order: 20,
  canvasView: selectColumnsCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createSelectColumnsConfiguration,
  summarize: summarizeSelectColumns,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
