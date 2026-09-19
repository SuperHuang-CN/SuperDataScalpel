import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createGeometryRepairConfiguration } from './defaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeGeometryRepair } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { geometryRepairCanvasView } from './canvasView';

export const geometryRepairSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.GeometryRepair,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: 'Geometry 修复',
  description: '修复无效 Geometry，并保留原空间字段',
  searchKeywords: ['空间', 'geometry', 'repair', 'make valid', '修复', '拓扑'],
  iconKey: CanvasNodeIconKey.GeometryRepair,
  order: 40,
  canvasView: geometryRepairCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createGeometryRepairConfiguration,
  summarize: summarizeGeometryRepair,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
