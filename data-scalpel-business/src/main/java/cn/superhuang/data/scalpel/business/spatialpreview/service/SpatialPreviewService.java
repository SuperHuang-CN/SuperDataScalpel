package cn.superhuang.data.scalpel.business.spatialpreview.service;

import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import cn.superhuang.data.scalpel.business.spatialpreview.domain.SpatialPreviewState;
import cn.superhuang.data.scalpel.business.spatialpreview.repository.SpatialPreviewRepository;
import cn.superhuang.data.scalpel.business.spatialpreview.web.response.SpatialPreviewStatusResponse;
import cn.superhuang.data.scalpel.dialect.model.SpatialPreviewViewport;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** Bounded preparation and immutable generations shared by model and file previews. */
@Service
public class SpatialPreviewService {
    private static final Logger log=LoggerFactory.getLogger(SpatialPreviewService.class);
    private final SpatialPreviewRepository repository;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectProvider<FileObjectStorage> storage;
    private final Path root;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
            new SynchronousQueue<>(),Thread.ofPlatform().daemon().name("spatial-preview-prepare").factory());
    private final Semaphore preparation=new Semaphore(1), rendering=new Semaphore(1);
    private final ConcurrentHashMap<UUID,Integer> active=new ConcurrentHashMap<>();
    private void retain(UUID generation) { synchronized(active) { active.merge(generation,1,Integer::sum); } }
    private void release(UUID generation) { synchronized(active) { active.computeIfPresent(generation,(key,count) -> count==1?null:count-1); } }
    private UUID indexedGeneration;
    private SpatialPreviewFiles.Index index;
    private final LinkedHashMap<String,Image> images=new LinkedHashMap<>(16,.75f,true);
    private long imageBytes;
    private final LinkedHashMap<String,java.util.concurrent.atomic.AtomicLong> viewports=new LinkedHashMap<>(16,.75f,true);
    private java.util.function.BooleanSupplier cancellation(String source, UUID clientId, long sequence) {
        if(clientId==null) return () -> false;
        synchronized(viewports) {
            var latest=viewports.computeIfAbsent(source+":"+clientId,key -> new java.util.concurrent.atomic.AtomicLong(sequence));
            latest.accumulateAndGet(sequence,Math::max);
            while(viewports.size()>512) { var iterator=viewports.values().iterator(); iterator.next().set(Long.MAX_VALUE); iterator.remove(); }
            return () -> latest.get()!=sequence;
        }
    }

    public SpatialPreviewService(SpatialPreviewRepository repository, JdbcTemplate jdbc,
            PlatformTransactionManager manager, ObjectProvider<FileObjectStorage> storage,
            @Value("${data-scalpel.spatial-preview.directory:${java.io.tmpdir}/data-scalpel-spatial-preview}") String directory) {
        this.repository=repository; this.jdbc=jdbc; this.tx=new TransactionTemplate(manager); this.storage=storage;
        root=Path.of(directory).toAbsolutePath().normalize();
        try { Files.createDirectories(root); } catch(IOException e) { throw new UncheckedIOException(e); }
    }
    public static String fingerprint(String value) { return SpatialPreviewFiles.key(value); }

    public SpatialPreviewStatusResponse status(SpatialPreviewSource source) { return response(current(source)); }

    public SpatialPreviewStatusResponse prepare(SpatialPreviewSource source, boolean force) {
        SpatialPreviewState existing=current(source);
        if(source.updating() || preparing(existing) || (!force && displayable(existing)))
            return response(existing);
        if(!force && Set.of("LIMIT_EXCEEDED","UNSUPPORTED","FAILED").contains(existing.state)) return response(existing);
        if(!preparation.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"其他图层正在准备，请稍后重试");
        SpatialPreviewState claimed;
        boolean submitted=false;
        try {
            claimed=locked(source.key(), row -> {
                requireRevision(source);
                refresh(row,source);
                if(preparing(row) || (!force && displayable(row))) return null;
                row.reset(source.revision(),"PREPARING","正在读取数据并准备地图");
                row.observedAt=Instant.now(); row.leaseUntil=Instant.now().plusSeconds(300);
                if(source.estimatedRows()!=null && source.estimatedRows()>SpatialPreviewBudget.FEATURES) {
                    row.state="LIMIT_EXCEEDED"; row.message="来源超过 100 万条直接预览上限，请使用已发布的空间服务";
                    row.leaseUntil=null;
                }
                return row;
            },source.revision());
            if(claimed==null) return status(source);
            if(!"PREPARING".equals(claimed.state)) return response(claimed);
            retain(claimed.generation);
            UUID generation=claimed.generation;
            Instant observedAt=claimed.observedAt;
            try { worker.execute(() -> build(source,generation,observedAt)); submitted=true; }
            catch(RejectedExecutionException e) {
                release(claimed.generation); fail(source,claimed.generation,"FAILED","准备队列繁忙，请重新加载");
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"准备队列繁忙，请稍后重试");
            }
            return response(claimed);
        } finally { if(!submitted) preparation.release(); }
    }

    private void build(SpatialPreviewSource source, UUID generation, Instant observedAt) {
        Path dir=directory(generation);
        String prefix=prefix(generation);
        String stage="检查缓存空间";
        long started=System.nanoTime(), stageStarted=started;
        boolean published=false;
        try {
            cleanDisk(SpatialPreviewBudget.DATA_BYTES+64L*1024*1024);
            stage="读取来源数据";
            stageStarted=System.nanoTime();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
            SpatialPreviewFiles.Writer writer=new SpatialPreviewFiles.Writer(dir,deadline);
            try(writer) { source.reader().read(writer); SpatialPreviewBudget.deadline(deadline); }
            log.info("Spatial preview {} source read: {} ms, {} features, {} WKB bytes",generation,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-stageStarted),writer.count,writer.bytes);
            requireRevision(source);
            stage="生成完整概览";
            preparationMessage(source,generation,"数据读取完成，正在生成地图概览");
            stageStarted=System.nanoTime();
            var viewport=SpatialPreviewFiles.overview(writer.envelope);
            byte[] png=SpatialPreviewFiles.render(dir,viewport,null,Long.MAX_VALUE,System.nanoTime()+TimeUnit.SECONDS.toNanos(30));
            Files.write(dir.resolve("overview.png"),png);
            log.info("Spatial preview {} overview: {} ms, {} PNG bytes",generation,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-stageStarted),png.length);
            stage="保存预览副本";
            stageStarted=System.nanoTime();
            long storageDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
            String[] hashes={writer.dataHash(),writer.indexHash(),SpatialPreviewFiles.hash(png)};
            FileObjectStorage objects=requireStorage();
            // A complete overview is usable without waiting for the large recovery copy to upload.
            store(objects,prefix,dir,"overview.png","image/png",storageDeadline);
            requireRevision(source);
            Files.writeString(dir.resolve("complete"),source.revision());
            locked(source.key(),row -> {
                if(!row.generation.equals(generation)||!"PREPARING".equals(row.state))
                    throw new IllegalStateException("预览代次已被撤销");
                row.featureCount=writer.count; row.emptyCount=writer.empty; row.dataBytes=writer.bytes;
                row.bounds=bbox(viewport); row.dataHash=hashes[0]; row.indexHash=hashes[1]; row.imageHash=hashes[2];
                row.expiresAt=observedAt.plusSeconds(600);
                if(!row.expiresAt.isAfter(Instant.now())) throw new IllegalStateException("预览已过期");
                row.state="OVERVIEW_READY";
                row.message=writer.count==0?"没有可显示的空间要素":"地图已可浏览，正在保存预览副本";
                return null;
            },source.revision());
            published=true;
            log.info("Spatial preview {} first image ready: {} ms total",generation,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
            for(String name:List.of("geometry.wkb","geometry.idx"))
                store(objects,prefix,dir,name,"application/octet-stream",storageDeadline);
            log.info("Spatial preview {} artifact storage: {} ms",generation,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-stageStarted));
            requireRevision(source);
            locked(source.key(),row -> {
                if(!row.generation.equals(generation)||!"OVERVIEW_READY".equals(row.state)
                        ||row.expiresAt==null||!row.expiresAt.isAfter(Instant.now()))
                    throw new IllegalStateException("预览代次已被撤销或已过期");
                row.leaseUntil=null; row.state="READY";
                row.message=writer.count==0?"没有可显示的空间要素":"地图已就绪";
                return null;
            },source.revision());
            log.info("Spatial preview {} ready: {} ms total",generation,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
        } catch(Exception e) {
            log.warn("Spatial preview {} failed during {} after {} ms (total {} ms)",generation,stage,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-stageStarted),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started),e);
            String state=e instanceof SpatialPreviewBudget.Exceeded ? "LIMIT_EXCEEDED" : "FAILED";
            String message=e instanceof SpatialPreviewBudget.Exceeded || e instanceof IllegalArgumentException
                    ? e.getMessage() : "地图准备失败，请重新加载；详细原因可在运行日志查看";
            if(e.getMessage()!=null && e.getMessage().contains("超过")) { state="LIMIT_EXCEEDED"; message=e.getMessage(); }
            if(e instanceof SpatialPreviewBudget.Exceeded && message.startsWith("预览处理超过时间预算"))
                message=stage+"超过时间预算，请重新加载；多次失败可使用已发布的空间服务";
            // Failure to save the recovery copy does not invalidate an already complete map.
            boolean kept=false;
            if(published) {
                try {
                    requireRevision(source);
                    kept=Boolean.TRUE.equals(locked(source.key(),row -> {
                        if(!row.generation.equals(generation)||!"OVERVIEW_READY".equals(row.state)
                                ||row.expiresAt==null||!row.expiresAt.isAfter(Instant.now())) return false;
                        row.leaseUntil=null;
                        row.message="地图可浏览，预览副本保存未完成；需要时可重新加载";
                        return true;
                    },source.revision()));
                } catch(Exception stale) { log.debug("Published preview is no longer current: {}",generation); }
            }
            if(!kept) {
                fail(source,generation,state,message);
                deleteDirectory(dir);
                try { requireStorage().deletePrefix(prefix,Duration.ofSeconds(10)); }
                catch(Exception ignored) { deferCleanup(dir); log.debug("Preview artifact cleanup deferred: {}",prefix); }
            }
        } finally { release(generation); preparation.release(); }
    }

    private void store(FileObjectStorage objects, String prefix, Path dir, String name, String contentType, long deadline) throws IOException {
        SpatialPreviewBudget.deadline(deadline);
        Path file=dir.resolve(name);
        try(InputStream in=Files.newInputStream(file)) {
            long milliseconds=TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime());
            if(milliseconds<1) throw new SpatialPreviewBudget.Exceeded("预览处理超过时间预算");
            objects.store(prefix+name,in,Files.size(file),contentType,Duration.ofMillis(milliseconds));
        }
        SpatialPreviewBudget.deadline(deadline);
    }

    private static boolean displayable(SpatialPreviewState row) {
        return "READY".equals(row.state)||"OVERVIEW_READY".equals(row.state);
    }
    private static boolean preparing(SpatialPreviewState row) {
        return "PREPARING".equals(row.state)||("OVERVIEW_READY".equals(row.state)&&row.leaseUntil!=null);
    }

    private void preparationMessage(SpatialPreviewSource source, UUID generation, String message) {
        locked(source.key(),row -> {
            if(!row.generation.equals(generation)||!"PREPARING".equals(row.state))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"预览代次已被撤销");
            row.message=message;
            return null;
        },source.revision());
    }

    private void requireRevision(SpatialPreviewSource source) {
        if(!source.revision().equals(source.currentRevision().get())) throw new ResponseStatusException(HttpStatus.CONFLICT,"来源已变化，旧预览已撤销，请重新加载");
    }
    private void fail(SpatialPreviewSource source, UUID generation, String state, String message) {
        locked(source.key(), row -> {
            if(row.generation.equals(generation)) { row.state=state; row.message=message; row.leaseUntil=null; }
            return null;
        },source.revision());
    }

    public Image image(SpatialPreviewSource source, UUID generation, String bbox, int width, int height, boolean overview) {
        return image(source,generation,bbox,width,height,overview,null,0);
    }
    public Image image(SpatialPreviewSource source, UUID generation, String bbox, int width, int height, boolean overview, UUID clientId, long sequence) {
        var cancelled=cancellation(source.key(),clientId,sequence);
        if(cancelled.getAsBoolean()) throw new ResponseStatusException(HttpStatus.CONFLICT,"视口已更新");
        var viewport=viewport(bbox,width,height);
        var state=current(source);
        if(!displayable(state) || (generation!=null&&!generation.equals(state.generation)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"预览数据已更新或尚未就绪，请重新获取状态");
        String cacheKey=state.generation+":"+(overview?"overview":bbox+":"+width+":"+height);
        Image cached;
        synchronized(images) { cached=images.get(cacheKey); }
        if(cached!=null) {
            requireRevision(source);
            var after=current(source);
            if(cancelled.getAsBoolean()) throw new ResponseStatusException(HttpStatus.CONFLICT,"视口已更新");
            if(!displayable(after)||!after.generation.equals(state.generation))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"来源已更新，旧预览已撤销");
            return cached;
        }
        retain(state.generation);
        try {
            Image result;
            var full=viewport(state.bounds,1200,900);
            boolean fullView=(viewport.maxX()-viewport.minX())>=.75*(full.maxX()-full.minX())
                    &&(viewport.maxY()-viewport.minY())>=.75*(full.maxY()-full.minY());
            Path dir=directory(state.generation);
            if(overview||fullView) result=overview(state,false);
            else if(!Files.exists(dir.resolve("complete"))) {
                if("READY".equals(state.state)) { materializeAsync(state); result=overview(state,"RESTORING"); }
                else result=overview(state,"SHARING");
            }
            else if(!rendering.tryAcquire()) result=overview(state,"BUSY");
            else {
                try {
                    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
                    if(!state.generation.equals(indexedGeneration)) {
                        index=null; indexedGeneration=null;
                        index=new SpatialPreviewFiles.Index(dir,state.featureCount,state.dataBytes);
                        indexedGeneration=state.generation;
                    }
                    var selection=index.select(viewport);
                    if(selection.exceeded()) result=overview(state,true);
                    else {
                        byte[] bytes=SpatialPreviewFiles.render(dir,viewport,selection.entries(),SpatialPreviewBudget.DETAIL_COORDINATES,deadline,cancelled);
                        result=new Image(bytes,bounds(viewport),state.generation,selection.entries().size(),false,false);
                    }
                } catch(SpatialPreviewBudget.Exceeded e) { result=overview(state,true); }
                finally { rendering.release(); }
            }
            requireRevision(source);
            var after=current(source);
            if(cancelled.getAsBoolean()) throw new CancellationException("视口已更新");
            if(!displayable(after)||!after.generation.equals(state.generation))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"来源已更新，旧预览已撤销");
            if(!result.degraded()) cache(cacheKey,result);
            return result;
        } catch(CancellationException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,"视口已更新"); }
        catch(IOException e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"预览副本读取失败，请重新加载",e); }
        finally { release(state.generation); }
    }

    private Image overview(SpatialPreviewState state, boolean degraded) throws IOException {
        return overview(state,degraded?"DENSE":null);
    }
    private Image overview(SpatialPreviewState state, String reason) throws IOException {
        Path dir=directory(state.generation); Files.createDirectories(dir);
        Path file=dir.resolve("overview.png");
        if(!Files.exists(file)) download(state,"overview.png",state.imageHash,4*1024*1024);
        return new Image(Files.readAllBytes(file),bounds(viewport(state.bounds,1200,900)),state.generation,state.featureCount,true,reason!=null,reason);
    }
    private void materializeAsync(SpatialPreviewState state) {
        if(!preparation.tryAcquire()) return;
        try {
            worker.execute(() -> {
                retain(state.generation);
                try {
                    cleanDisk(state.dataBytes+48*state.featureCount+16);
                    download(state,"geometry.wkb",state.dataHash,state.dataBytes+8);
                    download(state,"geometry.idx",state.indexHash,48*state.featureCount+8);
                    Files.writeString(directory(state.generation).resolve("complete"),state.revision);
                } catch(Exception e) { log.warn("Cannot materialize preview {}",state.generation,e); }
                finally { release(state.generation); preparation.release(); }
            });
        } catch(RejectedExecutionException e) { preparation.release(); }
    }
    private void download(SpatialPreviewState state, String name, String hash, long maximum) throws IOException {
        Path dir=directory(state.generation); Files.createDirectories(dir);
        Path temp=Files.createTempFile(dir,"download-",".part");
        var digest=SpatialPreviewFiles.digest();
        try {
            var content=requireStorage().open(prefix(state.generation)+name,Duration.ofSeconds(120));
            boolean complete=false;
            try(content) {
                try(OutputStream out=new java.security.DigestOutputStream(Files.newOutputStream(temp),digest)) {
                    if(content.contentLength()>maximum) throw new IOException("预览产物大小无效");
                    byte[] buffer=new byte[65536]; long size=0, deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120); int n;
                    while((n=content.inputStream().read(buffer))!=-1) {
                        SpatialPreviewBudget.deadline(deadline);
                        size+=n; if(size>maximum) throw new IOException("预览产物大小无效"); out.write(buffer,0,n);
                    }
                    complete=true;
                } finally { if(!complete) content.abort(); }
            }
            if(!HexFormat.of().formatHex(digest.digest()).equals(hash)) throw new IOException("预览产物校验失败");
            Files.move(temp,dir.resolve(name),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        } catch(IOException e) { throw e; }
        catch(Exception e) { throw new IOException(e); }
        finally { Files.deleteIfExists(temp); }
    }

    private SpatialPreviewState current(SpatialPreviewSource source) {
        return locked(source.key(), row -> { requireRevision(source); refresh(row,source); return row; },source.revision());
    }
    private void refresh(SpatialPreviewState row, SpatialPreviewSource source) {
        if(source.updating()) {
            if(!"UPDATING".equals(row.state)||!row.revision.equals(source.revision())) row.reset(source.revision(),"UPDATING","来源正在更新，完成后重新准备地图");
        } else if(!row.revision.equals(source.revision()) || "UPDATING".equals(row.state)) {
            row.reset(source.revision(),"NOT_PREPARED","来源已更新，正在准备新地图");
        } else if(displayable(row)&&row.expiresAt!=null&&row.expiresAt.isBefore(Instant.now())) {
            row.reset(source.revision(),"NOT_PREPARED","预览已过期，正在重新读取来源");
        } else if("FAILED".equals(row.state)&&failureCooldownElapsed(row)) {
            row.reset(source.revision(),"NOT_PREPARED","上次准备失败，正在重新准备地图");
        } else if("PREPARING".equals(row.state)&&(row.leaseUntil==null||row.leaseUntil.isBefore(Instant.now()))) {
            row.reset(source.revision(),"FAILED","准备任务中断，请重新加载");
        } else if("OVERVIEW_READY".equals(row.state)&&row.leaseUntil!=null&&row.leaseUntil.isBefore(Instant.now())) {
            row.leaseUntil=null; row.message="地图可浏览，预览副本保存未完成；需要时可重新加载";
        }
    }
    private boolean failureCooldownElapsed(SpatialPreviewState row) {
        // Failed preparations are persisted across visits and restarts. Do not let a transient
        // failure block this source forever, or let status polling immediately retry a fresh failure.
        Instant failedAt=row.getUpdatedAt()!=null?row.getUpdatedAt():row.observedAt;
        return failedAt!=null&&!failedAt.plusSeconds(600).isAfter(Instant.now());
    }
    private <T> T locked(String key, Function<SpatialPreviewState,T> action, String revision) {
        return tx.execute(status -> {
            jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
                try(var statement=connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                    statement.setString(1,"spatial-preview:"+key); statement.execute();
                } return null;
            });
            SpatialPreviewState row=repository.findBySourceKey(key).orElseGet(() -> repository.save(new SpatialPreviewState(key,revision)));
            return action.apply(row);
        });
    }
    private SpatialPreviewStatusResponse response(SpatialPreviewState row) {
        List<Double> box=row.bounds==null?List.of():bounds(viewport(row.bounds,1200,900));
        List<Double> geographic=box.isEmpty()||row.featureCount==0?List.of():List.of(lon(box.get(0)),lat(box.get(1)),lon(box.get(2)),lat(box.get(3)));
        return new SpatialPreviewStatusResponse(row.state,row.generation,row.message,row.featureCount,row.emptyCount,
                geographic,box,row.observedAt,row.expiresAt);
    }
    private void cache(String key, Image value) {
        synchronized(images) {
            Image previous=images.put(key,value); if(previous!=null) imageBytes-=previous.png().length;
            imageBytes+=value.png().length;
            while(imageBytes>128L*1024*1024) {
                var first=images.entrySet().iterator(); imageBytes-=first.next().getValue().png().length; first.remove();
            }
        }
    }
    private void cleanDisk(long reserve) throws IOException {
        long cleanupDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        List<Path> dirs;
        try(var list=Files.list(root)) { dirs=list.filter(Files::isDirectory).sorted(Comparator.comparingLong(p -> p.toFile().lastModified())).toList(); }
        // List directories first: a newly published generation must not be mistaken for an orphan
        // by a retained-generation snapshot taken before its local directory existed.
        Set<UUID> retained=new HashSet<>(Objects.requireNonNull(tx.execute(status -> repository.findRetainedGenerations(Instant.now()))));
        long total=0;
        for(Path dir:dirs) total+=directorySize(dir);
        for(Path dir:dirs) {
            UUID id; try { id=UUID.fromString(dir.getFileName().toString()); } catch(IllegalArgumentException e) { continue; }
            boolean old=dir.toFile().lastModified()<System.currentTimeMillis()-TimeUnit.HOURS.toMillis(24);
            boolean obsolete=!retained.contains(id);
            if(obsolete||old||total+reserve>SpatialPreviewBudget.DISK_BYTES) {
                synchronized(active) {
                    if(active.containsKey(id)) continue;
                    long size=directorySize(dir); deleteDirectory(dir); total-=size;
                    // Keep the marker until remote cleanup finishes; retain cannot race local deletion.
                    Files.createDirectories(dir);
                }
                if(obsolete||old) {
                    long remaining=TimeUnit.NANOSECONDS.toMillis(cleanupDeadline-System.nanoTime());
                    if(remaining<1) { deferCleanup(dir); continue; }
                    try {
                        requireStorage().deletePrefix(prefix(id),Duration.ofMillis(remaining));
                        synchronized(active) { if(!active.containsKey(id)) Files.deleteIfExists(dir); }
                    }
                    catch(Exception e) { deferCleanup(dir); log.debug("Preview cleanup deferred",e); }
                } else {
                    // Keep a tiny directory marker so disk eviction cannot orphan shared objects.
                    Files.createDirectories(dir);
                }
            }
        }
        if(total+reserve>SpatialPreviewBudget.DISK_BYTES||Files.getFileStore(root).getUsableSpace()<reserve+64*1024*1024)
            throw new SpatialPreviewBudget.Exceeded("预览缓存空间不足，请稍后重试或使用已发布的空间服务");
    }
    private void deferCleanup(Path dir) {
        try { Files.createDirectories(dir); dir.toFile().setLastModified(System.currentTimeMillis()-TimeUnit.HOURS.toMillis(25)); }
        catch(IOException e) { log.warn("Cannot retain preview cleanup marker: {}",dir,e); }
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=600_000)
    void cleanExpiredArtifacts() {
        if(!preparation.tryAcquire()) return;
        try { cleanDisk(0); }
        catch(IOException e) { log.warn("Cannot clean expired preview artifacts",e); }
        finally { preparation.release(); }
    }
    private long directorySize(Path dir) throws IOException {
        try(var files=Files.walk(dir)) { return files.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum(); }
    }
    private void deleteDirectory(Path dir) {
        if(!dir.normalize().getParent().equals(root)) throw new IllegalArgumentException("非法预览缓存目录");
        try(var files=Files.walk(dir)) { for(Path p:files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        catch(IOException e) { log.debug("Local preview cleanup deferred: {}",dir,e); }
    }
    private FileObjectStorage requireStorage() {
        var result=storage.getIfAvailable();
        if(result==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"尚未配置文件对象存储");
        return result;
    }
    private Path directory(UUID generation) { return root.resolve(generation.toString()); }
    private static String prefix(UUID generation) { return "spatial-preview/v1/"+generation+"/"; }
    public static SpatialPreviewViewport viewport(String bbox, int width, int height) {
        if(width<256||width>1600||height<256||height>1200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"图片宽度须为 256–1600，高度须为 256–1200");
        try {
            String[] parts=bbox.split(",",-1); if(parts.length!=4) throw new IllegalArgumentException();
            double[] values=Arrays.stream(parts).mapToDouble(Double::parseDouble).toArray();
            for(double v:values) if(!Double.isFinite(v)||Math.abs(v)>SpatialPreviewFiles.WORLD+1) throw new IllegalArgumentException();
            return new SpatialPreviewViewport(values[0],values[1],values[2],values[3],width,height);
        } catch(Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"bbox 须为有效的 EPSG:3857 范围 minX,minY,maxX,maxY"); }
    }
    private static List<Double> bounds(SpatialPreviewViewport v) { return List.of(v.minX(),v.minY(),v.maxX(),v.maxY()); }
    private static String bbox(SpatialPreviewViewport v) { return v.minX()+","+v.minY()+","+v.maxX()+","+v.maxY(); }
    private static double lon(double x) { return x/6378137*180/Math.PI; }
    private static double lat(double y) { return (2*Math.atan(Math.exp(y/6378137))-Math.PI/2)*180/Math.PI; }
    @PreDestroy void close() { worker.shutdownNow(); }
    public record Image(byte[] png,List<Double> bounds,UUID generation,long featureCount,boolean overview,boolean degraded,String fallbackReason) {
        public Image(byte[] png,List<Double> bounds,UUID generation,long featureCount,boolean overview,boolean degraded) {
            this(png,bounds,generation,featureCount,overview,degraded,degraded?"DENSE":null);
        }
        public ResponseEntity<byte[]> response() {
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                    .cacheControl(CacheControl.noCache().cachePrivate().mustRevalidate())
                    .header("X-Spatial-Generation",generation.toString())
                    .header("X-Spatial-Bounds",bounds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")))
                    .header("X-Spatial-Overview",Boolean.toString(overview))
                    .header("X-Spatial-Degraded",Boolean.toString(degraded))
                    .header("X-Spatial-Fallback",fallbackReason==null?"":fallbackReason)
                    .header("X-Spatial-Feature-Count",Long.toString(featureCount))
                    .header("X-Spatial-Skipped-Count","0").header("X-Spatial-Truncated","false").body(png);
        }
    }
}
