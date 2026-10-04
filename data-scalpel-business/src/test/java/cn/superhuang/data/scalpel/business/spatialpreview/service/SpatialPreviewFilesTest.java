package cn.superhuang.data.scalpel.business.spatialpreview.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKBWriter;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewViewport;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class SpatialPreviewFilesTest {
    @TempDir Path directory;
    private final GeometryFactory factory=new GeometryFactory();
    private byte[] point(int x,int y) { return new WKBWriter().write(factory.createPoint(new Coordinate(x,y))); }

    @Test void streamingHashesMatchCompleteFilesAcrossBufferFlushesAndOnlyPublishAfterClose() throws Exception {
        var writer=new SpatialPreviewFiles.Writer(directory,Long.MAX_VALUE);
        assertThrows(IllegalStateException.class,writer::dataHash);
        assertThrows(IllegalStateException.class,writer::indexHash);
        try(writer) {
            for(int n=0;n<60_000;n++) writer.acceptGeometry(factory.createPoint(new Coordinate(n,1)));
            // Both one-MiB buffers have flushed at least once, but the final buffered suffix still
            // belongs to the artifact; publishing a digest now would certify incomplete data.
            assertTrue(writer.bytes>1024*1024);
            assertThrows(IllegalStateException.class,writer::dataHash);
            assertThrows(IllegalStateException.class,writer::indexHash);
        }
        assertEquals(completeHash(directory.resolve("geometry.wkb")),writer.dataHash());
        assertEquals(completeHash(directory.resolve("geometry.idx")),writer.indexHash());
        String dataHash=writer.dataHash(),indexHash=writer.indexHash();
        writer.close();
        assertEquals(dataHash,writer.dataHash());assertEquals(indexHash,writer.indexHash());
        assertThrows(IllegalStateException.class,() -> writer.accept(point(1,1)));
        assertThrows(IllegalStateException.class,() -> writer.acceptGeometry(factory.createPoint()));
    }

    @Test void directGeometryProducesIdenticalArtifactsToWkbIncludingHolesCollectionsAndEmptyValues() throws Exception {
        Path binaryDirectory=directory.resolve("binary"),directDirectory=directory.resolve("direct");
        Geometry polygon=new org.locationtech.jts.io.WKTReader().read(
                "POLYGON ((0 0,10 0,10 10,0 10,0 0),(2 2,2 4,4 4,4 2,2 2))");
        Geometry[] geometries={factory.createPoint(new Coordinate(1,1)),polygon,
                factory.createGeometryCollection(new Geometry[]{polygon,factory.createLineString(new Coordinate[]{
                        new Coordinate(20,20),new Coordinate(30,30)})}),null,factory.createPoint(),factory.createPolygon()};
        var binary=new SpatialPreviewFiles.Writer(binaryDirectory,Long.MAX_VALUE);
        var direct=new SpatialPreviewFiles.Writer(directDirectory,Long.MAX_VALUE);
        var encoder=new WKBWriter(2);
        try(binary;direct) {
            for(Geometry geometry:geometries) {
                binary.accept(geometry==null?null:encoder.write(geometry));
                direct.acceptGeometry(geometry);
            }
        }
        assertEquals(3,direct.count);assertEquals(3,direct.empty);assertEquals(6,direct.scanned);
        assertEquals(binary.bytes,direct.bytes);assertEquals(binary.envelope,direct.envelope);
        assertEquals(binary.dataHash(),direct.dataHash());assertEquals(binary.indexHash(),direct.indexHash());
        assertArrayEquals(Files.readAllBytes(binaryDirectory.resolve("geometry.wkb")),Files.readAllBytes(directDirectory.resolve("geometry.wkb")));
        assertArrayEquals(Files.readAllBytes(binaryDirectory.resolve("geometry.idx")),Files.readAllBytes(directDirectory.resolve("geometry.idx")));
        var viewport=SpatialPreviewFiles.overview(direct.envelope);
        assertArrayEquals(SpatialPreviewFiles.render(binaryDirectory,viewport,null,Long.MAX_VALUE,Long.MAX_VALUE),
                SpatialPreviewFiles.render(directDirectory,viewport,null,Long.MAX_VALUE,Long.MAX_VALUE));
    }

    @Test void directGeometryRetainsDeadlineCoordinateAndPointCountGuards() throws Exception {
        try(var expired=new SpatialPreviewFiles.Writer(directory,0)) {
            assertThrows(SpatialPreviewBudget.Exceeded.class,() -> expired.acceptGeometry(factory.createPoint(new Coordinate(1,1))));
        }
        try(var writer=new SpatialPreviewFiles.Writer(directory,Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException.class,() -> writer.accept(new byte[]{1,1,0}));
            assertThrows(IllegalArgumentException.class,() -> writer.acceptGeometry(factory.createPoint(new Coordinate(30_000_000,0))));
            assertThrows(IllegalArgumentException.class,() -> writer.acceptGeometry(factory.createPoint(new Coordinate(1,Double.POSITIVE_INFINITY))));
            Coordinate[] coordinates=new Coordinate[SpatialPreviewBudget.FEATURE_COORDINATES+1];
            java.util.Arrays.fill(coordinates,new Coordinate(0,0));
            assertThrows(SpatialPreviewBudget.Exceeded.class,() -> writer.acceptGeometry(factory.createLineString(coordinates)));
            assertEquals(0,writer.count);assertEquals(0,writer.bytes);
        }
    }

    @Test void corruptDataHeaderAndSequentialIndexOffsetAreRejectedInsteadOfPublishingAnImage() throws Exception {
        var writer=new SpatialPreviewFiles.Writer(directory,Long.MAX_VALUE);
        try(writer) { writer.acceptGeometry(factory.createPoint(new Coordinate(1,1))); }
        try(var index=new java.io.RandomAccessFile(directory.resolve("geometry.idx").toFile(),"rw")) {
            index.seek(8);index.writeLong(9);
        }
        assertThrows(java.io.UncheckedIOException.class,() -> SpatialPreviewFiles.render(directory,
                SpatialPreviewFiles.overview(writer.envelope),null,Long.MAX_VALUE,Long.MAX_VALUE));
        assertThrows(java.io.IOException.class,() -> new SpatialPreviewFiles.Index(directory,writer.count,writer.bytes));
        try(var data=new java.io.RandomAccessFile(directory.resolve("geometry.wkb").toFile(),"rw")) { data.writeLong(0); }
        assertThrows(java.io.IOException.class,() -> SpatialPreviewFiles.render(directory,
                SpatialPreviewFiles.overview(writer.envelope),null,Long.MAX_VALUE,Long.MAX_VALUE));
    }

    private static String completeHash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    @Test void fullOverviewAndIndexedDetailContainAllFeaturesWithoutPrefixTruncation() throws Exception {
        var writer=new SpatialPreviewFiles.Writer(directory,System.nanoTime()+TimeUnit.SECONDS.toNanos(10));
        try(writer) { for(int i=0;i<6000;i++) writer.accept(point(i%100,i/100)); writer.accept(null); }
        assertEquals(6000,writer.count);assertEquals(1,writer.empty);
        var index=new SpatialPreviewFiles.Index(directory,writer.count,writer.bytes);
        var selection=index.select(new SpatialPreviewViewport(0,0,10,10,256,256));
        assertFalse(selection.exceeded());assertEquals(121,selection.entries().size());
        var png=SpatialPreviewFiles.render(directory,SpatialPreviewFiles.overview(writer.envelope),null,Long.MAX_VALUE,System.nanoTime()+TimeUnit.SECONDS.toNanos(10));
        var image=ImageIO.read(new ByteArrayInputStream(png));assertEquals(1200,image.getWidth());
        assertTrue(png.length<1024*1024);
    }
    @Test void deadlineAndMalformedCoordinateFailTheWholePreparation() throws Exception {
        try(var writer=new SpatialPreviewFiles.Writer(directory,0)) {
            assertThrows(SpatialPreviewBudget.Exceeded.class,() -> writer.accept(point(0,0)));
        }
        try(var writer=new SpatialPreviewFiles.Writer(directory,Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException.class,() -> writer.accept(point(30_000_000,1)));
            assertEquals(0,writer.count);
        }
    }
    @Test void indexCorruptionIsNotSilentlyRendered() throws Exception {
        var writer=new SpatialPreviewFiles.Writer(directory,Long.MAX_VALUE);
        try(writer) { writer.accept(point(1,1)); }
        Files.write(directory.resolve("geometry.idx"),new byte[]{1,2,3});
        assertThrows(java.io.IOException.class,() -> new SpatialPreviewFiles.Index(directory,writer.count,writer.bytes));
    }
    @Test void emptySourceStillHasAnExplicitCompleteOverview() throws Exception {
        var writer=new SpatialPreviewFiles.Writer(directory,Long.MAX_VALUE);writer.close();
        byte[] png=SpatialPreviewFiles.render(directory,SpatialPreviewFiles.overview(writer.envelope),null,Long.MAX_VALUE,Long.MAX_VALUE);
        assertEquals(0,ImageIO.read(new ByteArrayInputStream(png)).getRGB(10,10));
    }
    @Test void sequentialOverviewMatchesIndexedRenderingAcrossBufferBoundaries() throws Exception {
        var writer=new SpatialPreviewFiles.Writer(directory,Long.MAX_VALUE);
        var wkb=new WKBWriter();
        try(writer) {
            for(int n=0;n<12000;n++) {
                double x=n%200, y=n/200;
                writer.accept(wkb.write(factory.createPolygon(new Coordinate[]{
                        new Coordinate(x,y),new Coordinate(x+.4,y),new Coordinate(x+.4,y+.4),
                        new Coordinate(x,y+.4),new Coordinate(x,y)})));
            }
        }
        assertTrue(writer.bytes>1024*1024);
        var viewport=SpatialPreviewFiles.overview(writer.envelope);
        var selection=new SpatialPreviewFiles.Index(directory,writer.count,writer.bytes).select(viewport);
        assertEquals(12000,selection.entries().size());
        assertArrayEquals(SpatialPreviewFiles.render(directory,viewport,selection.entries(),Long.MAX_VALUE,Long.MAX_VALUE),
                SpatialPreviewFiles.render(directory,viewport,null,Long.MAX_VALUE,Long.MAX_VALUE));
    }
}
