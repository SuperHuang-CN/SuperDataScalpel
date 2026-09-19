import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createModelInputConfiguration } from './defaults';
import { collectModelInputMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeModelInput } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { modelInputCanvasView } from './canvasView';

export const modelInputSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.ModelInput,
  category: CanvasNodeCategory.Input,
  group: CanvasNodeGroup.InputModel,
  label: '模型输入',
  description: '从已发布模型读取结构化数据',
  searchKeywords: ['model', '模型', '读取'],
  iconKey: CanvasNodeIconKey.Model,
  order: 10,
  canvasView: modelInputCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 0,
  graph: { minInputs: 0, maxInputs: 0, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createModelInputConfiguration,
  summarize: summarizeModelInput,
  collectMetadataReferences: collectModelInputMetadataReferences,
  loadInspector: () => import('./inspector'),
});
