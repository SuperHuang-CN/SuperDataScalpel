import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createJdbcSnapshotSyncOutputConfiguration } from './defaults';
import { collectJdbcSnapshotSyncOutputMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeJdbcSnapshotSyncOutput } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { jdbcSnapshotSyncOutputCanvasView } from './canvasView';

export const jdbcSnapshotSyncOutputSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.JdbcSnapshotSyncOutput,
  category: CanvasNodeCategory.Output,
  group: CanvasNodeGroup.OutputDatabase,
  label: 'JDBC 快照同步',
  description: '按业务 Key 对比并同步 JDBC 实体快照',
  searchKeywords: ['jdbc', '快照', '同步', '对比更新', 'insert', 'update', 'delete'],
  iconKey: CanvasNodeIconKey.SnapshotSync,
  order: 20,
  canvasView: jdbcSnapshotSyncOutputCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 0, maxOutputs: 0 },
  createDefaultConfiguration: createJdbcSnapshotSyncOutputConfiguration,
  summarize: summarizeJdbcSnapshotSyncOutput,
  collectMetadataReferences: collectJdbcSnapshotSyncOutputMetadataReferences,
  loadInspector: () => import('./inspector'),
});
