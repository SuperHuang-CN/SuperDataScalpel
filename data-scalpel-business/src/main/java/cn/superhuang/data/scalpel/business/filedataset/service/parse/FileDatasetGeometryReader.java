package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.spatialpreview.service.SpatialPreviewBudget;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadOptions;
import cn.superhuang.data.scalpel.filegdb.model.geometry.*;
import cn.superhuang.data.scalpel.shapefile.ShapefileReadOptions;
import cn.superhuang.data.scalpel.shapefile.model.geometry.*;
import cn.superhuang.data.scalpel.dialect.geopackage.GeoPackageReader;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.algorithm.PointLocation;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.apache.parquet.hadoop.api.ReadSupport;
import org.apache.parquet.hadoop.api.InitContext;
import org.apache.parquet.io.LocalInputFile;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntToDoubleFunction;

/** Full, forward-only geometry reads; deliberately separate from bounded attribute samples. */
final class FileDatasetGeometryReader {
    private static final GeometryFactory GF=new GeometryFactory();
    private FileDatasetGeometryReader() { }

    static void read(FileDatasetParseSource source, FileDatasetFormat format,
            FileDatasetParsingConfiguration configuration, String field, ObjectMapper mapper,
            Consumer<Geometry> consumer) throws IOException {
        switch(format) {
            case GDB -> {
                var database=FileDatasetParseSource.requireFileGdb(source);
                String layer=((FileDatasetParsingConfiguration.Gdb)configuration).layerId();
                try(var cursor=database.openCursor(layer,FileGdbReadOptions.limit(SpatialPreviewBudget.FEATURES+1))) {
                    while(cursor.hasNext()) consumer.accept(gdb(cursor.next().geometry()));
                }
            }
            case SHP -> {
                try(var cursor=FileDatasetParseSource.requireShapefile(source).openCursor(ShapefileReadOptions.limit(SpatialPreviewBudget.FEATURES+1))) {
                    while(cursor.hasNext()) consumer.accept(shp(cursor.next().geometry()));
                }
            }
            case GEOJSON, GEOJSONL -> json(FileDatasetParseSource.requireStream(source),format,mapper,consumer);
            case GPKG -> {
                var decoder=new WKBReader(GF);
                try(var reader=GeoPackageReader.open(FileDatasetParseSource.requireLocalFile(source))) {
                    var schema=reader.schema(((FileDatasetParsingConfiguration.GeoPackage)configuration).tableName());
                    if(schema.geometry()==null || !schema.geometry().columnName().equals(field))
                        throw new IllegalArgumentException("GeoPackage 空间字段不存在");
                    try(var cursor=reader.openGeometryRows(schema)) {
                        Map<String,Object> row;
                        while((row=cursor.next())!=null) consumer.accept(wkb((byte[])row.get(field),decoder));
                    }
                }
            }
            case GEOPARQUET -> {
                var decoder=new WKBReader(GF);
                var path=FileDatasetParseSource.requireLocalFile(source);
                GeoParquetFileDatasetParser.requireUncompressedSizeWithin(path,SpatialPreviewBudget.DATA_BYTES);
                var footer=org.apache.parquet.hadoop.ParquetFileReader.readFooter(new LocalInputFile(path),
                        org.apache.parquet.format.converter.ParquetMetadataConverter.NO_FILTER);
                var schema=footer.getFileMetaData().getSchema();
                if(!schema.containsField(field)) throw new IllegalArgumentException("GeoParquet 空间字段不存在");
                var geometryType=schema.getType(field);
                if(!geometryType.isPrimitive()
                        || geometryType.asPrimitiveType().getPrimitiveTypeName()!=org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.BINARY
                        || geometryType.getRepetition()==org.apache.parquet.schema.Type.Repetition.REPEATED)
                    throw new IllegalArgumentException("GeoParquet 空间字段必须是单值 WKB 列");
                // Keep the physical Type all the way into the read context: schema text parsing
                // cannot represent every legal column name (spaces, semicolons, Unicode, etc.).
                var projectedSchema=new org.apache.parquet.schema.MessageType(schema.getName(),geometryType);
                try(var reader=new ParquetReader.Builder<Group>(new LocalInputFile(path)) {
                    @Override protected ReadSupport<Group> getReadSupport() {
                        return new GroupReadSupport() {
                            @Override public ReadContext init(InitContext context) { return new ReadContext(projectedSchema); }
                        };
                    }
                }.build()) {
                    Group row;
                    while((row=reader.read())!=null) {
                        if(row.getFieldRepetitionCount(field)==0) consumer.accept(null);
                        else {
                            var binary=row.getBinary(field,0);
                            if(binary.length()>SpatialPreviewBudget.FEATURE_BYTES) throw new SpatialPreviewBudget.Exceeded("单个几何超过 16 MiB");
                            consumer.accept(wkb(binary.getBytes(),decoder));
                        }
                    }
                }
            }
            default -> throw new IllegalArgumentException("此文件类型不支持空间预览");
        }
    }
    private static Geometry wkb(byte[] bytes,WKBReader decoder) {
        if(bytes==null) return null;
        if(bytes.length>SpatialPreviewBudget.FEATURE_BYTES) throw new SpatialPreviewBudget.Exceeded("单个几何超过 16 MiB");
        try { return decoder.read(bytes); }
        catch(org.locationtech.jts.io.ParseException e) { throw new IllegalArgumentException("文件几何损坏",e); }
    }
    private static Geometry gdb(FileGdbGeometry geometry) {
        if(geometry==null) return null;
        if(geometry.hasZ()||geometry.hasM()) throw new IllegalArgumentException("空间预览仅支持二维 XY 几何");
        return switch(geometry) {
            case FileGdbPoint p -> GF.createPoint(new Coordinate(p.x(),p.y()));
            case FileGdbMultiPoint p -> GF.createMultiPointFromCoords(coordinates(p.coordinates().size(),p.coordinates()::x,p.coordinates()::y));
            case FileGdbPolyline p -> lines(coordinates(p.coordinates().size(),p.coordinates()::x,p.coordinates()::y),p.pathPointCounts());
            case FileGdbPolygon p -> polygons(coordinates(p.coordinates().size(),p.coordinates()::x,p.coordinates()::y),p.ringPointCounts());
        };
    }
    private static Geometry shp(ShapefileGeometry geometry) {
        if(geometry==null) return null;
        if(geometry.hasZ()||geometry.hasM()) throw new IllegalArgumentException("空间预览仅支持二维 XY 几何");
        return switch(geometry) {
            case ShapefilePoint p -> GF.createPoint(new Coordinate(p.x(),p.y()));
            case ShapefileMultiPoint p -> GF.createMultiPointFromCoords(coordinates(p.coordinates().size(),p.coordinates()::x,p.coordinates()::y));
            case ShapefilePolyline p -> lines(coordinates(p.coordinates().size(),p.coordinates()::x,p.coordinates()::y),p.partPointCounts());
            case ShapefilePolygon p -> polygons(coordinates(p.coordinates().size(),p.coordinates()::x,p.coordinates()::y),p.ringPointCounts());
        };
    }
    private static Coordinate[] coordinates(int count,IntToDoubleFunction x,IntToDoubleFunction y) {
        if(count>SpatialPreviewBudget.FEATURE_COORDINATES) throw new SpatialPreviewBudget.Exceeded("单个几何超过 100 万坐标");
        Coordinate[] values=new Coordinate[count];
        for(int i=0;i<count;i++) values[i]=new Coordinate(x.applyAsDouble(i),y.applyAsDouble(i));
        return values;
    }
    private static Geometry lines(Coordinate[] values,int[] counts) {
        LineString[] lines=new LineString[counts.length]; int offset=0;
        for(int i=0;i<counts.length;i++) { lines[i]=GF.createLineString(Arrays.copyOfRange(values,offset,offset+counts[i])); offset+=counts[i]; }
        return GF.createMultiLineString(lines);
    }
    /** Ring containment supports arbitrary winding, multiple shells and islands inside holes. */
    static Geometry polygons(Coordinate[] values,int[] counts) {
        // Most parcel features contain one shell; no containment index or sorting is needed.
        if(counts.length==1 && counts[0]==values.length) return GF.createMultiPolygon(new Polygon[]{GF.createPolygon(values)});
        List<LinearRing> rings=new ArrayList<>(); int offset=0;
        for(int count:counts) { rings.add(GF.createLinearRing(Arrays.copyOfRange(values,offset,offset+count))); offset+=count; }
        rings.sort(Comparator.comparingDouble((LinearRing r) -> GF.createPolygon(r).getArea()).reversed());
        int[] parents=new int[rings.size()],depths=new int[rings.size()]; Arrays.fill(parents,-1);
        var tree=new org.locationtech.jts.index.strtree.STRtree();
        for(int i=0;i<rings.size();i++) tree.insert(rings.get(i).getEnvelopeInternal(),i);
        tree.build();
        long comparisons=0;
        for(int i=0;i<rings.size();i++) {
            LinearRing ring=rings.get(i);
            for(Object item:tree.query(ring.getEnvelopeInternal())) {
                if(++comparisons>1_000_000) throw new SpatialPreviewBudget.Exceeded("单个几何的环关系过于复杂，请使用空间服务预览");
                int j=(Integer)item;
                if(j<i && j>parents[i] && rings.get(j).getEnvelopeInternal().covers(ring.getEnvelopeInternal())
                        && PointLocation.isInRing(ring.getCoordinateN(0),rings.get(j).getCoordinates())) parents[i]=j;
            }
            if(parents[i]>=0) depths[i]=depths[parents[i]]+1;
        }
        Map<Integer,List<LinearRing>> holesByShell=new HashMap<>();
        for(int i=0;i<rings.size();i++) if(depths[i]%2==1)
            holesByShell.computeIfAbsent(parents[i],key -> new ArrayList<>()).add(rings.get(i));
        List<Polygon> polygons=new ArrayList<>();
        for(int i=0;i<rings.size();i++) if(depths[i]%2==0) {
            List<LinearRing> holes=holesByShell.getOrDefault(i,List.of());
            polygons.add(GF.createPolygon(rings.get(i),holes.toArray(LinearRing[]::new)));
        }
        return GF.createMultiPolygon(polygons.toArray(Polygon[]::new));
    }
    private static void json(java.io.InputStream input,FileDatasetFormat format,ObjectMapper mapper,Consumer<Geometry> consumer) {
        var geometryReader=mapper.readerFor(JsonNode.class).without(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try(JsonParser parser=mapper.createParser(input)) {
            if(format==FileDatasetFormat.GEOJSONL) {
                while(parser.nextToken()!=null) feature(parser,geometryReader,consumer);
            } else {
                if(parser.nextToken()!=JsonToken.START_OBJECT) throw new IllegalArgumentException("GeoJSON 根节点无效");
                boolean found=false;
                while(parser.nextToken()!=JsonToken.END_OBJECT) {
                    if(parser.currentToken()==null) throw new IllegalArgumentException("GeoJSON 未完整结束");
                    String name=parser.currentName(); parser.nextToken();
                    if("features".equals(name)) {
                        if(parser.currentToken()!=JsonToken.START_ARRAY || found) throw new IllegalArgumentException("GeoJSON features 无效");
                        found=true;
                        while(parser.nextToken()!=JsonToken.END_ARRAY) feature(parser,geometryReader,consumer);
                    } else parser.skipChildren();
                }
                if(!found) throw new IllegalArgumentException("GeoJSON 缺少 features");
            }
        }
    }
    private static void feature(JsonParser parser,ObjectReader geometryReader,Consumer<Geometry> consumer) {
        if(parser.currentToken()!=JsonToken.START_OBJECT) throw new IllegalArgumentException("GeoJSON Feature 无效");
        Geometry geometry=null; boolean found=false;
        while(parser.nextToken()!=JsonToken.END_OBJECT) {
            if(parser.currentToken()==null) throw new IllegalArgumentException("GeoJSON Feature 未完整结束");
            String key=parser.currentName(); parser.nextToken();
            if("geometry".equals(key)) {
                if(found) throw new IllegalArgumentException("重复的 GeoJSON geometry"); found=true;
                long start=parser.currentTokenLocation().getByteOffset();
                var bounded=new tools.jackson.core.util.JsonParserDelegate(parser) {
                    int tokens;
                    @Override public JsonToken nextToken() {
                        JsonToken token=super.nextToken();
                        if(++tokens>4_000_000 || currentTokenLocation().getByteOffset()-start>32L*1024*1024)
                            throw new SpatialPreviewBudget.Exceeded("单个 GeoJSON 几何超过 32 MiB 文本或坐标读取预算");
                        return token;
                    }
                };
                geometry=jsonGeometry(geometryReader.readValue(bounded),new int[]{0},0);
            } else parser.skipChildren();
        }
        consumer.accept(geometry);
    }
    private static Geometry jsonGeometry(JsonNode node,int[] count,int depth) {
        if(node==null||node.isNull()) return null;
        if(depth>32) throw new IllegalArgumentException("几何集合嵌套过深");
        JsonNode c=node.get("coordinates");
        return switch(node.path("type").asString()) {
            case "Point" -> GF.createPoint(coordinate(c,count));
            case "MultiPoint" -> GF.createMultiPointFromCoords(jsonCoordinates(c,count));
            case "LineString" -> GF.createLineString(jsonCoordinates(c,count));
            case "MultiLineString" -> {
                LineString[] lines=new LineString[c.size()];
                for(int i=0;i<c.size();i++) lines[i]=GF.createLineString(jsonCoordinates(c.get(i),count));
                yield GF.createMultiLineString(lines);
            }
            case "Polygon" -> jsonPolygon(c,count);
            case "MultiPolygon" -> {
                Polygon[] polygons=new Polygon[c.size()];
                for(int i=0;i<c.size();i++) polygons[i]=jsonPolygon(c.get(i),count);
                yield GF.createMultiPolygon(polygons);
            }
            case "GeometryCollection" -> {
                JsonNode members=node.path("geometries"); Geometry[] values=new Geometry[members.size()];
                for(int i=0;i<values.length;i++) values[i]=jsonGeometry(members.get(i),count,depth+1);
                yield GF.createGeometryCollection(values);
            }
            default -> throw new IllegalArgumentException("不支持的 GeoJSON 几何类型");
        };
    }
    private static Polygon jsonPolygon(JsonNode c,int[] count) {
        if(c.isEmpty()) return GF.createPolygon();
        LinearRing shell=GF.createLinearRing(jsonCoordinates(c.get(0),count));
        LinearRing[] holes=new LinearRing[c.size()-1];
        for(int i=1;i<c.size();i++) holes[i-1]=GF.createLinearRing(jsonCoordinates(c.get(i),count));
        return GF.createPolygon(shell,holes);
    }
    private static Coordinate[] jsonCoordinates(JsonNode node,int[] count) {
        if(node.size()+count[0]>SpatialPreviewBudget.FEATURE_COORDINATES) throw new SpatialPreviewBudget.Exceeded("单个几何超过 100 万坐标");
        Coordinate[] result=new Coordinate[node.size()];
        for(int i=0;i<result.length;i++) result[i]=coordinate(node.get(i),count);
        return result;
    }
    private static Coordinate coordinate(JsonNode node,int[] count) {
        if(++count[0]>SpatialPreviewBudget.FEATURE_COORDINATES) throw new SpatialPreviewBudget.Exceeded("单个几何超过 100 万坐标");
        if(node==null||node.size()!=2||!node.get(0).isNumber()||!node.get(1).isNumber()) throw new IllegalArgumentException("预览只支持二维 XY 坐标");
        return new Coordinate(node.get(0).asDouble(),node.get(1).asDouble());
    }
}
