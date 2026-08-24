import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createGeometrySerializeConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeGeometrySerialize } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { geometrySerializeCanvasView } from './canvasView';

export const geometrySerializeSpec = createCanvasNodeSpec({
  type: CanvasNodeType.GeometrySerialize,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: 'Geometry 序列化',
  description: '将空间字段转换为 WKT、WKB 或 GeoJSON',
  searchKeywords: ['空间', 'geometry', 'serialize', 'wkt', 'wkb', 'geojson', '序列化'],
  iconKey: CanvasNodeIconKey.GeometrySerialize,
  order: 80,
  canvasView: geometrySerializeCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createGeometrySerializeConfiguration,
  summarize: summarizeGeometrySerialize,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
