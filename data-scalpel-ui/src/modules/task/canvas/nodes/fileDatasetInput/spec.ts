import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createFileDatasetInputConfiguration } from '../nodeDefaults';
import { collectFileDatasetInputMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeFileDatasetInput } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const fileDatasetInputSpec = createCanvasNodeSpec({
  type: CanvasNodeType.FileDatasetInput,
  category: CanvasNodeCategory.Input,
  group: CanvasNodeGroup.InputFile,
  label: '文件数据集输入',
  description: '从平台文件数据集读取表数据',
  searchKeywords: ['file', 'dataset', '文件', '数据集'],
  iconKey: CanvasNodeIconKey.File,
  order: 10,
  defaultSize: { width: 240, height: 120 },
  supportedModes: ['BATCH'],
  introducedInMinor: 4,
  graph: { minInputs: 0, maxInputs: 0, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createFileDatasetInputConfiguration,
  summarize: summarizeFileDatasetInput,
  collectMetadataReferences: collectFileDatasetInputMetadataReferences,
  loadInspector: () => import('./inspector'),
});
