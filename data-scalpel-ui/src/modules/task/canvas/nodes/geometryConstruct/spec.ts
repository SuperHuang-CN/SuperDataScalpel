import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createGeometryConstructConfiguration } from './defaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeGeometryConstruct } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { geometryConstructCanvasView } from './canvasView';

export const geometryConstructSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.GeometryConstruct,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: 'Geometry 构造',
  description: '从 WKT、WKB、GeoJSON 或 X/Y 字段构造空间字段',
  searchKeywords: ['空间', 'geometry', 'construct', 'wkt', 'wkb', 'geojson', 'xy', '构造'],
  iconKey: CanvasNodeIconKey.GeometryConstruct,
  order: 10,
  canvasView: geometryConstructCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createGeometryConstructConfiguration,
  summarize: summarizeGeometryConstruct,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
