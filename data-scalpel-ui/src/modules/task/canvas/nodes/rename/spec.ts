import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createRenameConfiguration } from './defaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeRename } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { renameCanvasView } from './canvasView';

export const renameSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.Rename,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorColumn,
  label: '重命名',
  description: '重命名数据表或字段',
  searchKeywords: ['rename', '名称', '表名', '字段名'],
  iconKey: CanvasNodeIconKey.Rename,
  order: 10,
  canvasView: renameCanvasView,
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createRenameConfiguration,
  summarize: summarizeRename,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
