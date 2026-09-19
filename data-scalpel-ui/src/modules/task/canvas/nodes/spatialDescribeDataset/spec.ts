import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createSpatialDescribeDatasetConfiguration } from './defaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialDescribeDataset } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { spatialDescribeDatasetCanvasView } from './canvasView';

export const spatialDescribeDatasetSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.SpatialDescribeDataset,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '描述数据集',
  description: '生成字段统计和数据集描述，并可选输出样本及空间范围',
  searchKeywords: ['空间', '描述数据集', '剖析', '统计', '样本', '范围', 'describe dataset', 'profile'],
  iconKey: CanvasNodeIconKey.SpatialDescribeDataset,
  order: 89,
  canvasView: spatialDescribeDatasetCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 76,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createSpatialDescribeDatasetConfiguration,
  summarize: summarizeSpatialDescribeDataset,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
