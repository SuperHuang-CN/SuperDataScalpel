import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createSpatialEnrichFromGridConfiguration } from '../nodeDefaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialEnrichFromGrid } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { spatialEnrichFromGridCanvasView } from './canvasView';

export const spatialEnrichFromGridSpec = createCanvasNodeSpec({
  type: CanvasNodeType.SpatialEnrichFromGrid,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '从多变量格网丰富',
  description: '把已有多变量格网的选定属性回填到 Point 要素',
  searchKeywords: ['空间', '多变量', '格网', '丰富', '回填', 'enrich from grid'],
  iconKey: CanvasNodeIconKey.SpatialEnrichFromGrid,
  order: 88,
  canvasView: spatialEnrichFromGridCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 71,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createSpatialEnrichFromGridConfiguration,
  summarize: summarizeSpatialEnrichFromGrid,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
