import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createSpatialPointClusterConfiguration } from './defaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialPointCluster } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { spatialPointClusterCanvasView } from './canvasView';

export const spatialPointClusterSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.SpatialPointCluster,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '空间点聚类',
  description: '通过 DBSCAN 或 HDBSCAN 识别点簇、噪声及密度诊断',
  searchKeywords: ['空间', '点聚类', 'cluster', 'DBSCAN', 'HDBSCAN', 'find point clusters'],
  iconKey: CanvasNodeIconKey.SpatialPointCluster,
  order: 83,
  canvasView: spatialPointClusterCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 19,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createSpatialPointClusterConfiguration,
  summarize: summarizeSpatialPointCluster,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
