import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createGeometryExplodeConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeGeometryExplode } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { geometryExplodeCanvasView } from './canvasView';

export const geometryExplodeSpec = createCanvasNodeSpec({
  type: CanvasNodeType.GeometryExplode,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: 'Geometry 拆分',
  description: '将 MultiGeometry 或集合拆成多行部件',
  searchKeywords: ['空间', 'geometry', 'explode', 'dump', '拆分', '多部件', '集合'],
  iconKey: CanvasNodeIconKey.GeometryExplode,
  order: 60,
  canvasView: geometryExplodeCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createGeometryExplodeConfiguration,
  summarize: summarizeGeometryExplode,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
