import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createJdbcQueryInputConfiguration } from '../nodeDefaults';
import { collectJdbcQueryInputMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeJdbcQueryInput } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const jdbcQueryInputSpec = createCanvasNodeSpec({
  type: CanvasNodeType.JdbcQueryInput,
  category: CanvasNodeCategory.Input,
  group: CanvasNodeGroup.InputDatabase,
  label: 'JDBC 查询输入',
  description: '通过只读 SQL 读取自定义查询结果',
  searchKeywords: ['jdbc', 'sql', '数据库', '查询', '维表'],
  iconKey: CanvasNodeIconKey.DatabaseQuery,
  order: 20,
  defaultSize: { width: 240, height: 120 },
  supportedModes: ['BATCH', 'STREAMING'],
  introducedInMinor: 24,
  graph: { minInputs: 0, maxInputs: 0, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createJdbcQueryInputConfiguration,
  summarize: summarizeJdbcQueryInput,
  collectMetadataReferences: collectJdbcQueryInputMetadataReferences,
  loadInspector: () => import('./inspector'),
});
