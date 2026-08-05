import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createJdbcInputConfiguration } from '../nodeDefaults';
import { collectJdbcInputMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeJdbcInput } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const jdbcInputSpec = createCanvasNodeSpec({
  type: CanvasNodeType.JdbcInput,
  category: CanvasNodeCategory.Input,
  group: CanvasNodeGroup.InputDatabase,
  label: 'JDBC 输入',
  description: '从 JDBC 数据源读取物理表',
  searchKeywords: ['jdbc', '数据库', '数据源', '物理表'],
  iconKey: CanvasNodeIconKey.Database,
  order: 10,
  defaultSize: { width: 240, height: 120 },
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 0,
  graph: { minInputs: 0, maxInputs: 0, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createJdbcInputConfiguration,
  summarize: summarizeJdbcInput,
  collectMetadataReferences: collectJdbcInputMetadataReferences,
  loadInspector: () => import('./inspector'),
});
