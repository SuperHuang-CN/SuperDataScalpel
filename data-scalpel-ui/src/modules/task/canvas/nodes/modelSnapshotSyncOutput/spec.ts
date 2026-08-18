import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createModelSnapshotSyncOutputConfiguration } from '../nodeDefaults';
import { collectModelSnapshotSyncOutputMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeModelSnapshotSyncOutput } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { modelSnapshotSyncOutputCanvasView } from './canvasView';

export const modelSnapshotSyncOutputSpec = createCanvasNodeSpec({
  type: CanvasNodeType.ModelSnapshotSyncOutput,
  category: CanvasNodeCategory.Output,
  group: CanvasNodeGroup.OutputModel,
  label: '模型快照同步',
  description: '按业务 Key 对比并同步 MANAGED 模型快照',
  searchKeywords: ['模型', '快照', '同步', '对比更新', '实体'],
  iconKey: CanvasNodeIconKey.SnapshotSync,
  order: 20,
  canvasView: modelSnapshotSyncOutputCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 0, maxOutputs: 0 },
  createDefaultConfiguration: createModelSnapshotSyncOutputConfiguration,
  summarize: summarizeModelSnapshotSyncOutput,
  collectMetadataReferences: collectModelSnapshotSyncOutputMetadataReferences,
  loadInspector: () => import('./inspector'),
});
