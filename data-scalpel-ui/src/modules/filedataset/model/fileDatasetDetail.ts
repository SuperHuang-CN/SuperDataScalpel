import type {
  FileDatasetParsingOptions,
  FileDatasetTable,
} from './fileDataset';

export type FileDatasetDetailTabKey = 'overview' | 'files' | 'tables';

export interface FileDatasetParsingOptionEntry {
  label: string;
  value: string;
}

const recordDelimiterLabels = {
  AUTO: '自动识别',
  LF: 'LF',
  CRLF: 'CRLF',
  CR: 'CR',
} as const;

const visibleCharacter = (value: string | undefined): string => {
  if (!value) return '不使用';
  if (value === '\t') return 'Tab (\\t)';
  if (value === ' ') return '空格';
  return value;
};

export const normalizeFileDatasetDetailTab = (value: string | null): FileDatasetDetailTabKey => {
  if (value === 'files' || value === 'tables') return value;
  return 'overview';
};

export const resolveFileDatasetTableId = (
  requestedTableId: string | null,
  tables: FileDatasetTable[],
): string | undefined => {
  if (requestedTableId && tables.some((table) => table.id === requestedTableId)) return requestedTableId;
  return tables[0]?.id;
};

export const fileDatasetParsingOptionEntries = (
  options: FileDatasetParsingOptions,
): FileDatasetParsingOptionEntry[] => {
  switch (options.kind) {
    case 'CSV':
      return [
        { label: '字符集', value: options.charset },
        { label: '字段分隔符', value: visibleCharacter(options.fieldDelimiter) },
        { label: '记录分隔符', value: recordDelimiterLabels[options.recordDelimiter] },
        { label: '首行为表头', value: options.firstRowHeader ? '是' : '否' },
        { label: '引用字符', value: visibleCharacter(options.quoteCharacter) },
        { label: '转义字符', value: visibleCharacter(options.escapeCharacter) },
      ];
    case 'TEXT':
      return [
        { label: '字符集', value: options.charset },
        { label: '记录分隔符', value: recordDelimiterLabels[options.recordDelimiter] },
      ];
    case 'JSON':
      return [
        { label: '字符集', value: options.charset },
        { label: '根指针', value: options.rootPointer || '文档根节点' },
      ];
    case 'JSON_LINES':
      return [
        { label: '字符集', value: options.charset },
        { label: '记录分隔符', value: recordDelimiterLabels[options.recordDelimiter] },
      ];
    case 'GEOJSON':
      return [
        { label: '输入结构', value: 'RFC 7946 FeatureCollection' },
        { label: '坐标参考', value: `EPSG:${options.epsgCode}（不转换坐标）` },
        { label: '几何维度', value: '二维 XY' },
      ];
    case 'GEOJSONL':
      return [
        { label: '输入结构', value: '每个非空物理行是一个 GeoJSON Feature' },
        { label: '坐标参考', value: `EPSG:${options.epsgCode}（不转换坐标）` },
        { label: '几何维度', value: '二维 XY' },
      ];
    case 'SPREADSHEET':
      return [
        { label: '表头行', value: `第 ${options.headerRowIndex + 1} 行` },
        { label: '数据起始行', value: `第 ${options.dataStartRowIndex + 1} 行` },
      ];
    case 'PARQUET':
      return [{ label: '解析方式', value: '读取 Parquet 内置 Schema' }];
    case 'GEOPARQUET':
      return [
        { label: '解析方式', value: '读取 GeoParquet Footer 和 Parquet 内置 Schema' },
        { label: 'Geometry', value: 'WKB · 二维 XY · 坐标参考由文件元数据识别' },
      ];
    case 'GPKG':
      return [
        { label: '上传格式', value: '单个 .gpkg 文件' },
        { label: '解析方式', value: '后台发现 features 图层和 attributes 属性表' },
        { label: 'Geometry', value: '二维 XY · 坐标参考由每个图层元数据识别' },
      ];
    case 'AVRO':
      return [{ label: '解析方式', value: '读取 Avro 内置 Schema' }];
    case 'GDB':
      return [
        { label: '上传格式', value: '包含一个 .gdb 目录的 ZIP' },
        { label: '解析方式', value: '解包为不可变目录并自动发现业务图层' },
      ];
    case 'SHP':
      return [
        { label: '上传格式', value: '包含一套同名 Shapefile 组件的 ZIP' },
        { label: '强制 DBF 编码', value: options.dbfCharsetOverride || '不强制（按 CPG / DBF 标记识别）' },
        { label: 'DBF 回退编码', value: options.dbfFallbackCharset },
        { label: '解析方式', value: '规范化组件并生成一张空间数据表' },
      ];
  }
};
