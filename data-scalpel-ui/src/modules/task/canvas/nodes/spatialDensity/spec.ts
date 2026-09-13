import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createSpatialDensityConfiguration } from '../nodeDefaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialDensity } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { spatialDensityCanvasView } from './canvasView';

export const spatialDensitySpec = createCanvasNodeSpec({
  type: CanvasNodeType.SpatialDensity,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '计算密度',
  description: '在投影坐标中按方格或六边形计算 Uniform / Kernel 点密度',
  searchKeywords: ['空间', '密度', 'density', 'kernel', '热点', '格网'],
  iconKey: CanvasNodeIconKey.SpatialDensity,
  order: 85,
  canvasView: spatialDensityCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 68,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createSpatialDensityConfiguration,
  summarize: summarizeSpatialDensity,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
