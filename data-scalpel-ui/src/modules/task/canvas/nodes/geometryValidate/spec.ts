import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createGeometryValidateConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeGeometryValidate } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { geometryValidateCanvasView } from './canvasView';

export const geometryValidateSpec = createCanvasNodeSpec({
  type: CanvasNodeType.GeometryValidate,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: 'Geometry 校验',
  description: '诊断 Geometry 的拓扑合法性并可输出无效原因',
  searchKeywords: ['空间', 'geometry', 'validate', 'valid', '校验', '合法性'],
  iconKey: CanvasNodeIconKey.GeometryValidate,
  order: 30,
  canvasView: geometryValidateCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createGeometryValidateConfiguration,
  summarize: summarizeGeometryValidate,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
