import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createFileOutputConfiguration } from '../nodeDefaults';
import { collectFileOutputMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeFileOutput } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { fileOutputCanvasView } from './canvasView';

export const fileOutputSpec = createCanvasNodeSpec({
  type: CanvasNodeType.FileOutput,
  category: CanvasNodeCategory.Output,
  group: CanvasNodeGroup.OutputFile,
  label: '文件输出',
  description: '将处理结果写入外部 S3 目录',
  searchKeywords: [
    'file', 's3', 'csv', 'json', 'parquet', 'shp', 'shapefile',
    'geoparquet', 'geojson', 'featurecollection', 'wkb parquet', 'rfc 7946',
    '文件', '对象存储', '空间文件', '空间 parquet', '空间 json',
  ],
  iconKey: CanvasNodeIconKey.FileOutput,
  order: 10,
  canvasView: fileOutputCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 0, maxOutputs: 0 },
  createDefaultConfiguration: createFileOutputConfiguration,
  summarize: summarizeFileOutput,
  collectMetadataReferences: collectFileOutputMetadataReferences,
  loadInspector: () => import('./inspector'),
});
