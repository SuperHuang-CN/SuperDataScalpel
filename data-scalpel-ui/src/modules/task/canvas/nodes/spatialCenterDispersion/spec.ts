import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createSpatialCenterDispersionConfiguration } from './defaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialCenterDispersion } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { spatialCenterDispersionCanvasView } from './canvasView';

export const spatialCenterDispersionSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.SpatialCenterDispersion,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '中心与离散统计',
  description: '按分组计算平均中心、中位中心、中央要素、标准距离和方向椭圆',
  searchKeywords: ['空间', '中心', '离散', 'mean center', 'median center', 'ellipse'],
  iconKey: CanvasNodeIconKey.SpatialCenterDispersion,
  order: 84,
  canvasView: spatialCenterDispersionCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 20,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createSpatialCenterDispersionConfiguration,
  summarize: summarizeSpatialCenterDispersion,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
