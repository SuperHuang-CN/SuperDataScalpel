package cn.superhuang.data.scalpel.business.panorama;

import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import cn.superhuang.data.scalpel.business.panorama.domain.*;
import cn.superhuang.data.scalpel.business.panorama.repository.*;
import cn.superhuang.data.scalpel.business.panorama.service.*;
import cn.superhuang.data.scalpel.business.panorama.web.request.UpdatePanoramaRequest;
import cn.superhuang.data.scalpel.business.panorama.web.response.PanoramaResponse;
import cn.superhuang.data.scalpel.search.SearchEngine;
import cn.superhuang.data.scalpel.web.error.CodedProblemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Single-Admin command/worker lifecycle, with in-memory object I/O and transaction/repository boundaries mocked. */
class PanoramaLifecycleTest {
    private final PanoramaRepository panoramas = mock(PanoramaRepository.class);
    private final PanoramaContentRepository contents = mock(PanoramaContentRepository.class);
    private final Map<UUID, Panorama> resources = new LinkedHashMap<>();
    private final Map<UUID, PanoramaContent> bundles = new LinkedHashMap<>();
    private final MemoryStorage storage = new MemoryStorage();
    private PanoramaService service;

    @BeforeEach void prepare() {
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var beans = new StaticListableBeanFactory(); beans.addBean("storage", storage);
        service = new PanoramaService(panoramas, contents, mock(DirectoryService.class), new SearchEngine(), beans.getBeanProvider(FileObjectStorage.class), new PanoramaImageProcessor(), JsonMapper.builder().build(), transactions);
        when(panoramas.saveAndFlush(any())).thenAnswer(invocation -> {
            Panorama p = invocation.getArgument(0); identify(p); resources.put(p.getId(), p); return p;
        });
        when(contents.saveAndFlush(any())).thenAnswer(invocation -> {
            PanoramaContent c = invocation.getArgument(0); identify(c); bundles.put(c.getId(), c); return c;
        });
        when(panoramas.findById(any())).thenAnswer(invocation -> Optional.ofNullable(resources.get(invocation.getArgument(0))));
        when(panoramas.findLockedById(any())).thenAnswer(invocation -> Optional.ofNullable(resources.get(invocation.getArgument(0))));
        when(panoramas.findByCreationRequestId(any())).thenAnswer(invocation -> resources.values().stream().filter(p -> Objects.equals(p.getCreationRequestId(), invocation.getArgument(0))).findFirst());
        when(contents.findById(any())).thenAnswer(invocation -> Optional.ofNullable(bundles.get(invocation.getArgument(0))));
        when(contents.findByClientRequestId(any())).thenAnswer(invocation -> bundles.values().stream().filter(c -> Objects.equals(c.getClientRequestId(), invocation.getArgument(0))).findFirst());
        when(panoramas.findFirstByProcessingStatusOrderByCreatedAtAsc(any())).thenAnswer(invocation -> resources.values().stream().filter(p -> p.getProcessingStatus() == invocation.getArgument(0)).findFirst());
        when(panoramas.findAllByProcessingStatus(any())).thenAnswer(invocation -> resources.values().stream().filter(p -> p.getProcessingStatus() == invocation.getArgument(0)).toList());
        when(panoramas.existsByCurrentContentIdOrCandidateContentId(any(), any())).thenAnswer(invocation -> resources.values().stream().anyMatch(p -> Objects.equals(p.getCurrentContentId(), invocation.getArgument(0)) || Objects.equals(p.getCandidateContentId(), invocation.getArgument(1))));
        when(contents.findTop20ByCleanupPendingTrueAndCleanupAfterBeforeOrderByCleanupAfterAsc(any())).thenAnswer(invocation -> bundles.values().stream().filter(c -> c.getCleanupPending() && c.getCleanupAfter().isBefore(invocation.getArgument(0))).toList());
        doAnswer(invocation -> { bundles.remove(invocation.getArgument(0)); return null; }).when(contents).deleteById(any());
        doAnswer(invocation -> { Panorama p = invocation.getArgument(0); resources.remove(p.getId()); return null; }).when(panoramas).delete(any(Panorama.class));
    }

    @Test void successfulReplacementPreservesManualValuesAndReclaimsOldFiles() throws Exception {
        UUID requestId = UUID.randomUUID(); PanoramaResponse p = service.upload(jpeg(), requestId, null, null, null); service.processNext();
        UUID id = p.id(); p = service.get(id); UUID old = p.currentContent().id();
        service.update(id, new UpdatePanoramaRequest("人工名称", "说明", null, 1L, PanoramaValueMode.MANUAL, LocalDateTime.of(2025, 1, 1, 10, 0), "+08:00", PanoramaValueMode.MANUAL, -20.0, 120.0));
        service.upload(jpeg(), UUID.randomUUID(), null, id, 1L); service.processNext(); p = service.get(id);
        assertThat(p.contentVersion()).isEqualTo(2);
        assertThat(p.captureTime()).isEqualTo(LocalDateTime.of(2025, 1, 1, 10, 0));
        assertThat(p.latitude()).isEqualTo(-20.0);
        assertThat(p.candidateContent()).isNull();
        assertThat(bundles.get(old).getCleanupPending()).isTrue();
        bundles.get(old).setCleanupAfter(Instant.EPOCH); service.cleanup();
        assertThat(bundles).doesNotContainKey(old);
        assertThat(service.upload(jpeg(), requestId, null, null, null).id()).isEqualTo(id);
        assertThat(resources).hasSize(1);
        assertThatThrownBy(() -> service.open(id, 1L, "preview")).isInstanceOfSatisfying(CodedProblemException.class, e -> assertThat(e.code()).isEqualTo("PANORAMA_CONTENT_CHANGED"));
    }

    @Test void failedReplacementKeepsCurrentAndCanRetryAfterRestart() throws Exception {
        PanoramaResponse p = service.upload(jpeg(), UUID.randomUUID(), null, null, null); service.processNext();
        UUID id = p.id(), old = service.get(id).currentContent().id();
        service.upload(jpeg(), UUID.randomUUID(), null, id, 1L); storage.failPreview = true; service.processNext();
        p = service.get(id); assertThat(p.processingStatus()).isEqualTo(PanoramaProcessingStatus.FAILED);
        assertThat(p.currentContent().id()).isEqualTo(old); assertThat(p.contentVersion()).isEqualTo(1);
        try (var content = service.open(id, 1L, "original").content()) { assertThat(content.inputStream().read()).isEqualTo(255); }
        storage.failPreview = false; service.retry(id, p.candidateContent().id());
        resources.get(id).setProcessingStatus(PanoramaProcessingStatus.PROCESSING); service.recover(); service.processNext();
        assertThat(service.get(id).contentVersion()).isEqualTo(2);
    }

    @Test void discardAndCleanupFailureKeepReferencesUntilDeletionSucceeds() throws Exception {
        PanoramaResponse p = service.upload(jpeg(), UUID.randomUUID(), null, null, null); service.processNext(); UUID id = p.id();
        p = service.upload(jpeg(), UUID.randomUUID(), null, id, 1L); UUID candidate = p.candidateContent().id();
        storage.failPreview = true; service.processNext(); service.discard(id, candidate);
        PanoramaContent retired = bundles.get(candidate); retired.setCleanupAfter(Instant.EPOCH); storage.failDelete = true; service.cleanup();
        assertThat(bundles).containsKey(candidate); assertThat(retired.getCleanupPending()).isTrue();
        storage.failDelete = false; retired.setCleanupAfter(Instant.EPOCH); service.cleanup(); assertThat(bundles).doesNotContainKey(candidate);
        assertThat(service.get(id).currentContent()).isNotNull();
    }

    @Test void queuedDeletionAndConflictingCommandsCannotActivateObsoleteContent() throws Exception {
        PanoramaResponse p = service.upload(jpeg(), UUID.randomUUID(), null, null, null); UUID id = p.id();
        assertThatThrownBy(() -> service.upload(jpeg(), UUID.randomUUID(), null, id, 0L)).isInstanceOf(CodedProblemException.class);
        resources.get(id).setProcessingStatus(PanoramaProcessingStatus.PROCESSING);
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(CodedProblemException.class);
        resources.get(id).setProcessingStatus(PanoramaProcessingStatus.QUEUED); service.delete(id); service.processNext();
        assertThat(resources).isEmpty(); assertThat(bundles.values()).allMatch(PanoramaContent::getCleanupPending);
    }

    @Test void oversizedUploadIsRejectedBeforeSavingAnyObjects() {
        var file = new MockMultipartFile("file", "oversize.jpg", "image/jpeg", new byte[]{1}) {
            @Override public long getSize() { return PanoramaImageProcessor.MAX_BYTES + 1; }
        };
        assertThatThrownBy(() -> service.upload(file, UUID.randomUUID(), null, null, null))
                .isInstanceOfSatisfying(CodedProblemException.class, e -> assertThat(e.code()).isEqualTo("PANORAMA_FILE_TOO_LARGE"));
        assertThat(storage.objects).isEmpty(); assertThat(resources).isEmpty(); assertThat(bundles).isEmpty();
    }

    private static void identify(Object entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID()); ReflectionTestUtils.setField(entity, "createdAt", Instant.now()); ReflectionTestUtils.setField(entity, "updatedAt", Instant.now());
    }
    private static MockMultipartFile jpeg() throws Exception {
        var image = new BufferedImage(1000, 500, BufferedImage.TYPE_INT_RGB); var bytes = new ByteArrayOutputStream();
        try { ImageIO.write(image, "jpeg", bytes); } finally { image.flush(); }
        return new MockMultipartFile("file", "sphere.jpg", "image/jpeg", bytes.toByteArray());
    }
    private static class MemoryStorage implements FileObjectStorage {
        final Map<String, byte[]> objects = new HashMap<>(); boolean failPreview; boolean failDelete;
        public StoredFileObject store(String key, InputStream input, long length, String type) {
            if (failPreview && key.endsWith("preview.jpg")) throw new IllegalStateException("storage failure");
            try { objects.put(key, input.readAllBytes()); return new StoredFileObject("test"); } catch (IOException e) { throw new UncheckedIOException(e); }
        }
        public FileObjectContent open(String key) { byte[] bytes = Objects.requireNonNull(objects.get(key)); return new FileObjectContent(new ByteArrayInputStream(bytes), bytes.length, "image/jpeg"); }
        public void delete(String key) { objects.remove(key); }
        public void deletePrefix(String prefix) { if (failDelete) throw new IllegalStateException("storage failure"); objects.keySet().removeIf(key -> key.startsWith(prefix)); }
    }
}
