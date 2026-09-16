import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createGeometryDeriveConfiguration } from './defaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeGeometryDerive } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { geometryDeriveCanvasView } from './canvasView';

export const geometryDeriveSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.GeometryDerive,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: 'Geometry 派生',
  description: '从 Geometry 生成中心点、面内点、包络、凸包或边界',
  searchKeywords: [
    '空间', 'geometry', 'derive', 'centroid', 'point on surface',
    'envelope', 'convex hull', 'boundary', '中心点', '面内点', '凸包', '边界',
  ],
  iconKey: CanvasNodeIconKey.GeometryDerive,
  order: 45,
  canvasView: geometryDeriveCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 9,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createGeometryDeriveConfiguration,
  summarize: summarizeGeometryDerive,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
