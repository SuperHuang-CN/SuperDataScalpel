import { CanvasNodeType, type FileOutputFormatOptions } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeSplit, NodeTitleLine } from '../../components/nodeView/CanvasNodePrimitives';
import { metadataSourceName, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const formatDetails = (options: FileOutputFormatOptions): { detail: string; meta: string } => {
  switch (options.type) {
    case 'CSV': return { detail: `分隔符 ${JSON.stringify(options.delimiter)} · ${options.header ? 'Header' : '无 Header'}`, meta: 'CSV' };
    case 'JSON_LINES': return { detail: options.ignoreNullFields ? '忽略 NULL 字段' : '保留 NULL 字段', meta: 'JSON Lines' };
    case 'PARQUET': return { detail: '标准列式文件', meta: 'Parquet' };
    case 'SHAPEFILE': return { detail: `${options.packageMode} · ${options.targetShapeType} · ${options.attributeMappings.length} 个属性`, meta: `SHP · ${options.geometryColumnName || '待选 Geometry'}` };
    case 'GEOPARQUET': return { detail: `${options.compression} · ${options.coveringMode}`, meta: `GeoParquet · ${options.geometryColumnName || '待选 Geometry'}` };
    case 'GEOJSON': return { detail: `${options.idColumnName ? `ID ${options.idColumnName}` : '无 Feature ID'} · ${options.ignoreNullProperties ? '忽略 NULL' : '保留 NULL'}`, meta: `GeoJSON · ${options.geometryColumnName || '待选 Geometry'}` };
  }
};

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.FileOutput>) => {
  const { sourceTableName, dataSourceId, targetPath, conflictPolicy, formatOptions } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表和文件输出目标</NodeEmpty>;
  const details = formatDetails(formatOptions);
  const baseName = 'baseName' in formatOptions ? formatOptions.baseName : null;
  return <NodeContent variant="output">
    <NodeTitleLine primary={dataSourceId ? metadataSourceName(data) : '待选择文件数据源'} secondary={targetPath || '待设置目标路径'} accent />
    <NodeFlow source={sourceTableName} operation={formatOptions.type} target={baseName ? `${targetPath}/${baseName}` : targetPath} />
    <NodeSplit leftLabel="输出格式" left={details.meta} rightLabel="格式参数" right={details.detail} />
    <NodeBadges><NodeBadge tone="strong">{formatOptions.type}</NodeBadge><NodeBadge tone={conflictPolicy === 'OVERWRITE' ? 'warning' : 'success'}>{conflictPolicy}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const fileOutputCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.FileOutput> = {
  resolveSize: (configuration) => {
    const spatial = ['SHAPEFILE', 'GEOPARQUET', 'GEOJSON'].includes(configuration.formatOptions.type);
    return resolvedNodeSize({ width: spatial ? 376 : 360, maxHeight: spatial ? 240 : 216, emptyHeight: 112, configured: Boolean(configuration.sourceTableName) });
  },
  Body: body,
};
