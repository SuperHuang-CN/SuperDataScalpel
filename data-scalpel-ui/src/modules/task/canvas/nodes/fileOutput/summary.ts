import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeFileOutput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.FileOutput>,
) => {
  const { dataSourceId } = data.configuration;
  const writes = data.configuration.writes ?? [];
  const first = writes[0];
  if (!first) return '请至少配置一条文件写入';
  const { sourceTableName, targetPath, conflictPolicy, formatOptions } = first;
  const format = formatOptions.type === 'SHAPEFILE'
    ? `SHAPEFILE · ${formatOptions.targetShapeType || '未选类型'} · ${formatOptions.packageMode}`
    : formatOptions.type === 'GEOPARQUET'
      ? `GEOPARQUET · ${formatOptions.compression} · ${formatOptions.coveringMode}`
      : formatOptions.type === 'GEOJSON'
        ? `GEOJSON · ${formatOptions.baseName || '未命名'}`
        : formatOptions.type;
  const preview = !sourceTableName || !dataSourceId || !targetPath
    ? '请配置文件输出'
    : `${sourceTableName} → ${format} · ${targetPath} (${conflictPolicy})`;
  return writes.length > 1 ? `${preview}，另 ${writes.length - 1} 条写入` : preview;
};
