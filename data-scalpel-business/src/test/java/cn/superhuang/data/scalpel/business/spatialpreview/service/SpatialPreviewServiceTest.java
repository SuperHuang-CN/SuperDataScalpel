package cn.superhuang.data.scalpel.business.spatialpreview.service;

import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import cn.superhuang.data.scalpel.business.spatialpreview.domain.SpatialPreviewState;
import cn.superhuang.data.scalpel.business.spatialpreview.repository.SpatialPreviewRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKBWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpatialPreviewServiceTest {
    @TempDir Path directory;
    SpatialPreviewService service;
    final Map<String,SpatialPreviewState> states=new ConcurrentHashMap<>();
    final Map<String,byte[]> objects=new ConcurrentHashMap<>();
    final List<String> opened=new CopyOnWriteArrayList<>();
    final List<String> deletedPrefixes=new CopyOnWriteArrayList<>();
    final AtomicInteger cleanupQueries=new AtomicInteger();
    final AtomicBoolean failCleanup=new AtomicBoolean();
    CountDownLatch sharing,continueSharing;
    boolean failSharing;
    SpatialPreviewRepository repo;
    PlatformTransactionManager manager;
    ObjectProvider<FileObjectStorage> provider;
    @BeforeEach @SuppressWarnings("unchecked") void setUp() {
        repo=mock(SpatialPreviewRepository.class);
        when(repo.findBySourceKey(anyString())).thenAnswer(i -> Optional.ofNullable(states.get(i.getArgument(0))));
        when(repo.save(any())).thenAnswer(i -> { SpatialPreviewState s=i.getArgument(0);states.put(s.sourceKey,s);return s; });
        when(repo.findRetainedGenerations(any())).thenAnswer(i -> {
            Instant now=i.getArgument(0);cleanupQueries.incrementAndGet();
            return states.values().stream().filter(s ->
                    (Set.of("READY","OVERVIEW_READY").contains(s.state)&&s.expiresAt!=null&&s.expiresAt.isAfter(now))
                    ||("PREPARING".equals(s.state)&&s.leaseUntil!=null&&s.leaseUntil.isAfter(now)))
                    .map(s -> s.generation).toList();
        });
        manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        provider=mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(new FileObjectStorage() {
            public StoredFileObject store(String key,InputStream in,long size,String type) {
                if(key.endsWith("geometry.wkb")&&sharing!=null) {
                    sharing.countDown();
                    try { if(!continueSharing.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("test timed out"); }
                    catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); }
                    if(failSharing) throw new IllegalStateException("object storage unavailable");
                }
                try { objects.put(key,in.readAllBytes());return new StoredFileObject("test"); } catch(IOException e) { throw new UncheckedIOException(e); }
            }
            public FileObjectContent open(String key) { opened.add(key);byte[] b=objects.get(key);if(b==null) throw new IllegalStateException("not found");return new FileObjectContent(new ByteArrayInputStream(b),b.length,"application/octet-stream"); }
            public void delete(String key) { objects.remove(key); }
            public void deletePrefix(String prefix) {
                deletedPrefixes.add(prefix);
                if(failCleanup.get()) throw new IllegalStateException("object storage unavailable during cleanup");
                objects.keySet().removeIf(k -> k.startsWith(prefix));
            }
        });
        service=new SpatialPreviewService(repo,mock(JdbcTemplate.class),manager,provider,directory.toString());
    }
    @AfterEach void close() { service.close(); }
    private byte[] point() { return new WKBWriter().write(new GeometryFactory().createPoint(new Coordinate(1,1))); }
    private String await(SpatialPreviewSource source) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);String state;
        do { state=service.status(source).state();if(!Set.of("PREPARING","OVERVIEW_READY").contains(state)) return state;Thread.sleep(20); } while(System.nanoTime()<deadline);
        fail("Preparation did not finish");return null;
    }
    private void cleanExpiredArtifacts() throws Exception {
        int before=cleanupQueries.get();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        do {
            service.cleanExpiredArtifacts();
            if(cleanupQueries.get()>before) return;
            Thread.sleep(10);
        } while(System.nanoTime()<deadline);
        fail("Preparation slot did not become available for cleanup");
    }
    private String prefix(UUID generation) { return "spatial-preview/v1/"+generation+"/"; }

    @Test void cleanupRetainsCurrentGenerationsForDifferentSources() throws Exception {
        var first=new SpatialPreviewSource("first","v1",1L,false,sink -> sink.accept(point()),() -> "v1");
        var second=new SpatialPreviewSource("second","v1",1L,false,sink -> sink.accept(point()),() -> "v1");
        UUID firstGeneration=service.prepare(first,false).generation();assertEquals("READY",await(first));
        cleanExpiredArtifacts();
        UUID secondGeneration=service.prepare(second,false).generation();assertEquals("READY",await(second));
        cleanExpiredArtifacts();
        for(UUID generation:List.of(firstGeneration,secondGeneration)) {
            assertTrue(Files.exists(directory.resolve(generation.toString()).resolve("complete")));
            assertTrue(objects.keySet().stream().anyMatch(k -> k.startsWith(prefix(generation))));
            assertFalse(deletedPrefixes.contains(prefix(generation)));
        }
        assertTrue(service.image(first,firstGeneration,"0,0,2,2",256,256,false).png().length>0);
        assertTrue(service.image(second,secondGeneration,"0,0,2,2",256,256,false).png().length>0);
    }

    @Test void forceRefreshReclaimsSupersededGenerationWithoutWaitingForTtl() throws Exception {
        var source=new SpatialPreviewSource("source","v1",1L,false,sink -> sink.accept(point()),() -> "v1");
        UUID old=service.prepare(source,false).generation();assertEquals("READY",await(source));
        assertTrue(states.get("source").expiresAt.isAfter(Instant.now()));
        cleanExpiredArtifacts();
        UUID current=service.prepare(source,true).generation();assertEquals("READY",await(source));
        cleanExpiredArtifacts();
        assertNotEquals(old,current);
        assertFalse(Files.exists(directory.resolve(old.toString())));
        assertTrue(objects.keySet().stream().noneMatch(k -> k.startsWith(prefix(old))));
        assertTrue(deletedPrefixes.contains(prefix(old)));
        assertTrue(Files.exists(directory.resolve(current.toString()).resolve("complete")));
        assertTrue(objects.keySet().stream().anyMatch(k -> k.startsWith(prefix(current))));
    }

    @Test void cleanupReclaimsExpiredArtifactsAtTheirFreshnessDeadline() throws Exception {
        var source=new SpatialPreviewSource("source","v1",1L,false,sink -> sink.accept(point()),() -> "v1");
        UUID generation=service.prepare(source,false).generation();assertEquals("READY",await(source));
        states.get("source").expiresAt=Instant.now().minusSeconds(1);
        cleanExpiredArtifacts();
        assertFalse(Files.exists(directory.resolve(generation.toString())));
        assertTrue(objects.keySet().stream().noneMatch(k -> k.startsWith(prefix(generation))));
        assertTrue(deletedPrefixes.contains(prefix(generation)));
    }

    @Test void failedRemoteCleanupKeepsMarkerAndNextCleanupRetriesIt() throws Exception {
        var source=new SpatialPreviewSource("source","v1",1L,false,sink -> sink.accept(point()),() -> "v1");
        UUID generation=service.prepare(source,false).generation();assertEquals("READY",await(source));
        states.get("source").expiresAt=Instant.now().minusSeconds(1);failCleanup.set(true);
        cleanExpiredArtifacts();
        Path marker=directory.resolve(generation.toString());
        assertTrue(Files.isDirectory(marker));
        try(var files=Files.list(marker)) { assertEquals(0,files.count()); }
        assertTrue(objects.keySet().stream().anyMatch(k -> k.startsWith(prefix(generation))));
        assertEquals(1,Collections.frequency(deletedPrefixes,prefix(generation)));
        failCleanup.set(false);cleanExpiredArtifacts();
        assertFalse(Files.exists(marker));
        assertTrue(objects.keySet().stream().noneMatch(k -> k.startsWith(prefix(generation))));
        assertEquals(2,Collections.frequency(deletedPrefixes,prefix(generation)));
    }
    @Test void repeatedImagesNeverRereadSourceAndRefreshRevokesTheOldGeneration() throws Exception {
        AtomicInteger reads=new AtomicInteger();AtomicReference<String> revision=new AtomicReference<>("v1");
        var source=new SpatialPreviewSource("source","v1",1L,false,sink -> { reads.incrementAndGet();sink.accept(point()); },revision::get);
        assertEquals("NOT_PREPARED",service.status(source).state());assertEquals(0,reads.get());
        var generation=service.prepare(source,false).generation();assertEquals("READY",await(source));
        for(int i=0;i<3;i++) assertTrue(service.image(source,generation,"0,0,2,2",256,256,false).png().length>0);
        assertEquals(1,reads.get());
        var refreshed=service.prepare(source,true);assertNotEquals(generation,refreshed.generation());assertEquals("READY",await(source));
        assertThrows(ResponseStatusException.class,() -> service.image(source,generation,"0,0,2,2",256,256,false));
        assertEquals(2,reads.get());
    }
    @Test void simultaneousPreparationOfOneSourceIsMerged() throws Exception {
        CountDownLatch began=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicInteger reads=new AtomicInteger();
        var source=new SpatialPreviewSource("source","v1",1L,false,sink -> { reads.incrementAndGet();began.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));sink.accept(point()); },() -> "v1");
        var first=service.prepare(source,false);assertTrue(began.await(5,TimeUnit.SECONDS));
        assertEquals(first.generation(),service.prepare(source,true).generation());
        release.countDown();assertEquals("READY",await(source));assertEquals(1,reads.get());
    }
    @Test void knownChangesClearOldDataEvenWhenTheNewPreparationFails() throws Exception {
        var old=new SpatialPreviewSource("source","v1",1L,false,sink -> sink.accept(point()),() -> "v1");
        var first=service.prepare(old,false);assertEquals("READY",await(old));
        var next=new SpatialPreviewSource("source","v2",1L,false,sink -> { throw new IOException("source offline"); },() -> "v2");
        assertEquals("NOT_PREPARED",service.status(next).state());
        service.prepare(next,false);assertEquals("FAILED",await(next));
        assertThrows(ResponseStatusException.class,() -> service.image(next,first.generation(),"0,0,2,2",256,256,false));
    }
    @Test void knownOversizeIsRejectedBeforeReadingAndUpdatingSourcesStayBlocked() {
        AtomicInteger reads=new AtomicInteger();
        var large=new SpatialPreviewSource("large","v1",1_000_001L,false,sink -> reads.incrementAndGet(),() -> "v1");
        assertEquals("LIMIT_EXCEEDED",service.prepare(large,false).state());assertEquals(0,reads.get());
        var writing=new SpatialPreviewSource("writing","v1",1L,true,sink -> reads.incrementAndGet(),() -> "v1");
        assertEquals("UPDATING",service.prepare(writing,true).state());assertEquals(0,reads.get());
    }
    @Test void historicalFailureBecomesUnpreparedAndRecoversWithoutForcedReload() throws Exception {
        AtomicInteger reads=new AtomicInteger();
        var source=new SpatialPreviewSource("historical-failure","v1",1L,false,sink -> {
            reads.incrementAndGet();sink.accept(point());
        },() -> "v1");
        var row=new SpatialPreviewState(source.key(),source.revision());
        row.reset("v1","FAILED","地图准备失败");
        org.springframework.test.util.ReflectionTestUtils.setField(row,"updatedAt",Instant.now().minusSeconds(601));
        states.put(source.key(),row);
        UUID failedGeneration=row.generation;

        assertEquals("NOT_PREPARED",service.status(source).state());
        assertNotEquals(failedGeneration,row.generation);
        assertEquals(0,reads.get()); // Status only releases the stale failure; it never reads the source.
        service.prepare(source,false);
        assertEquals("READY",await(source));
        assertEquals(1,reads.get());
        assertEquals(1,service.status(source).featureCount());
    }
    @Test void freshFailuresAndExplicitLimitsDoNotAutomaticallyRetry() {
        AtomicInteger reads=new AtomicInteger();
        var source=new SpatialPreviewSource("failure","v1",1L,false,sink -> reads.incrementAndGet(),() -> "v1");
        var row=new SpatialPreviewState(source.key(),source.revision());
        row.reset("v1","FAILED","地图准备失败");
        row.observedAt=Instant.now().minusSeconds(900);
        org.springframework.test.util.ReflectionTestUtils.setField(row,"updatedAt",Instant.now());
        states.put(source.key(),row);
        UUID generation=row.generation;
        for(int i=0;i<3;i++) {
            assertEquals("FAILED",service.status(source).state());
            assertEquals(generation,service.prepare(source,false).generation());
        }
        for(String state:List.of("LIMIT_EXCEEDED","UNSUPPORTED")) {
            row.reset("v1",state,"明确限制");
            org.springframework.test.util.ReflectionTestUtils.setField(row,"updatedAt",Instant.now().minusSeconds(86400));
            assertEquals(state,service.status(source).state());
            assertEquals(state,service.prepare(source,false).state());
        }
        assertEquals(0,reads.get());
    }
    @Test void expiredDataAndOutOfOrderViewportsCannotBeReused() throws Exception {
        var source=new SpatialPreviewSource("source","v1",1L,false,sink -> sink.accept(point()),() -> "v1");
        var generation=service.prepare(source,false).generation();assertEquals("READY",await(source));
        UUID client=UUID.randomUUID();
        service.image(source,generation,"0,0,2,2",256,256,false,client,2);
        var stale=assertThrows(ResponseStatusException.class,() -> service.image(source,generation,"0,0,2,2",256,256,false,client,1));
        assertEquals(409,stale.getStatusCode().value());
        states.get("source").expiresAt=java.time.Instant.now().minusSeconds(1);
        assertEquals("NOT_PREPARED",service.status(source).state());
        assertThrows(ResponseStatusException.class,() -> service.image(source,generation,"0,0,2,2",256,256,false));
    }
    @Test void evictedLocalCopyIsRestoredFromSharedArtifactsWithoutRereadingSource() throws Exception {
        AtomicInteger reads=new AtomicInteger();
        var source=new SpatialPreviewSource("source","v1",1L,false,sink -> { reads.incrementAndGet();sink.accept(point()); },() -> "v1");
        var generation=service.prepare(source,false).generation();assertEquals("READY",await(source));
        Path local=directory.resolve(generation.toString());
        for(String name:new String[]{"complete","geometry.wkb","geometry.idx"}) java.nio.file.Files.delete(local.resolve(name));
        assertTrue(service.image(source,generation,"0,0,2,2",256,256,false).degraded());
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!java.nio.file.Files.exists(local.resolve("complete"))&&System.nanoTime()<deadline) Thread.sleep(20);
        assertTrue(java.nio.file.Files.exists(local.resolve("complete")));
        assertFalse(service.image(source,generation,"0,0,2,2",256,256,false).degraded());
        assertEquals(1,reads.get());
    }

    @Test void completeMapIsUsableWhileSharingAndRemoteNodesNeverDownloadPartialGeometry() throws Exception {
        sharing=new CountDownLatch(1);continueSharing=new CountDownLatch(1);
        AtomicInteger reads=new AtomicInteger();
        var source=new SpatialPreviewSource("source","v1",1L,false,sink -> { reads.incrementAndGet();sink.accept(point()); },() -> "v1");
        var generation=service.prepare(source,false).generation();assertTrue(sharing.await(5,TimeUnit.SECONDS));
        var visible=service.status(source);
        assertEquals("OVERVIEW_READY",visible.state());assertEquals(1,visible.featureCount());
        assertEquals(generation,service.prepare(source,true).generation());
        assertFalse(service.image(source,generation,"0,0,2,2",256,256,false).degraded());
        var remote=new SpatialPreviewService(repo,mock(JdbcTemplate.class),manager,provider,directory.resolve("remote").toString());
        try {
            var first=remote.image(source,generation,"0,0,2,2",256,256,false);
            assertEquals("SHARING",first.fallbackReason());assertEquals(1,first.featureCount());
            assertTrue(opened.stream().allMatch(k -> k.endsWith("overview.png")));
            continueSharing.countDown();assertEquals("READY",await(source));
            assertEquals(generation,service.status(source).generation());
            assertEquals(visible.expiresAt(),service.status(source).expiresAt());
            assertEquals("RESTORING",remote.image(source,generation,"0,0,2,2",256,256,false).fallbackReason());
            Path marker=directory.resolve("remote").resolve(generation.toString()).resolve("complete");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(!java.nio.file.Files.exists(marker)&&System.nanoTime()<deadline) Thread.sleep(20);
            assertFalse(remote.image(source,generation,"0,0,2,2",256,256,false).degraded());
        } finally { remote.close(); }
        assertEquals(1,reads.get());
    }

    @Test void sourceFailureAfterRowsNeverPublishesAPartialMap() throws Exception {
        var source=new SpatialPreviewSource("source","v1",null,false,sink -> { sink.accept(point());throw new IOException("broken stream"); },() -> "v1");
        var generation=service.prepare(source,false).generation();assertEquals("FAILED",await(source));
        assertEquals(0,service.status(source).featureCount());assertTrue(objects.isEmpty());
        assertThrows(ResponseStatusException.class,() -> service.image(source,generation,"0,0,2,2",256,256,true));
    }

    @Test void sharingFailurePreservesCompleteMapAndDoesNotExtendFreshness() throws Exception {
        sharing=new CountDownLatch(1);continueSharing=new CountDownLatch(1);failSharing=true;
        var source=new SpatialPreviewSource("source","v1",1L,false,sink -> sink.accept(point()),() -> "v1");
        var generation=service.prepare(source,false).generation();assertTrue(sharing.await(5,TimeUnit.SECONDS));
        var expires=service.status(source).expiresAt();continueSharing.countDown();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(states.get("source").leaseUntil!=null&&System.nanoTime()<deadline) Thread.sleep(20);
        assertNull(states.get("source").leaseUntil);
        assertEquals("OVERVIEW_READY",service.status(source).state());
        assertEquals(expires,service.status(source).expiresAt());
        assertFalse(service.image(source,generation,"0,0,2,2",256,256,false).degraded());
        states.get("source").expiresAt=java.time.Instant.now().minusSeconds(1);
        assertEquals("NOT_PREPARED",service.status(source).state());
    }

    @Test void changedSourceDuringSharingCannotResurrectAnOldGeneration() throws Exception {
        sharing=new CountDownLatch(1);continueSharing=new CountDownLatch(1);
        AtomicReference<String> revision=new AtomicReference<>("v1");
        var old=new SpatialPreviewSource("source","v1",1L,false,sink -> sink.accept(point()),revision::get);
        var generation=service.prepare(old,false).generation();assertTrue(sharing.await(5,TimeUnit.SECONDS));
        revision.set("v2");
        var next=new SpatialPreviewSource("source","v2",1L,false,sink -> sink.accept(point()),revision::get);
        assertEquals("NOT_PREPARED",service.status(next).state());
        continueSharing.countDown();Thread.sleep(100);
        assertEquals("NOT_PREPARED",service.status(next).state());
        assertNotEquals(generation,service.status(next).generation());
        assertThrows(ResponseStatusException.class,() -> service.image(next,generation,"0,0,2,2",256,256,true));
        UUID currentGeneration=service.status(next).generation();
        assertThrows(ResponseStatusException.class,() -> service.status(old));
        assertEquals(currentGeneration,service.status(next).generation());
    }

    @Test void failedRemoteReadAbortsBeforeCloseAndRemovesPartialFile() throws Exception {
        var source=new SpatialPreviewSource("source","v1",1L,false,sink -> sink.accept(point()),() -> "v1");
        var generation=service.prepare(source,false).generation();assertEquals("READY",await(source));
        java.nio.file.Files.delete(directory.resolve(generation.toString()).resolve("overview.png"));
        List<String> events=new CopyOnWriteArrayList<>();
        var broken=mock(FileObjectStorage.class);
        when(broken.open(anyString(),any())).thenReturn(new FileObjectStorage.FileObjectContent(
                new ByteArrayInputStream(new byte[0]) { @Override public void close() { events.add("close"); } },
                5*1024*1024,"image/png",() -> events.add("abort")));
        when(provider.getIfAvailable()).thenReturn(broken);
        assertThrows(ResponseStatusException.class,() -> service.image(source,generation,"0,0,2,2",256,256,true));
        assertEquals(List.of("abort","close"),events);
        try(var files=java.nio.file.Files.list(directory.resolve(generation.toString()))) {
            assertFalse(files.anyMatch(p -> p.toString().endsWith(".part")));
        }
    }
}
