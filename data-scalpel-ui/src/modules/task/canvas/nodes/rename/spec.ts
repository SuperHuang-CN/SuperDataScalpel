import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createRenameConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeRename } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const renameSpec = createCanvasNodeSpec({
  type: CanvasNodeType.Rename,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorColumn,
  label: '重命名',
  description: '重命名数据表或字段',
  searchKeywords: ['rename', '名称', '表名', '字段名'],
  iconKey: CanvasNodeIconKey.Rename,
  order: 10,
  defaultSize: { width: 240, height: 120 },
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 2,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createRenameConfiguration,
  summarize: summarizeRename,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
