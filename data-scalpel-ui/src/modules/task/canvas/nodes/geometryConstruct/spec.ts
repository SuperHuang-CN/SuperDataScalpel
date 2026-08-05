import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createGeometryConstructConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeGeometryConstruct } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const geometryConstructSpec = createCanvasNodeSpec({
  type: CanvasNodeType.GeometryConstruct,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: 'Geometry 构造',
  description: '从 WKT、WKB、GeoJSON 或 X/Y 字段构造空间字段',
  searchKeywords: ['空间', 'geometry', 'construct', 'wkt', 'wkb', 'geojson', 'xy', '构造'],
  iconKey: CanvasNodeIconKey.GeometryConstruct,
  order: 10,
  defaultSize: { width: 250, height: 120 },
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 21,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createGeometryConstructConfiguration,
  summarize: summarizeGeometryConstruct,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
