package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoParquetFileDatasetParserTest {

    @TempDir
    Path temporaryDirectory;

    private final GeoParquetFileDatasetParser parser = new GeoParquetFileDatasetParser(new ObjectMapper());

    @Test
    void readsFooterSchemaDefaultsMissingCrsAndDoesNotExposeWkbInPreview() throws Exception {
        Path path = write("""
                {"version":"1.1.0","primary_column":"geometry","columns":{"geometry":
                {"encoding":"WKB","geometry_types":["Point"]}}}
                """, List.of(point(116.3, 39.9)));

        FileDatasetParser.ParseResult result = parse(path, 1000);

        assertEquals(1, result.rowCount());
        assertFalse(result.truncated());
        assertEquals(List.of("id", "geometry"), result.fields().stream().map(FileDatasetParser.Field::name).toList());
        assertEquals(PlatformDataType.GEOMETRY, result.fields().getLast().type().type());
        assertEquals(GeometryKind.POINT, result.fields().getLast().type().geometry().kind());
        assertEquals(4326, result.fields().getLast().type().geometry().crs().code());
        assertEquals(CoordinateDimension.XY, result.fields().getLast().type().geometry().dimension());
        assertEquals(1, result.rows().size());
        assertEquals(1, result.rows().getFirst().get("id"));
        assertFalse(result.rows().getFirst().containsKey("geometry"));
    }

    @Test
    void rejectsUnknownCrsAndWkbDimensionAfterPreviewWindow() throws Exception {
        Path unknownCrs = write("""
                {"version":"1.1.0","primary_column":"geometry","columns":{"geometry":
                {"encoding":"WKB","geometry_types":["Point"],"crs":null}}}
                """, List.of(point(0, 0)));
        FileDatasetParsingException crsError = assertThrows(
                FileDatasetParsingException.class, () -> parse(unknownCrs, 1)
        );
        assertTrue(crsError.getMessage().contains("CRS 未定义"));

        Path dimensional = write("""
                {"version":"1.1.0","primary_column":"geometry","columns":{"geometry":
                {"encoding":"WKB","geometry_types":["Point"]}}}
                """, List.of(point(0, 0), pointZ(1, 1, 5)));
        FileDatasetParsingException dimensionError = assertThrows(
                FileDatasetParsingException.class, () -> parse(dimensional, 1)
        );
        assertTrue(dimensionError.getMessage().contains("第 2 条记录"));
        assertTrue(dimensionError.getMessage().contains("二维 XY"));
    }

    @Test
    void acceptsEmptyPointAndRequiresPrimaryColumn() throws Exception {
        Path empty = write("""
                {"version":"1.0.0","primary_column":"geometry","columns":{"geometry":
                {"encoding":"WKB","geometry_types":["Point"]}}}
                """, List.of(emptyPoint()));
        assertEquals(GeometryKind.POINT, parse(empty, 1).fields().getLast().type().geometry().kind());

        Path missingPrimaryColumn = write("""
                {"version":"1.1.0","columns":{"geometry":{"encoding":"WKB","geometry_types":["Point"]}}}
                """, List.of(point(0, 0)));
        FileDatasetParsingException error = assertThrows(
                FileDatasetParsingException.class, () -> parse(missingPrimaryColumn, 1)
        );
        assertTrue(error.getMessage().contains("primary_column"));
    }

    private FileDatasetParser.ParseResult parse(Path path, int previewLimit) throws Exception {
        return parser.validate(
                new FileDatasetParseSource.LocalFile(path),
                new FileDatasetParsingConfiguration.GeoParquet(),
                previewLimit
        );
    }

    private Path write(String geoMetadata, List<byte[]> geometryValues) throws Exception {
        Path file = temporaryDirectory.resolve("input-" + geometryValues.size() + "-" + System.nanoTime() + ".parquet");
        MessageType schema = Types.buildMessage()
                .required(PrimitiveType.PrimitiveTypeName.INT32).named("id")
                .optional(PrimitiveType.PrimitiveTypeName.BINARY).named("geometry")
                .named("geo");
        SimpleGroupFactory groups = new SimpleGroupFactory(schema);
        try (ParquetWriter<Group> writer = ExampleParquetWriter.builder(new LocalOutputFile(file))
                .withType(schema)
                .withExtraMetaData(Map.of("geo", geoMetadata.replaceAll("\\s+", "")))
                .build()) {
            for (int index = 0; index < geometryValues.size(); index++) {
                writer.write(groups.newGroup()
                        .append("id", index + 1)
                        .append("geometry", Binary.fromConstantByteArray(geometryValues.get(index))));
            }
        }
        return file;
    }

    private static byte[] point(double x, double y) {
        return ByteBuffer.allocate(1 + Integer.BYTES + Double.BYTES * 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) 1)
                .putInt(1)
                .putDouble(x)
                .putDouble(y)
                .array();
    }

    private static byte[] emptyPoint() {
        return point(Double.NaN, Double.NaN);
    }

    private static byte[] pointZ(double x, double y, double z) {
        return ByteBuffer.allocate(1 + Integer.BYTES + Double.BYTES * 3)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) 1)
                .putInt(1001)
                .putDouble(x)
                .putDouble(y)
                .putDouble(z)
                .array();
    }
}
