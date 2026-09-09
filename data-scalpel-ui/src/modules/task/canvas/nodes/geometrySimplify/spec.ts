import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createGeometrySimplifyConfiguration } from '../nodeDefaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeGeometrySimplify } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { geometrySimplifyCanvasView } from './canvasView';

export const geometrySimplifySpec = createCanvasNodeSpec({
  type: CanvasNodeType.GeometrySimplify,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: 'Geometry 简化',
  description: '使用 Douglas-Peucker 或拓扑保持算法简化 Geometry',
  searchKeywords: [
    '空间', 'geometry', 'simplify', 'Douglas-Peucker', 'topology', '简化', '抽稀',
  ],
  iconKey: CanvasNodeIconKey.GeometrySimplify,
  order: 46,
  canvasView: geometrySimplifyCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 10,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createGeometrySimplifyConfiguration,
  summarize: summarizeGeometrySimplify,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
