import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createModelOutputConfiguration } from '../nodeDefaults';
import { collectModelOutputMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeModelOutput } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { modelOutputCanvasView } from './canvasView';

export const modelOutputSpec = createCanvasNodeSpec({
  type: CanvasNodeType.ModelOutput,
  category: CanvasNodeCategory.Output,
  group: CanvasNodeGroup.OutputModel,
  label: '模型输出',
  description: '将处理结果写入平台模型',
  searchKeywords: ['model', '模型', '写入', '保存'],
  iconKey: CanvasNodeIconKey.ModelOutput,
  order: 10,
  canvasView: modelOutputCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 0, maxOutputs: 0 },
  createDefaultConfiguration: createModelOutputConfiguration,
  summarize: summarizeModelOutput,
  collectMetadataReferences: collectModelOutputMetadataReferences,
  loadInspector: () => import('./inspector'),
});
