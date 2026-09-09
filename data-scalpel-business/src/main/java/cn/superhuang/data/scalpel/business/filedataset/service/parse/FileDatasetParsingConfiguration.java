package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileRecordDelimiter;

/**
 * Parser-internal configuration for formats with an available sample parser.
 *
 * <p>It deliberately contains no HTTP or JSON-serialization annotations. The
 * file-dataset service translates the persisted API configuration at its
 * boundary before invoking a parser.</p>
 */
public sealed interface FileDatasetParsingConfiguration permits
        FileDatasetParsingConfiguration.Csv,
        FileDatasetParsingConfiguration.Text,
        FileDatasetParsingConfiguration.Json,
        FileDatasetParsingConfiguration.JsonLines,
        FileDatasetParsingConfiguration.GeoJson,
        FileDatasetParsingConfiguration.GeoJsonLines,
        FileDatasetParsingConfiguration.GeoParquet,
        FileDatasetParsingConfiguration.GeoPackage,
        FileDatasetParsingConfiguration.Spreadsheet,
        FileDatasetParsingConfiguration.Parquet,
        FileDatasetParsingConfiguration.Avro,
        FileDatasetParsingConfiguration.Gdb,
        FileDatasetParsingConfiguration.Shp {

    record Csv(
            String charset,
            String fieldDelimiter,
            FileRecordDelimiter recordDelimiter,
            String quoteCharacter,
            String escapeCharacter,
            boolean firstRowHeader
    ) implements FileDatasetParsingConfiguration {
    }

    record Text(String charset, FileRecordDelimiter recordDelimiter) implements FileDatasetParsingConfiguration {
    }

    record Json(String charset, String rootPointer) implements FileDatasetParsingConfiguration {
    }

    record JsonLines(String charset, FileRecordDelimiter recordDelimiter) implements FileDatasetParsingConfiguration {
    }

    record GeoJson(int epsgCode) implements FileDatasetParsingConfiguration {
        public GeoJson {
            if (epsgCode < 1) {
                throw new IllegalArgumentException("GeoJSON EPSG code 必须为正整数");
            }
        }
    }

    record GeoJsonLines(int epsgCode) implements FileDatasetParsingConfiguration {
        public GeoJsonLines {
            if (epsgCode < 1) {
                throw new IllegalArgumentException("GEOJSONL EPSG code 必须为正整数");
            }
        }
    }

    record GeoParquet() implements FileDatasetParsingConfiguration {
    }

    record GeoPackage(String tableName) implements FileDatasetParsingConfiguration {
        public GeoPackage {
            if (tableName == null || tableName.isBlank()) {
                throw new IllegalArgumentException("GeoPackage 表名不能为空");
            }
            tableName = tableName.trim();
        }
    }

    record Spreadsheet(String sourceKey, int headerRowIndex, int dataStartRowIndex)
            implements FileDatasetParsingConfiguration {
    }

    record Parquet() implements FileDatasetParsingConfiguration {
    }

    record Avro() implements FileDatasetParsingConfiguration {
    }

    record Gdb(String layerId, Integer epsgCode) implements FileDatasetParsingConfiguration {
        public Gdb {
            if (layerId == null || layerId.isBlank()) {
                throw new IllegalArgumentException("GDB 图层 ID 不能为空");
            }
            layerId = layerId.trim();
            if (epsgCode != null && epsgCode < 1) {
                throw new IllegalArgumentException("GDB EPSG code 必须为正整数");
            }
        }
    }

    record Shp(String dbfCharsetOverride, String dbfFallbackCharset, Integer epsgCode)
            implements FileDatasetParsingConfiguration {
        public Shp {
            dbfCharsetOverride = dbfCharsetOverride == null || dbfCharsetOverride.isBlank()
                    ? null : dbfCharsetOverride.trim();
            if (dbfFallbackCharset == null || dbfFallbackCharset.isBlank()) {
                throw new IllegalArgumentException("SHP DBF 回退编码不能为空");
            }
            dbfFallbackCharset = dbfFallbackCharset.trim();
            if (epsgCode != null && epsgCode < 1) {
                throw new IllegalArgumentException("SHP EPSG code 必须为正整数");
            }
        }
    }
}
