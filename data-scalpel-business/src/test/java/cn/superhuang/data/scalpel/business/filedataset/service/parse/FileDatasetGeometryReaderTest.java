package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.nio.file.Path;
import java.io.RandomAccessFile;
import org.junit.jupiter.api.io.TempDir;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.apache.parquet.io.LocalInputFile;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.Types;
import org.apache.parquet.schema.PrimitiveType;
import static org.junit.jupiter.api.Assertions.*;

class FileDatasetGeometryReaderTest {
    @TempDir Path directory;

    @Test void parquetReadsOnlyGeometryColumnPagesAndPreservesNulls() throws Exception {
        var schema=Types.buildMessage()
                .required(PrimitiveType.PrimitiveTypeName.BINARY).named("payload")
                .optional(PrimitiveType.PrimitiveTypeName.BINARY).named("geometry")
                .named("sample");
        Path file=directory.resolve("projected.parquet");
        var factory=new SimpleGroupFactory(schema);
        var geometry=new GeometryFactory().createPoint(new Coordinate(116,30));
        byte[] wkb=new org.locationtech.jts.io.WKBWriter(2).write(geometry);
        try(var writer=ExampleParquetWriter.builder(new LocalOutputFile(file)).withType(schema)
                .withDictionaryEncoding(false).build()) {
            writer.write(factory.newGroup().append("payload","large property").append("geometry",Binary.fromConstantByteArray(wkb)));
            writer.write(factory.newGroup().append("payload","unused property"));
        }
        var metadata=ParquetFileReader.readFooter(new LocalInputFile(file),
                org.apache.parquet.format.converter.ParquetMetadataConverter.NO_FILTER);
        long attributePage=metadata.getBlocks().getFirst().getColumns().getFirst().getStartingPos();
        try(var bytes=new RandomAccessFile(file.toFile(),"rw")) {
            bytes.seek(attributePage);
            // Invalid Thrift page header: full column reads fail; projection must never read it.
            bytes.write(new byte[8]);
        }
        var values=new ArrayList<Geometry>();
        FileDatasetGeometryReader.read(new FileDatasetParseSource.LocalFile(file),FileDatasetFormat.GEOPARQUET,
                new FileDatasetParsingConfiguration.GeoParquet(),"geometry",new ObjectMapper(),values::add);
        assertEquals(2,values.size());
        assertTrue(geometry.equalsExact(values.getFirst()));
        assertNull(values.getLast());
        assertThrows(Exception.class,() -> {
            try(var reader=new ParquetReader.Builder<Group>(new LocalInputFile(file)) {
                @Override protected org.apache.parquet.hadoop.api.ReadSupport<Group> getReadSupport() { return new GroupReadSupport(); }
            }.build()) { reader.read(); }
        });
    }

    @Test void singleShellFastPathPreservesPolygonCoordinatesAndWinding() {
        Coordinate[] points={new Coordinate(0,0),new Coordinate(0,10),new Coordinate(10,10),new Coordinate(10,0),new Coordinate(0,0)};
        Geometry geometry=FileDatasetGeometryReader.polygons(points,new int[]{5});
        assertEquals(100,geometry.getArea());assertEquals(1,geometry.getNumGeometries());
        assertArrayEquals(points,geometry.getCoordinates());assertTrue(geometry.isValid());
    }

    @Test void parquetProjectionPreservesLegalGeometryColumnNamesWithoutParsingSchemaText() throws Exception {
        String field="空间 shape; (XY)";
        var schema=Types.buildMessage()
                .required(PrimitiveType.PrimitiveTypeName.INT64).named("id")
                .optional(PrimitiveType.PrimitiveTypeName.BINARY).named(field)
                .named("sample");
        Path file=directory.resolve("column-names.parquet");
        var point=new GeometryFactory().createPoint(new Coordinate(116,30));
        try(var writer=ExampleParquetWriter.builder(new LocalOutputFile(file)).withType(schema).build()) {
            writer.write(new SimpleGroupFactory(schema).newGroup().append("id",1L)
                    .append(field,Binary.fromConstantByteArray(new org.locationtech.jts.io.WKBWriter(2).write(point))));
        }
        var values=new ArrayList<Geometry>();
        FileDatasetGeometryReader.read(new FileDatasetParseSource.LocalFile(file),FileDatasetFormat.GEOPARQUET,
                new FileDatasetParsingConfiguration.GeoParquet(),field,new ObjectMapper(),values::add);
        assertEquals(1,values.size());assertTrue(point.equalsExact(values.getFirst()));
    }
    @Test void multiShellHoleAndIslandRetainContainmentRegardlessOfWinding() {
        Coordinate[] points={new Coordinate(0,0),new Coordinate(10,0),new Coordinate(10,10),new Coordinate(0,10),new Coordinate(0,0),
                new Coordinate(2,2),new Coordinate(8,2),new Coordinate(8,8),new Coordinate(2,8),new Coordinate(2,2),
                new Coordinate(3,3),new Coordinate(4,3),new Coordinate(4,4),new Coordinate(3,4),new Coordinate(3,3)};
        Geometry geometry=FileDatasetGeometryReader.polygons(points,new int[]{5,5,5});
        assertEquals(65,geometry.getArea());assertEquals(2,geometry.getNumGeometries());
        assertTrue(geometry.isValid());
    }
    @Test void jsonAndLinesStreamTheSameCompleteGeometryAndSkipAttributes() throws Exception {
        String feature="{\"type\":\"Feature\",\"properties\":{\"x\":[1,2]},\"geometry\":{\"type\":\"Point\",\"coordinates\":[116,30]}}";
        for(var format:new FileDatasetFormat[]{FileDatasetFormat.GEOJSON,FileDatasetFormat.GEOJSONL}) {
            String json=format==FileDatasetFormat.GEOJSON?"{\"type\":\"FeatureCollection\",\"features\":["+feature+","+feature+"]}":feature+"\n"+feature;
            var values=new ArrayList<Geometry>();
            FileDatasetGeometryReader.read(new FileDatasetParseSource.Stream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))),format,
                    new FileDatasetParsingConfiguration.GeoJson(4326),"geometry",new ObjectMapper(),values::add);
            assertEquals(2,values.size());assertEquals(116,values.getFirst().getCoordinate().x);
        }
    }
    @Test void missingCrsIsRecoverableWithoutGuessing() {
        var exception=assertThrows(FileDatasetMissingCrsException.class,() -> FileDatasetCrsResolver.requireEpsg("PROJCS[\"unknown\"]",null,"test"));
        assertEquals("PROJCS[\"unknown\"]",exception.wkt());
    }
}
