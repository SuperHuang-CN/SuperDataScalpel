package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.dialect.geopackage.GeoPackageException;
import cn.superhuang.data.scalpel.dialect.geopackage.GeoPackageReader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses one discovered GeoPackage features or attributes table from a local, read-only SQLite file. */
@Component
public class GeoPackageFileDatasetParser implements FileDatasetParser {

    @Override
    public boolean supports(FileDatasetFormat format) {
        return format == FileDatasetFormat.GPKG;
    }

    @Override
    public FileDatasetParserInputMode inputMode() {
        return FileDatasetParserInputMode.LOCAL_FILE;
    }

    @Override
    public ParseResult parse(
            FileDatasetParseSource source,
            FileDatasetParsingConfiguration configuration,
            int recordLimit
    ) throws IOException {
        return read(source, configuration, recordLimit, false);
    }

    @Override
    public ParseResult validate(
            FileDatasetParseSource source,
            FileDatasetParsingConfiguration configuration,
            int previewLimit
    ) throws IOException {
        return read(source, configuration, previewLimit, true);
    }

    @Override
    public List<DiscoveredTable> discoverTables(FileDatasetParseSource source) {
        Path path = FileDatasetParseSource.requireLocalFile(source);
        try (GeoPackageReader reader = GeoPackageReader.open(path)) {
            return reader.discoverTables().stream()
                    .map(table -> new DiscoveredTable(table.tableName(), 0))
                    .toList();
        } catch (GeoPackageException exception) {
            throw new FileDatasetParsingException(exception.getMessage(), exception);
        }
    }

    private ParseResult read(
            FileDatasetParseSource source,
            FileDatasetParsingConfiguration configuration,
            int previewLimit,
            boolean validateAll
    ) {
        if (!(configuration instanceof FileDatasetParsingConfiguration.GeoPackage options)) {
            throw new FileDatasetParsingException("GPKG 解析参数类型不匹配");
        }
        if (previewLimit < 1) {
            throw new IllegalArgumentException("抽样记录数必须大于零");
        }
        Path path = FileDatasetParseSource.requireLocalFile(source);
        try (GeoPackageReader reader = GeoPackageReader.open(path)) {
            GeoPackageReader.TableSchema schema = reader.schema(options.tableName());
            List<Field> fields = schema.columns().stream()
                    .map(column -> new Field(
                            column.name(), column.sortOrder(), column.type(), column.nullable()
                    ))
                    .toList();
            List<Map<String, Object>> preview = new ArrayList<>();
            long count = 0;
            // Full validation reads and structurally checks Geometry BLOBs, but deliberately
            // leaves them out of the preview payload. Normal preview only projects attributes.
            try (GeoPackageReader.RowCursor cursor = reader.openRows(schema, validateAll, false)) {
                Map<String, Object> row;
                while ((row = cursor.next()) != null) {
                    count++;
                    if (preview.size() < previewLimit) {
                        preview.add(new LinkedHashMap<>(row));
                    }
                    if (!validateAll && count >= previewLimit) {
                        break;
                    }
                }
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("geoPackage", true);
            metadata.put("tableName", schema.tableName());
            metadata.put("tableType", schema.features() ? "FEATURES" : "ATTRIBUTES");
            metadata.put("previewGeometryExcluded", schema.geometry() != null);
            if (schema.geometry() != null) {
                metadata.put("geometryField", schema.geometry().columnName());
                metadata.put("geometryKind", schema.geometry().kind().name());
                metadata.put("crsAuthority", schema.geometry().crs().authority());
                metadata.put("crsCode", schema.geometry().crs().code());
                metadata.put("coordinateDimension", "XY");
            }
            return new ParseResult(
                    fields, preview, validateAll && count > preview.size(), true, metadata, count
            );
        } catch (GeoPackageException exception) {
            throw new FileDatasetParsingException(exception.getMessage(), exception);
        }
    }
}
