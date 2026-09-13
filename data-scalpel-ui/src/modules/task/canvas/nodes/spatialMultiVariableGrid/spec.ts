import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createSpatialMultiVariableGridConfiguration } from '../nodeDefaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialMultiVariableGrid } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { spatialMultiVariableGridCanvasView } from './canvasView';

export const spatialMultiVariableGridSpec = createCanvasNodeSpec({
  type: CanvasNodeType.SpatialMultiVariableGrid,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '构建多变量格网',
  description: '在多张空间表的统一格网上计算最近距离、最近属性和关联要素汇总',
  searchKeywords: ['空间', '多变量', '格网', '最近距离', '最近属性', '汇总', 'multi variable grid'],
  iconKey: CanvasNodeIconKey.SpatialMultiVariableGrid,
  order: 87,
  canvasView: spatialMultiVariableGridCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 70,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createSpatialMultiVariableGridConfiguration,
  summarize: summarizeSpatialMultiVariableGrid,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
