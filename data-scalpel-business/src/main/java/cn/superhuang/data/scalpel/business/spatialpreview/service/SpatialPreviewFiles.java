package cn.superhuang.data.scalpel.business.spatialpreview.service;

import cn.superhuang.data.scalpel.business.model.service.SpatialPreviewPngRenderer;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewViewport;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.index.strtree.STRtree;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.DigestOutputStream;
import java.util.*;

/** Versioned append-only WKB and fixed-width index; no source geometry retained in heap. */
final class SpatialPreviewFiles {
    static final long DATA_MAGIC=0x53505631574b4200L, INDEX_MAGIC=0x5350563149445800L;
    static final double WORLD=20_037_508.342789244;
    static final String[] NAMES={"geometry.wkb", "geometry.idx", "overview.png"};
    private static final GeometryFactory WKB_GEOMETRY_FACTORY=new GeometryFactory(
            new PrecisionModel(),0,PackedCoordinateSequenceFactory.DOUBLE_FACTORY);
    private static final CoordinateSequenceFilter VALIDATE_COORDINATES=new CoordinateSequenceFilter() {
        @Override public void filter(CoordinateSequence sequence,int index) {
            double x=sequence.getX(index),y=sequence.getY(index);
            if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>WORLD+1||Math.abs(y)>WORLD+1)
                throw new IllegalArgumentException("坐标超出地图有效范围，请确认来源空间参考");
        }
        @Override public boolean isDone() { return false; }
        @Override public boolean isGeometryChanged() { return false; }
    };
    private SpatialPreviewFiles() { }

    static final class Writer implements SpatialPreviewSource.Sink, AutoCloseable {
        private final DataOutputStream data, index;
        private final WKBReader reader=new WKBReader(WKB_GEOMETRY_FACTORY);
        private final org.locationtech.jts.io.WKBWriter encoder=new org.locationtech.jts.io.WKBWriter(2);
        private final MessageDigest dataDigest=digest(), indexDigest=digest();
        private String dataHash, indexHash;
        private boolean closed;
        private final long deadline;
        final Envelope envelope=new Envelope();
        long count, empty, bytes, scanned;
        Writer(Path dir, long deadline) throws IOException {
            Files.createDirectories(dir);
            this.deadline=deadline;
            data=new DataOutputStream(new BufferedOutputStream(new DigestOutputStream(Files.newOutputStream(dir.resolve(NAMES[0])),dataDigest),1024*1024));
            try {
                index=new DataOutputStream(new BufferedOutputStream(new DigestOutputStream(Files.newOutputStream(dir.resolve(NAMES[1])),indexDigest),1024*1024));
                try { data.writeLong(DATA_MAGIC); index.writeLong(INDEX_MAGIC); }
                catch(IOException e) { try { index.close(); } catch(IOException close) { e.addSuppressed(close); } throw e; }
            } catch(IOException e) { try { data.close(); } catch(IOException close) { e.addSuppressed(close); } throw e; }
        }
        private void next() {
            if(closed) throw new IllegalStateException("预览副本已关闭");
            SpatialPreviewBudget.deadline(deadline);
            if(++scanned>SpatialPreviewBudget.FEATURES) throw new SpatialPreviewBudget.Exceeded("来源超过 100 万条直接预览上限，请使用已发布的空间服务");
        }
        @Override public void accept(byte[] wkb) {
            next();
            if(wkb==null) { empty++; return; }
            checkBytes(wkb.length);
            try { append(reader.read(wkb),wkb); }
            catch(org.locationtech.jts.io.ParseException e) { throw new IllegalArgumentException("来源含损坏的几何数据",e); }
        }
        @Override public void acceptGeometry(org.locationtech.jts.geom.Geometry geometry) {
            next();
            if(geometry==null) { empty++; return; }
            append(geometry,null);
        }
        private void checkBytes(int length) {
            if(length>SpatialPreviewBudget.FEATURE_BYTES) throw new SpatialPreviewBudget.Exceeded("单个几何超过 16 MiB 预览上限");
            if(bytes+length>SpatialPreviewBudget.DATA_BYTES) throw new SpatialPreviewBudget.Exceeded("几何数据超过 2 GiB 预览上限，请使用已发布的空间服务");
        }
        private void append(org.locationtech.jts.geom.Geometry geometry, byte[] wkb) {
            try {
                if(geometry.isEmpty()) { empty++; return; }
                if(geometry.getNumPoints()>SpatialPreviewBudget.FEATURE_COORDINATES)
                    throw new SpatialPreviewBudget.Exceeded("单个几何超过 100 万坐标预览上限");
                var box=geometry.getEnvelopeInternal();
                // Access ordinates directly: CoordinateFilter would materialize packed coordinates.
                geometry.apply(VALIDATE_COORDINATES);
                if(wkb==null) { wkb=encoder.write(geometry); checkBytes(wkb.length); }
                index.writeLong(bytes+8); index.writeInt(wkb.length); index.writeInt(geometry.getNumPoints());
                index.writeDouble(box.getMinX()); index.writeDouble(box.getMinY());
                index.writeDouble(box.getMaxX()); index.writeDouble(box.getMaxY());
                data.write(wkb); bytes+=wkb.length; count++; envelope.expandToInclude(box);
            } catch(IOException e) { throw new UncheckedIOException(e); }
        }
        @Override public void close() throws IOException {
            if(closed) return;
            closed=true;
            try(data; index) { /* Both streams must flush successfully before their hashes can be published. */ }
            dataHash=HexFormat.of().formatHex(dataDigest.digest());
            indexHash=HexFormat.of().formatHex(indexDigest.digest());
        }
        String dataHash() { if(dataHash==null) throw new IllegalStateException("预览副本尚未完整写入"); return dataHash; }
        String indexHash() { if(indexHash==null) throw new IllegalStateException("预览索引尚未完整写入"); return indexHash; }
    }

    record Entry(long offset, int length, int coordinates) { }
    record Selection(List<Entry> entries, boolean exceeded) { }
    static final class Index {
        private final STRtree tree=new STRtree();
        Index(Path dir, long expectedCount, long expectedBytes) throws IOException {
            Path path=dir.resolve(NAMES[1]);
            if(Files.size(path)!=8+48*expectedCount || Files.size(dir.resolve(NAMES[0]))!=8+expectedBytes)
                throw new IOException("预览副本长度不完整");
            try(DataInputStream input=new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
                if(input.readLong()!=INDEX_MAGIC) throw new IOException("预览索引版本无效");
                for(long n=0;n<expectedCount;n++) {
                    long offset=input.readLong(); int length=input.readInt(), coordinates=input.readInt();
                    double x1=input.readDouble(), y1=input.readDouble(), x2=input.readDouble(), y2=input.readDouble();
                    if(length<1||length>SpatialPreviewBudget.FEATURE_BYTES||offset<8||offset+length>expectedBytes+8
                            ||coordinates<1||coordinates>SpatialPreviewBudget.FEATURE_COORDINATES)
                        throw new IOException("预览索引记录无效");
                    tree.insert(new Envelope(x1,x2,y1,y2),new Entry(offset,length,coordinates));
                }
            }
            tree.build();
        }
        Selection select(SpatialPreviewViewport viewport) {
            List<Entry> found=new ArrayList<>();
            long[] totals={0,0};
            boolean[] exceeded={false};
            tree.query(new Envelope(viewport.minX(),viewport.maxX(),viewport.minY(),viewport.maxY()), item -> {
                if(exceeded[0]) return;
                Entry entry=(Entry)item;
                totals[0]+=entry.length(); totals[1]+=entry.coordinates();
                if(totals[0]>SpatialPreviewBudget.DETAIL_BYTES||totals[1]>SpatialPreviewBudget.DETAIL_COORDINATES) {
                    exceeded[0]=true; found.clear();
                } else found.add(entry);
            });
            found.sort(Comparator.comparingLong(Entry::offset));
            return new Selection(found,exceeded[0]);
        }
    }

    static byte[] render(Path dir, SpatialPreviewViewport viewport, List<Entry> selected, long coordinates, long deadline) throws IOException {
        return render(dir,viewport,selected,coordinates,deadline,() -> false);
    }
    static byte[] render(Path dir, SpatialPreviewViewport viewport, List<Entry> selected, long coordinates, long deadline,
                         java.util.function.BooleanSupplier cancelled) throws IOException {
        // Full overviews follow the append order: buffer the scan instead of seeking once per feature.
        try(DataInputStream sequential=selected==null
                    ? new DataInputStream(new BufferedInputStream(Files.newInputStream(dir.resolve(NAMES[0])),1024*1024)) : null;
            RandomAccessFile random=selected==null ? null : new RandomAccessFile(dir.resolve(NAMES[0]).toFile(),"r");
            DataInputStream index=new DataInputStream(new BufferedInputStream(Files.newInputStream(dir.resolve(NAMES[1]))))) {
            DataInput data=sequential==null ? random : sequential;
            if(data.readLong()!=DATA_MAGIC||index.readLong()!=INDEX_MAGIC) throw new IOException("预览副本版本无效");
            if((Files.size(dir.resolve(NAMES[1]))-8)%48!=0) throw new IOException("预览索引长度无效");
            long count=(Files.size(dir.resolve(NAMES[1]))-8)/48;
            Iterator<Entry> chosen=selected==null ? null : selected.iterator();
            Iterable<byte[]> rows=() -> new Iterator<>() {
                long position, offset=8;
                public boolean hasNext() { return chosen==null ? position<count : chosen.hasNext(); }
                public byte[] next() {
                    if(cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException("视口已更新");
                    if(!hasNext()) throw new NoSuchElementException();
                    SpatialPreviewBudget.deadline(deadline);
                    try {
                        Entry entry;
                        if(chosen==null) {
                            entry=new Entry(index.readLong(),index.readInt(),index.readInt()); index.skipNBytes(32); position++;
                        } else entry=chosen.next();
                        if(entry.length()<1||entry.length()>SpatialPreviewBudget.FEATURE_BYTES)
                            throw new IOException("预览索引记录无效");
                        if(random!=null) random.seek(entry.offset());
                        else if(entry.offset()!=offset) throw new IOException("预览索引偏移无效");
                        byte[] wkb=new byte[entry.length()]; data.readFully(wkb); offset+=wkb.length; return wkb;
                    } catch(IOException e) { throw new UncheckedIOException(e); }
                }
            };
            var result=new SpatialPreviewPngRenderer().render(rows,viewport,coordinates,0,false,deadline);
            SpatialPreviewBudget.deadline(deadline);
            if(result.truncated()||result.skippedCount()>0) throw new IOException("预览图未完整绘制，未发布该图");
            if(result.png().length>4*1024*1024) throw new SpatialPreviewBudget.Exceeded("图片超过 4 MiB 预览上限");
            return result.png();
        }
    }
    static SpatialPreviewViewport overview(Envelope box) {
        if(box.isNull()) return new SpatialPreviewViewport(-WORLD,-WORLD,WORLD,WORLD,1200,900);
        double dx=Math.max(box.getWidth()*1.04,10), dy=Math.max(box.getHeight()*1.04,10);
        double cx=(box.getMinX()+box.getMaxX())/2, cy=(box.getMinY()+box.getMaxY())/2;
        return new SpatialPreviewViewport(Math.max(-WORLD,cx-dx/2),Math.max(-WORLD,cy-dy/2),
                Math.min(WORLD,cx+dx/2),Math.min(WORLD,cy+dy/2),1200,900);
    }
    static String hash(Path file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=Files.newInputStream(file)) {
            byte[] buffer=new byte[64*1024]; int n;
            while((n=in.read(buffer))!=-1) digest.update(buffer,0,n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static String hash(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    static String key(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
}
